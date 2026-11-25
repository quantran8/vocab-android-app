#!/usr/bin/env python3
"""Convert the iOS string catalog (Localizable.xcstrings) into Android string resources.

The iOS catalog is the source of truth for every string both apps share. This script writes
`app/src/main/res/values*/strings_ios.xml` (generated: never edit by hand). Android-only strings go in
`strings.xml` next to them.

Resource names are derived from the English key once and then kept in `string_names.json`, so a name never
changes when other keys are added or removed. Commit that file with the generated XML.

Format specifiers: `%@` -> `%s`, `%lld`/`%ld`/`%d` -> `%d`, and every string with arguments is written with
positional arguments (`%1$s`), which is what Android expects and keeps translations that reorder arguments right.
Plural variations become <plurals>; a key that is plural in any language is plural in all, with an `other` item.

Usage:
    python3 tools/xcstrings2android/convert.py [--catalog PATH] [--res PATH] [--strict]

--strict exits non-zero when any key lacks a translated value in any language (the iOS rule: every key in every
language), so CI can enforce it.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from xml.sax.saxutils import escape as xml_escape

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
DEFAULT_CATALOG = ROOT.parent / "ios_app" / "VocabFlow" / "Resources" / "Localizable.xcstrings"
DEFAULT_RES = ROOT / "app" / "src" / "main" / "res"
NAMES_FILE = HERE / "string_names.json"
OUTPUT_NAME = "strings_ios.xml"

# iOS language code -> Android resource directory. English is the unqualified default.
LOCALES = {
    "en": "values",
    "vi": "values-vi",
    "hi": "values-hi",
    "es": "values-es",
    "ja": "values-ja",
    "pt-BR": "values-pt-rBR",
    "ko": "values-ko",
    "zh-Hant": "values-b+zh+Hant",
    "th": "values-th",
    "ar": "values-ar",
    # Android resolves Indonesian through the legacy `in` code.
    "id": "values-in",
}

SPEC = re.compile(r"%(?:(\d+)\$)?([-+ 0#]*)(\d+)?(?:\.(\d+))?(lld|llu|ld|lu|hhd|hd|d|i|u|@|f|s|%)")
PLURAL_ORDER = ["zero", "one", "two", "few", "many", "other"]


def slug(key: str) -> str:
    text = SPEC.sub(" x ", key)
    text = re.sub(r"[^A-Za-z0-9]+", "_", text).strip("_").lower()
    text = re.sub(r"_+", "_", text)[:48].strip("_")
    if not text:
        text = "str"
    if text[0].isdigit():
        text = "s_" + text
    return text


def assign_names(keys: list[str]) -> dict[str, str]:
    names: dict[str, str] = json.loads(NAMES_FILE.read_text()) if NAMES_FILE.exists() else {}
    names = {k: v for k, v in names.items() if k in keys}
    taken = set(names.values())
    for key in sorted(keys):
        if key in names:
            continue
        base = slug(key)
        name, n = base, 2
        while name in taken:
            name, n = f"{base}_{n}", n + 1
        names[key] = name
        taken.add(name)
    NAMES_FILE.write_text(json.dumps(dict(sorted(names.items())), ensure_ascii=False, indent=2) + "\n")
    return names


def convert_value(value: str) -> str:
    """Swift format string -> Android resource text (unescaped XML-wise)."""
    args = [m for m in SPEC.finditer(value) if m.group(5) != "%"]
    if not args:
        # Not formatted on Android: a literal percent stays a single "%".
        return value.replace("%%", "%")

    index = 0

    def repl(m: re.Match) -> str:
        nonlocal index
        position, flags, width, precision, kind = m.groups()
        if kind == "%":
            return "%%"
        if position is None:
            index += 1
            position = str(index)
        conv = {"@": "s", "s": "s", "f": "f"}.get(kind, "d")
        out = f"%{position}${flags or ''}{width or ''}"
        if precision is not None:
            out += f".{precision}"
        return out + conv

    return SPEC.sub(repl, value)


def android_escape(text: str) -> str:
    text = text.replace("\\", "\\\\")
    text = xml_escape(text)
    text = text.replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n").replace("\t", "\\t")
    if text.startswith("@") or text.startswith("?"):
        text = "\\" + text
    return text


def unit_value(loc: dict) -> tuple[str | None, bool]:
    unit = loc.get("stringUnit")
    if not unit:
        return None, False
    return unit.get("value"), unit.get("state") == "translated"


def read_entry(key: str, entry: dict, lang: str, source: str):
    """Returns (kind, value, translated): kind 'string' with str value or 'plural' with {category: str}."""
    loc = entry.get("localizations", {}).get(lang)
    if loc is None:
        if lang == source:
            return "string", key, True
        return None, None, False
    if "variations" in loc:
        variations = loc["variations"]
        if set(variations) != {"plural"}:
            raise SystemExit(f"Unsupported variation {list(variations)} for key {key!r} ({lang})")
        items, translated = {}, True
        for category, sub in variations["plural"].items():
            value, ok = unit_value(sub)
            if value is not None:
                items[category] = value
            translated &= ok
        return "plural", items, translated
    value, ok = unit_value(loc)
    return ("string", value, ok) if value is not None else (None, None, False)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--res", type=Path, default=DEFAULT_RES)
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()

    catalog = json.loads(args.catalog.read_text())
    source = catalog.get("sourceLanguage", "en")
    # Stale keys are no longer in the iOS code; they stay out of Android too.
    strings = {
        k: v
        for k, v in catalog["strings"].items()
        if k.strip() and v.get("shouldTranslate", True) and v.get("extractionState") != "stale"
    }
    names = assign_names(list(strings))

    # A key is plural if any language has plural variations.
    plural_keys = {
        key
        for key, entry in strings.items()
        if any("variations" in loc for loc in entry.get("localizations", {}).values())
    }

    missing: dict[str, list[str]] = {}
    for lang, folder in LOCALES.items():
        lines = ['<?xml version="1.0" encoding="utf-8"?>',
                 "<!-- Generated by tools/xcstrings2android/convert.py from the iOS Localizable.xcstrings. Do not edit. -->",
                 '<resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="MissingTranslation,UnusedResources,TypographyEllipsis,TypographyDashes,PluralsCandidate">']
        for key in sorted(strings, key=lambda k: names[k]):
            kind, value, translated = read_entry(key, strings[key], lang, source)
            # The source language's value is the text itself, whatever state Xcode gave it.
            translated = translated or (lang == source and kind is not None)
            if kind is None or not translated:
                missing.setdefault(lang, []).append(key)
            if kind is None:
                continue
            name = names[key]
            if key in plural_keys:
                items = value if kind == "plural" else {"other": value}
                if "other" not in items:
                    items["other"] = next(iter(items.values()))
                lines.append(f'    <plurals name="{name}">')
                for category in PLURAL_ORDER:
                    if category in items:
                        lines.append(f'        <item quantity="{category}">{android_escape(convert_value(items[category]))}</item>')
                lines.append("    </plurals>")
            else:
                converted = convert_value(value)
                attrs = ' formatted="false"' if "%" in converted and not SPEC.search(value.replace("%%", "")) else ""
                lines.append(f'    <string name="{name}"{attrs}>{android_escape(converted)}</string>')
        lines.append("</resources>")
        out = args.res / folder
        out.mkdir(parents=True, exist_ok=True)
        (out / OUTPUT_NAME).write_text("\n".join(lines) + "\n")

    print(f"{len(strings)} keys ({len(plural_keys)} plural) -> {len(LOCALES)} locales")
    for lang, keys in sorted(missing.items()):
        print(f"  {lang}: {len(keys)} key(s) without a translated value, e.g. {keys[:3]}")
    return 1 if args.strict and missing else 0


if __name__ == "__main__":
    sys.exit(main())
