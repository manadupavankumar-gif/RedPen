import { Link } from "react-router-dom";

export default function Navbar() {
  return (
    <header className="navbar">
      <Link to="/" className="logo">📄 Resume Matcher</Link>
      <nav className="nav-links">
        <Link to="/matcher" className="nav-link">Analyze</Link>
        <Link to="/history" className="nav-link">History</Link>
      </nav>
    </header>
  );
}
