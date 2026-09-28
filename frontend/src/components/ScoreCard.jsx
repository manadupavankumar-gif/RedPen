// Shows the match percentage with a progress bar.
function getLevel(score) {
  if (score >= 75) return { label: "Strong match", cls: "good" };
  if (score >= 50) return { label: "Decent match", cls: "ok" };
  return { label: "Needs work", cls: "low" };
}

export default function ScoreCard({ score }) {
  const level = getLevel(score);
  return (
    <section className="card score-card">
      <h2>Match Score</h2>
      <p className={`score ${level.cls}`}>{score}%</p>
      <div className="progress" aria-label={`Match score ${score} percent`}>
        <div className={`progress-fill ${level.cls}`} style={{ width: `${score}%` }} />
      </div>
      <p className="muted">{level.label}</p>
    </section>
  );
}
