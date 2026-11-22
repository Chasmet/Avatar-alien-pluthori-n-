#!/usr/bin/env python3
"""Garde-fous MAJAUTO pour les GLB locaux et le rendu dynamique."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=root/"app/src/main/java/fr/chk/alienlive"
layout=(root/"app/src/main/res/layout/activity_library.xml").read_text()
lib=(p/"LibraryActivity.java").read_text()
store=(p/"GlbCollection.java").read_text()
glb=(p/"Alien3DView.java").read_text()
main=(p/"MainActivity.java").read_text()
weather=(p/"AtmosphereRenderer.java").read_text()
chunk=(p/"GlbMeshChunker.java").read_text()
for tag in ('@+id/importGlb','@+id/chooseGlb'):
    assert tag in layout,tag
for item in ('REQUEST_GLB','ACTION_OPEN_DOCUMENT','GlbCollection.importFile',
             'showGlbLibrary','showGlbDeletion'):
    assert item in lib,item
for item in ('MAX_BYTES','MAX_MODELS','verify(staging)','putString(INDEX',
             'selected(Context c)','putInt("character",AlienData.CUSTOM_CHARACTER)'):
    assert item in store,item
for item in ('setCustomModel','FileInputStream(activeCustomFile)','GlbMeshChunker.split',
             'GlbFaceAutoRig.fromPositions','importedLoc','easedBlink',
             'customScale','inSampleSize'):
    assert item in glb,item
for item in ('GlbCollection.selected','setCustomModel(imported.file)'):
    assert item in main,item
assert 'drawAncientTempleEnergy' in weather
assert 'VERTICES_PER_BATCH=54000' in chunk
print("OK: import GLB privé, gestion multi-modèles, 32-bit remap, mini-rig et décor annunaki")
