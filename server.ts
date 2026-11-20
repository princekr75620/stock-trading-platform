import 'dotenv/config';
import express, { Request, Response, NextFunction } from 'express';
import crypto from 'crypto';
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
const GATEWAY_PROXY_SECRET = process.env.GATEWAY_PROXY_SECRET || crypto.randomBytes(32).toString('hex');

let javaProcess: ChildProcess | null = null;
let shuttingDown = false;

function isProductionEnv(): boolean {
  const appEnv = (process.env.APP_ENV || process.env.NODE_ENV || 'development').trim().toLowerCase();
  return appEnv === 'production';
}

function addOriginEntries(target: Set<string>, raw?: string): void {
  if (!raw) return;
  for (const item of raw.split(',')) {
    const trimmed = item.trim();
    if (!trimmed) continue;
    try {
      const u = new URL(trimmed);
      if (u.protocol === 'http:' || u.protocol === 'https:') {
        target.add(u.origin.toLowerCase());
      }
    } catch {
      // ignore malformed env origin entry
    }
  }
}

function getAllowedOrigins(): Set<string> {
  const origins = new Set<string>([
    'http://localhost:3000',
    'http://127.0.0.1:3000',
  ]);
  addOriginEntries(origins, process.env.ALLOWED_ORIGINS);
  addOriginEntries(origins, process.env.APP_URL);
  if (!isProductionEnv() && Number.isInteger(PORT) && PORT > 0) {
    origins.add(`http://localhost:${PORT}`);
    origins.add(`http://127.0.0.1:${PORT}`);
  }
  return origins;
}

function getAllowedHosts(): Set<string> {
  const hosts = new Set<string>();
  const rawHosts = process.env.ALLOWED_HOSTS || '';
  for (const item of rawHosts.split(',')) {
    const trimmed = item.trim().toLowerCase();
    if (trimmed) hosts.add(trimmed);
  }
  for (const origin of getAllowedOrigins()) {
    try {
      const u = new URL(origin);
      hosts.add(u.hostname.toLowerCase());
      hosts.add(u.host.toLowerCase());
    } catch {
      // ignore
    }
  }
  if (!isProductionEnv()) {
    hosts.add('localhost');
    hosts.add('127.0.0.1');
    hosts.add('::1');
  }
  return hosts;
}

function isAllowedHostHeader(rawHost: string | undefined): boolean {
  if (!rawHost) return false;
  const trimmed = rawHost.trim();
  if (!trimmed || trimmed.length > 255) return false;
  if (/[\s,@/\\?#\r\n]/.test(trimmed)) return false;

  const lower = trimmed.toLowerCase();
  let hostname = lower;
  let port = -1;

  if (lower.startsWith('[')) {
    const close = lower.indexOf(']');
    if (close < 2) return false;
    hostname = lower.slice(1, close);
    if (close + 1 < lower.length) {
      if (lower[close + 1] !== ':') return false;
      const p = Number(lower.slice(close + 2));
      if (!Number.isInteger(p) || p < 1 || p > 65535) return false;
      port = p;
    }
  } else {
    const colonIdx = lower.indexOf(':');
    if (colonIdx >= 0) {
      if (lower.indexOf(':', colonIdx + 1) >= 0) return false;
      hostname = lower.slice(0, colonIdx);
      const p = Number(lower.slice(colonIdx + 1));
      if (!Number.isInteger(p) || p < 1 || p > 65535) return false;
      port = p;
    }
  }

  if (!hostname) return false;
  const hostPort = port > 0 ? `${hostname}:${port}` : hostname;
  const allowedHosts = getAllowedHosts();
  return allowedHosts.has(hostPort) || allowedHosts.has(hostname);
}

function isAllowedOriginHeader(rawOrigin: string, req: Request): boolean {
  try {
    const u = new URL(rawOrigin);
    if (u.protocol !== 'http:' && u.protocol !== 'https:') return false;
    if (u.username || u.password) return false;
    const normalizedOrigin = u.origin.toLowerCase();
    if (getAllowedOrigins().has(normalizedOrigin)) return true;

    if (!isProductionEnv()) {
      const h = u.hostname.toLowerCase();
      if (h === 'localhost' || h === '127.0.0.1' || h === '::1') {
        const reqHost = typeof req.headers.host === 'string' ? req.headers.host.trim().toLowerCase() : '';
        return isAllowedHostHeader(reqHost) && u.host.toLowerCase() === reqHost;
      }
    }
    return false;
  } catch {
    return false;
  }
}

function normalizeIp(raw: string | undefined): string {
  if (!raw) return '127.0.0.1';
  let s = raw.trim();
  if (s.startsWith('::ffff:')) s = s.slice(7);
  if (s === '::1' || s === '0:0:0:0:0:0:0:1') return '127.0.0.1';
  return s || '127.0.0.1';
}

function isTrustedProxyMode(): { enabled: boolean; ips: string[] } {
  const ips = (process.env.TRUSTED_PROXY_IPS || process.env.TRUSTED_PROXIES || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  if (ips.length > 0) {
    return { enabled: true, ips };
  }
  const flag = (process.env.TRUST_PROXY || 'false').trim().toLowerCase();
  return { enabled: flag === 'true', ips: [] };
}

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
      env: {
        ...process.env,
        GATEWAY_PROXY_SECRET,
      },
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
  const proxyConfig = isTrustedProxyMode();
  if (proxyConfig.ips.length > 0) {
    app.set('trust proxy', proxyConfig.ips);
  } else if (proxyConfig.enabled) {
    app.set('trust proxy', 1);
  } else {
    app.set('trust proxy', false);
  }

  app.use((req: Request, res: Response, next: NextFunction) => {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'SAMEORIGIN');
    res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
    res.setHeader('Permissions-Policy', 'geolocation=(), microphone=(), camera=()');

    const hostHeader = typeof req.headers.host === 'string' ? req.headers.host : undefined;
    if (hostHeader && !isAllowedHostHeader(hostHeader)) {
      return res.status(403).json({
        success: false,
        error: 'Security validation failed: Untrusted Host header.',
      });
    }

    const xForwardedHost = req.headers['x-forwarded-host'];
    if (xForwardedHost !== undefined) {
      const rawXfh = Array.isArray(xForwardedHost) ? xForwardedHost.join(',') : String(xForwardedHost);
      if (!isAllowedHostHeader(rawXfh)) {
        return res.status(403).json({
          success: false,
          error: 'Security validation failed: Untrusted forwarded host header.',
        });
      }
    }

    const forwarded = req.headers.forwarded;
    if (forwarded !== undefined) {
      const rawFwd = Array.isArray(forwarded) ? forwarded.join(';') : String(forwarded);
      const match = /(?:^|[;,\s])host=("[^"]+"|[^;,\s]+)/i.exec(rawFwd);
      if (match && match[1]) {
        const candidate = match[1].replace(/^"|"$/g, '').trim();
        if (!isAllowedHostHeader(candidate)) {
          return res.status(403).json({
            success: false,
            error: 'Security validation failed: Untrusted Forwarded header host.',
          });
        }
      }
    }

    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(req.method.toUpperCase()) && req.path.startsWith('/api')) {
      const origin = typeof req.headers.origin === 'string' ? req.headers.origin.trim() : '';
      if (origin && !isAllowedOriginHeader(origin, req)) {
        return res.status(403).json({
          success: false,
          error: 'Security validation failed: Untrusted request Origin.',
        });
      }
      if (!origin && typeof req.headers.referer === 'string' && req.headers.referer.trim()) {
        if (!isAllowedOriginHeader(req.headers.referer.trim(), req)) {
          return res.status(403).json({
            success: false,
            error: 'Security validation failed: Untrusted request Referer.',
          });
        }
      }
    }

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
      const hopByHopAndProxyHeaders = new Set([
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
        'forwarded',
        'x-forwarded-host',
        'x-forwarded-proto',
        'x-forwarded-for',
        'x-forwarded-port',
        'x-real-ip',
        'x-gateway-proxy-secret',
      ]);

      const forwardHeaders: Record<string, string> = {};
      for (const [key, val] of Object.entries(req.headers)) {
        const lowerKey = key.toLowerCase();
        if (val && !hopByHopAndProxyHeaders.has(lowerKey)) {
          forwardHeaders[lowerKey] = Array.isArray(val) ? val.join('; ') : String(val);
        }
      }

      const clientIp = normalizeIp(proxyConfig.enabled ? req.ip : req.socket.remoteAddress);
      forwardHeaders['x-real-ip'] = clientIp;
      forwardHeaders['x-forwarded-for'] = clientIp;
      forwardHeaders['x-forwarded-proto'] = (proxyConfig.enabled && req.protocol === 'https') ? 'https' : 'http';
      if (typeof req.headers.host === 'string' && isAllowedHostHeader(req.headers.host)) {
        forwardHeaders['x-forwarded-host'] = req.headers.host.trim();
      }
      forwardHeaders['x-gateway-proxy-secret'] = GATEWAY_PROXY_SECRET;

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
