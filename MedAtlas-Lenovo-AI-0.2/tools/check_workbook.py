"""Check relationships and completeness of the offline Romanian study catalog."""
import csv
import re
from pathlib import Path

app = Path(__file__).resolve().parents[1] / "app/src/main"
def table(path):
    with (app / "assets" / path).open(encoding="utf8", newline="") as stream:
        rows = list(csv.DictReader(stream, delimiter="\t"))
    assert rows and all(None not in row and all(row.values()) for row in rows), path
    return rows

def unique(rows, key):
    keys = [row[key] for row in rows]
    assert len(keys) == len(set(keys)), key
    return set(keys)

patients = table("clinical/patients.tsv")
profiles = table("clinical/profiles.tsv")
labs = table("clinical/laboratory.tsv")
organs = table("atlas-details.tsv")
ids = unique(patients, "id")
unique(patients, "complaint")
unique(patients, "history")
unique(patients, "evolution")
profile_ids = unique(profiles, "id")
assert {p["profile"] for p in patients} == profile_ids
for p in profiles:
    assert len({p["diagnosis"], p["alternative1"], p["alternative2"]}) == 3
    assert p["day7"] != p["month3"]
clinical = next(app.rglob("ClinicalSimulatorActivity.kt")).read_text(encoding="utf8")
legacy_ids = re.findall(r'^        Case\("([^"]+)"', clinical, flags=re.M)
assert not ids.intersection(legacy_ids)
assert len(ids) + len(set(legacy_ids)) >= 100
unique(labs, "id")
for lab in labs:
    assert int(lab["correct"]) in range(3)
    assert len({lab["a"], lab["b"], lab["c"]}) == 3
atlas = next(app.rglob("OrganAtlasActivity.kt")).read_text(encoding="utf8")
groups = re.findall(r'pages/[^"]+",\s*"([^"]+)"\)', atlas)
names = {entry.split("|")[0].strip() for group in groups for entry in group.split(";")}
details = unique(organs, "name")
assert len(groups) == 11 and names == details, names.symmetric_difference(details)
print(f"{len(patients) + len(legacy_ids)} patients, {len(profiles)} additional profiles, "
      f"{len(labs)} lab reports, {len(details)} named structures across {len(groups)} systems")
