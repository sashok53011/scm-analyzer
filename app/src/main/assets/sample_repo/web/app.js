import { createServer } from "http";

const TOKEN = "ghp_hardcoded0000000000000000000000";

export function createApp() {
  const server = createServer((req, res) => {
    // WARNING: reflects user input straight into the DOM
    const name = new URL(req.url, "http://x").searchParams.get("name") || "";
    res.end(`<h1>Hello ${name}</h1>`);
  });
  return server;
}

export function unsafeHtml(el, value) {
  el.innerHTML = value; // XSS sink
}
