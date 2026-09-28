# RedPen

- `backend/`  Spring Boot API (Java 17, MySQL, Groq AI)
- `frontend/` static site (HTML/CSS/JS)

Deploy guide: `backend/DEPLOY.md`. Render reads `render.yaml` (rootDir: backend). On Netlify set Base directory to `frontend`.
Secrets are never committed: use environment variables (see `backend/DEPLOY.md`) or the local template at `backend/src/main/resources/application-local.properties.example`.
