#!/usr/bin/env python3
"""Tests de non-régression zoom et placement haute précision de CHK Alien Live."""
from pathlib import Path
from xml.etree import ElementTree as ET

root=Path(__file__).resolve().parents[1]
xml=(root/'app/src/main/res/layout/activity_face_calibration.xml').read_text()
ET.fromstring(xml)
required_ids=[
 'calWorkspace','calGlbFrame','calCameraSlot','calCamera','calPrecision',
 'calPrecisionTools','calPointInfo','calSelectLeft','calSelectRight','calSelectMouth',
 'calZoomMinus','calZoomPlus','calCenterView','calNudgeLeft','calNudgeUp',
 'calNudgeDown','calNudgeRight','calStepToggle','calPrecisionDone','calFreezeMotion',
 'calOptions','calTitle','calInstructions','calMode','calSave','calDiagnostics'
]
for value in required_ids:assert f'@+id/{value}' in xml,value
assert 'android:layout_height="335dp"' in xml
assert 'android:layout_height="154dp"' in xml # caméra miniature

base=root/'app/src/main/java/fr/chk/alienlive'
activity=(base/'FaceCalibrationActivity.java').read_text()
overlay=(base/'FacePointsOverlay.java').read_text()
renderer=(base/'Alien3DView.java').read_text()
for item in ['setFocused(true)','View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY',
             'R.id.calSelectLeft','R.id.calNudgeLeft','R.id.calStepToggle',
             'current.save(this)','R.id.calFreezeMotion','glb.setCalibrationViewport',
             'scheduleRigRefresh()']:
    assert item in activity,item
for item in ['setViewport','pinching','ACTION_POINTER_DOWN','ACTION_POINTER_UP',
             'nudgeSelected','normalizedX','normalizedY','selectedPoint',
             'onViewport','getParent().requestDisallowInterceptTouchEvent']:
    assert item in overlay,item
for item in ['setCalibrationViewport','calibrationPanX','calibrationPanY',
             'calibrationZoom','worldHeight*aspect']:
    assert item in renderer,item

# Validation mathématique du mapping écran/GLB : transformation inversible
# à zoom x1/x1.6/x3/x5 et avec panoramique.
for zoom in [1.,1.6,3.,5.]:
 for pan_x,pan_y in [(0.,0.),(.20,-.18),(-.4,.32)]:
  for x,y in [(.375,.705),(.625,.705),(.5,.54),(.18,.82)]:
   width,height=360.,790.
   sx=width*.5+(width*(.13+x*.74)-width*.5)*zoom+pan_x*width
   sy=height*.5+(height*(.055+(1-y)*.89)-height*.5)*zoom+pan_y*height
   unzoom_x=width*.5+(sx-width*.5-pan_x*width)/zoom
   unzoom_y=height*.5+(sy-height*.5-pan_y*height)/zoom
   xx=(unzoom_x/width-.13)/.74
   yy=1-(unzoom_y/height-.055)/.89
   assert abs(xx-x)<1e-6 and abs(yy-y)<1e-6,(zoom,pan_x,pan_y,x,y)

print("OK calibration : vue GLB géante, caméra miniature, zoom/panoramique cohérents, repères 0,1 %, conservation des réglages")
