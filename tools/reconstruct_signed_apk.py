#!/usr/bin/env python3
"""Reconstitue exactement une APK déjà signée sans exposer ni utiliser sa clé privée."""
import base64
import hashlib
import json
import lzma
from pathlib import Path
import sys

if len(sys.argv)!=3:
    raise SystemExit("Usage: reconstruct_signed_apk.py <dossier_artifact> <patch.json>")
source_dir=Path(sys.argv[1])
patch=json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
if patch.get("format")!="chk-signature-delta-v1":
    raise SystemExit("Format de patch inconnu")
source=source_dir/patch["source_name"]
unsigned=source.read_bytes()
if hashlib.sha256(unsigned).hexdigest()!=patch["source_sha256"]:
    raise SystemExit("Artefact source différent : signature non réutilisable")
prefix=int(patch["prefix_length"])
suffix=int(patch["suffix_length"])
if prefix<0 or suffix<0 or prefix+suffix>len(unsigned):
    raise SystemExit("Longueurs de signature invalides")
middle=lzma.decompress(base64.b64decode(patch["middle_xz_base64"]))
signed=unsigned[:prefix]+middle+(unsigned[-suffix:] if suffix else b"")
if hashlib.sha256(signed).hexdigest()!=patch["signed_sha256"]:
    raise SystemExit("APK reconstruite différente de la version signée localement")
out=Path("distribution")/patch["signed_name"]
out.parent.mkdir(parents=True,exist_ok=True)
out.write_bytes(signed)
print("APK reconstruite et contrôlée :",out.name,"octets :",len(signed))
