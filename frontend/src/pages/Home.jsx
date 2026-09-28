import { Link } from "react-router-dom";

export default function Home() {
  return (
    <div className="hero">
      <h1>AI Resume & Job Matcher</h1>
      <p className="lead">
        Upload your resume, paste a job description, and see how well you match.
        You'll get a score, the skills you have, the skills you're missing, and
        tips to improve your resume for that job.
      </p>
      <Link to="/matcher" className="btn">Get Started</Link>

      <div className="steps">
        <div className="card">
          <span className="step-icon">📤</span>
          <h3>Upload</h3>
          <p className="muted">Add your resume as a PDF.</p>
        </div>
        <div className="card">
          <span className="step-icon">📋</span>
          <h3>Paste</h3>
          <p className="muted">Paste the job description.</p>
        </div>
        <div className="card">
          <span className="step-icon">✨</span>
          <h3>Improve</h3>
          <p className="muted">Get your score and AI tips.</p>
        </div>
      </div>
    </div>
  );
}
