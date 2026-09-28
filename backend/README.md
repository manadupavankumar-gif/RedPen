# Backend – Spring Boot API

Java 17, Spring Boot 3, MySQL, Groq AI. Runs on http://localhost:8080

## Run in Spring Tool Suite
1. Start MySQL. The `resume_ai` database is created automatically.
2. File > Import > Maven > Existing Maven Projects > choose this `backend` folder.
3. Right-click `ResumeAiApplication` > Run As > Spring Boot App.

Defaults are in `src/main/resources/application.properties`. Put local credentials in the ignored `src/main/resources/application-local.properties` file (see the `.example` template); production secrets are environment variables documented in `DEPLOY.md`.

## API
| Method | Path | Auth |
|---|---|---|
| POST | /api/auth/guest (one private token per browser) | no |
| GET | /api/public/report/{token} | no (secret link) |
| POST | /api/resume/analyze (multipart: file or resumeText, level, role, jobDescription) | browser token |
| POST | /api/resume/compare (multipart: fileA, fileB, level, role, jobDescription) | browser token |
| GET | /api/resume/usage | browser token |
| POST | /api/resume/job-from-url | browser token |
| POST | /api/resume/rewrite | browser token |
| POST | /api/resume/history/{id}/generate (kind: summary, coverLetter, interview) | browser token |
| POST | /api/resume/export/docx | browser token |
| GET, DELETE | /api/resume/history, /api/resume/history/{id} | browser token |
| POST, DELETE | /api/resume/history/{id}/share | browser token |

Run the tests: `mvn test`.
