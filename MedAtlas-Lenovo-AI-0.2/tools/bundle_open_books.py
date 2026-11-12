#!/usr/bin/env python3
"""Bundle original OpenStax PDF editions for offline, noncommercial MedAtlas use.

Each PDF keeps its publisher attribution, embedded figures and license text.
Do not modify, debrand or remove attribution; review license for each edition.
Only publisher-hosted PDFs listed below are fetched. We intentionally do NOT
crawl NCBI Bookshelf, scrape publishers, or silently bundle restricted books.
"""
from __future__ import annotations
import hashlib
import json
import pathlib
import time
import urllib.request

OUTPUT = pathlib.Path("app/src/main/assets/open_books")
BOOKS = [
    {
        "id": "anatomy-physiology-2e",
        "title": "Anatomy & Physiology 2e — Oregon State University adaptation",
        "subject": "Anatomie și fiziologie",
        "publisher": "Oregon State University / OpenStax",
        "file": "Anatomy_and_Physiology_2e.pdf",
        "url": "https://open.oregonstate.education/anatomy2e/open/download?type=pdf",
        "source": "https://open.oregonstate.education/anatomy2e/",
        "rights": "Oregon State 2025 adaptation: CC BY-SA 4.0 except where noted; attribution kept.",
        "alternate": {
            "url": "https://assets.openstax.org/oscms-prodcms/media/documents/anatomy-and-physiology-2e_-_WEB.pdf",
            "title": "Anatomy and Physiology 2e",
            "publisher": "OpenStax",
            "source": "https://openstax.org/details/books/anatomy-and-physiology-2e",
            "rights": "OpenStax edition: check CC terms; keep original PDF credit."
        },
    },
    {
        "id": "microbiology",
        "title": "Microbiology",
        "subject": "Microbiologie",
        "publisher": "OpenStax",
        "file": "Microbiology.pdf",
        "url": "https://openlibrary-repo.ecampusontario.ca/jspui/bitstream/123456789/351/1/Microbiology-LR.pdf",
        "source": "https://openlibrary-repo.ecampusontario.ca/jspui/handle/123456789/351",
        "rights": "OpenStax/ASM CC edition; keep original copyright and attribution pages.",
        "alternate": {
            "url": "https://assets.openstax.org/oscms-prodcms/media/documents/microbiology_-_WEB.pdf",
            "title": "Microbiology",
            "publisher": "OpenStax",
            "source": "https://openstax.org/books/microbiology/pages/1-introduction",
            "rights": "OpenStax publisher PDF; check edition license and credit."
        },
    },
    {
        "id": "pharmacology-for-nurses",
        "title": "Pharmacology for Nurses",
        "subject": "Farmacologie",
        "publisher": "OpenStax",
        "file": "Pharmacology_for_Nurses.pdf",
        "url": "https://assets.openstax.org/oscms-prodcms/media/documents/Pharmacology-WEB.pdf",
        "source": "https://openstax.org/books/pharmacology/pages/1-introduction",
        "rights": "OpenStax CC BY-NC-SA; for noncommercial educational use. Original attribution retained.",
    },
    {
        "id": "medical-surgical-nursing",
        "title": "Medical-Surgical Nursing",
        "subject": "Medicină clinică",
        "publisher": "OpenStax",
        "file": "Medical_Surgical_Nursing.pdf",
        "url": "https://assets.openstax.org/oscms-prodcms/media/documents/Medical-Surgical_Nursing-WEB.pdf",
        "source": "https://openstax.org/books/medical-surgical-nursing/pages/1-introduction",
        "rights": "Check edition-specific CC BY or CC BY-NC-SA terms; original attribution retained.",
    },
]

def download(book: dict) -> dict:
    filename = OUTPUT / book["file"]
    candidates = [book] + ([book["alternate"]] if "alternate" in book else [])
    errors = []
    for candidate in candidates:
        for attempt in range(2):
            try:
                req = urllib.request.Request(candidate["url"], headers={
                    "User-Agent": "Mozilla/5.0 (compatible; MedAtlasEducationalBooks/1.0)"
                })
                with urllib.request.urlopen(req, timeout=150) as source, filename.open("wb") as dest:
                    first = source.read(16)
                    if not first.startswith(b"%PDF-"):
                        raise ValueError("Publisher did not return a PDF")
                    dest.write(first)
                    total = len(first)
                    while True:
                        block = source.read(1024 * 1024)
                        if not block:
                            break
                        total += len(block)
                        if total > 512 * 1024 * 1024:
                            raise ValueError("PDF exceeds 512 MiB")
                        dest.write(block)
                if filename.stat().st_size < 100_000:
                    raise ValueError("Unexpectedly small PDF")
                record = {k: v for k, v in book.items() if k != "alternate"}
                record.update({k: candidate[k] for k in
                    ("title", "publisher", "source", "rights", "url") if k in candidate})
                digest = hashlib.sha256()
                with filename.open("rb") as stream:
                    while chunk := stream.read(1024 * 1024):
                        digest.update(chunk)
                return {**record, "sha256": digest.hexdigest(),
                        "bytes": filename.stat().st_size}
            except Exception as exc:
                filename.unlink(missing_ok=True)
                errors.append(str(exc))
                if attempt == 0:
                    time.sleep(2)
    raise RuntimeError("All licensed publisher PDF downloads failed: " + "; ".join(errors))

def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    records = []
    for b in BOOKS:
        print("Checking licensed publisher PDF:", b["title"], flush=True)
        try:
            record = download(b)
        except Exception as exc:
            # Publisher CDNs sometimes deny automated requests (HTTP 403).
            # Never claim this volume is bundled unless it was downloaded.
            print("::warning::Not bundled: " + b["title"] + " — " + str(exc), flush=True)
            continue
        records.append(record)
        print(" - bytes:", record["bytes"], "sha256:", record["sha256"], flush=True)
    (OUTPUT / "catalog.json").write_text(
        json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print("Offline full textbooks actually bundled:", len(records))

if __name__ == "__main__":
    main()
