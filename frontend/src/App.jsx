import { Routes, Route } from "react-router-dom";
import Navbar from "./components/Navbar.jsx";
import Home from "./pages/Home.jsx";
import ResumeMatcher from "./pages/ResumeMatcher.jsx";
import Results from "./pages/Results.jsx";
import History from "./pages/History.jsx";

// Each <Route> maps a URL to a page component.
export default function App() {
  return (
    <>
      <Navbar />

      <main className="container">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/matcher" element={<ResumeMatcher />} />
          <Route path="/results" element={<Results />} />
          <Route path="/history" element={<History />} />
        </Routes>
      </main>

      <footer>
        © 2026 Manadu Pavan Kumar. All Rights Reserved.
      </footer>
    </>
  );
}
