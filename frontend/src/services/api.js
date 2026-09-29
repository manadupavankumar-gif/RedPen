// All communication with the backend lives here.

const API_URL = "https://redpen-m3z4.onrender.com";

export async function analyzeResume(resumeFile, jobDescription) {
  const formData = new FormData();
  formData.append("resume", resumeFile);
  formData.append("jobDescription", jobDescription);

  let response;
  try {
    response = await fetch(`${API_URL}/api/analyze`, {
      method: "POST",
      body: formData
    });
  } catch {
    throw new Error("Can't reach the server. Is the backend running?");
  }

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new Error(data.error || "Something went wrong. Please try again.");
  }

  return data;
}

// Past analyses saved in MySQL
export async function getHistory() {
  let response;

  try {
    response = await fetch(`${API_URL}/api/analyses`);
  } catch {
    throw new Error("Can't reach the server. Is the backend running?");
  }

  if (!response.ok) throw new Error("Could not load history.");

  return response.json();
}
