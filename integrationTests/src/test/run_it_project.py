#!/usr/bin/env python3
"""Run an integration-test sample project locally.

This mirrors the Java integration test runner closely:
- copy a project from src/test/resources to a temp directory
- replace Maven-filtered placeholders used by the sample projects
- run the copied Gradle wrapper with the sonar task

Expected environment for the analysis itself is unchanged. For example,
`SONAR_HOST_URL` and authentication properties should already be available
through the environment, `gradle.properties`, or extra Gradle args.
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Iterable


DEFAULT_GRADLE_VERSION = "7.5.1"
DEFAULT_AGP_VERSION = "7.1.0"
BASE_GRADLE_OPTS = "-Xmx1024m"
JAVA_9_OPTS = (
    " --add-opens=java.prefs/java.util.prefs=ALL-UNNAMED"
    " --add-opens=java.base/java.lang.invoke=ALL-UNNAMED"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run sonar on an integration-test sample project."
    )
    parser.add_argument("project_name", help="Folder name under src/test/resources")
    parser.add_argument(
        "--gradle-version",
        default=DEFAULT_GRADLE_VERSION,
        help=f"Gradle version to inject (default: {DEFAULT_GRADLE_VERSION})",
    )
    parser.add_argument(
        "--agp-version",
        default=DEFAULT_AGP_VERSION,
        help=f"Android Gradle Plugin version to inject (default: {DEFAULT_AGP_VERSION})",
    )
    parser.add_argument(
        "--java-version",
        help="Optional Java version or JAVA_HOME path to use for the Gradle run",
    )
    parser.add_argument(
        "-debug",
        action="store_true",
        dest="debug",
        help="Run with -Dorg.gradle.debug=true --no-daemon so a debugger can attach",
    )
    # New flag added here
    parser.add_argument(
        "-task-graph",
        action="store_true",
        dest="task_graph",
        help="Preview the task graph using Gradle dry-run (-m) without executing tasks",
    )
    args, extra_args = parser.parse_known_args()
    args.extra_args = extra_args
    return args


def repo_root(script_path: Path) -> Path:
    return script_path.parents[3]


def is_windows() -> bool:
    return os.name == "nt"


def load_plugin_version(root: Path) -> str:
    gradle_properties = root / "gradle.properties"
    for line in gradle_properties.read_text(encoding="utf-8").splitlines():
        if line.startswith("version="):
            return line.split("=", 1)[1].strip()
    raise RuntimeError(f"Could not find version=... in {gradle_properties}")


def replace_placeholders(project_dir: Path, replacements: dict[str, str]) -> None:
    candidates = list(project_dir.rglob("build.gradle"))
    candidates.extend(project_dir.rglob("build.gradle.kts"))
    candidates.extend(project_dir.rglob("gradle-wrapper.properties"))

    for path in candidates:
        text = path.read_text(encoding="utf-8")
        original = text
        for key, value in replacements.items():
            text = text.replace("${" + key + "}", value)
        if text != original:
            path.write_text(text, encoding="utf-8")


def java_major_from_release_file(java_home: Path) -> int | None:
    release_file = java_home / "release"
    if not release_file.is_file():
        return None
    content = release_file.read_text(encoding="utf-8", errors="ignore")
    match = re.search(r'JAVA_VERSION="([^"]+)"', content)
    if not match:
        return None
    return java_major_from_string(match.group(1))


def java_major_from_string(version: str) -> int:
    if version.startswith("1."):
        return int(version.split(".")[1])
    return int(version.split(".", 1)[0])


def candidate_java_homes(version: str) -> Iterable[Path]:
    env_candidates = [
        f"JAVA_HOME_{version}",
        f"JAVA{version}_HOME",
        f"JDK_{version}",
        f"JDK{version}_HOME",
    ]
    for name in env_candidates:
        value = os.environ.get(name)
        if value:
            yield Path(value)

    java_root = os.environ.get("JAVA_HOME")
    if java_root:
        java_root_path = Path(java_root)
    else:
        java_root_path = None

    if java_root_path and java_root_path.is_dir():
        for child in java_root_path.iterdir():
            if child.is_dir() and re.search(rf"(\D|^){re.escape(version)}(\D|$)", child.name):
                yield child


def resolve_java_home(java_version: str | None) -> tuple[str | None, int]:
    current_java_home = os.environ.get("JAVA_HOME")
    if not java_version:
        major = java_major_from_release_file(Path(current_java_home)) if current_java_home else None
        if major is None:
            major = java_major_from_string(os.environ.get("JAVA_VERSION", "11"))
        return current_java_home, major

    requested = Path(java_version)
    if requested.exists():
        major = java_major_from_release_file(requested)
        if major is None:
            raise RuntimeError(f"Could not determine Java version from {requested}")
        return str(requested), major

    for candidate in candidate_java_homes(java_version):
        major = java_major_from_release_file(candidate)
        if major is not None:
            return str(candidate), major

    raise RuntimeError(
        "Could not resolve --java-version. Pass a JAVA_HOME path or define one of: "
        f"JAVA_HOME_{java_version}, JAVA{java_version}_HOME, JDK_{java_version}, JDK{java_version}_HOME."
    )


def build_gradle_opts(java_major: int) -> str:
    return BASE_GRADLE_OPTS + (JAVA_9_OPTS if java_major > 8 else "")


def gradlew_path(project_dir: Path) -> Path:
    return project_dir / ("gradlew.bat" if is_windows() else "gradlew")


def gradle_command(gradlew: Path, debug: bool, task_graph: bool, extra_args: list[str]) -> list[str]:
    command: list[str]
    if is_windows():
        command = ["cmd.exe", "/C", str(gradlew)]
    else:
        command = [str(gradlew)]

    command.extend(["--stacktrace", "--no-daemon", "--warning-mode", "all"])

    if debug:
        command.append("-Dorg.gradle.debug=true")

    # Inject dry-run flag if task-graph is requested
    if task_graph:
        command.append("--dry-run")

    command.append("sonar")
    if extra_args and extra_args[0] == "--":
        command.extend(extra_args[1:])
    else:
        command.extend(extra_args)
    return command


def main() -> int:
    args = parse_args()
    script_path = Path(__file__).resolve()
    src_test_dir = script_path.parent
    root = repo_root(script_path)
    resources = src_test_dir / "resources"
    source_project = resources / args.project_name

    if not source_project.is_dir():
        available = ", ".join(sorted(p.name for p in resources.iterdir() if p.is_dir()))
        raise RuntimeError(
            f"Project '{args.project_name}' not found in {resources}.\nAvailable projects: {available}"
        )

    plugin_version = load_plugin_version(root)
    java_home, java_major = resolve_java_home(args.java_version)

    temp_root = Path(tempfile.mkdtemp(prefix="sonar-gradle-it-"))
    temp_project = temp_root / args.project_name
    shutil.copytree(source_project, temp_project)

    replace_placeholders(
        temp_project,
        {
            "gradle.version": args.gradle_version,
            "androidGradle.version": args.agp_version,
            "version": plugin_version,
        },
    )

    gradlew = gradlew_path(temp_project)
    if not gradlew.is_file():
        raise RuntimeError(f"Could not find {gradlew}")

    if not is_windows():
        gradlew.chmod(gradlew.stat().st_mode | 0o111)

    command = gradle_command(gradlew, args.debug, args.task_graph, args.extra_args)

    env = os.environ.copy()
    env["GRADLE_OPTS"] = build_gradle_opts(java_major)
    if java_home:
        env["JAVA_HOME"] = java_home

    print(f"Copied project to: {temp_project}")
    print(f"Plugin version: {plugin_version}")
    print(f"Gradle version: {args.gradle_version}")
    print(f"AGP version: {args.agp_version}")
    if java_home:
        print(f"JAVA_HOME: {java_home}")
    print("Running:")
    print(" ".join(command))

    completed = subprocess.run(
        command,
        cwd=temp_project,
        env=env,
        check=False,
    )
    return completed.returncode


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RuntimeError as exc:
        print(f"error: {exc}", file=sys.stderr)
        raise SystemExit(1)
