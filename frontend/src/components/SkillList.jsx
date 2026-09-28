// Reusable list for matched (✓) and missing (✗) skills.
export default function SkillList({ title, skills, type }) {
  const icon = type === "matched" ? "✓" : "✗";
  return (
    <section className="card">
      <h2>{title}</h2>
      {skills.length === 0 ? (
        <p className="muted">Nothing to show here.</p>
      ) : (
        <ul className="chips">
          {skills.map((skill) => (
            <li key={skill} className={`chip ${type}`}>
              {icon} {skill}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
