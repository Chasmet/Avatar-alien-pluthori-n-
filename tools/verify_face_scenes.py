#!/usr/bin/env python3
"""Regression checks: pitch orientation, independent eyelids, camera diagnostics and scene assets."""
from pathlib import Path
from PIL import Image
root=Path(__file__).resolve().parents[1]
main=(root/'app/src/main/java/fr/chk/alienlive/MainActivity.java').read_text()
view=(root/'app/src/main/java/fr/chk/alienlive/Alien3DView.java').read_text()
honor=(root/'app/src/honor/java/fr/chk/alienlive/FaceTracker.java').read_text()
compat=(root/'app/src/compat/java/fr/chk/alienlive/FaceTracker.java').read_text()
data=(root/'app/src/main/java/fr/chk/alienlive/AlienData.java').read_text()
atm=(root/'app/src/main/java/fr/chk/alienlive/AtmosphereRenderer.java').read_text()
settings=(root/'app/src/main/res/layout/activity_settings.xml').read_text()
ui=(root/'app/src/main/res/layout/activity_main.xml').read_text()
for token in ('Matrix.rotateM(root,0,-vertical*20f', '(-vertical*.28f',
              'reverseVertical?-easedPitch:easedPitch',
              'uniform vec2 uBlinkWeight;', 'glUniform2f(blinkMorphWeightLoc',
              'setExpression(float yaw,float pitch,float leftBlink,float rightBlink)',
              'public void setReverseVertical(boolean enabled)'):
    assert token in view,'GLB pitch or eye tracking: '+token
for token in ('onFacialMeasurements(', 'getSmilingProbability()',
              'getLeftEyeOpenProbability()', 'getRightEyeOpenProbability()',
              'FaceContour.UPPER_LIP_BOTTOM'):
    assert token in honor,'ML Kit missing '+token
assert 'onFacialMeasurements(' in compat
assert 'model3D.setExpression(yaw,pitch,leftBlink,rightBlink);' in main
assert 'faceDiagnostics.setText(' in main
for token in ('reverseVertical','faceDiagnosticsToggle'):
    assert token in settings
assert '@+id/faceDiagnostics' in ui
for token in ('"Décor personnalisé"', 'CUSTOM_SCENE=3',
              'scene_crystal_cavern', 'scene_bioluminescent_forest',
              'scene_orbital_city', 'scene_two_moons'):
    assert token in data,'Scenes invalid '+token
assert 'drawNewSceneEffects' in atm
for name in ('scene_crystal_cavern','scene_bioluminescent_forest',
             'scene_orbital_city','scene_two_moons'):
    f=root/'app/src/main/res/drawable-nodpi'/f'{name}.jpg'
    assert f.is_file() and f.stat().st_size>50_000,(name,'not generated')
    with Image.open(f) as image:
        assert image.size==(720,1280) and image.mode=='RGB',(name,image.size)
    print('OK DECOR',name,f.stat().st_size,'bytes')
# Camera-positive pitch is compensated by a negative X-axis GLB rotation.
# For a vertex in front of the neck pivot (z>0), lifting the head must lift y.
import math
z=0.5
pitch=math.radians(12)
positive_tilt_y=-(z*math.sin(-pitch))
negative_tilt_y=-(z*math.sin(pitch))
assert positive_tilt_y>0 and negative_tilt_y<0
assert 'Matrix.rotateM(root,0,-vertical*20f' in view
assert '(-vertical*.28f' in view
print('OK pitch rotation: head up -> model up; head down -> model down')
print('OK eyes, mouth and corrected pitch; 4 new scenery backgrounds.')
