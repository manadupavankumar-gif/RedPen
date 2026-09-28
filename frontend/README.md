# Frontend – Redpen web app

Static HTML, CSS and JavaScript. The Vite build bundles the app for production; the Docker image serves the source files directly.

## Run (start the backend first)
Pick one:
- VS Code: install "Live Server", right-click `index.html` > Open with Live Server
- Terminal, Python: `python -m http.server 5500` inside this folder, then open http://localhost:5500
- Terminal, Node: `npx serve -l 5500 .` inside this folder

Serve it over HTTP (one of the options above) rather than double-clicking the file: share links use the page address.
There is no login. The first visit creates a private token that is kept in this browser (localStorage).
If the backend is not on http://localhost:8080, edit `js/config.js`.

## Build for static hosting
From this directory, run `npm ci` and `npm run build`. Set the deployed backend URL in `js/config.js` before building, then publish `dist/`.
For Netlify, set the base directory to `frontend`, the build command to `npm ci && npm run build`, and the publish directory to `dist`.
