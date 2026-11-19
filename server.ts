import 'dotenv/config';
import express from 'express';
import path from 'path';
import { createServer as createViteServer } from 'vite';
import { spawn, execSync, ChildProcess } from 'child_process';

const rawPort = process.env.PORT;
const PORT = Number(
  rawPort && rawPort !== process.env.NGINX_PORT
    ? rawPort
    : process.env.DEFAULT_APP_PORT || 3000
);
const JAVA_HOST = process.env.JAVA_HOST || '127.0.0.1';
const JAVA_PORT = Number(process.env.JAVA_PORT || 8765);
const HEALTH_POLL_INTERVAL_MS = 250;
const HEALTH_TIMEOUT_MS = Number(process.env.JAVA_STARTUP_TIMEOUT_MS || 20000);

let javaProcess: ChildProcess | null = null;
let shuttingDown = false;

function validateEnvironmentConfig(): void {
  if (!Number.isInteger(PORT) || PORT < 1 || PORT > 65535) {
    throw new Error('PORT must be a valid TCP port (1-65535).');
  }
  if (!Number.isInteger(JAVA_PORT) || JAVA_PORT < 1 || JAVA_PORT > 65535) {
    throw new Error('JAVA_PORT must be a valid TCP port (1-65535).');
  }

  const appEnv = (process.env.APP_ENV || process.env.NODE_ENV || 'development').trim().toLowerCase();
  if (appEnv !== 'production') {
    return;
  }

  const dbType = (process.env.DB_TYPE || '').trim().toLowerCase();
  if (dbType !== 'mysql' && dbType !== 'sqlite') {
    throw new Error("Production startup requires DB_TYPE to be explicitly set to 'mysql' or 'sqlite'.");
  }

  if (dbType === 'mysql') {
    const host = (process.env.MYSQL_HOST || process.env.DB_HOST || '').trim();
    const dbName = (process.env.MYSQL_DATABASE || process.env.DB_NAME || '').trim();
    const user = (process.env.MYSQL_USER || process.env.DB_USER || '').trim();
    const password = process.env.MYSQL_PASSWORD || process.env.DB_PASSWORD || '';
    const sslMode = (process.env.DB_SSL_MODE || process.env.MYSQL_SSL_MODE || 'VERIFY_IDENTITY').trim().toUpperCase();

    if (!host || !dbName || !user || !password) {
      throw new Error('Production MySQL configuration is incomplete (MYSQL_HOST, MYSQL_DATABASE, MYSQL_USER, MYSQL_PASSWORD required).');
    }
    if (user.toLowerCase() === 'root') {
      throw new Error("Production MySQL startup rejected: 'root' user is forbidden; configure a dedicated least-privilege database user.");
    }
    const lowerPass = password.trim().toLowerCase();
    if (
      password.length < 8 ||
      lowerPass === 'root' ||
      lowerPass === 'password' ||
      lowerPass === 'changeme' ||
      lowerPass === 'admin123' ||
      lowerPass === 'replace_with_strong_database_password'
    ) {
      throw new Error('Production MySQL password rejected: must be at least 8 characters and not a default placeholder.');
    }
    if (sslMode !== 'VERIFY_IDENTITY' && sslMode !== 'VERIFY_CA') {
      throw new Error('Production MySQL startup requires certificate-verified TLS (DB_SSL_MODE=VERIFY_IDENTITY or VERIFY_CA).');
    }
  }
}

function resolveJavaBinary(): string {
  const candidates = [
    'java',
    '/usr/bin/java',
    '/usr/lib/jvm/java-17-openjdk-amd64/bin/java',
    '/usr/lib/jvm/java-17-openjdk-arm64/bin/java',
  ];
  for (const bin of candidates) {
    try {
      execSync(`${bin} -version`, { stdio: 'ignore' });
      return bin;
    } catch {
      // try next candidate
    }
  }
  return 'java';
}

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
    const res = await fetch(`http://${JAVA_HOST}:${JAVA_PORT}/api/health`, {
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
    const javaBin = resolveJavaBinary();
    console.log(`[Server Gateway] Launching authoritative Java HTTP Server on ${JAVA_HOST}:${JAVA_PORT}...`);
    const child = spawn(javaBin, ['-cp', 'lib/*:out', 'server.TradingPlatformHttpServer', String(JAVA_PORT)], {
      stdio: 'inherit',
      env: process.env,
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
        failStartup(new Error(`Timed out after ${HEALTH_TIMEOUT_MS}ms waiting for Java /api/health readiness on ${JAVA_HOST}:${JAVA_PORT}`));
        return;
      }

      const health = await probeJavaHealth(1000);
      if (health.ok) {
        if (!settled) {
          settled = true;
          console.log(`[Server Gateway] Java backend verified healthy on ${JAVA_HOST}:${JAVA_PORT}`);
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
  validateEnvironmentConfig();
  compileJavaBackend();
  await startAndAwaitJavaBackend();

  const app = express();
  app.disable('x-powered-by');
  app.use((_req, res, next) => {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'SAMEORIGIN');
    res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
    res.setHeader('Permissions-Policy', 'geolocation=(), microphone=(), camera=()');
    next();
  });
  app.use(express.json({ limit: '1mb' }));
  app.use((err: any, _req: any, res: any, next: any) => {
    if (err?.type === 'entity.too.large') {
      return res.status(413).json({ success: false, error: 'Request payload exceeds maximum allowed size.' });
    }
    if (err instanceof SyntaxError && 'body' in err) {
      return res.status(400).json({ success: false, error: 'Malformed JSON request body.' });
    }
    return next(err);
  });

  // Gateway health check reflects real downstream Java + JDBC health (never reports UP while Java is down)
  app.get('/api/health', async (_req, res) => {
    const javaAlive = javaProcess !== null;
    if (!javaAlive) {
      return res.status(503).json({
        status: 'DOWN',
        javaRunning: false,
        backend: 'Trading Engine Offline',
        primaryArchitecture: 'Express Gateway -> Java HttpServer + ServletBridge -> JDBC',
        timestamp: new Date().toISOString(),
      });
    }

    const health = await probeJavaHealth(1500);
    if (!health.ok) {
      return res.status(503).json({
        status: 'DOWN',
        javaRunning: false,
        backend: 'Trading Engine Unavailable',
        primaryArchitecture: 'Express Gateway -> Java HttpServer + ServletBridge -> JDBC',
        timestamp: new Date().toISOString(),
      });
    }

    return res.status(200).json({
      status: 'UP',
      javaRunning: true,
      backend: 'Online Stock Trading Engine',
      databaseConnected: Boolean(health.data?.databaseConnected),
      databaseEngine: health.data?.databaseEngine || 'Unknown',
      primaryArchitecture: 'Express Gateway -> Java HttpServer + ServletBridge -> JDBC',
      timestamp: new Date().toISOString(),
    });
  });

  // Reverse proxy all remaining /api/* traffic to Java backend on JAVA_HOST:JAVA_PORT
  app.use('/api', async (req, res) => {
    if (javaProcess === null) {
      return res.status(503).json({
        success: false,
        error: `Authoritative Java backend (port ${JAVA_PORT}) is offline.`,
      });
    }

    try {
      const targetUrl = `http://${JAVA_HOST}:${JAVA_PORT}${req.originalUrl}`;
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
      if (!forwardHeaders['x-forwarded-proto']) {
        forwardHeaders['x-forwarded-proto'] = req.protocol || 'http';
      }
      if (!forwardHeaders['x-forwarded-host'] && req.headers.host) {
        forwardHeaders['x-forwarded-host'] = String(req.headers.host);
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
