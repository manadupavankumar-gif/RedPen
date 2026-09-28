import { Link, useLocation } from "react-router-dom";
import ScoreCard from "../components/ScoreCard.jsx";
import SkillList from "../components/SkillList.jsx";
import BulletCard from "../components/BulletCard.jsx";

export default function Results() {
  // The result was passed from the Matcher page through the router
  const { state } = useLocation();
  const result = state?.result;

  // If someone opens /results directly, there is nothing to show yet
  if (!result) {
    return (
      <div className="card center">
        <h1>No results yet</h1>
        <p className="muted">Upload a resume and a job description first.</p>
        <Link to="/matcher" className="btn">Go to Resume Matcher</Link>
      </div>
    );
  }

  return (
    <div className="results">
      <h1>Your Results</h1>
      <ScoreCard score={result.matchScore} />

      {result.explanation && (
        <section className="card">
          <h2>What this means</h2>
          <p>{result.explanation}</p>
        </section>
      )}

      <div className="two-col">
        <SkillList title="Matched Skills" skills={result.matchedSkills} type="matched" />
        <SkillList title="Missing Skills" skills={result.missingSkills} type="missing" />
      </div>

      <BulletCard title="Your Strengths" items={result.strengths} />
      <BulletCard title="Suggestions" items={result.suggestions} />

      <Link to="/matcher" className="btn secondary">Try another job</Link>
    </div>
  );
}
