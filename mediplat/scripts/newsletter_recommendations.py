#!/usr/bin/env python3
"""Score newsletter recommendations from subscribed newsletter tags.

This mirrors the lightweight rule used by the MediPlat portal:
recommend non-subscribed newsletters with the most tag overlap.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def split_tags(raw: str) -> set[str]:
    return {tag.strip().lower() for tag in raw.split(",") if tag.strip()}


def score(newsletters: list[dict], subscribed_codes: set[str], limit: int) -> list[dict]:
    subscribed_tags: set[str] = set()
    for item in newsletters:
        if item.get("code") in subscribed_codes:
            subscribed_tags.update(split_tags(item.get("tags", "")))

    recommendations = []
    for item in newsletters:
        if item.get("code") in subscribed_codes:
            continue
        tags = split_tags(item.get("tags", ""))
        overlap = len(tags & subscribed_tags)
        if not subscribed_tags:
            overlap = max(1, 1000 - int(item.get("displayOrder", 999)))
        if overlap > 0:
            recommendations.append({**item, "score": overlap})

    return sorted(recommendations, key=lambda row: (-row["score"], row.get("title", "")))[:limit]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("catalog", type=Path, help="JSON file containing newsletter objects")
    parser.add_argument("--subscribed", default="", help="Comma-separated newsletter codes")
    parser.add_argument("--limit", type=int, default=3)
    args = parser.parse_args()

    newsletters = json.loads(args.catalog.read_text(encoding="utf-8"))
    subscribed = {code.strip().upper() for code in args.subscribed.split(",") if code.strip()}
    print(json.dumps(score(newsletters, subscribed, args.limit), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
