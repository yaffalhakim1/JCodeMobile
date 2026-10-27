#!/usr/bin/env python3
"""Project list bridge for the JCode Android client.

jcode's gateway exposes /health, /pair and /ws only. It has no project-list
route, so the phone cannot discover the host's projects the way OpenCode's
/api/project allows. This bridge fills that one gap.

It reads jcode's own session store, groups sessions by `working_dir`, and
serves the ranked result over plain HTTP on a second port:

    GET /health   -> {"status": "ok", "bridge": true}
    GET /projects -> {"projects": [{"path", "name", "sessions", "last_used"}]}

Ranking matches the point of a recent-project list: most sessions first, then
most recently used. Directories that no longer exist are dropped.

Run on the host:

    python tools\\projects_bridge.py --port 7645

Bind to 0.0.0.0 so Tailscale peers can reach it. Add --token to require
`Authorization: Bearer <token>` (the app currently calls without one, so only
enable this if you also configure the app for it).

No third-party dependencies.
"""

import argparse
import json
import os
import re
import sys
import time
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

VERSION = "0.1.0"
SKIP_DIRS = {
    "node_modules", ".git", ".gradle", ".idea", "build", "dist", "target",
    "AppData", "Windows", "Program Files", "Program Files (x86)", "$Recycle.Bin",
    "System Volume Information", "venv", ".venv", "__pycache__",
}
PROJECT_MARKERS = (".git", "Cargo.toml", "package.json", "build.gradle.kts", "build.gradle", "pubspec.yaml", "go.mod", "pyproject.toml")
COMMON_ROOTS = ("Desktop", "Documents", "Downloads", "projects", "src", "code", "dev", "repos")


def jcode_home() -> Path:
    env = os.environ.get("JCODE_HOME")
    if env:
        return Path(env).expanduser()
    return Path.home() / ".jcode"


def parse_time(value) -> float:
    if not value:
        return 0.0
    if isinstance(value, (int, float)):
        return float(value)
    text = str(value).strip().replace("Z", "+00:00")
    try:
        return datetime.fromisoformat(text).timestamp()
    except ValueError:
        return 0.0


def project_name(path: str) -> str:
    parts = [part for part in re.split(r"[\\/]", path) if part]
    return parts[-1] if parts else path


def projects_from_sessions(home: Path, limit: int = 4000):
    sessions_dir = home / "sessions"
    if not sessions_dir.is_dir():
        return []

    by_path = {}
    files = sorted(sessions_dir.glob("session_*.json"))[-limit:]
    for file in files:
        try:
            data = json.loads(file.read_text("utf-8"))
        except (OSError, ValueError):
            continue
        raw = (data.get("working_dir") or data.get("cwd") or "").strip()
        if not raw or not os.path.isabs(raw):
            continue
        try:
            if not os.path.isdir(raw):
                continue
        except OSError:
            continue

        key = os.path.normcase(os.path.normpath(raw))
        entry = by_path.get(key)
        last = max(
            parse_time(data.get("last_active_at")),
            parse_time(data.get("updated_at")),
            parse_time(data.get("created_at")),
        )
        if entry is None:
            by_path[key] = {
                "path": os.path.normpath(raw),
                "name": project_name(raw),
                "sessions": 1,
                "last_used": last,
            }
        else:
            entry["sessions"] += 1
            entry["last_used"] = max(entry["last_used"], last)

    projects = list(by_path.values())
    projects.sort(key=lambda item: (item["sessions"], item["last_used"]), reverse=True)
    return projects


def scan_for_git_repos(root: Path, max_depth: int = 3, limit: int = 200):
    """Fallback when the session store is empty: find git worktrees."""
    root = root.expanduser()
    if not root.is_dir():
        return []
    found = []
    root_depth = len(root.parts)
    for current, dirs, _files in os.walk(root, topdown=True):
        depth = len(Path(current).parts) - root_depth
        dirs[:] = [d for d in dirs if d.lower() not in SKIP_DIRS and not d.startswith(".")]
        if depth >= max_depth:
            dirs[:] = []
        if ".git" in os.listdir(current) if os.access(current, os.R_OK) else False:
            try:
                last = os.path.getmtime(os.path.join(current, ".git"))
            except OSError:
                last = 0.0
            found.append(
                {
                    "path": os.path.normpath(current),
                    "name": project_name(current),
                    "sessions": 0,
                    "last_used": last,
                }
            )
            dirs[:] = []
            if len(found) >= limit:
                break
    found.sort(key=lambda item: item["last_used"], reverse=True)
    return found


def candidate_projects(existing):
    """Folders the user probably means even before any jcode session exists."""
    known = {os.path.normcase(os.path.normpath(item["path"])) for item in existing}
    home = Path.home()
    found = []

    def add(directory: Path):
        if not directory.is_dir():
            return
        key = os.path.normcase(os.path.normpath(str(directory)))
        if key in known:
            return
        known.add(key)
        try:
            last = directory.stat().st_mtime
        except OSError:
            last = 0.0
        found.append(
            {
                "path": os.path.normpath(str(directory)),
                "name": project_name(str(directory)),
                "sessions": 0,
                "last_used": last,
            }
        )

    for name in COMMON_ROOTS:
        base = home / name
        if not base.is_dir():
            continue
        add(base)
        try:
            children = sorted(
                (child for child in base.iterdir() if child.is_dir()),
                key=lambda child: child.stat().st_mtime,
                reverse=True,
            )
        except OSError:
            continue
        for child in children[:12]:
            if any((child / marker).exists() for marker in PROJECT_MARKERS):
                add(child)
            if len(found) >= 16:
                return found
    return found


class ProjectsHandler(BaseHTTPRequestHandler):
    server_version = f"jcode-projects-bridge/{VERSION}"
    token = None
    cache_seconds = 20
    cache = {"at": 0.0, "projects": []}
    scan_root = None
    candidates = True

    def log_message(self, fmt, *args):
        print(f"[bridge] {self.address_string()} {fmt % args}", file=sys.stderr)

    def _authorized(self) -> bool:
        if not self.token:
            return True
        header = self.headers.get("Authorization", "")
        return header == f"Bearer {self.token}"

    def _json(self, status: int, payload: dict):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if not self._authorized():
            self._json(401, {"error": "unauthorized"})
            return

        if path == "/health":
            self._json(200, {"status": "ok", "bridge": True, "version": VERSION})
            return

        if path == "/projects":
            now = time.time()
            if now - self.cache["at"] > self.cache_seconds:
                home = jcode_home()
                projects = projects_from_sessions(home)
                source = "sessions"
                if not projects and self.scan_root:
                    projects = scan_for_git_repos(Path(self.scan_root))
                    source = "scan"
                elif self.candidates:
                    extra = candidate_projects(projects)
                    if extra:
                        projects = projects + extra
                        source = "sessions+candidates"
                self.cache = {"at": now, "projects": projects, "source": source}
            self._json(
                200,
                {
                    "projects": self.cache["projects"],
                    "source": self.cache.get("source", "sessions"),
                    "generated_at": datetime.now().astimezone().isoformat(),
                },
            )
            return

        self._json(404, {"error": "not found"})


def main():
    parser = argparse.ArgumentParser(description="Serve jcode projects to the phone")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=7645)
    parser.add_argument("--token", default=None, help="require Bearer <token>")
    parser.add_argument("--jcode-home", default=None, help="override ~/.jcode")
    parser.add_argument("--scan-root", default=str(Path.home()), help="fallback git scan root")
    parser.add_argument("--cache-seconds", type=int, default=20)
    parser.add_argument(
        "--no-candidates",
        action="store_true",
        help="only list folders that already have jcode sessions",
    )
    args = parser.parse_args()

    if args.jcode_home:
        os.environ["JCODE_HOME"] = args.jcode_home
    ProjectsHandler.token = args.token
    ProjectsHandler.cache_seconds = args.cache_seconds
    ProjectsHandler.scan_root = args.scan_root
    ProjectsHandler.candidates = not args.no_candidates

    server = ThreadingHTTPServer((args.host, args.port), ProjectsHandler)
    print(
        f"projects bridge: http://{args.host}:{args.port} "
        f"(home={jcode_home()}, token={'yes' if args.token else 'no'})",
        file=sys.stderr,
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
