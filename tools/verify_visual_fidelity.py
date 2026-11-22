#!/usr/bin/env python3
"""Empêche une préversion de remplacer silencieusement le visage d'origine."""
from pathlib import Path
import hashlib
root=Path(__file__).resolve().parents[1]
src=(root/"app/src/main/java/fr/chk/alienlive")
assets=root/"app/src/main/assets"
original=assets/"pluthorien_alpha.glb"
data=original.read_bytes()
git_sha=hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
assert git_sha=="bf81d4ec17731d4535ab0bfa7d91feda9976caee", "Le GLB Alpha original a changé"
source=(src/"Alien3DView.java").read_text(encoding="utf-8")
activity=(src/"MainActivity.java").read_text(encoding="utf-8")
settings=(src/"SettingsActivity.java").read_text(encoding="utf-8")
layout=(root/"app/src/main/res/layout/activity_settings.xml").read_text(encoding="utf-8")
assert 'activeAsset="pluthorien_alpha.glb"' in source, "Alpha doit charger le GLB stable"
assert 'boolean originalAlpha=!experimentalAlpha;' in activity, "Alpha stable n'est plus la valeur par défaut"
assert 'getBoolean("experimental3D",false)' in activity, "Autres aliens doivent être des tests optionnels"
assert 'getBoolean("experimentalAlpha",false)' in activity, "Alpha reconstruit ne doit pas être forcé"
assert 'setup(R.id.experimental3D,"experimental3D",false)' in settings
assert 'setup(R.id.experimentalAlpha,"experimentalAlpha",false)' in settings
assert '@+id/experimental3D' in layout and '@+id/experimentalAlpha' in layout
assert "PARTAGE D’ÉCRAN TIKTOK" not in (root/"app/src/main/res/layout/activity_main.xml").read_text()
for tag in ("alpha","sage","guard","shadow","annunaki"):
    assert (assets/f"pluthorien_{tag}_rig.glb").is_file(), f"GLB manquant: {tag}"
print("OK : visage Alpha historique intact ; cinq GLB facultatifs ; interface LIVE sans mention TikTok.")
