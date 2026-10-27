import express, { Request, Response } from 'express';
import path from 'path';
import { createServer as createViteServer } from 'vite';
import { spawn, ChildProcess } from 'child_process';
import fs from 'fs';

const PORT = 3000;
const JAVA_PORT = 8765;
let javaProcess: ChildProcess | null = null;
let javaAvailable = false;

/**
 * Supervised Java EE REST Engine Lifecycle Manager.
 * Spawns the authoritative Java SE 17 HTTP Server hosting all Servlets,
 * AuthenticationFilter, DAOs, and JDBC persistence on port 8765.
 */
function startJavaBackendIfAvailable() {
  const targetClass = path.join(process.cwd(), 'out', 'server', 'TradingPlatformHttpServer.class');
  if (!fs.existsSync(targetClass)) {
    try {
      const { execSync } = require('child_process');
      console.log('[Server Gateway] Compiling Java classes for REST backend...');
      execSync('bash compile.sh', { stdio: 'inherit' });
    } catch (e: any) {
      console.warn('[Server Gateway] Note: automatic Java compilation skipped:', e.message);
      return;
    }
  }

  try {
    console.log(`[Server Gateway] Launching authoritative Java EE REST Engine on port ${JAVA_PORT}...`);
    javaProcess = spawn('java', ['-cp', 'lib/*:out', 'server.TradingPlatformHttpServer', String(JAVA_PORT)], {
      stdio: 'inherit',
    });

    javaProcess.on('exit', (code) => {
      console.log(`[Server Gateway] Java backend process exited with code ${code}`);
      javaProcess = null;
      javaAvailable = false;
    });

    javaProcess.on('error', (err) => {
      console.warn('[Server Gateway] Java process spawn error:', err.message);
      javaProcess = null;
      javaAvailable = false;
    });

    // Check availability periodically
    const checkAvailability = async () => {
      try {
        const res = await fetch(`http://127.0.0.1:${JAVA_PORT}/api/stocks`, { signal: AbortSignal.timeout(1000) });
        if (res.ok) {
          if (!javaAvailable) {
            console.log(`[Server Gateway] ✓ Authoritative Java EE REST backend active & responding on port ${JAVA_PORT}`);
          }
          javaAvailable = true;
        } else {
          javaAvailable = false;
        }
      } catch {
        javaAvailable = false;
      }
    };

    setTimeout(checkAvailability, 1200);
    setInterval(checkAvailability, 5000);
  } catch (err: any) {
    console.warn('[Server Gateway] Could not start Java backend:', err.message);
    javaAvailable = false;
  }
}

function cleanup() {
  if (javaProcess) {
    try {
      javaProcess.kill('SIGTERM');
    } catch {}
    javaProcess = null;
  }
}
process.on('SIGINT', () => { cleanup(); process.exit(); });
process.on('SIGTERM', () => { cleanup(); process.exit(); });
process.on('exit', cleanup);

// ---------------------------------------------------------------------------
// MAIN SERVER GATEWAY ENTRY POINT
// ---------------------------------------------------------------------------
async function startServer() {
  startJavaBackendIfAvailable();

  const app = express();
  app.use(express.json());

  // 1. Health & Architecture Telemetry Endpoint
  app.get('/api/health', (_req, res) => {
    res.json({
      status: 'UP',
      backend: javaAvailable
        ? `Java SE 17 REST Engine (Port ${JAVA_PORT})`
        : 'Java Backend Initializing',
      javaRunning: javaAvailable,
      primaryArchitecture: 'Java EE Servlets / JDBC / DAO Pattern',
      timestamp: new Date().toISOString(),
    });
  });

  // ---------------------------------------------------------------------------
  // AUTHORITATIVE JAVA EE BACKEND REVERSE PROXY GATEWAY
  // All /api/* traffic is strictly routed to the authoritative Java REST server
  // running Servlets, AuthenticationFilter, and JDBC persistence on port 8765.
  // ---------------------------------------------------------------------------
  app.use('/api', async (req, res) => {
    if (!javaAvailable) {
      return res.status(503).json({
        success: false,
        error: `The authoritative Java EE Backend (Port ${JAVA_PORT}) is currently initializing or offline. All trading, authentication, and database operations require the authoritative Java backend.`,
      });
    }

    try {
      const targetUrl = `http://127.0.0.1:${JAVA_PORT}${req.originalUrl}`;
      const hopByHopHeaders = new Set([
        'connection',
        'keep-alive',
        'proxy-authenticate',
        'proxy-authorization',
        'te',
        'trailer',
        'transfer-encoding',
        'upgrade',
        'host',
        'content-length',
      ]);

      const forwardHeaders: Record<string, string> = {};
      for (const [key, val] of Object.entries(req.headers)) {
        const lowerKey = key.toLowerCase();
        if (val && !hopByHopHeaders.has(lowerKey)) {
          forwardHeaders[lowerKey] = Array.isArray(val) ? val.join('; ') : String(val);
        }
      }

      const fetchOptions: RequestInit = {
        method: req.method,
        headers: forwardHeaders,
      };

      if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(req.method.toUpperCase()) && req.body && Object.keys(req.body).length > 0) {
        fetchOptions.body = JSON.stringify(req.body);
        forwardHeaders['content-type'] = 'application/json';
      }

      const javaRes = await fetch(targetUrl, fetchOptions);

      // Forward response headers including cookies and security headers
      if (typeof (javaRes.headers as any).getSetCookie === 'function') {
        const cookies = (javaRes.headers as any).getSetCookie();
        if (cookies && cookies.length > 0) {
          res.setHeader('Set-Cookie', cookies);
        }
      }
      javaRes.headers.forEach((val, key) => {
        const lower = key.toLowerCase();
        if (lower !== 'set-cookie' && lower !== 'transfer-encoding' && lower !== 'content-length' && lower !== 'connection') {
          res.setHeader(key, val);
        }
      });

      res.status(javaRes.status);
      const data = await javaRes.text();
      res.send(data);
    } catch (err: any) {
      console.error(`[API Gateway] Error proxying request to authoritative Java backend (port ${JAVA_PORT}):`, err.message);
      return res.status(502).json({
        success: false,
        error: `Bad Gateway: Unable to reach authoritative Java backend at port ${JAVA_PORT}.`,
      });
    }
  });

  // ---------------------------------------------------------------------------
  // VITE DEV MIDDLEWARE / STATIC PROD SERVING
  // ---------------------------------------------------------------------------
  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`[Server Gateway] Online Stock Trading Platform running at http://0.0.0.0:${PORT}`);
  });
}

startServer();
