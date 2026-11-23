package fr.chk.alienlive;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ScrollView;
import android.util.DisplayMetrics;

/** Calibration réelle des signaux de la caméra, contrôles personnalisés par avatar. */
public final class FaceCalibrationActivity extends Activity {
    private FaceCalibration current;
    private TextureView camera;
    private Alien3DView glb;
    private FaceTracker tracker;
    private FacePointsOverlay overlay;
    private TextView diagnostics,note;
    private FrameLayout workspace;
    private View titleView,instructionsView,optionsView,precisionTools;
    private TextView zoomStatus,pointInfo;
    private FacePointsOverlay points;
    private boolean focused=false,freezeMotion=true;
    private float step=.005f;
    private final android.os.Handler updateHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable reloadFace=()->{if(glb!=null)glb.refreshFaceRig();};
    private boolean resumed=false;
    private volatile float detectedMouth=0f,eyeLeftOpen=-1f,eyeRightOpen=-1f,facePitch=0f;
    private volatile float faceX=.5f,faceY=.5f,faceRoll=0f;
    private volatile String faceStatus="Initialisation caméra…";

    private interface ValueChange{void apply(int value);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        setContentView(R.layout.activity_face_calibration);
        current=FaceCalibration.load(this);
        TextView title=findViewById(R.id.calTitle);
        titleView=title;
        instructionsView=findViewById(R.id.calInstructions);
        optionsView=findViewById(R.id.calOptions);
        precisionTools=findViewById(R.id.calPrecisionTools);
        workspace=findViewById(R.id.calWorkspace);
        zoomStatus=findViewById(R.id.calZoomStatus);
        pointInfo=findViewById(R.id.calPointInfo);
        String name=AlienData.NAMES[AlienData.character(this)];
        GlbCollection.Model m=GlbCollection.selected(this);
        if(AlienData.character(this)==AlienData.CUSTOM_CHARACTER&&m!=null)name=m.name;
        title.setText("Calibration 3D • "+name);
        diagnostics=findViewById(R.id.calDiagnostics);
        note=findViewById(R.id.calCaptureNote);
        camera=findViewById(R.id.calCamera);
        // L'aperçu frontal est mirroir comme un selfie, les mesures restent indépendantes.
        camera.setScaleX(-1f);
        FrameLayout modelSlot=findViewById(R.id.calGlbFrame);
        glb=new Alien3DView(this,new Alien3DView.Status(){
            @Override public void onReady(){runOnUiThread(()->diagnostics.setText("Modèle 3D prêt • "+faceStatus));}
            @Override public void onError(String error){runOnUiThread(()->diagnostics.setText("GLB : "+error));}
        },false);
        modelSlot.addView(glb,new FrameLayout.LayoutParams(-1,-1));
        if(AlienData.character(this)==AlienData.CUSTOM_CHARACTER&&m!=null)glb.setCustomModel(m.file);
        else glb.setCharacter(Math.min(AlienData.character(this),AlienData.MODEL_FILES.length-1),
                 !AlienData.prefs(this).getBoolean("experimentalAlpha",false));
        overlay=new FacePointsOverlay(this);
        modelSlot.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        overlay.configure(current,new FacePointsOverlay.Listener(){
            @Override public void onMove(int which,float x,float y){
                applySettings();
                updatePointInfo();
                note.setText("Point "+(which==0?"œil gauche":which==1?"œil droit":"bouche")
                    +" : "+Math.round(x*100f)+" / "+Math.round(y*100f)+" %. Enregistre pour conserver.");
            }
            @Override public void onRelease(){scheduleRigRefresh();}
            @Override public void onViewport(float zoom,float dx,float dy){
                glb.setCalibrationViewport(zoom,dx,dy);
                updatePointInfo();
            }
        });
        points=overlay;
        setupPrecisionUi();
        RadioGroup modes=findViewById(R.id.calMode);
        modes.check(current.mode==FaceCalibration.AUTO?R.id.calAuto
            :current.mode==FaceCalibration.MANUAL?R.id.calManual:R.id.calHybrid);
        modes.setOnCheckedChangeListener((group,id)->{
            current.mode=id==R.id.calAuto?FaceCalibration.AUTO:
                id==R.id.calManual?FaceCalibration.MANUAL:FaceCalibration.HYBRID;
            overlay.setVisibility(current.mode==FaceCalibration.AUTO?View.INVISIBLE:View.VISIBLE);
            applySettings();
            glb.refreshFaceRig();
        });
        overlay.setVisibility(current.mode==FaceCalibration.AUTO?View.INVISIBLE:View.VISIBLE);
        Switch independent=findViewById(R.id.calIndependent);
        independent.setChecked(current.independentEyes);
        independent.setOnCheckedChangeListener((v,checked)->{current.independentEyes=checked;applySettings();});
        Switch invert=findViewById(R.id.calReverse);
        invert.setChecked(current.reverseVertical);
        invert.setOnCheckedChangeListener((v,checked)->{current.reverseVertical=checked;applySettings();});
        LinearLayout sliders=findViewById(R.id.calSliders);
        addSlider(sliders,"Sensibilité de la bouche",current.mouthSensitivity,v->current.mouthSensitivity=v);
        addSlider(sliders,"Ouverture maximale des lèvres",current.mouthMax,v->current.mouthMax=v);
        addSlider(sliders,"Sensibilité des paupières",current.eyeSensitivity,v->current.eyeSensitivity=v);
        addSlider(sliders,"Amplitude du regard",current.gazeRange,v->current.gazeRange=v);
        addSlider(sliders,"Fluidité du suivi",current.smoothing,v->current.smoothing=v);
        addSlider(sliders,"Amplitude des mouvements de tête",current.headRange,v->current.headRange=v);
        findViewById(R.id.calNeutral).setOnClickListener(v->{
            current.neutralMouth=FaceCalibration.clamp(detectedMouth);
            note.setText("Visage au repos mesuré : bouche "+Math.round(detectedMouth*100f)
                +" %. Ouvre maintenant la bouche puis touche « Bouche ouverte ».");
        });
        findViewById(R.id.calMouthOpen).setOnClickListener(v->{
            current.openMouthReference=Math.max(current.neutralMouth+.10f,
                FaceCalibration.clamp(detectedMouth));
            note.setText("Ouverture mesurée : "+Math.round(detectedMouth*100f)
                +" %. La différence avec le repos règle l'amplitude.");
        });
        findViewById(R.id.calBlinkTest).setOnClickListener(v->{
            if(eyeLeftOpen<0||eyeRightOpen<0){
                note.setText("Ce téléphone n'a pas de mesure précise des paupières dans ce mode.");
            }else{
                note.setText("Œil gauche "+Math.round(eyeLeftOpen*100f)
                    +" % ouvert • droit "+Math.round(eyeRightOpen*100f)
                    +" %. Ferme un œil pour tester son indépendance.");
            }
        });
        findViewById(R.id.calReset).setOnClickListener(v->{
            FaceCalibration.reset(this);
            Toast.makeText(this,"Calibration d'origine restaurée",Toast.LENGTH_SHORT).show();
            recreate();
        });
        findViewById(R.id.calSave).setOnClickListener(v->{
            current.save(this);
            Toast.makeText(this,"Calibration sauvegardée pour ce personnage",Toast.LENGTH_LONG).show();
            finish();
        });
        tracker=new FaceTracker(this,new FaceTracker.Listener(){
            @Override public void onEngine(String name){faceStatus="Suivi facial • "+name;renderDiagnostics();}
            @Override public void onGaze(float x,float y){glb.setGaze(x,y);}
            @Override public void onPose(float x,float y,float roll,float mouth){
                detectedMouth=mouth;faceX=x;faceY=y;faceRoll=roll;
                glb.setPose(focused&&freezeMotion?.5f:x,focused&&freezeMotion?.5f:y,
                    focused&&freezeMotion?0f:roll,current.mouth(mouth));
                renderDiagnostics();
            }
            @Override public void onExpression(float yaw,float pitch,float leftBlink,float rightBlink){
                facePitch=pitch*28f;
                float a=current.blink(leftBlink),b=current.blink(rightBlink);
                if(!current.independentEyes){float both=(a+b)*.5f;a=both;b=both;}
                glb.setExpression(focused&&freezeMotion?0f:current.head(yaw),
                    focused&&freezeMotion?0f:current.head(pitch),a,b);
            }
            @Override public void onCheeksAndLips(float left,float right,float pucker,boolean lipContours){
                glb.setCheeksAndLips(left,right,pucker);
            }
            @Override public void onFacialMeasurements(float left,float right,float mouth,float smile){
                eyeLeftOpen=left;eyeRightOpen=right;
                glb.setSmile(smile<0f?0f:FaceCalibration.clamp((smile-.25f)/.75f));
                renderDiagnostics();
            }
            @Override public void onStatus(String status){
                faceStatus=status;renderDiagnostics();
            }
        },camera);
        camera.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
            @Override public void onSurfaceTextureAvailable(SurfaceTexture surface,int width,int height){startTracking();}
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int width,int height){}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){if(tracker!=null)tracker.stop();return true;}
            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture){}
        });
        applySettings();
        // Lancement en mode géant par défaut : la petite prévisualisation n’est
        // plus l’étape obligatoire pour placer des yeux et des lèvres.
        workspace.post(()->setFocused(true));
    }

    private void setupPrecisionUi(){
        findViewById(R.id.calPrecision).setOnClickListener(v->setFocused(!focused));
        findViewById(R.id.calFreezeMotion).setOnClickListener(v->{
            freezeMotion=!freezeMotion;
            ((Button)findViewById(R.id.calFreezeMotion)).setText(
                freezeMotion?"TÊTE GELÉE":"SUIVI LIVE");
            if(freezeMotion){
                glb.setPose(.5f,.5f,0f,current.mouth(detectedMouth));
                glb.setExpression(0f,0f,0f,0f);
            }
        });
        findViewById(R.id.calPrecisionDone).setOnClickListener(v->{
            current.save(this);
            scheduleRigRefresh();
            setFocused(false);
            Toast.makeText(this,"Points sauvegardés pour ce GLB",Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.calSelectLeft).setOnClickListener(v->selectPoint(0));
        findViewById(R.id.calSelectRight).setOnClickListener(v->selectPoint(1));
        findViewById(R.id.calSelectMouth).setOnClickListener(v->selectPoint(2));
        findViewById(R.id.calZoomPlus).setOnClickListener(v->overlay.zoomBy(1.35f));
        findViewById(R.id.calZoomMinus).setOnClickListener(v->overlay.zoomBy(1f/1.35f));
        findViewById(R.id.calCenterView).setOnClickListener(v->overlay.resetViewport());
        findViewById(R.id.calNudgeLeft).setOnClickListener(v->nudge(-step,0f));
        findViewById(R.id.calNudgeRight).setOnClickListener(v->nudge(step,0f));
        findViewById(R.id.calNudgeUp).setOnClickListener(v->nudge(0f,step));
        findViewById(R.id.calNudgeDown).setOnClickListener(v->nudge(0f,-step));
        findViewById(R.id.calStepToggle).setOnClickListener(v->{
            step=step>.002f?.001f:.005f;
            ((TextView)findViewById(R.id.calFineStep)).setText(step>.002f?"Pas 0,5 %":"Pas 0,1 %");
            updatePointInfo();
        });
        updatePointInfo();
    }
    private void scheduleRigRefresh(){
        updateHandler.removeCallbacks(reloadFace);
        // Sur un GLB dense (Annunaki 221k sommets), reconstruire les morphs
        // une seule fois à la fin du glisser, pas à chaque pixel.
        updateHandler.postDelayed(reloadFace,260L);
    }
    private void selectPoint(int which){
        overlay.selectPoint(which);
        updatePointInfo();
    }
    private void nudge(float dx,float dy){
        overlay.nudgeSelected(dx,dy);
        scheduleRigRefresh();
    }
    private void updatePointInfo(){
        if(overlay==null||zoomStatus==null||pointInfo==null||current==null)return;
        int which=overlay.selectedPoint();
        String label=which==0?"ŒIL GAUCHE":which==1?"ŒIL DROIT":"BOUCHE";
        float[] p=current.point(which);
        zoomStatus.setText("Zoom "+Math.round(overlay.zoom()*100f)+" %");
        pointInfo.setText(label+"   X "+String.format(java.util.Locale.FRANCE,"%.1f",p[0]*100f)
            +" % · Y "+String.format(java.util.Locale.FRANCE,"%.1f",p[1]*100f)+" %");
        int[] ids={R.id.calSelectLeft,R.id.calSelectRight,R.id.calSelectMouth};
        for(int i=0;i<ids.length;i++){
            View button=findViewById(ids[i]);
            if(button!=null)button.setAlpha(which==i?1f:.65f);
        }
    }
    private void setFocused(boolean enable){
        if(workspace==null||overlay==null)return;
        focused=enable;
        titleView.setVisibility(enable?View.GONE:View.VISIBLE);
        instructionsView.setVisibility(enable?View.GONE:View.VISIBLE);
        optionsView.setVisibility(enable?View.GONE:View.VISIBLE);
        precisionTools.setVisibility(enable?View.VISIBLE:View.GONE);
        findViewById(R.id.calFreezeMotion).setVisibility(enable?View.VISIBLE:View.GONE);
        ((Button)findViewById(R.id.calPrecision)).setText(enable?"RÉGLAGES":"GRAND ÉCRAN");
        // Le mode AUTO n'expose pas les points personnalisés. Lors du passage
        // en précision, activer HYBRIDE sans supprimer la détection ML Kit.
        if(enable&&current.mode==FaceCalibration.AUTO){
            ((RadioGroup)findViewById(R.id.calMode)).check(R.id.calHybrid);
        }
        overlay.setVisibility(current.mode==FaceCalibration.AUTO&&!enable?View.INVISIBLE:View.VISIBLE);
        overlay.setPrecision(enable);
        int h;
        if(enable){
            int flags=View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                |View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            getWindow().getDecorView().setSystemUiVisibility(flags);
            float density=getResources().getDisplayMetrics().density;
            h=Math.max((int)(350*density),
                getResources().getDisplayMetrics().heightPixels-(int)(24*density));
            if(overlay.zoom()<1.25f)overlay.zoomBy(1.6f);
        }else{
            getWindow().getDecorView().setSystemUiVisibility(0);
            h=(int)(335*getResources().getDisplayMetrics().density);
            overlay.resetViewport();
        }
        android.view.ViewGroup.LayoutParams params=workspace.getLayoutParams();
        params.height=h;
        workspace.setLayoutParams(params);
        ((ScrollView)findViewById(R.id.calScroll)).smoothScrollTo(0,0);
        updatePointInfo();
    }
    @Override public void onBackPressed(){
        if(focused){setFocused(false);return;}
        super.onBackPressed();
    }
    private void addSlider(LinearLayout parent,String title,int initial,ValueChange changed){
        TextView label=new TextView(this);
        label.setTextColor(0xFFE7D7F8);
        label.setTextSize(13f);
        label.setPadding(2,10,2,2);
        label.setText(title+" : "+initial+" %");
        parent.addView(label,new LinearLayout.LayoutParams(-1,-2));
        SeekBar bar=new SeekBar(this);
        bar.setMax(100);bar.setProgress(initial);
        parent.addView(bar,new LinearLayout.LayoutParams(-1,45*(int)getResources().getDisplayMetrics().density));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar seek,int value,boolean fromUser){
                label.setText(title+" : "+value+" %");
                if(fromUser){changed.apply(value);applySettings();}
            }
            @Override public void onStartTrackingTouch(SeekBar seek){}
            @Override public void onStopTrackingTouch(SeekBar seek){}
        });
    }
    private void applySettings(){
        if(glb==null)return;
        glb.setFaceCalibration(current);
        glb.setReverseVertical(current.reverseVertical);
        glb.setHeadTracking(true);
        glb.setEyeAnimations(true);
        glb.setBatterySaver(false);
    }
    private void renderDiagnostics(){
        runOnUiThread(()->{
            if(!resumed||diagnostics==null)return;
            String l=eyeLeftOpen<0?"N/D":Math.round(eyeLeftOpen*100f)+"%";
            String r=eyeRightOpen<0?"N/D":Math.round(eyeRightOpen*100f)+"%";
            diagnostics.setText(faceStatus+"\nŒil G "+l+" • Œil D "+r
                +" • Bouche "+Math.round(detectedMouth*100f)
                +"% • Haut/bas "+Math.round(facePitch)+"°");
        });
    }
    @Override protected void onResume(){
        super.onResume();resumed=true;
        glb.onResume();
        startTracking();
    }
    private void startTracking(){
        if(!resumed||tracker==null||!camera.isAvailable())return;
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.CAMERA)
            !=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CAMERA},903);
        }else tracker.start();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==903){
            if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startTracking();
            else diagnostics.setText("Autorise la caméra pour calibrer les yeux et la bouche");
        }
    }
    @Override protected void onPause(){
        updateHandler.removeCallbacks(reloadFace);
        resumed=false;
        if(tracker!=null)tracker.stop();
        if(glb!=null)glb.onPause();
        super.onPause();
    }
}
