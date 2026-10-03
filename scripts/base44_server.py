#!/usr/bin/env python3
"""Base44 build & status server for ARIA Voice Assistant (Android).

Builds the debug APK in a background thread and serves a status page on
port 3000 with a download link once the build succeeds.
"""

import http.server
import json
import os
import subprocess
import threading

PORT = 3000
APP_DIR = "/app"
APK_PATH = f"{APP_DIR}/app/build/outputs/apk/debug/app-debug.apk"
KEYSTORE_PATH = f"{APP_DIR}/debug.keystore"
LOCAL_PROPS_PATH = f"{APP_DIR}/local.properties"
ANDROID_HOME = os.environ.get("ANDROID_HOME", "/opt/android-sdk")

build_status = "building"  # building | success | failed
build_logs: list[str] = []
build_lock = threading.Lock()


def generate_keystore():
    if not os.path.exists(KEYSTORE_PATH):
        subprocess.run(
            [
                "keytool", "-genkey", "-v",
                "-keystore", KEYSTORE_PATH,
                "-storepass", "android",
                "-alias", "androiddebugkey",
                "-keypass", "android",
                "-dname", "CN=Android Debug,O=Android,C=US",
                "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "10000",
            ],
            check=True, capture_output=True,
        )


def write_local_properties():
    with open(LOCAL_PROPS_PATH, "w") as f:
        f.write(f"sdk.dir={ANDROID_HOME}\n")


def write_env_file():
    """Create .env from the GEMINI_API_KEY env var for the Gradle secrets plugin."""
    gemini_key = os.environ.get("GEMINI_API_KEY", "")
    if gemini_key:
        with open(f"{APP_DIR}/.env", "w") as f:
            f.write(f"GEMINI_API_KEY={gemini_key}\n")


def run_build():
    global build_status, build_logs
    try:
        generate_keystore()
        write_local_properties()
        write_env_file()

        result = subprocess.run(
            ["gradle", ":app:assembleDebug", "--no-daemon", "--stacktrace"],
            cwd=APP_DIR,
            capture_output=True,
            text=True,
            timeout=900,
        )

        with build_lock:
            build_logs = (result.stdout + result.stderr).splitlines()[-150:]

        if result.returncode == 0 and os.path.exists(APK_PATH):
            build_status = "success"
        else:
            build_status = "failed"
    except subprocess.TimeoutExpired:
        with build_lock:
            build_logs.append("Build timed out after 15 minutes.")
        build_status = "failed"
    except Exception as e:
        with build_lock:
            build_logs.append(f"Build error: {e}")
        build_status = "failed"


def start_build():
    threading.Thread(target=run_build, daemon=True).start()


HTML_PAGE = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>A.R.I.A. Voice Assistant — Build Status</title>
<style>
  :root {
    --bg: #0a0e1a;
    --card: #111827;
    --accent: #00e5ff;
    --accent-dim: #0288d1;
    --text: #e0e7ff;
    --muted: #6b7280;
    --success: #00e676;
    --error: #ff5252;
    --building: #ffc107;
  }
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    background: var(--bg);
    color: var(--text);
    font-family: 'Courier New', monospace;
    min-height: 100vh;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 2rem 1rem;
  }
  .header { text-align: center; margin-bottom: 2rem; }
  .header h1 {
    font-size: 2rem;
    letter-spacing: 4px;
    color: var(--accent);
    text-shadow: 0 0 20px rgba(0,229,255,0.3);
  }
  .header p { color: var(--muted); margin-top: .5rem; font-size: .9rem; }
  .card {
    background: var(--card);
    border: 1px solid rgba(0,229,255,0.15);
    border-radius: 12px;
    padding: 2rem;
    width: 100%;
    max-width: 640px;
    margin-bottom: 1.5rem;
  }
  .status-row {
    display: flex;
    align-items: center;
    gap: 1rem;
    margin-bottom: 1.5rem;
  }
  .status-dot {
    width: 14px; height: 14px;
    border-radius: 50%;
    flex-shrink: 0;
  }
  .status-dot.building { background: var(--building); animation: pulse 1s infinite; }
  .status-dot.success { background: var(--success); box-shadow: 0 0 10px var(--success); }
  .status-dot.failed  { background: var(--error);   box-shadow: 0 0 10px var(--error); }
  @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.3} }
  .status-text { font-size: 1.1rem; font-weight: bold; }
  .status-text.building { color: var(--building); }
  .status-text.success { color: var(--success); }
  .status-text.failed  { color: var(--error); }
  .btn {
    display: inline-block;
    padding: .75rem 1.5rem;
    border-radius: 8px;
    text-decoration: none;
    font-family: inherit;
    font-size: .95rem;
    font-weight: bold;
    cursor: pointer;
    border: none;
    transition: all .2s;
  }
  .btn-primary {
    background: var(--accent);
    color: #000;
  }
  .btn-primary:hover { box-shadow: 0 0 20px rgba(0,229,255,0.4); }
  .btn-secondary {
    background: transparent;
    color: var(--accent);
    border: 1px solid var(--accent-dim);
  }
  .btn-secondary:hover { background: rgba(0,229,255,0.1); }
  .btn:disabled { opacity: .4; cursor: not-allowed; }
  .btn-row { display: flex; gap: 1rem; flex-wrap: wrap; }
  .info { color: var(--muted); font-size: .85rem; line-height: 1.6; }
  .info strong { color: var(--text); }
  .info ul { margin-left: 1.5rem; margin-top: .5rem; }
  .info li { margin-bottom: .3rem; }
  .logs-toggle {
    color: var(--accent-dim);
    cursor: pointer;
    font-size: .85rem;
    margin-top: 1rem;
    user-select: none;
  }
  .logs-toggle:hover { color: var(--accent); }
  .logs {
    margin-top: 1rem;
    max-height: 300px;
    overflow-y: auto;
    background: #0d1117;
    border-radius: 8px;
    padding: 1rem;
    font-size: .8rem;
    line-height: 1.5;
    white-space: pre-wrap;
    word-break: break-word;
    display: none;
  }
  .logs.open { display: block; }
  .apk-size { color: var(--muted); font-size: .8rem; margin-top: .5rem; }
</style>
</head>
<body>
  <div class="header">
    <h1>A.R.I.A.</h1>
    <p>Autonomous Reactive Intelligent Assistant — Android Build</p>
  </div>

  <div class="card">
    <div class="status-row">
      <div class="status-dot building" id="dot"></div>
      <div class="status-text building" id="statusText">Building APK…</div>
    </div>
    <div class="btn-row" id="actions"></div>
    <div class="apk-size" id="apkSize"></div>
    <div class="logs-toggle" id="logsToggle" onclick="toggleLogs()">▸ Show build logs</div>
    <div class="logs" id="logs">Loading…</div>
  </div>

  <div class="card">
    <div class="info">
      <strong>Project</strong>
      <p style="margin-top:.3rem">JARVIS-inspired Voice Assistant with Speech Recognition, Text-To-Speech, Weather, Reminders, Wikipedia, and Python Code Tutorial.</p>
    </div>
  </div>

  <div class="card">
    <div class="info">
      <strong>Tech Stack</strong>
      <ul>
        <li>Kotlin + Jetpack Compose</li>
        <li>Android SDK 36 (minSdk 24)</li>
        <li>Firebase AI (Gemini) + reCAPTCHA AppCheck</li>
        <li>Room, Retrofit/OkHttp, Moshi</li>
      </ul>
    </div>
  </div>

  <div class="card">
    <div class="info">
      <strong>Install on Device</strong>
      <ul>
        <li>Download the APK below once the build finishes</li>
        <li>Enable "Install from unknown sources" in Android settings</li>
        <li>Open the APK file on your device to install</li>
        <li>For Gemini AI features, set your <code>GEMINI_API_KEY</code> in the Base44 Secrets panel</li>
      </ul>
    </div>
  </div>

<script>
function fmtSize(b) {
  if (b < 1024) return b + ' B';
  if (b < 1048576) return (b/1024).toFixed(1) + ' KB';
  return (b/1048576).toFixed(1) + ' MB';
}

async function poll() {
  try {
    const r = await fetch('/status');
    const d = await r.json();
    const dot = document.getElementById('dot');
    const txt = document.getElementById('statusText');
    const actions = document.getElementById('actions');
    const size = document.getElementById('apkSize');

    dot.className = 'status-dot ' + d.status;
    txt.className = 'status-text ' + d.status;

    if (d.status === 'building') {
      txt.textContent = 'Building APK…';
      actions.innerHTML = '';
      size.textContent = '';
    } else if (d.status === 'success') {
      txt.textContent = 'Build successful!';
      actions.innerHTML = '<a class="btn btn-primary" href="/app-debug.apk" download>⬇ Download APK</a>' +
        '<button class="btn btn-secondary" onclick="rebuild()">↻ Rebuild</button>';
      size.textContent = d.apkSize ? 'Size: ' + fmtSize(d.apkSize) : '';
    } else {
      txt.textContent = 'Build failed';
      actions.innerHTML = '<button class="btn btn-secondary" onclick="rebuild()">↻ Retry build</button>';
      size.textContent = '';
    }
    if (d.status !== 'building') setTimeout(fetchLogs, 100);
  } catch(e) {}
  if (document.getElementById('statusText').classList.contains('building')) {
    setTimeout(poll, 3000);
  }
}

async function fetchLogs() {
  try {
    const r = await fetch('/logs');
    document.getElementById('logs').textContent = await r.text();
  } catch(e) {}
}

async function rebuild() {
  await fetch('/rebuild', {method:'POST'});
  document.getElementById('dot').className = 'status-dot building';
  document.getElementById('statusText').className = 'status-text building';
  document.getElementById('statusText').textContent = 'Rebuilding…';
  document.getElementById('actions').innerHTML = '';
  document.getElementById('apkSize').textContent = '';
  setTimeout(poll, 2000);
}

function toggleLogs() {
  const l = document.getElementById('logs');
  const t = document.getElementById('logsToggle');
  l.classList.toggle('open');
  t.textContent = l.classList.contains('open') ? '▾ Hide build logs' : '▸ Show build logs';
}

poll();
</script>
</body>
</html>
"""


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path in ("/", "/index.html"):
            self._send_html(HTML_PAGE)
        elif self.path == "/status":
            apk_size = os.path.getsize(APK_PATH) if os.path.exists(APK_PATH) else 0
            self._send_json({"status": build_status, "apkSize": apk_size})
        elif self.path == "/logs":
            self._send_text("\n".join(build_logs))
        elif self.path == "/app-debug.apk":
            if build_status == "success" and os.path.exists(APK_PATH):
                self.send_response(200)
                self.send_header("Content-Type", "application/vnd.android.package-archive")
                self.send_header("Content-Disposition", 'attachment; filename="aria-voice-assistant-debug.apk"')
                self.end_headers()
                with open(APK_PATH, "rb") as f:
                    self.wfile.write(f.read())
            else:
                self.send_error(404)
        else:
            self.send_error(404)

    def do_POST(self):
        if self.path == "/rebuild":
            global build_status
            if build_status != "building":
                build_status = "building"
                start_build()
            self._send_json({"status": "rebuilding"})
        else:
            self.send_error(404)

    def _send_html(self, body):
        self.send_response(200)
        self.send_header("Content-Type", "text/html")
        self.end_headers()
        self.wfile.write(body.encode())

    def _send_json(self, obj):
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(obj).encode())

    def _send_text(self, body):
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.end_headers()
        self.wfile.write(body.encode())

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    start_build()
    server = http.server.HTTPServer(("0.0.0.0", PORT), Handler)
    print(f"Serving on port {PORT}", flush=True)
    server.serve_forever()
