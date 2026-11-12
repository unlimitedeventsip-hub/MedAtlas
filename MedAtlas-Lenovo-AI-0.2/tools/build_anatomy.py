"""Build reproducible offline body layers and transparent atlas stickers from BodyParts3D 4.0."""
from collections import defaultdict
from pathlib import Path
import csv
import gzip
import hashlib
import io
import os
import sys
import urllib.request
import zipfile
import numpy as np
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
out = Path(sys.argv[1])
cache = Path(os.environ.get("MEDATLAS_BODY_DATA", root / ".gradle/bodyparts"))
cache.mkdir(parents=True, exist_ok=True)
(out / "body").mkdir(parents=True, exist_ok=True)
(out / "stickers").mkdir(parents=True, exist_ok=True)
files = {
    "partof_BP3D_4.0_obj_99.zip": "9fbc713fffeee924a5a657d9813d84d7eb957bded63adb854931dd5e3eb61c97",
    "partof_element_parts.txt": "3f5f6df1028eb122b30de77c711597b6bb8e5541658e5985859fd228adbf88ea",
}
for name, digest in files.items():
    path = cache / name
    if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
        tmp = path.with_suffix(".download")
        urllib.request.urlretrieve("https://dbarchive.biosciencedbc.jp/data/bodyparts3d/LATEST/" + name, tmp)
        if hashlib.sha256(tmp.read_bytes()).hexdigest() != digest:
            raise ValueError("BodyParts3D checksum mismatch: " + name)
        tmp.replace(path)

parts = defaultdict(set)
for row in (cache / "partof_element_parts.txt").read_text().splitlines()[1:]:
    concept, _, mesh = row.split("\t")
    parts[concept].add(mesh)
layers = [("FMA72979","Piele"), ("FMA23881","Schelet"), ("FMA7157","Nervi"), ("FMA7161","Vase și inimă"),
          ("FMA7158","Respirație"), ("FMA7152","Digestie"), ("FMA7159","Urinar"), ("FMA7160","Reproducător"),
          ("FMA9668","Endocrin"), ("FMA7482","Mușchi și articulații"), ("FMA20394","Alte structuri")]
with (root / "tools/sticker-models.tsv").open(encoding="utf8") as f:
    stickers = list(csv.DictReader(f,delimiter="\t"))
geometry = defaultdict(list)
buffers = [bytearray() for _ in layers]
counts = [0] * len(layers)
archive = zipfile.ZipFile(cache / next(iter(files)))
for name in archive.namelist():
    if not name.endswith(".obj"): continue
    key = Path(name).stem
    lines = archive.read(name).splitlines()
    verts = np.array([line.split()[1:4] for line in lines if line.startswith(b"v ")], dtype=np.float32)
    faces = np.array([[int(v.split(b"/")[0])-1 for v in line.split()[1:]] for line in lines if line.startswith(b"f ")],dtype=np.int32)
    tri = verts[faces]
    normals = np.cross(tri[:,1]-tri[:,0],tri[:,2]-tri[:,0])
    normals /= np.maximum(np.linalg.norm(normals,axis=1)[:,None],1e-12)
    for sticker in stickers:
        if key in parts[sticker["id"]]: geometry[sticker["id"]].append(tri)
    layer = next((i for i,(concept,_) in enumerate(layers) if key in parts[concept]),len(layers)-1)
    # X, superior, anterior in the OpenGL view. Signed coordinates retain millimetre scale.
    xyz = np.rint(tri[:,:,[0,2,1]] * [8,8,-8]).astype("<i2")
    nr = np.rint(normals[:,[0,2,1]] * [127,127,-127]).astype("i1")
    packed = np.zeros((len(tri),3,10),dtype=np.uint8)
    packed[:,:,:6] = np.ascontiguousarray(xyz).view("u1").reshape(-1,3,6)
    packed[:,:,6:9] = nr[:,None,:]
    buffers[layer].extend(packed.tobytes())
    counts[layer] += 1
index = []
for i,(_,label) in enumerate(layers):
    if not buffers[i]: continue
    with gzip.GzipFile(str(out / f"body/layer{i}.mesh"),"wb",mtime=0) as f: f.write(buffers[i])
    index.append(f"{i}\t{label}\t{counts[i]}\t{len(buffers[i])}")
(out/"body/layers.tsv").write_text("\n".join(index),encoding="utf8")
# Orthographic plate, painter's depth order, amber upper-left lighting and transparent background.
for spec in stickers:
    tri = np.concatenate(geometry[spec["id"]])
    angle = .32
    x = tri[:,:,0] * np.cos(angle) + tri[:,:,1] * np.sin(angle)
    depth = tri[:,:,1] * np.cos(angle) - tri[:,:,0] * np.sin(angle)
    y = -tri[:,:,2]
    scale = 460 / max(np.ptp(x),np.ptp(y),1)
    screen = np.stack(((x-(x.min()+x.max())/2)*scale+256,(y-(y.min()+y.max())/2)*scale+256),axis=2).astype(int)
    v = np.stack((x,y,depth),axis=2)
    normal = np.cross(v[:,1]-v[:,0],v[:,2]-v[:,0])
    normal /= np.maximum(np.linalg.norm(normal,axis=1)[:,None],1e-12)
    light = .53+.47*np.abs(normal @ np.array([-.38,-.55,-.74]))
    colors = np.clip(np.array(list(bytes.fromhex(spec["color"])))[None,:]*light[:,None],0,255).astype(int)
    im=Image.new("RGBA",(512,512))
    draw=ImageDraw.Draw(im)
    for i in np.argsort(-depth.mean(axis=1)):
        draw.polygon([tuple(p) for p in screen[i]],fill=tuple(colors[i])+(255,))
    assert im.getbbox()
    im.save(out / ("stickers/"+spec["id"]+".png"))
(out/"stickers/catalog.tsv").write_text("\n".join("\t".join((s["id"],s["label"],s["atlas"])) for s in stickers),encoding="utf8")
print(f"{sum(counts)} meshes · {len(index)} layers · {len(stickers)} stickers")
