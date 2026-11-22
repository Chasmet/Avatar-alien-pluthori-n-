#!/usr/bin/env python3
"""Vérification structurelle des 5 maillages GLB avant packaging Android."""
import json
import struct
from pathlib import Path

BASE = Path(__file__).resolve().parents[1] / "app/src/main/assets"
NAMES = ("alpha", "sage", "guard", "shadow", "annunaki")

def read(name):
    path=BASE / ("pluthorien_"+name+"_rig.glb")
    blob=path.read_bytes()
    assert len(blob)>512 and len(blob)<8_000_000, (name, "taille GLB invalide")
    magic,version,length=struct.unpack_from("<III",blob,0)
    assert magic==0x46546c67 and version==2 and length==len(blob), (name,"entête GLB")
    jl,jtype=struct.unpack_from("<II",blob,12)
    assert jtype==0x4E4F534A and 20+jl+8<=len(blob), (name,"JSON")
    data=json.loads(blob[20:20+jl].decode("utf-8").strip())
    binary_len,btype=struct.unpack_from("<II",blob,20+jl)
    assert btype==0x004E4942 and 28+jl+binary_len==len(blob), (name,"BIN")
    views=data["bufferViews"]
    acc=data["accessors"]
    assert data["buffers"][0]["byteLength"]<=binary_len
    for v in views:
        assert v.get("byteOffset",0)+v["byteLength"]<=binary_len,(name,"bufferView")
    model_names={x["name"] for x in data["nodes"]}
    assert {"Head_Main","Jaw_Lower_Animatable","Torso_Armored"}<=model_names,(name,"articulations")
    assert "EXT_texture_webp" in data["extensionsUsed"],(name,"texture extension")
    assert len(data["images"])==1 and data["images"][0]["mimeType"]=="image/webp"
    image_view=views[data["images"][0]["bufferView"]]
    start=28+jl+image_view["byteOffset"]
    content=blob[start:start+image_view["byteLength"]]
    assert content[:4]==b"RIFF" and content[8:12]==b"WEBP",(name,"texture incorrecte")
    vertices=0
    for mesh in data["meshes"]:
        for primitive in mesh["primitives"]:
            idx=acc[primitive["indices"]]
            coord=acc[primitive["attributes"]["POSITION"]]
            uv=acc[primitive["attributes"]["TEXCOORD_0"]]
            assert idx["componentType"]==5123 and idx["count"]%3==0
            assert coord["componentType"]==5126 and coord["type"]=="VEC3"
            assert uv["componentType"]==5126 and uv["type"]=="VEC2"
            assert coord["count"]==uv["count"]
            view=views[idx["bufferView"]]
            position=28+jl+view["byteOffset"]+idx.get("byteOffset",0)
            raw=struct.unpack_from("<"+("H"*idx["count"]),blob,position)
            assert max(raw)<coord["count"], (name,"index out of range")
            vertices+=coord["count"]
    print(f"OK : {name} — {vertices} sommets — {len(blob)} octets — texture intégrée")

if __name__=="__main__":
    for n in NAMES:
        read(n)
