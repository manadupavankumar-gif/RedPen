import "./config.js";

(() => {
  'use strict';

  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const show = (el, on = true) => el.classList.toggle('hidden', !on);
  const API_BASE = () => window.API_BASE || '';

  const PEN = 'M96 20 C150 12 186 44 184 88 C182 134 138 168 92 164 C44 160 10 126 14 82 C18 38 62 14 112 26';
  const LEVELS = [
    { key: 'fresher', label: 'Fresher', hint: 'Recent graduate, 0-1 yr' },
    { key: 'intern', label: 'Internship', hint: 'Current student' },
    { key: 'junior', label: '1 to 3 years', hint: 'Junior' },
    { key: 'mid', label: '3 to 6 years', hint: 'Mid-level' },
    { key: 'senior', label: '6+ years', hint: 'Senior' },
    { key: 'switcher', label: 'Career switch', hint: 'Moving to a new field' }
  ];
  const ROLES = [
    'Software Developer', 'Java Developer', 'Full Stack Developer', 'Frontend Developer', 'Backend Developer',
    'Python Developer', 'Mobile App Developer', 'DevOps Engineer', 'Cloud Engineer', 'QA / Test Engineer',
    'Data Analyst', 'Data Scientist', 'Machine Learning Engineer', 'Data Engineer', 'Cybersecurity Analyst',
    'UI/UX Designer', 'Graphic Designer', 'Product Manager', 'Project Manager', 'Business Analyst',
    'Digital Marketing Executive', 'Content Writer', 'SEO Specialist', 'Sales Executive', 'Customer Support Executive',
    'HR Executive', 'Recruiter', 'Accountant', 'Financial Analyst', 'Operations Executive',
    'Mechanical Engineer', 'Civil Engineer', 'Electrical Engineer', 'Teacher', 'Nurse'
  ];
  const SECTION_LABELS = {
    keywordMatch: 'Keyword match',
    formatting: 'Formatting and ATS',
    impact: 'Impact and results',
    skills: 'Skills relevance',
    experience: 'Experience fit'
  };
  const LOADING_STEPS = ['Reading your resume', 'Matching it to your target', 'Scoring each section', 'Writing your fixes'];

  let token = localStorage.getItem('rp_token');
  localStorage.removeItem('rp_user'); // left over from the old sign-in version
  let inputMode = 'file';
  let dropMain, dropA, dropB;
  let timers = [];

  function parse(s) { try { return JSON.parse(s); } catch { return null; } }
  function esc(s) {
    return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }
  const tone = n => (n >= 75 ? 'var(--ok)' : n >= 50 ? 'var(--warn)' : 'var(--red)');
  function fmtDate(iso) {
    const d = new Date(iso);
    return isNaN(d) ? '' : d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
  }

  /* ---------------- theme ---------------- */
  function toggleTheme() {
    const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('rp_theme', next);
  }
  $$('.theme-toggle').forEach(b => { b.onclick = toggleTheme; });

  /* ---------------- API ---------------- */
  async function api(path, opts = {}, retried = false) {
    const headers = { ...(opts.headers || {}) };
    if (token) headers.Authorization = 'Bearer ' + token;
    let body = opts.body;
    if (opts.json) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(opts.json); }

    let res;
    try {
      res = await fetch(API_BASE() + '/api' + path, { method: opts.method || 'GET', headers, body });
    } catch {
      throw new Error('Cannot reach the server. Check that the backend is running.');
    }
    let data = null;
    try { data = await res.json(); } catch { /* empty body */ }

    if (res.status === 401 && token && !retried && !path.startsWith('/auth/')) {
      await startGuest();               // token expired or unknown: quietly get a new one and retry once
      return api(path, opts, true);
    }
    if (!res.ok) throw new Error((data && data.error) || 'Something went wrong. Please try again.');
    return data;
  }

  async function downloadDocx(title, text) {
    let res;
    try {
      res = await fetch(API_BASE() + '/api/resume/export/docx', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
        body: JSON.stringify({ title, text })
      });
    } catch { throw new Error('Cannot reach the server.'); }
    if (!res.ok) {
      let d = null; try { d = await res.json(); } catch { /* ignore */ }
      throw new Error((d && d.error) || 'Could not create the Word file.');
    }
    const url = URL.createObjectURL(await res.blob());
    const a = document.createElement('a');
    a.href = url;
    a.download = (title || 'redpen-document').replace(/[^\w -]+/g, '').trim().replace(/\s+/g, '-').toLowerCase() + '.docx';
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  }

  async function copyText(text, btn) {
    try { await navigator.clipboard.writeText(text); }
    catch {
      const ta = document.createElement('textarea');
      ta.value = text; document.body.appendChild(ta); ta.select();
      try { document.execCommand('copy'); } catch { /* ignore */ }
      ta.remove();
    }
    if (btn) { const old = btn.textContent; btn.textContent = 'Copied'; setTimeout(() => { btn.textContent = old; }, 1500); }
  }

  /* ---------------- browser session (no login) ---------------- */
  async function startGuest() {
    token = null;
    localStorage.removeItem('rp_token');
    const data = await api('/auth/guest', { method: 'POST' });
    token = data.token;
    localStorage.setItem('rp_token', token);
  }

  /* ---------------- account (optional; the app is fully usable as a guest) ---------------- */
  let me = { name: 'Guest', guest: true, email: null };

  async function loadMe() {
    try { me = await api('/auth/me'); }
    catch { me = { name: 'Guest', guest: true, email: null }; }
    renderAuthSlot();
  }

  function renderAuthSlot() {
    const slot = $('#auth-slot');
    if (!slot) return;
    const note = $('#me-note');
    if (me.guest) {
      slot.innerHTML = `<button type="button" class="auth-btn" id="open-auth">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
        Sign in</button>`;
      $('#open-auth').onclick = openAuthModal;
      if (note) note.textContent = 'No account needed. Your work is saved in this browser.';
    } else {
      const initial = (me.name || '?').trim().charAt(0).toUpperCase() || '?';
      slot.innerHTML = `<div class="auth-me">
        <div class="avatar">${esc(initial)}</div>
        <div class="auth-me-text"><strong>${esc(me.name || 'Signed in')}</strong>
          <button type="button" class="link-btn light" id="sign-out-btn">Sign out</button></div>
      </div>`;
      $('#sign-out-btn').onclick = signOut;
      if (note) note.textContent = 'Signed in as ' + esc(me.email || me.name) + '.';
    }
  }

  function openAuthModal() {
    show($('#auth-modal'), true);
    switchAuthTab('signin');
    show($('#signin-error'), false); show($('#signup-error'), false);
  }
  function closeAuthModal() { show($('#auth-modal'), false); }

  function switchAuthTab(tab) {
    $$('.tab', $('#auth-modal')).forEach(t => t.classList.toggle('active', t.dataset.tab === tab));
    show($('#signin-form'), tab === 'signin');
    show($('#signup-form'), tab === 'signup');
    $('#auth-modal-title').textContent = tab === 'signin' ? 'Sign in' : 'Create account';
    $('#auth-sub').textContent = tab === 'signin'
      ? 'Sign in to keep your info handy across devices. You can also just continue as a guest.'
      : 'Create a free account to save your info across devices. You can also just continue as a guest.';
  }
  $$('.tab', $('#auth-modal')).forEach(t => { t.onclick = () => switchAuthTab(t.dataset.tab); });
  $('#auth-close').onclick = closeAuthModal;
  $('#auth-guest-btn').onclick = closeAuthModal;
  $('#auth-modal').addEventListener('click', e => { if (e.target.id === 'auth-modal') closeAuthModal(); });
  $$('.pw-toggle').forEach(b => {
    b.onclick = () => {
      const inp = document.getElementById(b.dataset.for);
      if (!inp) return;
      inp.type = inp.type === 'password' ? 'text' : 'password';
      b.textContent = inp.type === 'password' ? 'Show' : 'Hide';
    };
  });

  $('#signin-form').addEventListener('submit', async e => {
    e.preventDefault();
    const btn = $('button[type="submit"]', e.target);
    show($('#signin-error'), false);
    btn.disabled = true;
    try {
      const data = await api('/auth/login', { method: 'POST', json: { email: $('#signin-email').value.trim(), password: $('#signin-password').value } });
      token = data.token; localStorage.setItem('rp_token', token);
      me = { name: data.name, guest: false, email: data.email };
      renderAuthSlot(); closeAuthModal(); refreshUsage();
    } catch (ex) { $('#signin-error').textContent = ex.message; show($('#signin-error'), true); }
    finally { btn.disabled = false; }
  });

  $('#signup-form').addEventListener('submit', async e => {
    e.preventDefault();
    const btn = $('button[type="submit"]', e.target);
    show($('#signup-error'), false);
    btn.disabled = true;
    try {
      const data = await api('/auth/signup', {
        method: 'POST',
        json: { name: $('#signup-name').value.trim(), email: $('#signup-email').value.trim(), password: $('#signup-password').value }
      });
      token = data.token; localStorage.setItem('rp_token', token);
      me = { name: data.name, guest: false, email: data.email };
      renderAuthSlot(); closeAuthModal(); refreshUsage();
    } catch (ex) { $('#signup-error').textContent = ex.message; show($('#signup-error'), true); }
    finally { btn.disabled = false; }
  });

  async function signOut() {
    if (!confirm('Sign out and continue as a guest in this browser?')) return;
    try {
      await startGuest();
      await loadMe();
      showPage('analyze');
      resetAnalyze(); resetCompare();
      refreshUsage();
    } catch (ex) { alert(ex.message); }
  }

  function enterApp() {
    show($('#boot-splash'), false); show($('#boot-error'), false); show($('#public-view'), false);
    show($('#app-view'), true);
    renderAuthSlot();
    loadMe();
    showPage('analyze');
    resetAnalyze();
    resetCompare();
    refreshUsage();
  }

  async function refreshUsage() {
    try {
      const u = await api('/resume/usage');
      $('#usage-line').textContent = u.remaining + ' of ' + u.limit + ' scores left today';
    } catch { /* not important */ }
  }

  /* ---------------- navigation ---------------- */
  function showPage(p) {
    $$('.nav-item').forEach(b => b.classList.toggle('active', b.dataset.page === p));
    show($('#page-analyze'), p === 'analyze');
    show($('#page-builder'), p === 'builder');
    show($('#page-compare'), p === 'compare');
    show($('#page-history'), p === 'history');
    if (p === 'history') loadHistory();
    if (p === 'builder') mountBuilder();
    window.scrollTo({ top: 0 });
  }
  $$('.nav-item').forEach(b => b.onclick = () => {
    if (b.dataset.page === 'analyze' && !$('#result').classList.contains('hidden')) resetAnalyze();
    if (b.dataset.page === 'compare' && !$('#compare-result').classList.contains('hidden')) resetCompare();
    showPage(b.dataset.page);
  });

  /* ---------------- reusable pieces: file drop + target settings ---------------- */
  const UPLOAD_ICON = '<svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M12 16V4"/><path d="M7 9l5-5 5 5"/><path d="M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3"/></svg>';
  const FILE_ICON = '<svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/></svg>';

  function createDrop(host, onError) {
    host.innerHTML = `
      <div class="drop" tabindex="0" role="button" aria-label="Choose resume file">
        <input type="file" accept=".pdf,.docx,.txt" hidden>
        <div class="d-empty">${UPLOAD_ICON}<strong>Drop your resume here</strong>
          <span class="muted">or click to browse. PDF, DOCX or TXT, up to 5 MB</span></div>
        <div class="d-file hidden">${FILE_ICON}<strong class="d-name"></strong><span class="muted d-size"></span>
          <button type="button" class="link-btn d-clear">Choose a different file</button></div>
      </div>`;
    const drop = $('.drop', host);
    const input = $('input', host);
    let file = null;

    function set(f) {
      file = f;
      drop.classList.toggle('has-file', !!f);
      show($('.d-empty', host), !f);
      show($('.d-file', host), !!f);
      if (f) {
        $('.d-name', host).textContent = f.name;
        $('.d-size', host).textContent = f.size > 1048576 ? (f.size / 1048576).toFixed(1) + ' MB' : Math.max(1, Math.round(f.size / 1024)) + ' KB';
      }
    }
    function pick(f) {
      if (!f) return;
      if (!/\.(pdf|docx|txt)$/i.test(f.name)) return onError('Upload a PDF, DOCX or TXT file.');
      if (f.size > 5 * 1024 * 1024) return onError('That file is over 5 MB. Upload a smaller one.');
      onError('');
      set(f);
    }
    drop.addEventListener('click', e => { if (e.target !== input) input.click(); });
    drop.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); input.click(); } });
    input.addEventListener('change', () => { pick(input.files[0]); input.value = ''; });
    ['dragenter', 'dragover'].forEach(ev => drop.addEventListener(ev, e => { e.preventDefault(); drop.classList.add('over'); }));
    ['dragleave', 'drop'].forEach(ev => drop.addEventListener(ev, e => { e.preventDefault(); drop.classList.remove('over'); }));
    drop.addEventListener('drop', e => pick(e.dataTransfer.files[0]));
    $('.d-clear', host).onclick = e => { e.stopPropagation(); set(null); input.click(); };

    return { get file() { return file; }, clear() { set(null); } };
  }

  function targetHTML(p) {
    return `
      <div class="field-group">
        <span class="lbl" id="${p}-level-lbl">I am a</span>
        <div class="levels" role="radiogroup" aria-labelledby="${p}-level-lbl">
          ${LEVELS.map((l, i) => `
            <label class="level"><input type="radio" name="${p}-level" value="${l.key}" ${i === 0 ? 'checked' : ''}>
              <span>${l.label}<small>${l.hint}</small></span></label>`).join('')}
        </div>
      </div>
      <div class="field-group">
        <label class="lbl" for="${p}-role">Target role</label>
        <input id="${p}-role" type="text" list="roles" maxlength="120" autocomplete="off" placeholder="Pick one or type your own, e.g. Java Developer">
      </div>
      <div class="field-group">
        <label class="lbl" for="${p}-jd">Job description <span class="opt">optional</span></label>
        <div class="jd-link">
          <input id="${p}-url" type="url" placeholder="Paste a job link to load it" aria-label="Job link">
          <button type="button" class="btn ghost small" data-load="${p}">Load</button>
        </div>
        <textarea id="${p}-jd" rows="6" maxlength="5000" placeholder="Or paste the job posting here."></textarea>
        <div class="count muted"><span id="${p}-count">0</span> / 5000</div>
        <p class="hint">No job description? Just pick a role and level. We score against what employers usually ask for.</p>
      </div>`;
  }

  function mountTarget(host, p, errEl) {
    host.innerHTML = targetHTML(p);
    $(`#${p}-jd`).addEventListener('input', e => { $(`#${p}-count`).textContent = e.target.value.length; });
    $(`[data-load="${p}"]`, host).onclick = async ev => {
      const btn = ev.currentTarget;
      const url = $(`#${p}-url`).value.trim();
      if (!url) { errEl.textContent = 'Paste a job link first.'; show(errEl); return; }
      show(errEl, false);
      btn.disabled = true; btn.textContent = 'Loading…';
      try {
        const r = await api('/resume/job-from-url', { method: 'POST', json: { url } });
        $(`#${p}-jd`).value = r.text;
        $(`#${p}-count`).textContent = r.text.length;
      } catch (ex) { errEl.textContent = ex.message; show(errEl); }
      finally { btn.disabled = false; btn.textContent = 'Load'; }
    };
  }

  function readTarget(p) {
    const checked = document.querySelector(`input[name="${p}-level"]:checked`);
    return { level: checked ? checked.value : 'junior', role: $(`#${p}-role`).value.trim(), jd: $(`#${p}-jd`).value.trim() };
  }
  function resetTarget(p) {
    const first = document.querySelector(`input[name="${p}-level"]`);
    if (first) first.checked = true;
    $(`#${p}-role`).value = ''; $(`#${p}-url`).value = ''; $(`#${p}-jd`).value = ''; $(`#${p}-count`).textContent = '0';
  }
  function targetError(t) {
    return (!t.role && t.jd.length < 20) ? 'Pick a target role or paste a job description so your resume has something to be scored against.' : '';
  }

  $('#roles').innerHTML = ROLES.map(r => `<option value="${esc(r)}">`).join('');

  /* ---------------- loading animation ---------------- */
  function startLoading(view, label, timeEl) {
    show(view.form, false); show(view.result, false); show(view.loading, true);
    const t0 = performance.now();
    let i = 0;
    if (label) label.textContent = LOADING_STEPS[0];
    if (label) timers.push(setInterval(() => { i = Math.min(i + 1, LOADING_STEPS.length - 1); label.textContent = LOADING_STEPS[i]; }, 1100));
    timers.push(setInterval(() => { timeEl.textContent = ((performance.now() - t0) / 1000).toFixed(1); }, 100));
    return t0;
  }
  function stopLoading() { timers.forEach(clearInterval); timers = []; }

  /* ---------------- score one resume ---------------- */
  const analyzeView = { form: $('#form-area'), loading: $('#loading'), result: $('#result') };

  dropMain = createDrop($('#drop-main'), msg => showErr($('#analyze-error'), msg));
  mountTarget($('#target-analyze'), 'an', $('#analyze-error'));

  function showErr(el, msg) { el.textContent = msg; show(el, !!msg); }

  $$('.seg-btn').forEach(b => b.onclick = () => {
    inputMode = b.dataset.input;
    $$('.seg-btn').forEach(x => x.classList.toggle('active', x === b));
    show($('#drop-main'), inputMode === 'file');
    show($('#resume-text'), inputMode === 'text');
  });

  function resetAnalyze() {
    stopLoading();
    dropMain.clear();
    $('#resume-text').value = '';
    resetTarget('an');
    showErr($('#analyze-error'), '');
    show($('#form-area'), true); show($('#loading'), false); show($('#result'), false);
    $('#result').innerHTML = '';
    $('#analyze-btn').disabled = false;
  }

  $('#analyze-form').addEventListener('submit', async e => {
    e.preventDefault();
    const err = $('#analyze-error');
    const t = readTarget('an');
    const fd = new FormData();

    if (inputMode === 'file') {
      if (!dropMain.file) return showErr(err, 'Choose your resume file first, or switch to Paste text.');
      fd.append('file', dropMain.file);
    } else {
      const text = $('#resume-text').value.trim();
      if (text.length < 80) return showErr(err, 'Paste your full resume text (at least a few lines).');
      fd.append('resumeText', text);
    }
    const problem = targetError(t);
    if (problem) return showErr(err, problem);
    showErr(err, '');
    fd.append('level', t.level); fd.append('role', t.role); fd.append('jobDescription', t.jd);

    const t0 = startLoading(analyzeView, $('#loading-text'), $('#loading-time'));
    try {
      const r = await api('/resume/analyze', { method: 'POST', body: fd });
      stopLoading();
      show($('#loading'), false);
      mountResult($('#result'), r, { mode: 'fresh', secs: ((performance.now() - t0) / 1000).toFixed(1) });
      show($('#result'), true);
      window.scrollTo({ top: 0 });
      refreshUsage();
    } catch (ex) {
      stopLoading();
      show($('#loading'), false); show($('#form-area'), true);
      showErr(err, ex.message);
    }
  });

  /* ---------------- result view (used by fresh, history and public) ---------------- */
  function mountResult(c, r, opts) {
    const mode = opts.mode;                 // fresh | history | public
    const owner = mode !== 'public';
    const good = r.score >= 75;
    const color = good ? 'var(--ok)' : 'var(--red)';
    const suggestions = r.suggestions || [];
    const checks = r.atsChecks || [];
    const weak = r.weakBullets || [];

    const bars = Object.keys(SECTION_LABELS).map(k => {
      const v = (r.sections && r.sections[k]) ?? 0;
      return `<div class="bar-row"><span>${SECTION_LABELS[k]}</span>
        <div class="bar"><i data-w="${v}" style="background:${tone(v)}"></i></div><b>${v}</b></div>`;
    }).join('');

    const chips = (arr, cls, empty) => arr && arr.length
      ? `<div class="chips">${arr.map(k => `<span class="chip ${cls}">${esc(k)}</span>`).join('')}</div>`
      : `<p class="none">${empty}</p>`;

    const meta = mode === 'fresh'
      ? (r.cached ? 'Same resume and target as before, so this is your saved result' : 'Scored in ' + opts.secs + ' seconds')
      : 'Scored on ' + fmtDate(r.createdAt);
    const who = [r.levelLabel, r.jobLabel].filter(Boolean).map(esc).join(', ');

    const backLabel = mode === 'history' ? 'Back to past scores' : 'Score another resume';

    c.innerHTML = `
      <div class="result-bar">
        <div>
          <div class="file">${owner ? esc(r.fileName) : 'Shared resume report'}</div>
          <div class="meta muted">${who}</div>
          <div class="meta muted">${meta}</div>
        </div>
        ${owner ? `<div class="actions">
          <button class="btn ghost small" data-act="share" type="button">Share link</button>
          <button class="btn ghost small" data-act="print" type="button">Download PDF report</button>
          <button class="btn ghost small" data-act="back" type="button">${backLabel}</button>
        </div>` : `<div class="actions"><button class="btn ghost small" data-act="print" type="button">Download PDF</button></div>`}
      </div>
      ${owner ? '<div class="share-box hidden" data-share></div>' : ''}

      <div class="result-top">
        <div class="sheet">
          <div class="score-fig">
            <svg viewBox="0 0 200 184" role="img" aria-label="Score ${r.score} out of 100">
              <path class="pen" d="${PEN}" style="stroke:${color}"/>
              <text x="100" y="112" text-anchor="middle" style="fill:${color}" data-num>0</text>
            </svg>
          </div>
          <div class="score-of">out of 100</div>
          <div class="verdict">${esc(r.verdict)}</div>
          <p class="summary">${esc(r.summary)}</p>
          <div class="split">
            <div><b>${r.aiScore ?? r.score}</b>Job match (AI)</div>
            <div><b>${r.atsScore ?? r.sections?.formatting ?? 0}</b>Format checks</div>
          </div>
        </div>
        <div>
          <h3 class="block-title">Section scores</h3>
          <div class="bars">${bars}</div>
          <h3 class="block-title">Keywords for this target</h3>
          <div class="kw-group"><div class="kw-label">Missing from your resume</div>${chips(r.missingKeywords, 'missing', 'Nothing important is missing.')}</div>
          <div class="kw-group"><div class="kw-label">Already in your resume</div>${chips(r.matchedKeywords, 'found', 'No matching keywords found.')}</div>
        </div>
      </div>

      <div class="result-bottom">
        <div>
          <h3 class="block-title">${good ? 'Optional polish' : suggestions.length + ' fixes to raise your score'}</h3>
          <ol class="notes ${good ? 'good' : ''}">${suggestions.map(s => `<li>${esc(s)}</li>`).join('')}</ol>
        </div>
        <div>
          <h3 class="block-title">What is working</h3>
          <ul class="strengths">${(r.strengths || []).map(s => `<li>${esc(s)}</li>`).join('') || '<li>No standout strengths found for this target yet.</li>'}</ul>
        </div>
      </div>

      ${weak.length ? `<div class="section-block">
        <h3 class="block-title">Weakest bullets</h3>
        <p class="lead">These lines from your resume are vague or do not show results.${owner ? ' Press Rewrite to get a stronger version.' : ''}</p>
        <div class="weak-list">${weak.map((w, i) => `
          <div class="weak"><q>${esc(w)}</q>
            ${owner ? `<button class="btn ghost small" data-rewrite="${i}" type="button">Rewrite</button><div class="rewrite-out hidden" data-rw="${i}"></div>` : ''}
          </div>`).join('')}</div>
      </div>` : ''}

      ${checks.length ? `<div class="section-block">
        <h3 class="block-title">Resume checks</h3>
        <p class="lead">Rule-based checks that run without AI, the same way an applicant tracking system reads your resume.</p>
        <ul class="ck-list">${checks.map(k => `
          <li class="ck ${esc(k.status)}"><span class="ck-ico" aria-hidden="true">${k.status === 'pass' ? '✓' : k.status === 'warn' ? '!' : '×'}</span>
            <div><b>${esc(k.label)}<span class="sr"> ${esc(k.status)}</span></b><span class="d">${esc(k.detail)}</span>${k.fix ? `<em>${esc(k.fix)}</em>` : ''}</div></li>`).join('')}</ul>
      </div>` : ''}

      ${owner ? `<div class="section-block" style="border-bottom:0">
        <h3 class="block-title">Do more with this resume</h3>
        ${r.hasResumeText === false
          ? '<p class="lead">This score was saved before these tools existed. Score the resume again to use them.</p>'
          : `<p class="lead">Written only from what is in your resume, for this target.</p>
             <div class="tool-btns">
               <button class="btn ghost" data-tool="summary" type="button">Tailored summary and skills</button>
               <button class="btn ghost" data-tool="coverLetter" type="button">Cover letter</button>
               <button class="btn ghost" data-tool="interview" type="button">Interview questions</button>
             </div>
             <div class="tool-out hidden" data-tool-out></div>`}
      </div>` : ''}`;

    wireResult(c, r, opts);
    animateResult(c, r.score);
  }

  function wireResult(c, r, opts) {
    const printBtn = $('[data-act="print"]', c);
    if (printBtn) printBtn.onclick = () => {
      const prev = document.title;
      document.title = 'Resume report ' + r.score;
      window.print();
      setTimeout(() => { document.title = prev; }, 500);
    };
    if (opts.mode === 'public') return;

    $('[data-act="back"]', c).onclick = () => {
      if (opts.mode === 'history') { show($('#result'), false); showPage('history'); }
      else resetAnalyze();
    };

    // ---- share ----
    const shareBox = $('[data-share]', c);
    function shareUrl(t) { return location.origin + location.pathname + '#/r/' + t; }
    function paintShare(t) {
      shareBox.innerHTML = `<span>Anyone with this link can view the report (not your resume file).</span>
        <input type="text" readonly value="${esc(shareUrl(t))}" aria-label="Share link">
        <button class="btn ghost small" data-copy type="button">Copy</button>
        <button class="link-btn" data-stop type="button">Stop sharing</button>`;
      show(shareBox, true);
      $('input', shareBox).onfocus = ev => ev.target.select();
      $('[data-copy]', shareBox).onclick = ev => copyText(shareUrl(t), ev.currentTarget);
      $('[data-stop]', shareBox).onclick = async () => {
        try { await api('/resume/history/' + r.id + '/share', { method: 'DELETE' }); r.shareToken = null; show(shareBox, false); }
        catch (ex) { alert(ex.message); }
      };
    }
    if (r.shareToken) paintShare(r.shareToken);
    $('[data-act="share"]', c).onclick = async ev => {
      const btn = ev.currentTarget; btn.disabled = true;
      try { const s = await api('/resume/history/' + r.id + '/share', { method: 'POST' }); r.shareToken = s.token; paintShare(s.token); }
      catch (ex) { alert(ex.message); }
      finally { btn.disabled = false; }
    };

    // ---- bullet rewriter ----
    $$('[data-rewrite]', c).forEach(btn => btn.onclick = async () => {
      const i = Number(btn.dataset.rewrite);
      const out = $(`[data-rw="${i}"]`, c);
      btn.disabled = true; btn.textContent = 'Rewriting…';
      try {
        const res = await api('/resume/rewrite', { method: 'POST', json: { bullet: r.weakBullets[i], analysisId: r.id } });
        out.innerHTML = `<div>${esc(res.improved)}</div>${res.why ? `<div class="why">${esc(res.why)}</div>` : ''}
          <div class="row"><button class="link-btn" data-c type="button">Copy</button></div>`;
        $('[data-c]', out).onclick = ev => copyText(res.improved, ev.currentTarget);
        show(out, true);
        btn.textContent = 'Rewrite again';
      } catch (ex) { out.textContent = ex.message; show(out, true); btn.textContent = 'Rewrite'; }
      finally { btn.disabled = false; }
    });

    // ---- summary / cover letter / interview ----
    const toolOut = $('[data-tool-out]', c);
    $$('[data-tool]', c).forEach(btn => btn.onclick = async () => {
      const kind = btn.dataset.tool;
      const old = btn.textContent;
      $$('[data-tool]', c).forEach(b => { b.disabled = true; });
      btn.textContent = 'Writing…';
      try {
        const res = await api('/resume/history/' + r.id + '/generate', { method: 'POST', json: { kind } });
        renderTool(toolOut, kind, res);
        show(toolOut, true);
        toolOut.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      } catch (ex) {
        toolOut.innerHTML = `<p class="error" style="margin:0">${esc(ex.message)}</p>`; show(toolOut, true);
      } finally {
        $$('[data-tool]', c).forEach(b => { b.disabled = false; });
        btn.textContent = old;
      }
    });
  }

  function renderTool(box, kind, res) {
    let plain = '', title = '', html = '';
    if (kind === 'summary') {
      title = 'Tailored summary';
      plain = res.summary + '\n\nSkills: ' + (res.skills || []).join(', ');
      html = `<h4>Professional summary</h4><p>${esc(res.summary)}</p>
        <h4 style="margin-top:16px">Skills to list first</h4>
        <div class="chips">${(res.skills || []).map(s => `<span class="chip found">${esc(s)}</span>`).join('')}</div>`;
    } else if (kind === 'coverLetter') {
      title = 'Cover letter';
      plain = res.coverLetter;
      html = `<h4>Cover letter</h4><div class="letter">${esc(res.coverLetter)}</div>`;
    } else {
      title = 'Interview questions';
      const qs = res.questions || [];
      plain = qs.map((q, i) => (i + 1) + '. ' + q.question + '\n   Why: ' + q.why + '\n   Tip: ' + q.tip).join('\n\n');
      html = `<h4>Likely interview questions</h4><ol class="qa">${qs.map(q => `<li><b>${esc(q.question)}</b>
        <span>${esc(q.why)}</span><span><em style="font-style:normal;font-weight:600">How to answer:</em> ${esc(q.tip)}</span></li>`).join('')}</ol>`;
    }
    box.innerHTML = html + `<div class="row">
      <button class="btn ghost small" data-copy type="button">Copy</button>
      ${kind !== 'interview' ? '<button class="btn ghost small" data-docx type="button">Download Word file</button>' : ''}
      <span class="muted" data-msg></span></div>`;
    $('[data-copy]', box).onclick = ev => copyText(plain, ev.currentTarget);
    const d = $('[data-docx]', box);
    if (d) d.onclick = async ev => {
      const b = ev.currentTarget; b.disabled = true;
      try { await downloadDocx(title, plain); } catch (ex) { $('[data-msg]', box).textContent = ex.message; }
      finally { b.disabled = false; }
    };
  }

  function animateResult(c, score) {
    const pen = $('.pen', c);
    const num = $('[data-num]', c);
    const len = pen.getTotalLength();
    pen.style.strokeDasharray = len;
    pen.style.strokeDashoffset = len;
    const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
    requestAnimationFrame(() => requestAnimationFrame(() => {
      $$('.bar > i', c).forEach(b => { b.style.width = b.dataset.w + '%'; });
      if (reduce) { pen.style.strokeDashoffset = 0; num.textContent = score; return; }
      pen.style.transition = 'stroke-dashoffset 1s ease-out .15s';
      pen.style.strokeDashoffset = 0;
      const start = performance.now();
      const tick = now => {
        const p = Math.min(1, (now - start) / 900);
        num.textContent = Math.round(score * (1 - Math.pow(1 - p, 3)));
        if (p < 1) requestAnimationFrame(tick);
      };
      requestAnimationFrame(tick);
    }));
  }

  /* ---------------- compare two versions ---------------- */
  const compareView = { form: $('#compare-form-area'), loading: $('#compare-loading'), result: $('#compare-result') };
  dropA = createDrop($('#drop-a'), msg => showErr($('#compare-error'), msg));
  dropB = createDrop($('#drop-b'), msg => showErr($('#compare-error'), msg));
  mountTarget($('#target-compare'), 'cp', $('#compare-error'));

  function resetCompare() {
    stopLoading();
    dropA.clear(); dropB.clear(); resetTarget('cp');
    showErr($('#compare-error'), '');
    show($('#compare-form-area'), true); show($('#compare-loading'), false); show($('#compare-result'), false);
    $('#compare-result').innerHTML = '';
  }

  $('#compare-form').addEventListener('submit', async e => {
    e.preventDefault();
    const err = $('#compare-error');
    const t = readTarget('cp');
    if (!dropA.file || !dropB.file) return showErr(err, 'Upload both versions to compare them.');
    const problem = targetError(t);
    if (problem) return showErr(err, problem);
    showErr(err, '');

    const fd = new FormData();
    fd.append('fileA', dropA.file); fd.append('fileB', dropB.file);
    fd.append('level', t.level); fd.append('role', t.role); fd.append('jobDescription', t.jd);

    startLoading(compareView, null, $('#compare-time'));
    try {
      const res = await api('/resume/compare', { method: 'POST', body: fd });
      stopLoading();
      show($('#compare-loading'), false);
      renderCompare(res);
      refreshUsage();
    } catch (ex) {
      stopLoading();
      show($('#compare-loading'), false); show($('#compare-form-area'), true);
      showErr(err, ex.message);
    }
  });

  function renderCompare(res) {
    const a = res.a, b = res.b;
    const diff = b.score - a.score;
    const banner = diff > 0 ? `Version B scores ${diff} point${diff === 1 ? '' : 's'} higher.`
      : diff < 0 ? `Version A scores ${-diff} point${diff === -1 ? '' : 's'} higher.` : 'Both versions score the same.';
    const rows = [
      ['Overall score', a.score, b.score, true],
      ['Job match (AI)', a.aiScore, b.aiScore],
      ['Format checks', a.atsScore, b.atsScore],
      ...Object.keys(SECTION_LABELS).filter(k => k !== 'formatting').map(k => [SECTION_LABELS[k], a.sections[k], b.sections[k]])
    ];
    const delta = d => d > 0 ? `<span class="delta up">+${d}</span>` : d < 0 ? `<span class="delta down">${d}</span>` : '<span class="delta same">0</span>';
    const missing = r => (r.missingKeywords || []).length;

    const box = $('#compare-result');
    box.innerHTML = `
      <div class="cmp-banner">${banner}</div>
      <p class="muted">${esc(a.levelLabel || '')}${a.jobLabel ? ', ' + esc(a.jobLabel) : ''}</p>
      <table class="cmp-table">
        <thead><tr><th></th><th>A: ${esc(a.fileName)}</th><th>B: ${esc(b.fileName)}</th><th>Change</th></tr></thead>
        <tbody>
          ${rows.map(([l, x, y, total]) => `<tr class="${total ? 'total' : ''}"><td>${l}</td><td>${x}</td><td>${y}</td><td>${delta(y - x)}</td></tr>`).join('')}
          <tr><td>Missing keywords</td><td>${missing(a)}</td><td>${missing(b)}</td><td>${delta(missing(a) - missing(b))}</td></tr>
        </tbody>
      </table>
      <div class="actions">
        <button class="btn ghost small" data-open="${a.id}" type="button">Full report for A</button>
        <button class="btn ghost small" data-open="${b.id}" type="button">Full report for B</button>
        <button class="btn primary small" data-again type="button">Compare other versions</button>
      </div>`;
    show(box, true);
    $$('[data-open]', box).forEach(btn => btn.onclick = () => openHistoryItem(btn.dataset.open));
    $('[data-again]', box).onclick = resetCompare;
    window.scrollTo({ top: 0 });
  }

  /* ---------------- history ---------------- */
  async function openHistoryItem(id) {
    try {
      const r = await api('/resume/history/' + id);
      showPage('analyze');
      show($('#form-area'), false); show($('#loading'), false);
      mountResult($('#result'), r, { mode: 'history' });
      show($('#result'), true);
    } catch (ex) { alert(ex.message); }
  }

  function chartSVG(items) {
    const pts = items.slice().reverse().slice(-20);
    if (pts.length < 2) return '';
    const W = 640, H = 200, pl = 32, pr = 14, pt = 14, pb = 28;
    const x = i => pl + i * (W - pl - pr) / (pts.length - 1);
    const y = v => pt + (100 - v) * (H - pt - pb) / 100;
    const grid = [0, 25, 50, 75, 100].map(v => `<line class="grid" x1="${pl}" x2="${W - pr}" y1="${y(v)}" y2="${y(v)}"/><text class="axis" x="${pl - 6}" y="${y(v) + 4}" text-anchor="end">${v}</text>`).join('');
    const line = pts.map((p, i) => `${x(i).toFixed(1)},${y(p.score).toFixed(1)}`).join(' ');
    const dots = pts.map((p, i) => `<circle class="dot" cx="${x(i).toFixed(1)}" cy="${y(p.score).toFixed(1)}" r="5"><title>Score ${p.score} on ${esc(fmtDate(p.createdAt))}</title></circle>`).join('');
    return `<div class="chart"><h3 class="block-title">Your score over time</h3>
      <svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Line chart of your last ${pts.length} scores">${grid}
        <polyline class="line" points="${line}"/>${dots}
        <text class="axis" x="${pl}" y="${H - 6}">${esc(fmtDate(pts[0].createdAt))}</text>
        <text class="axis" x="${W - pr}" y="${H - 6}" text-anchor="end">${esc(fmtDate(pts[pts.length - 1].createdAt))}</text>
      </svg></div>`;
  }

  async function loadHistory() {
    const box = $('#history-list');
    const chart = $('#history-chart');
    chart.innerHTML = '';
    box.innerHTML = '<p class="muted">Loading…</p>';
    try {
      const items = await api('/resume/history');
      if (!items.length) {
        box.innerHTML = `<div class="empty"><h2>No scores yet</h2>
          <p class="muted">Score your first resume and it will show up here.</p>
          <button class="btn primary" id="first" type="button">Score a resume</button></div>`;
        $('#first').onclick = () => { resetAnalyze(); showPage('analyze'); };
        return;
      }
      chart.innerHTML = chartSVG(items);
      box.innerHTML = items.map(i => `
        <div class="hist">
          <div class="hist-score ${i.score >= 75 ? 'hi' : i.score >= 50 ? 'mid' : 'lo'}">${i.score}</div>
          <div class="hist-main">
            <strong>${esc(i.fileName)}</strong>
            <span>${[i.levelLabel, i.jobLabel].filter(Boolean).map(esc).join(', ')}</span>
            <span>${esc(fmtDate(i.createdAt))}${i.shared ? ', shared' : ''}</span>
          </div>
          <div class="hist-actions">
            <button class="link-btn" data-open="${i.id}" type="button">View</button>
            <button class="link-btn del" data-del="${i.id}" type="button">Delete</button>
          </div>
        </div>`).join('');

      $$('[data-open]', box).forEach(b => { b.onclick = () => openHistoryItem(b.dataset.open); });
      $$('[data-del]', box).forEach(b => b.onclick = async () => {
        if (!confirm('Delete this score? This cannot be undone.')) return;
        try { await api('/resume/history/' + b.dataset.del, { method: 'DELETE' }); loadHistory(); refreshUsage(); }
        catch (ex) { alert(ex.message); }
      });
    } catch (ex) {
      box.innerHTML = `<p class="error">${esc(ex.message)}</p>`;
    }
  }

  /* ---------------- build a resume (new, additive feature) ---------------- */
  const WIZ_STEPS = ['Personal info', 'Target role', 'Experience', 'Education', 'Skills & projects', 'Review & generate'];
  const FIELD_TYPES = [
    'Software / IT', 'Data & Analytics', 'Design (UI/UX, Graphic)', 'Product & Project Management',
    'Business Analysis', 'Marketing & Content', 'Sales & Customer Success', 'Human Resources',
    'Finance & Accounting', 'Operations & Supply Chain', 'Engineering (Mechanical/Civil/Electrical)',
    'Healthcare & Nursing', 'Education & Teaching', 'ERP / SAP Consulting', 'Legal', 'Other'
  ];

  let builderMounted = false;
  let builderStep = 0;
  let entryUid = 0;
  let currentResume = null;

  function personalStepHTML() {
    return `
      <h2>Personal information</h2>
      <p class="muted">This goes at the top of your resume exactly as you type it — we never change names, emails or dates.</p>
      <div class="wiz-grid">
        <div class="field-group"><label class="lbl" for="b-name">Full name *</label><input id="b-name" type="text" maxlength="100" autocomplete="name"></div>
        <div class="field-group"><label class="lbl" for="b-email">Email</label><input id="b-email" type="email" maxlength="150" autocomplete="email"></div>
        <div class="field-group"><label class="lbl" for="b-phone">Phone</label><input id="b-phone" type="text" maxlength="40" autocomplete="tel"></div>
        <div class="field-group"><label class="lbl" for="b-location">Location</label><input id="b-location" type="text" maxlength="100" placeholder="City, Country"></div>
        <div class="field-group"><label class="lbl" for="b-linkedin">LinkedIn</label><input id="b-linkedin" type="text" maxlength="200" placeholder="linkedin.com/in/you"></div>
        <div class="field-group"><label class="lbl" for="b-github">GitHub</label><input id="b-github" type="text" maxlength="200" placeholder="github.com/you"></div>
        <div class="field-group full"><label class="lbl" for="b-portfolio">Portfolio / website <span class="opt">optional</span></label><input id="b-portfolio" type="text" maxlength="200"></div>
      </div>`;
  }

  function targetStepHTML() {
    return `
      <h2>What are you targeting?</h2>
      <p class="muted">Pick the field your resume is for, and the specific role. This steers the wording — it never invents experience.</p>
      <div class="field-group">
        <span class="lbl">I am a</span>
        <div class="levels" role="radiogroup" aria-label="Experience level">
          ${LEVELS.map((l, i) => `<label class="level"><input type="radio" name="b-level" value="${l.key}" ${i === 0 ? 'checked' : ''}><span>${l.label}<small>${l.hint}</small></span></label>`).join('')}
        </div>
      </div>
      <div class="field-group">
        <span class="lbl">Field / type</span>
        <div class="type-grid">
          ${FIELD_TYPES.map((t, i) => `<label class="type-opt"><input type="radio" name="b-type" value="${esc(t)}" ${i === 0 ? 'checked' : ''}><span>${esc(t)}</span></label>`).join('')}
        </div>
        <input id="b-type-other" type="text" maxlength="80" placeholder="Type your own field, e.g. SAP FICO, Embedded Systems" class="hidden">
      </div>
      <div class="field-group">
        <label class="lbl" for="b-role">Specific role title</label>
        <input id="b-role" type="text" list="roles" maxlength="120" placeholder="e.g. Java Developer, SAP FICO Consultant">
      </div>
      <div class="field-group">
        <label class="lbl" for="b-summary">A few lines about yourself <span class="opt">optional</span></label>
        <textarea id="b-summary" rows="4" maxlength="600" placeholder="Optional: write a short summary yourself, or leave this blank and we will write one from your experience and skills."></textarea>
      </div>`;
  }

  function experienceStepHTML() {
    return `
      <h2>Work experience</h2>
      <p class="muted">Add each role. It's fine to leave this empty for freshers and interns.</p>
      <div id="exp-list"></div>
      <button type="button" class="btn ghost small add-entry-btn" id="add-exp">+ Add experience</button>`;
  }

  function educationStepHTML() {
    return `
      <h2>Education</h2>
      <p class="muted">Add your most relevant qualifications, most recent first.</p>
      <div id="edu-list"></div>
      <button type="button" class="btn ghost small add-entry-btn" id="add-edu">+ Add education</button>`;
  }

  function skillsStepHTML() {
    return `
      <h2>Skills, projects & certifications</h2>
      <p class="muted">Only add skills and facts that are true — wording gets polished, but nothing you didn't list here is ever added.</p>
      <div class="field-group">
        <span class="lbl">Skills</span>
        <div class="skills-input-row">
          <input id="b-skill-input" type="text" maxlength="60" placeholder="Type a skill and press Enter, e.g. Java, SAP FICO, Figma">
          <button type="button" class="btn ghost small" id="add-skill-btn">Add</button>
        </div>
        <div class="skill-tags" id="skill-tags"></div>
      </div>
      <div class="field-group">
        <span class="lbl">Projects <span class="opt">optional</span></span>
        <div id="proj-list"></div>
        <button type="button" class="btn ghost small add-entry-btn" id="add-proj">+ Add project</button>
      </div>
      <div class="field-group">
        <span class="lbl">Certifications <span class="opt">optional</span></span>
        <div id="cert-list"></div>
        <button type="button" class="btn ghost small add-entry-btn" id="add-cert">+ Add certification</button>
      </div>`;
  }

  function reviewStepHTML() {
    return `
      <h2>Review & generate</h2>
      <p class="muted">We polish grammar and phrasing using only what you entered below, then hand you a clean PDF.</p>
      <div id="review-summary" class="review-block"></div>
      <p id="gen-error" class="error hidden wiz-error" role="alert"></p>
      <button type="button" class="btn primary" id="gen-btn">Generate my resume</button>
      <div id="gen-loading" class="loading hidden">
        <svg class="scribble" viewBox="0 0 200 184" width="90" height="82"><path class="pen" d="M96 20 C150 12 186 44 184 88 C182 134 138 168 92 164 C44 160 10 126 14 82 C18 38 62 14 112 26"/></svg>
        <h2>Writing your resume</h2>
      </div>
      <div id="resume-output" class="hidden"></div>`;
  }

  function navHTML(i, total) {
    return `
      <div class="wiz-nav">
        ${i > 0 ? `<button type="button" class="btn ghost" data-nav="prev">Previous</button>` : `<span></span>`}
        <span class="spacer"></span>
        ${i < total - 1 ? `<button type="button" class="btn primary" data-nav="next">Next</button>` : ''}
      </div>`;
  }

  function expEntryHTML(id) {
    return `<div class="entry-card" data-uid="${id}">
      <div class="entry-head"><strong>Experience</strong><button type="button" class="link-btn entry-remove">Remove</button></div>
      <div class="wiz-grid">
        <div class="field-group"><label class="lbl">Job title</label><input class="f-title" type="text" maxlength="100"></div>
        <div class="field-group"><label class="lbl">Company</label><input class="f-company" type="text" maxlength="100"></div>
        <div class="field-group"><label class="lbl">Location</label><input class="f-location" type="text" maxlength="100"></div>
        <div class="field-group"><label class="lbl">Start date</label><input class="f-start" type="text" maxlength="30" placeholder="e.g. Jun 2022"></div>
        <div class="field-group"><label class="lbl">End date</label><input class="f-end" type="text" maxlength="30" placeholder="e.g. Aug 2024"></div>
        <div class="field-group"><label class="checkline"><input type="checkbox" class="f-current"> I currently work here</label></div>
        <div class="field-group full">
          <label class="lbl">Achievements / responsibilities</label>
          <textarea class="f-bullets" rows="4" maxlength="2000" placeholder="One per line, e.g.&#10;Led a team of 4 engineers to ship the new checkout flow&#10;Reduced page load time by 30%"></textarea>
        </div>
      </div>
    </div>`;
  }

  function eduEntryHTML(id) {
    return `<div class="entry-card" data-uid="${id}">
      <div class="entry-head"><strong>Education</strong><button type="button" class="link-btn entry-remove">Remove</button></div>
      <div class="wiz-grid">
        <div class="field-group"><label class="lbl">School / University</label><input class="f-school" type="text" maxlength="150"></div>
        <div class="field-group"><label class="lbl">Degree</label><input class="f-degree" type="text" maxlength="100" placeholder="e.g. B.Tech"></div>
        <div class="field-group"><label class="lbl">Field of study</label><input class="f-field" type="text" maxlength="100" placeholder="e.g. Computer Science"></div>
        <div class="field-group"><label class="lbl">Grade / GPA <span class="opt">optional</span></label><input class="f-grade" type="text" maxlength="40"></div>
        <div class="field-group"><label class="lbl">Start date</label><input class="f-start" type="text" maxlength="30"></div>
        <div class="field-group"><label class="lbl">End date</label><input class="f-end" type="text" maxlength="30" placeholder="e.g. 2024 or Expected 2026"></div>
      </div>
    </div>`;
  }

  function projEntryHTML(id) {
    return `<div class="entry-card" data-uid="${id}">
      <div class="entry-head"><strong>Project</strong><button type="button" class="link-btn entry-remove">Remove</button></div>
      <div class="wiz-grid">
        <div class="field-group"><label class="lbl">Name</label><input class="f-name" type="text" maxlength="100"></div>
        <div class="field-group"><label class="lbl">Tech used</label><input class="f-tech" type="text" maxlength="150" placeholder="e.g. React, Node, PostgreSQL"></div>
        <div class="field-group full"><label class="lbl">Link <span class="opt">optional</span></label><input class="f-link" type="text" maxlength="200"></div>
        <div class="field-group full"><label class="lbl">What did you build?</label><textarea class="f-desc" rows="3" maxlength="500"></textarea></div>
      </div>
    </div>`;
  }

  function certEntryHTML(id) {
    return `<div class="entry-card" data-uid="${id}">
      <div class="entry-head"><strong>Certification</strong><button type="button" class="link-btn entry-remove">Remove</button></div>
      <div class="wiz-grid">
        <div class="field-group"><label class="lbl">Name</label><input class="f-name" type="text" maxlength="150"></div>
        <div class="field-group"><label class="lbl">Issuer</label><input class="f-issuer" type="text" maxlength="100"></div>
        <div class="field-group"><label class="lbl">Year</label><input class="f-year" type="text" maxlength="20"></div>
      </div>
    </div>`;
  }

  function addEntry(listSelector, htmlFn) {
    const id = ++entryUid;
    const wrap = document.createElement('div');
    wrap.innerHTML = htmlFn(id);
    const card = wrap.firstElementChild;
    $(listSelector).appendChild(card);
    $('.entry-remove', card).onclick = () => card.remove();
    return card;
  }
  function addExperienceEntry() {
    const c = addEntry('#exp-list', expEntryHTML);
    const cur = $('.f-current', c), end = $('.f-end', c);
    cur.onchange = () => { end.disabled = cur.checked; if (cur.checked) end.value = ''; };
  }
  function addEducationEntry() { addEntry('#edu-list', eduEntryHTML); }
  function addProjectEntry() { addEntry('#proj-list', projEntryHTML); }
  function addCertEntry() { addEntry('#cert-list', certEntryHTML); }

  function wireTypeOptions() {
    const other = $('#b-type-other');
    function sync() {
      const checked = document.querySelector('input[name="b-type"]:checked');
      show(other, !!(checked && checked.value === 'Other'));
    }
    $$('input[name="b-type"]').forEach(r => { r.onchange = sync; });
    sync();
  }

  function wireSkillsInput() {
    const input = $('#b-skill-input');
    function addSkill() {
      const v = input.value.trim();
      if (!v) return;
      const tags = $('#skill-tags');
      if ([...tags.querySelectorAll('.skill-tag span')].some(s => s.textContent.toLowerCase() === v.toLowerCase())) { input.value = ''; return; }
      const tag = document.createElement('span');
      tag.className = 'skill-tag';
      tag.innerHTML = `<span>${esc(v)}</span><button type="button" aria-label="Remove skill">&times;</button>`;
      tag.querySelector('button').onclick = () => tag.remove();
      tags.appendChild(tag);
      input.value = '';
      input.focus();
    }
    input.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); addSkill(); } });
    $('#add-skill-btn').onclick = addSkill;
  }

  function collectEntries(listSelector, mapper) {
    return $$(listSelector + ' .entry-card').map(mapper);
  }
  function collectPersonal() {
    return {
      fullName: $('#b-name').value.trim(), email: $('#b-email').value.trim(), phone: $('#b-phone').value.trim(),
      location: $('#b-location').value.trim(), linkedin: $('#b-linkedin').value.trim(),
      portfolio: $('#b-portfolio').value.trim(), github: $('#b-github').value.trim()
    };
  }
  function collectTarget() {
    const typeChecked = document.querySelector('input[name="b-type"]:checked');
    const levelChecked = document.querySelector('input[name="b-level"]:checked');
    return {
      targetType: typeChecked ? typeChecked.value : '',
      targetTypeOther: $('#b-type-other').value.trim(),
      targetRole: $('#b-role').value.trim(),
      level: levelChecked ? levelChecked.value : 'fresher',
      summary: $('#b-summary').value.trim()
    };
  }
  function collectExperience() {
    return collectEntries('#exp-list', c => ({
      title: $('.f-title', c).value.trim(), company: $('.f-company', c).value.trim(), location: $('.f-location', c).value.trim(),
      startDate: $('.f-start', c).value.trim(), endDate: $('.f-end', c).value.trim(), current: $('.f-current', c).checked,
      bullets: $('.f-bullets', c).value.split('\n').map(s => s.trim()).filter(Boolean)
    })).filter(e => e.title || e.company || e.bullets.length);
  }
  function collectEducation() {
    return collectEntries('#edu-list', c => ({
      school: $('.f-school', c).value.trim(), degree: $('.f-degree', c).value.trim(), field: $('.f-field', c).value.trim(),
      grade: $('.f-grade', c).value.trim(), startDate: $('.f-start', c).value.trim(), endDate: $('.f-end', c).value.trim()
    })).filter(e => e.school || e.degree);
  }
  function collectSkills() { return $$('#skill-tags .skill-tag span').map(s => s.textContent).filter(Boolean); }
  function collectProjects() {
    return collectEntries('#proj-list', c => ({
      name: $('.f-name', c).value.trim(), tech: $('.f-tech', c).value.trim(), link: $('.f-link', c).value.trim(), description: $('.f-desc', c).value.trim()
    })).filter(p => p.name || p.description);
  }
  function collectCertifications() {
    return collectEntries('#cert-list', c => ({
      name: $('.f-name', c).value.trim(), issuer: $('.f-issuer', c).value.trim(), year: $('.f-year', c).value.trim()
    })).filter(c2 => c2.name);
  }

  function validateStep(i) {
    if (i === 0) {
      const name = $('#b-name').value.trim();
      const email = $('#b-email').value.trim();
      if (!name) { alert('Enter your full name to continue.'); $('#b-name').focus(); return false; }
      if (email && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) { alert('Enter a valid email, or leave it blank.'); $('#b-email').focus(); return false; }
    }
    return true;
  }

  function renderReviewRecap() {
    const box = $('#review-summary');
    if (!box) return;
    const name = $('#b-name').value.trim() || '(no name yet)';
    const target = collectTarget();
    const typeLabel = target.targetType === 'Other' && target.targetTypeOther ? target.targetTypeOther : target.targetType;
    const expN = collectExperience().length, eduN = collectEducation().length, skillN = collectSkills().length;
    box.innerHTML = `<h3>${esc(name)}</h3>
      <p class="muted">${esc([target.targetRole, typeLabel].filter(Boolean).join(' · ') || 'No target set yet')}</p>
      <p class="muted">${expN} experience ${expN === 1 ? 'entry' : 'entries'} · ${eduN} education ${eduN === 1 ? 'entry' : 'entries'} · ${skillN} skill${skillN === 1 ? '' : 's'}</p>`;
  }

  function goToStep(n) {
    const panels = $$('.wiz-panel');
    if (n < 0 || n >= panels.length) return;
    builderStep = n;
    panels.forEach((p, i) => show(p, i === n));
    $$('.wiz-step').forEach((b, i) => {
      b.classList.toggle('active', i === n);
      b.classList.toggle('done', i < n);
    });
    if (n === 5) renderReviewRecap();
    window.scrollTo({ top: 0 });
  }

  function buildTracker() {
    const host = $('#wiz-steps');
    host.innerHTML = WIZ_STEPS.map((label, i) => `<button type="button" class="wiz-step" data-step="${i}"><span class="n">${i + 1}</span>${esc(label)}</button>`).join('');
    $$('.wiz-step', host).forEach(b => { b.onclick = () => goToStep(Number(b.dataset.step)); });
  }

  function renderPreview(r) {
    const p = r.personal || {};
    const contact = [p.location, p.phone, p.email, p.linkedin, p.portfolio, p.github].filter(Boolean).map(esc).join(' &nbsp;|&nbsp; ');
    let html = `<h1>${esc(p.fullName || '')}</h1><div class="rp-contact">${contact}</div>`;
    if (r.summary) html += `<h4>Summary</h4><p>${esc(r.summary)}</p>`;
    if (r.skills && r.skills.length) html += `<h4>Skills</h4><div class="rp-skills">${r.skills.map(s => `<span>${esc(s)}</span>`).join('')}</div>`;
    if (r.experience && r.experience.length) {
      html += `<h4>Experience</h4>` + r.experience.map(e => `
        <div class="rp-row"><span>${esc(e.title || '')}${e.company ? ' · ' + esc(e.company) : ''}</span><span>${esc(e.dateRange || '')}</span></div>
        ${e.location ? `<div class="rp-sub">${esc(e.location)}</div>` : ''}
        <ul>${(e.bullets || []).map(b => `<li>${esc(b)}</li>`).join('')}</ul>`).join('');
    }
    if (r.projects && r.projects.length) {
      html += `<h4>Projects</h4>` + r.projects.map(pr => `
        <div class="rp-row"><span>${esc(pr.name || '')}</span><span>${esc(pr.tech || '')}</span></div>
        ${pr.description ? `<p>${esc(pr.description)}</p>` : ''}`).join('');
    }
    if (r.education && r.education.length) {
      html += `<h4>Education</h4>` + r.education.map(ed => {
        let left = ed.degree || '';
        if (ed.field) left = left ? left + ' in ' + ed.field : ed.field;
        if (!left) left = ed.school || '';
        return `<div class="rp-row"><span>${esc(left)}</span><span>${esc(ed.dateRange || '')}</span></div>
          ${(ed.degree || ed.field) && ed.school ? `<div class="rp-sub">${esc(ed.school)}</div>` : ''}
          ${ed.grade ? `<div class="rp-sub">${esc(ed.grade)}</div>` : ''}`;
      }).join('');
    }
    if (r.certifications && r.certifications.length) {
      html += `<h4>Certifications</h4><ul>${r.certifications.map(c => `<li>${esc(c.name || '')}${c.issuer ? ' — ' + esc(c.issuer) : ''}${c.year ? ' (' + esc(c.year) + ')' : ''}</li>`).join('')}</ul>`;
    }
    $('#resume-preview').innerHTML = html;
  }

  function renderResumeOutput(resume) {
    currentResume = resume;
    const box = $('#resume-output');
    show(box, true);
    box.innerHTML = `
      <div class="review-block">
        <h3>Summary</h3>
        <textarea id="rv-summary" rows="3">${esc(resume.summary || '')}</textarea>
      </div>
      ${(resume.experience || []).map((e, i) => `
        <div class="review-block review-exp">
          <strong>${esc(e.title || '')}${e.company ? ' · ' + esc(e.company) : ''}</strong>
          <span>${esc(e.dateRange || '')}</span>
          <textarea class="rv-exp-bullets" data-i="${i}" rows="3">${esc((e.bullets || []).join('\n'))}</textarea>
        </div>`).join('')}
      ${(resume.projects || []).filter(p => p.description).map((p) => {
        const i = resume.projects.indexOf(p);
        return `<div class="review-block">
          <strong>${esc(p.name || '')}</strong>
          <textarea class="rv-proj-desc" data-i="${i}" rows="2">${esc(p.description || '')}</textarea>
        </div>`;
      }).join('')}
      <div class="resume-preview" id="resume-preview"></div>
      <div class="submit-row">
        <button type="button" class="btn primary" id="download-pdf-btn">Download as PDF</button>
        <button type="button" class="btn ghost" id="regen-btn">Start over</button>
      </div>`;
    renderPreview(resume);
    $('#rv-summary').addEventListener('input', e => { currentResume.summary = e.target.value; renderPreview(currentResume); });
    $$('.rv-exp-bullets').forEach(t => t.addEventListener('input', e => {
      currentResume.experience[Number(e.target.dataset.i)].bullets = e.target.value.split('\n').map(s => s.trim()).filter(Boolean);
      renderPreview(currentResume);
    }));
    $$('.rv-proj-desc').forEach(t => t.addEventListener('input', e => {
      currentResume.projects[Number(e.target.dataset.i)].description = e.target.value;
      renderPreview(currentResume);
    }));
    $('#download-pdf-btn').onclick = downloadBuilderPdf;
    $('#regen-btn').onclick = () => { if (confirm('Start over? This clears everything you entered.')) resetBuilder(); };
  }

  async function generateResume() {
    const btn = $('#gen-btn');
    show($('#gen-error'), false);
    const payload = Object.assign({ personal: collectPersonal() }, collectTarget(), {
      experience: collectExperience(), education: collectEducation(), skills: collectSkills(),
      projects: collectProjects(), certifications: collectCertifications()
    });
    if (!payload.personal.fullName) { $('#gen-error').textContent = 'Enter your full name in Step 1.'; show($('#gen-error'), true); goToStep(0); return; }
    btn.disabled = true;
    show($('#gen-loading'), true); show($('#resume-output'), false);
    try {
      const resume = await api('/builder/generate', { method: 'POST', json: payload });
      renderResumeOutput(resume);
    } catch (ex) {
      $('#gen-error').textContent = ex.message; show($('#gen-error'), true);
    } finally {
      btn.disabled = false;
      show($('#gen-loading'), false);
    }
  }

  async function downloadBuilderPdf() {
    if (!currentResume) return;
    const btn = $('#download-pdf-btn');
    const old = btn.textContent;
    btn.disabled = true; btn.textContent = 'Preparing…';
    try {
      const res = await fetch(API_BASE() + '/api/builder/export/pdf', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
        body: JSON.stringify({ resume: currentResume })
      });
      if (!res.ok) {
        let d = null; try { d = await res.json(); } catch { /* ignore */ }
        throw new Error((d && d.error) || 'Could not create the PDF file.');
      }
      const url = URL.createObjectURL(await res.blob());
      const a = document.createElement('a');
      a.href = url;
      const name = (currentResume.personal.fullName || 'resume').replace(/[^\w -]+/g, '').trim().replace(/\s+/g, '-').toLowerCase();
      a.download = (name || 'resume') + '.pdf';
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 2000);
    } catch (ex) { alert(ex.message); }
    finally { btn.disabled = false; btn.textContent = old; }
  }

  function resetBuilder() {
    builderMounted = false;
    currentResume = null;
    $('#builder-root').innerHTML = '';
    mountBuilder();
  }

  function mountBuilder() {
    if (builderMounted) return;
    builderMounted = true;
    const root = $('#builder-root');
    root.innerHTML = `
      <header class="page-head wiz-head">
        <h1>Build a resume</h1>
        <p class="muted">Fill this in at your own pace — Previous never clears what you typed. When you're ready, we polish the wording and hand you a PDF.</p>
      </header>
      <div class="wiz-steps" id="wiz-steps" role="tablist"></div>
      <div id="wiz-panels"></div>`;

    const panels = $('#wiz-panels');
    const stepHTML = [personalStepHTML(), targetStepHTML(), experienceStepHTML(), educationStepHTML(), skillsStepHTML(), reviewStepHTML()];
    stepHTML.forEach((html, i) => {
      const panel = document.createElement('div');
      panel.className = 'wiz-panel' + (i === 0 ? '' : ' hidden');
      panel.dataset.step = String(i);
      panel.innerHTML = html + navHTML(i, stepHTML.length);
      panels.appendChild(panel);
    });

    wireTypeOptions();
    wireSkillsInput();
    $('#add-exp').onclick = addExperienceEntry;
    $('#add-edu').onclick = addEducationEntry;
    $('#add-proj').onclick = addProjectEntry;
    $('#add-cert').onclick = addCertEntry;
    $('#gen-btn').onclick = generateResume;

    $$('[data-nav]', panels).forEach(b => {
      b.onclick = () => {
        if (b.dataset.nav === 'next') { if (validateStep(builderStep)) goToStep(builderStep + 1); }
        else goToStep(builderStep - 1);
      };
    });

    buildTracker();
    goToStep(0);
  }

  /* ---------------- start: shared report or the app itself ---------------- */
  async function openPublic(t) {
    show($('#boot-splash'), false); show($('#boot-error'), false); show($('#app-view'), false); show($('#public-view'), true);
    const box = $('#public-result');
    box.innerHTML = '<p class="muted">Loading report…</p>';
    try {
      const r = await api('/public/report/' + encodeURIComponent(t));
      mountResult(box, r, { mode: 'public' });
    } catch (ex) {
      box.innerHTML = `<div class="empty"><h2>Report not found</h2><p class="muted">${esc(ex.message)}</p></div>`;
    }
  }

  async function boot() {
    const m = location.hash.match(/^#\/r\/([\w-]+)$/);
    if (m) return openPublic(m[1]);
    show($('#public-view'), false);
    try {
      if (!token) await startGuest();
      enterApp();
    } catch (ex) {
      show($('#boot-splash'), false); show($('#app-view'), false);
      $('#boot-msg').textContent = ex.message;
      show($('#boot-error'), true);
    }
  }
  $('#retry').onclick = () => { show($('#boot-error'), false); show($('#boot-splash'), true); boot(); };
  window.addEventListener('hashchange', boot);
  boot();
})();
