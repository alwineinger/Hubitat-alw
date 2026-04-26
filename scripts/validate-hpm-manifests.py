#!/usr/bin/env python3
"""Validate HPM package list and package manifests against Groovy metadata."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent
PACKAGE_LIST = REPO_ROOT / "hpm" / "packageList.json"
RAW_PREFIX = "https://raw.githubusercontent.com/alwineinger/Hubitat-alw/main/"

DEFINITION_RE = re.compile(r"definition\s*\((.*?)\)", re.DOTALL)
NAME_RE = re.compile(r"name\s*:\s*['\"]([^'\"]+)['\"]")
NAMESPACE_RE = re.compile(r"namespace\s*:\s*['\"]([^'\"]+)['\"]")


def error(errors: list[str], message: str) -> None:
    errors.append(message)


def load_json(path: Path, errors: list[str]) -> dict[str, Any] | None:
    try:
        with path.open("r", encoding="utf-8") as handle:
            payload = json.load(handle)
    except FileNotFoundError:
        error(errors, f"Missing JSON file: {path}")
        return None
    except json.JSONDecodeError as exc:
        error(errors, f"Invalid JSON in {path}: {exc}")
        return None

    if not isinstance(payload, dict):
        error(errors, f"JSON root must be an object: {path}")
        return None
    return payload


def require_keys(obj: dict[str, Any], keys: list[str], context: str, errors: list[str]) -> None:
    for key in keys:
        if key not in obj:
            error(errors, f"Missing key '{key}' in {context}")


def raw_url_to_local_path(raw_url: str, errors: list[str], context: str) -> Path | None:
    if not raw_url.startswith(RAW_PREFIX):
        error(errors, f"Unsupported URL format in {context}: {raw_url}")
        return None
    relative_path = raw_url[len(RAW_PREFIX) :]
    local_path = REPO_ROOT / relative_path
    if not local_path.exists():
        error(errors, f"Referenced file does not exist for {context}: {local_path}")
        return None
    return local_path


def extract_name_namespace(groovy_path: Path, errors: list[str], context: str) -> tuple[str, str] | None:
    text = groovy_path.read_text(encoding="utf-8")
    definition_match = DEFINITION_RE.search(text)
    if not definition_match:
        error(errors, f"Could not find definition(...) in {context}: {groovy_path}")
        return None

    definition_body = definition_match.group(1)
    name_match = NAME_RE.search(definition_body)
    namespace_match = NAMESPACE_RE.search(definition_body)
    if not name_match or not namespace_match:
        error(errors, f"Could not extract name/namespace from {context}: {groovy_path}")
        return None

    return name_match.group(1), namespace_match.group(1)


def validate_manifest_item(
    item: dict[str, Any],
    manifest_path: Path,
    section_name: str,
    index: int,
    errors: list[str],
) -> None:
    context = f"{manifest_path}:{section_name}[{index}]"
    require_keys(item, ["name", "namespace", "location"], context, errors)
    if any(k not in item for k in ("name", "namespace", "location")):
        return

    local_path = raw_url_to_local_path(item["location"], errors, context)
    if not local_path:
        return

    extracted = extract_name_namespace(local_path, errors, context)
    if not extracted:
        return
    actual_name, actual_namespace = extracted

    manifest_name = item["name"]
    manifest_namespace = item["namespace"]

    if manifest_name != actual_name or manifest_namespace != actual_namespace:
        error(
            errors,
            (
                f"Name/namespace mismatch in {context}: manifest has "
                f"('{manifest_name}', '{manifest_namespace}'), source has "
                f"('{actual_name}', '{actual_namespace}')"
            ),
        )


def validate_manifest(manifest_path: Path, errors: list[str]) -> None:
    manifest = load_json(manifest_path, errors)
    if not manifest:
        return

    require_keys(
        manifest,
        ["packageName", "author", "version", "minimumHEVersion", "dateReleased"],
        str(manifest_path),
        errors,
    )

    for section_name in ("apps", "drivers", "bundles"):
        section = manifest.get(section_name, [])
        if section is None:
            continue
        if not isinstance(section, list):
            error(errors, f"Section '{section_name}' must be a list in {manifest_path}")
            continue
        for index, item in enumerate(section):
            if not isinstance(item, dict):
                error(errors, f"Entry must be object in {manifest_path}:{section_name}[{index}]")
                continue
            validate_manifest_item(item, manifest_path, section_name, index, errors)


def main() -> int:
    errors: list[str] = []
    package_list = load_json(PACKAGE_LIST, errors)
    if not package_list:
        for msg in errors:
            print(f"ERROR: {msg}")
        return 1

    require_keys(package_list, ["author", "gitHubUrl", "payPalUrl", "packages"], str(PACKAGE_LIST), errors)

    packages = package_list.get("packages")
    if not isinstance(packages, list):
        error(errors, f"'packages' must be a list in {PACKAGE_LIST}")
        packages = []

    for index, package in enumerate(packages):
        context = f"{PACKAGE_LIST}:packages[{index}]"
        if not isinstance(package, dict):
            error(errors, f"Package entry must be object in {context}")
            continue
        require_keys(package, ["name", "category", "location", "description"], context, errors)
        if "location" not in package:
            continue
        local_manifest = raw_url_to_local_path(package["location"], errors, context)
        if not local_manifest:
            continue
        validate_manifest(local_manifest, errors)

    if errors:
        for msg in errors:
            print(f"ERROR: {msg}")
        print(f"\nValidation failed with {len(errors)} error(s).")
        return 1

    print("HPM manifest validation passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
