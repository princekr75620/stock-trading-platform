import express from 'express';
import path from 'path';
import { createServer as createViteServer } from 'vite';
import { spawn, execSync, ChildProcess } from 'child_process';

const PORT = Number(process.env.PORT || 3000);
const JAVA_PORT = Number(process.env.JAVA_PORT || 8765);
const HEALTH_POLL_INTERVAL_MS = 250;
const HEALTH_TIMEOUT_MS = Number(process.env.JAVA_STARTUP_TIMEOUT_MS || 20000);

let javaProcess: ChildProcess | null = null;
let shuttingDown = false;

function compileJavaBackend(): void {
  if (process.env.SKIP_JAVA_COMPILE === 'true') {
    console.log('[Server Gateway] SKIP_JAVA_COMPILE=true; using existing ./out classes.');
    return;
  }
  console.log('[Server Gateway] Compiling Java backend (compile.sh)...');
  // Fail fast: never catch and continue when Java compilation fails
  execSync('bash compile.sh', { stdio: 'inherit' });
}

async function probeJavaHealth(timeoutMs = 1500): Promise<{ ok: boolean; status: number; data?: any; error?: string }> {
  try {
    const res = await fetch(`http://127.0.0.1:${JAVA_PORT}/api/health`, {
      signal: AbortSignal.timeout(timeoutMs),
    });
    const text = await res.text();
    let data: any = null;
    try {
      data = JSON.parse(text);
    } catch {
      data = { raw: text };
    }
    return {
      ok: res.ok && data?.status === 'UP',
      status: res.status,
      data,
    };
  } catch (err: any) {
    return {
      ok: false,
      status: 503,
      error: err?.message || 'Java backend unreachable',
    };
  }
}

async function startAndAwaitJavaBackend(): Promise<void> {
  return new Promise((resolve, reject) => {
    console.log(`[Server Gateway] Launching authoritative Java HTTP Server on port ${JAVA_PORT}...`);
    const child = spawn('java', ['-cp', 'lib/*:out', 'server.TradingPlatformHttpServer', String(JAVA_PORT)], {
      stdio: 'inherit',
    });
    javaProcess = child;

    let settled = false;
    const deadline = Date.now() + HEALTH_TIMEOUT_MS;

    const failStartup = (err: Error) => {
      if (settled) return;
      settled = true;
      reject(err);
    };

    child.on('error', (err) => {
      javaProcess = null;
      failStartup(new Error(`Failed to spawn Java backend: ${err.message}`));
    });

    child.on('exit', (code, signal) => {
      javaProcess = null;
      const msg = `[Server Gateway] Java backend exited (code=${code}, signal=${signal})`;
      if (!settled) {
        failStartup(new Error(`${msg} before becoming ready`));
      } else if (!shuttingDown) {
        console.error(msg);
      }
    });

    const poll = async () => {
      if (settled) return;
      if (Date.now() > deadline) {
        failStartup(new Error(`Timed out after ${HEALTH_TIMEOUT_MS}ms waiting for Java /api/health readiness on port ${JAVA_PORT}`));
        return;
      }

      const health = await probeJavaHealth(1000);
      if (health.ok) {
        if (!settled) {
          settled = true;
          console.log(`[Server Gateway] Java backend verified healthy on port ${JAVA_PORT}`);
          resolve();
        }
        return;
      }

      setTimeout(poll, HEALTH_POLL_INTERVAL_MS);
    };

    setTimeout(poll, HEALTH_POLL_INTERVAL_MS);
  });
}

function cleanup(): void {
  shuttingDown = true;
  if (javaProcess) {
    try {
      javaProcess.kill('SIGTERM');
    } catch {
      // ignore
    }
    javaProcess = null;
  }
}

process.on('SIGINT', () => {
  cleanup();
  process.exit(0);
});
process.on('SIGTERM', () => {
  cleanup();
  process.exit(0);
});
process.on('SIGHUP', () => {
  cleanup();
  process.exit(0);
});
process.on('exit', cleanup);

async function startServer(): Promise<void> {
  compileJavaBackend();
  await startAndAwaitJavaBackend();

  const app = express();
  app.use(express.json());

  // Gateway health check reflects real downstream Java + JDBC health (never reports UP while Java is down)
  app.get('/api/health', async (_req, res) => {
    const javaAlive = javaProcess !== null;
    if (!javaAlive) {
      return res.status(503).json({
        status: 'DOWN',
        javaRunning: false,
        backend: `Java SE 17 HTTP Server (Port ${JAVA_PORT}) offline`,
        primaryArchitecture: 'Express Gateway -> Java HttpServer + ServletBridge -> JDBC',
        timestamp: new Date().toISOString(),
      });
    }

    const health = await probeJavaHealth(1500);
    if (!health.ok) {
      return res.status(503).json({
        status: 'DOWN',
        javaRunning: false,
        backend: `Java SE 17 HTTP Server (Port ${JAVA_PORT}) unavailable`,
        error: health.error || health.data?.error || `HTTP ${health.status}`,
        primaryArchitecture: 'Express Gateway -> Java HttpServer + ServletBridge -> JDBC',
        timestamp: new Date().toISOString(),
      });
    }

    return res.status(200).json({
      status: 'UP',
      javaRunning: true,
      backend: `Java SE 17 HTTP Server + ServletBridge (Port ${JAVA_PORT})`,
      databaseConnected: Boolean(health.data?.databaseConnected),
      databaseEngine: health.data?.databaseEngine || 'Unknown',
      primaryArchitecture: 'Express Gateway -> Java HttpServer + ServletBridge -> JDBC',
      timestamp: new Date().toISOString(),
    });
  });

  // Reverse proxy all remaining /api/* traffic to Java backend on JAVA_PORT
  app.use('/api', async (req, res) => {
    if (javaProcess === null) {
      return res.status(503).json({
        success: false,
        error: `Authoritative Java backend (port ${JAVA_PORT}) is offline.`,
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
      return res.send(data);
    } catch (err: any) {
      console.error(`[API Gateway] Proxy error forwarding to Java backend (port ${JAVA_PORT}):`, err?.message || err);
      return res.status(502).json({
        success: false,
        error: `Bad Gateway: Unable to reach authoritative Java backend at port ${JAVA_PORT}.`,
      });
    }
  });

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
    console.log(`[Server Gateway] Online Stock Trading Platform listening on http://0.0.0.0:${PORT}`);
  });
}

startServer().catch((err) => {
  console.error('[Server Gateway] Fatal startup failure:', err?.message || err);
  cleanup();
  process.exit(1);
});
