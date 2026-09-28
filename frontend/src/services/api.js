// All communication with the backend lives here.

export async function analyzeResume(resumeFile, jobDescription) {
  // FormData is how browsers send files
  const formData = new FormData();
  formData.append("resume", resumeFile);
  formData.append("jobDescription", jobDescription);

  let response;
  try {
    response = await fetch("/api/analyze", { method: "POST", body: formData });
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
    response = await fetch("/api/analyses");
  } catch {
    throw new Error("Can't reach the server. Is the backend running?");
  }
  if (!response.ok) throw new Error("Could not load history.");
  return response.json();
}
