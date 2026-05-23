package com.client.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HostAdapterController
{
    @GetMapping(value = "/host/shutdown", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> shutdownPage()
    {
        return ResponseEntity.ok("""
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Client Shutdown</title>
                  <style>
                    :root {
                      --bg: linear-gradient(135deg, #f4efe7 0%, #d9e6f2 100%);
                      --panel: rgba(255, 255, 255, 0.92);
                      --text: #1f2a37;
                      --muted: #5a6a7a;
                      --accent: #b6482f;
                      --accent-strong: #8f311c;
                      --border: rgba(31, 42, 55, 0.12);
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      min-height: 100vh;
                      display: grid;
                      place-items: center;
                      padding: 24px;
                      background: var(--bg);
                      color: var(--text);
                      font-family: Georgia, "Times New Roman", serif;
                    }
                    .panel {
                      width: min(560px, 100%);
                      padding: 32px;
                      border-radius: 24px;
                      background: var(--panel);
                      border: 1px solid var(--border);
                      box-shadow: 0 28px 80px rgba(31, 42, 55, 0.16);
                    }
                    h1 {
                      margin: 0 0 12px;
                      font-size: clamp(2rem, 5vw, 3rem);
                      line-height: 0.95;
                    }
                    p {
                      margin: 0 0 14px;
                      color: var(--muted);
                      font-size: 1.02rem;
                      line-height: 1.6;
                    }
                    button {
                      margin-top: 12px;
                      border: 0;
                      border-radius: 999px;
                      padding: 14px 22px;
                      background: var(--accent);
                      color: #fff;
                      font: inherit;
                      font-weight: 700;
                      cursor: pointer;
                    }
                    button:hover { background: var(--accent-strong); }
                    button:disabled { opacity: 0.6; cursor: wait; }
                    #status { margin-top: 18px; min-height: 1.5em; }
                  </style>
                </head>
                <body>
                  <main class="panel">
                    <h1>Shutdown Adapter</h1>
                    <p>This page is a host-facing adapter for wrappers or local automation that need an explicit in-process shutdown surface.</p>
                    <p>It calls <code>POST /api/system/shutdown</code> and then waits for the client process to exit cleanly.</p>
                    <button id="shutdown-button" type="button">Shut Down Client</button>
                    <p id="status" aria-live="polite"></p>
                  </main>
                  <script>
                    const button = document.getElementById('shutdown-button');
                    const status = document.getElementById('status');
                    button.addEventListener('click', async () => {
                      button.disabled = true;
                      status.textContent = 'Requesting shutdown...';
                      try {
                        const response = await fetch('/api/system/shutdown', { method: 'POST' });
                        if (!response.ok) {
                          throw new Error('HTTP ' + response.status);
                        }
                        status.textContent = 'Shutdown accepted. The client should stop in a moment.';
                      } catch (error) {
                        button.disabled = false;
                        status.textContent = 'Shutdown request failed: ' + error.message;
                      }
                    });
                  </script>
                </body>
                </html>
                """);
    }
}
