import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { analyzeResume } from "../services/api.js";
import Loader from "../components/Loader.jsx";

export default function ResumeMatcher() {
  const [file, setFile] = useState(null);
  const [jobDescription, setJobDescription] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const navigate = useNavigate();

  function handleFileChange(e) {
    const chosen = e.target.files[0];
    setError("");
    if (chosen && chosen.type !== "application/pdf") {
      setError("Please choose a PDF file.");
      setFile(null);
      return;
    }
    setFile(chosen || null);
  }

  async function handleAnalyze() {
    // Check inputs before calling the backend
    if (!file) return setError("Please upload your resume as a PDF.");
    if (jobDescription.trim().length < 30)
      return setError("Please paste a longer job description.");

    setError("");
    setLoading(true);
    try {
      const result = await analyzeResume(file, jobDescription);
      // Send the result to the Results page
      navigate("/results", { state: { result } });
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <Loader text="Reading your resume and comparing it with the job..." />;

  return (
    <div className="card form-card">
      <h1>Resume Matcher</h1>

      <label className="label" htmlFor="resume">1. Upload your resume (PDF)</label>
      <input id="resume" type="file" accept="application/pdf" onChange={handleFileChange} />
      {file && <p className="muted">Selected: {file.name}</p>}

      <label className="label" htmlFor="job">2. Paste the job description</label>
      <textarea
        id="job"
        rows={10}
        placeholder="Paste the full job description here..."
        value={jobDescription}
        onChange={(e) => setJobDescription(e.target.value)}
      />

      {error && <p className="error" role="alert">{error}</p>}

      <button className="btn" onClick={handleAnalyze}>Analyze</button>
    </div>
  );
}
