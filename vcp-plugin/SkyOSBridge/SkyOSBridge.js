#!/usr/bin/env node

/**
 * SkyOSBridge
 * - VCP synchronous plugin (stdio)
 * - Supports single command and batch commandN style
 */

const DEFAULT_TIMEOUT_MS = 15000;

async function main() {
  try {
    const input = await readStdin();
    if (!input || !input.trim()) {
      return writeSuccess("SkyOSBridge ready. No input provided.");
    }

    const payload = safeJsonParse(input);
    if (!payload || typeof payload !== "object") {
      return writeError("Invalid JSON input.");
    }

    const commandKeys = Object.keys(payload).filter((k) => /^command\d+$/.test(k));
    if (commandKeys.length > 0) {
      const result = await processBatch(payload, commandKeys);
      return writeSuccess(result);
    }

    const command = resolveCommand(payload);
    const result = await dispatch(command, payload);
    return writeSuccess(result);
  } catch (err) {
    return writeError(normalizeError(err));
  }
}

async function processBatch(payload, commandKeys) {
  const sorted = commandKeys.sort((a, b) => Number(a.replace("command", "")) - Number(b.replace("command", "")));
  const outputs = [];

  for (const commandKey of sorted) {
    const idx = commandKey.replace("command", "");
    const command = payload[commandKey];
    const scopedArgs = { ...extractScopedArgs(payload, idx), command };
    try {
      const result = await dispatch(command, scopedArgs);
      outputs.push({ command, status: "success", result });
    } catch (err) {
      outputs.push({ command, status: "error", error: normalizeError(err) });
    }
  }

  return {
    mode: "batch",
    total: outputs.length,
    outputs,
  };
}

function extractScopedArgs(payload, idx) {
  const scoped = {};
  for (const [key, value] of Object.entries(payload)) {
    if (key === `command${idx}`) continue;
    if (key.endsWith(idx)) {
      const baseKey = key.slice(0, -idx.length);
      scoped[baseKey] = value;
    }
  }
  return scoped;
}

async function dispatch(command, args) {
  switch (command) {
    case "SkyOSParseInput":
      return callSkyOS("POST", "/api/v1/ai/parse", {
        raw_text: pick(args, ["raw_text", "text", "content"], ""),
        context_date: pick(args, ["context_date", "date"], null),
        parser_hint: pick(args, ["parser_hint", "mode"], null),
      });

    case "SkyOSCommitEntries":
      return callSkyOS("POST", "/api/v1/entries/commit", {
        request_id: pick(args, ["request_id", "req_id"], ""),
        entries: ensureArray(args.entries),
      });

    case "SkyOSGetOverview": {
      const window = pick(args, ["window"], "day");
      const date = pick(args, ["date"], "");
      const query = buildQuery({ window, date });
      return callSkyOS("GET", `/api/v1/overview${query}`);
    }

    case "SkyOSListRecent": {
      const limit = Number(pick(args, ["limit"], 30));
      const safeLimit = Number.isFinite(limit) ? Math.max(1, Math.min(limit, 200)) : 30;
      const query = buildQuery({ limit: safeLimit });
      return callSkyOS("GET", `/api/v1/recent${query}`);
    }

    case "SkyOSBackupUpload":
      return callSkyOS("POST", "/api/v1/backup/upload", {
        backup_type: pick(args, ["backup_type", "type"], "manual"),
      });

    case "SkyOSBackupRestore": {
      validateAdminIfEnabled(args);
      return callSkyOS("POST", "/api/v1/backup/restore", {
        filename: pick(args, ["filename", "file"], ""),
      });
    }

    default:
      throw new Error(`Unknown command: ${String(command || "")}`);
  }
}

function validateAdminIfEnabled(args) {
  const realCode = process.env.DECRYPTED_AUTH_CODE;
  if (!realCode) {
    return;
  }
  const requireAdmin = pick(args, ["requireAdmin"], "");
  if (String(requireAdmin) !== String(realCode)) {
    const err = new Error("管理员验证码错误。");
    err.code = "ADMIN_CODE_INVALID";
    throw err;
  }
}

async function callSkyOS(method, path, body) {
  const baseUrl = (process.env.SKYOS_BASE_URL || "").trim().replace(/\/+$/, "");
  const apiKey = (process.env.SKYOS_API_KEY || "").trim();
  const timeoutMs = Number(process.env.SKYOS_TIMEOUT_MS || DEFAULT_TIMEOUT_MS);

  if (!baseUrl) {
    throw new Error("SKYOS_BASE_URL is not configured");
  }
  if (!apiKey) {
    throw new Error("SKYOS_API_KEY is not configured");
  }

  const url = `${baseUrl}${path.startsWith("/") ? path : "/" + path}`;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), Number.isFinite(timeoutMs) ? timeoutMs : DEFAULT_TIMEOUT_MS);

  try {
    const response = await fetch(url, {
      method,
      headers: {
        "Content-Type": "application/json",
        "x-api-key": apiKey,
      },
      body: method === "GET" ? undefined : JSON.stringify(body || {}),
      signal: controller.signal,
    });

    const text = await response.text();
    const data = safeJsonParse(text) ?? text;

    if (!response.ok) {
      const err = new Error(`SkyOS API ${response.status}: ${typeof data === "string" ? data : JSON.stringify(data)}`);
      err.code = "SKYOS_API_ERROR";
      throw err;
    }
    return data;
  } finally {
    clearTimeout(timeout);
  }
}

function resolveCommand(payload) {
  return (
    payload.command ||
    payload.commandIdentifier ||
    payload.action ||
    payload.tool_name ||
    ""
  );
}

function buildQuery(params) {
  const entries = Object.entries(params).filter(([, v]) => v !== null && v !== undefined && String(v).trim() !== "");
  if (entries.length === 0) return "";
  const q = entries
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
    .join("&");
  return `?${q}`;
}

function pick(obj, keys, fallback = null) {
  for (const key of keys) {
    if (obj && Object.prototype.hasOwnProperty.call(obj, key) && obj[key] !== undefined && obj[key] !== null) {
      return obj[key];
    }
  }
  return fallback;
}

function ensureArray(v) {
  if (Array.isArray(v)) return v;
  if (typeof v === "string") {
    const parsed = safeJsonParse(v);
    return Array.isArray(parsed) ? parsed : [];
  }
  return [];
}

function safeJsonParse(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function normalizeError(err) {
  if (!err) return "unknown error";
  if (typeof err === "string") return err;
  return err.message || JSON.stringify(err);
}

function readStdin() {
  return new Promise((resolve) => {
    let data = "";
    process.stdin.setEncoding("utf8");
    process.stdin.on("data", (chunk) => (data += chunk));
    process.stdin.on("end", () => resolve(data));
  });
}

function writeSuccess(result) {
  process.stdout.write(
    JSON.stringify({
      status: "success",
      result,
    })
  );
}

function writeError(error) {
  process.stdout.write(
    JSON.stringify({
      status: "error",
      error: String(error || "unknown error"),
    })
  );
  process.exitCode = 1;
}

main();

