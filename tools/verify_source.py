#!/usr/bin/env python3
"""Local source integrity checks; not a replacement for a Gradle APK build."""
from pathlib import Path
import re
import sys
from xml.etree import ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
MAIN=ROOT/'app/src/main'
REQUIRED=[
    'build.gradle','app/build.gradle','settings.gradle',
    'gradle/wrapper/gradle-wrapper.properties','gradlew','gradlew.bat',
    'app/src/main/AndroidManifest.xml',
    'app/src/main/res/layout/activity_main.xml',
    'app/src/main/res/layout/activity_library.xml',
    'app/src/main/res/layout/activity_settings.xml',
    '.github/workflows/android.yml'
]
problems=[]
for name in REQUIRED:
    if not (ROOT/name).exists():problems.append(f'Fichier absent : {name}')
xmls=list(MAIN.rglob('*.xml'))
for path in xmls:
    try:ET.parse(path)
    except Exception as exc:problems.append(f'XML incorrect : {path}: {exc}')
resource_names={}
for folder in (MAIN/'res').iterdir():
    if not folder.is_dir():continue
    group=folder.name.split('-')[0]
    for p in folder.iterdir():
        if p.suffix not in ('.xml','.png','.jpg','.webp'):continue
        resource_names.setdefault(group,set()).add(p.stem)
ids=set()
for path in (MAIN/'res/layout').glob('*.xml'):
    ids.update(re.findall(r'@\+id/([A-Za-z0-9_]+)',path.read_text()))
java='\n'.join(p.read_text() for p in (MAIN/'java').rglob('*.java'))
for name in re.findall(r'\bR\.id\.([A-Za-z0-9_]+)',java):
    if name not in ids:problems.append(f'R.id.{name} non déclaré dans un layout')
for name in re.findall(r'\bR\.drawable\.([A-Za-z0-9_]+)',java):
    if name not in resource_names.get('drawable',set()):problems.append(f'R.drawable.{name} non trouvé')
for kind,name in re.findall(r'@(?:\+)?(drawable|xml|layout)/([A-Za-z0-9_]+)', '\n'.join(p.read_text() for p in xmls)):
    if name not in resource_names.get(kind,set()):problems.append(f'@{kind}/{name} inexistant')
build=(ROOT/'app/build.gradle').read_text()
for expected in ('minSdk 21','targetSdk 34','compileSdk 34'):
    if expected not in build:problems.append('Gradle manquant : '+expected)
for token in ['cloud_volume','Météo évolutive','drawRain','drawClouds','drawLightning','drawParticles']:
    if token not in java:problems.append('Animation manquante : '+token)
if problems:
    print('ECHEC du contrôle statique :')
    for p in problems:print('-',p)
    sys.exit(1)
print(f'OK : {len(xmls)} XML analysés ; {len(ids)} IDs ; {len(list((MAIN/"java").rglob("*.java")))} fichiers Java ; animations présentes.')
print('ATTENTION : il faut toujours lancer ./gradlew assembleDebug sur une machine avec Android SDK 34.')
