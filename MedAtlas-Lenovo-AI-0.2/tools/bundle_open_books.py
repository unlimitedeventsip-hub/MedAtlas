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
        "title": "Anatomy and Physiology 2e",
        "subject": "Anatomie și fiziologie",
        "publisher": "OpenStax",
        "file": "Anatomy_and_Physiology_2e.pdf",
        "url": "https://assets.openstax.org/oscms-prodcms/media/documents/Anatomy_and_Physiology_2e_-_WEB_c9nD9QL.pdf",
        "source": "https://openstax.org/details/books/anatomy-and-physiology-2e",
        "rights": "Verify edition's CC license. Keep original PDF attribution intact.",
    },
    {
        "id": "microbiology",
        "title": "Microbiology",
        "subject": "Microbiologie",
        "publisher": "OpenStax",
        "file": "Microbiology.pdf",
        "url": "https://assets.openstax.org/oscms-prodcms/media/documents/Microbiology-WEB.pdf",
        "source": "https://openstax.org/books/microbiology/pages/1-introduction",
        "rights": "Verify edition's CC license. Keep original PDF attribution intact.",
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
    if not filename.exists() or filename.stat().st_size < 100_000:
        data = None
        for attempt in range(3):
            try:
                req = urllib.request.Request(book["url"], headers={
                    "User-Agent": "MedAtlasEduOfflineBundle/0.9 (non-commercial study; OpenStax attribution)"
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
                        if total > 150 * 1024 * 1024:
                            raise ValueError("PDF exceeds 150 MB")
                        dest.write(block)
                if filename.stat().st_size < 100_000:
                    raise ValueError("Unexpectedly small PDF")
                break
            except Exception:
                filename.unlink(missing_ok=True)
                if attempt == 2:
                    raise
                time.sleep(4 * (attempt + 1))
    digest = hashlib.sha256()
    with filename.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return {**book, "sha256": digest.hexdigest(), "bytes": filename.stat().st_size}

def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    records = []
    for b in BOOKS:
        print("Bundling OpenStax:", b["title"], flush=True)
        record = download(b)
        records.append(record)
        print(" - bytes:", record["bytes"], "sha256:", record["sha256"], flush=True)
    (OUTPUT / "catalog.json").write_text(
        json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print("Offline full textbooks bundled:", len(records))
    assert len(records) == len(BOOKS)

if __name__ == "__main__":
    main()
