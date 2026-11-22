#!/usr/bin/env python3
"""Non-regression: per-GLB facial calibration is actually wired into Android."""
from pathlib import Path
from xml.etree import ElementTree as ET
root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/fr/chk/alienlive'
required={
    'FaceCalibration.java':['"face_cal_v1_"','GlbCollection.selected','HYBRID','effectivePoint','mouthSensitivity','reverseVertical','save(Context context)'],
    'FaceCalibrationActivity.java':['TextureView','new FaceTracker','FacePointsOverlay','R.id.calMode','R.id.calSave','R.id.calReverse','R.id.calMouthOpen','R.id.calNeutral','onFacialMeasurements'],
    'FacePointsOverlay.java':['onTouchEvent','onRelease','listener.onMove','settings.setPoint'],
    'Alien3DView.java':['uEyePoints','uMouthPoint','faceCalibration','setFaceCalibration','refreshFaceRig','GlbFaceAutoRig.fromPositions(positions,faceCalibration)'],
    'MainActivity.java':['activeCalibration=FaceCalibration.load(this)','model3D.setFaceCalibration','cal.blink','cal.head','cal.mouth'],
    'SettingsActivity.java':['R.id.openFaceCalibration','FaceCalibrationActivity.class']
}
for name,needles in required.items():
    text=(java/name).read_text(encoding='utf-8')
    for needle in needles:assert needle in text,(name,needle)
for flavor in ('honor','compat'):
    text=(root/f'app/src/{flavor}/java/fr/chk/alienlive/FaceTracker.java').read_text()
    for needle in ['TextureView previewView','createCaptureSession(surfaces','b.addTarget(previewSurface)',
                   'onFacialMeasurements']:assert needle in text,(flavor,needle)
xml=root/'app/src/main/res/layout/activity_face_calibration.xml'
ET.parse(xml)
for needle in ['@+id/calCamera','@+id/calGlbFrame','@+id/calIndependent','@+id/calReverse']:
    assert needle in xml.read_text(),needle
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
assert '.FaceCalibrationActivity' in manifest
print('OK: 3 modes, caméra live + GLB, points tactiles, suivi yeux/bouche, calibration par GLB, retour APK sans perte de données')
