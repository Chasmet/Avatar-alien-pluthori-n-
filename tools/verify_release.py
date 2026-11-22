#!/usr/bin/env python3
"""Contrôle de l'APK signé avant publication dans la Release GitHub."""
import hashlib, json, os, pathlib, subprocess
root=pathlib.Path(__file__).resolve().parents[1]
data=json.loads((root/"update.json").read_text(encoding="utf-8"))
url=data.get("apkUrl","")
if not url.startswith("https://github.com/Chasmet/Avatar-alien-pluthori-n-/releases/download/chk-alien-live-v"):
    raise SystemExit("ÉCHEC : URL de Release invalide")
if data.get("packageName")!="fr.chk.alienlive":
    raise SystemExit("ÉCHEC : applicationId invalide")
if data.get("versionCode",0)<=10011:
    raise SystemExit("ÉCHEC : version trop ancienne")
name=pathlib.PurePosixPath(url).name
if name!="CHK-Alien-Live-v"+data["versionName"]+".apk":
    raise SystemExit("ÉCHEC : nom de fichier incohérent")
apk=root/"distribution"/name
if not apk.is_file():
    raise SystemExit("ÉCHEC : APK non fourni")
checksum=hashlib.sha256(apk.read_bytes()).hexdigest()
if checksum.lower()!=data.get("sha256","").lower():
    raise SystemExit("ÉCHEC : SHA-256 différent")
sdk=pathlib.Path(os.environ.get("ANDROID_HOME","/usr/local/lib/android/sdk"))/"build-tools"/"34.0.0"
subprocess.run([str(sdk/"apksigner"),"verify","--verbose","--print-certs",str(apk)],check=True)
badging=subprocess.check_output([str(sdk/"aapt"),"dump","badging",str(apk)],text=True)
if "package: name='fr.chk.alienlive'" not in badging:
    raise SystemExit("ÉCHEC : package non conforme")
if "versionCode='"+str(data["versionCode"])+"'" not in badging:
    raise SystemExit("ÉCHEC : versionCode non conforme")
print("Validation réussie",name,checksum)
