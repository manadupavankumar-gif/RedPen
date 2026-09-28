// A card with a simple bulleted list (used for strengths and suggestions).
export default function BulletCard({ title, items }) {
  if (items.length === 0) return null;
  return (
    <section className="card">
      <h2>{title}</h2>
      <ul className="bullets">
        {items.map((item, index) => (
          <li key={index}>{item}</li>
        ))}
      </ul>
    </section>
  );
}
