FROM nginx:1.27-alpine
COPY . /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Same-origin API (nginx proxies /api), so no backend URL is needed in the browser.
RUN echo "window.API_BASE = '';" > /usr/share/nginx/html/js/config.js
EXPOSE 80
