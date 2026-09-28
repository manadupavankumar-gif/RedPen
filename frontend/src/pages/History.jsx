import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { getHistory } from "../services/api.js";
import Loader from "../components/Loader.jsx";

export default function History() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const navigate = useNavigate();

  // Runs once when the page opens
  useEffect(() => {
    getHistory()
      .then(setItems)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <Loader text="Loading your past analyses..." />;

  return (
    <div>
      <h1>History</h1>
      {error && <p className="error">{error}</p>}

      {!error && items.length === 0 && (
        <div className="card center">
          <p className="muted">No analyses yet.</p>
          <Link to="/matcher" className="btn">Analyze a resume</Link>
        </div>
      )}

      {items.map((item) => (
        <div className="card history-item" key={item.id}>
          <div>
            <h3>{item.resumeFileName || "Resume"}</h3>
            <p className="muted">{new Date(item.createdAt).toLocaleString()}</p>
          </div>
          <strong className="history-score">{item.matchScore}%</strong>
          <button className="btn secondary" onClick={() => navigate("/results", { state: { result: item } })}>
            View
          </button>
        </div>
      ))}
    </div>
  );
}
