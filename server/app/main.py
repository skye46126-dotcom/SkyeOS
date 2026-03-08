import hashlib
import os
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import List, Optional

from fastapi import Depends, FastAPI, File, Header, HTTPException, Query, UploadFile
from fastapi.responses import FileResponse

APP_NAME = "lifeos-sync-server"
API_KEY_ENV = "LIFEOS_API_KEY"
STORAGE_DIR_ENV = "LIFEOS_STORAGE_DIR"
MAX_UPLOAD_MB_ENV = "LIFEOS_MAX_UPLOAD_MB"

DEFAULT_STORAGE_DIR = "/data/backups"
DEFAULT_MAX_UPLOAD_MB = 100

FILENAME_SAFE = re.compile(r"[^a-zA-Z0-9._-]+")

app = FastAPI(title=APP_NAME, version="1.0.0")


def _storage_root() -> Path:
    root = Path(os.getenv(STORAGE_DIR_ENV, DEFAULT_STORAGE_DIR)).resolve()
    root.mkdir(parents=True, exist_ok=True)
    return root


def _max_upload_bytes() -> int:
    raw = os.getenv(MAX_UPLOAD_MB_ENV, str(DEFAULT_MAX_UPLOAD_MB))
    try:
        mb = max(1, int(raw))
    except Exception:
        mb = DEFAULT_MAX_UPLOAD_MB
    return mb * 1024 * 1024


def _now_utc() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def _sanitize(value: str) -> str:
    safe = FILENAME_SAFE.sub("_", value.strip())
    return safe[:120] if safe else "unknown"


def _require_api_key(x_api_key: Optional[str] = Header(default=None)) -> None:
    expected = os.getenv(API_KEY_ENV, "").strip()
    if not expected:
        raise HTTPException(status_code=500, detail=f"{API_KEY_ENV} is not configured")
    if x_api_key != expected:
        raise HTTPException(status_code=401, detail="Unauthorized")


@app.get("/health")
def health():
    return {
        "service": APP_NAME,
        "status": "ok",
        "timestamp": _now_utc(),
    }


@app.post("/api/v1/backups/upload", dependencies=[Depends(_require_api_key)])
async def upload_backup(
    file: UploadFile = File(...),
    device_id: str = Query(default="android"),
    backup_type: str = Query(default="manual"),
):
    root = _storage_root()
    safe_device = _sanitize(device_id)
    safe_type = _sanitize(backup_type)
    filename = _sanitize(file.filename or "backup.db")
    prefix = _now_utc()
    final_name = f"{prefix}_{safe_device}_{safe_type}_{filename}"
    target = root / final_name

    max_bytes = _max_upload_bytes()
    size = 0
    sha = hashlib.sha256()

    with target.open("wb") as out:
        while True:
            chunk = await file.read(1024 * 1024)
            if not chunk:
                break
            size += len(chunk)
            if size > max_bytes:
                out.close()
                target.unlink(missing_ok=True)
                raise HTTPException(status_code=413, detail="File too large")
            sha.update(chunk)
            out.write(chunk)

    return {
        "filename": final_name,
        "size_bytes": size,
        "sha256": sha.hexdigest(),
        "uploaded_at": _now_utc(),
    }


@app.get("/api/v1/backups/list", dependencies=[Depends(_require_api_key)])
def list_backups(limit: int = Query(default=30, ge=1, le=200)) -> List[dict]:
    root = _storage_root()
    files = sorted(
        [p for p in root.iterdir() if p.is_file()],
        key=lambda p: p.stat().st_mtime,
        reverse=True,
    )[:limit]

    result: List[dict] = []
    for p in files:
        stat = p.stat()
        result.append(
            {
                "filename": p.name,
                "size_bytes": stat.st_size,
                "modified_at": datetime.fromtimestamp(stat.st_mtime, tz=timezone.utc).strftime(
                    "%Y%m%dT%H%M%SZ"
                ),
            }
        )
    return result


@app.get("/api/v1/backups/download/{filename}", dependencies=[Depends(_require_api_key)])
def download_backup(filename: str):
    safe_name = _sanitize(filename)
    root = _storage_root()
    target = root / safe_name
    if not target.exists() or not target.is_file():
        raise HTTPException(status_code=404, detail="backup not found")
    return FileResponse(path=str(target), media_type="application/octet-stream", filename=target.name)


@app.delete("/api/v1/backups/delete/{filename}", dependencies=[Depends(_require_api_key)])
def delete_backup(filename: str):
    safe_name = _sanitize(filename)
    root = _storage_root()
    target = root / safe_name
    if not target.exists() or not target.is_file():
        raise HTTPException(status_code=404, detail="backup not found")
    target.unlink()
    return {"deleted": safe_name}
