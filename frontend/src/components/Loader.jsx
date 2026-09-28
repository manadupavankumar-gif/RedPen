export default function Loader({ text }) {
  return (
    <div className="loader" role="status">
      <div className="spinner" />
      <p>{text}</p>
    </div>
  );
}
