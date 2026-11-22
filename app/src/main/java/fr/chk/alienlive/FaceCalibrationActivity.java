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

/** Calibration réelle des signaux de la caméra, contrôles personnalisés par avatar. */
public final class FaceCalibrationActivity extends Activity {
    private FaceCalibration current;
    private TextureView camera;
    private Alien3DView glb;
    private FaceTracker tracker;
    private FacePointsOverlay overlay;
    private TextView diagnostics,note;
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
                note.setText("Point "+(which==0?"œil gauche":which==1?"œil droit":"bouche")
                    +" : "+Math.round(x*100f)+" / "+Math.round(y*100f)+" %. Enregistre pour conserver.");
            }
            @Override public void onRelease(){glb.refreshFaceRig();}
        });
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
            @Override public void onPose(float x,float y,float roll,float mouth){
                detectedMouth=mouth;faceX=x;faceY=y;faceRoll=roll;
                glb.setPose(x,y,roll,current.mouth(mouth));
                renderDiagnostics();
            }
            @Override public void onExpression(float yaw,float pitch,float leftBlink,float rightBlink){
                facePitch=pitch*28f;
                float a=current.blink(leftBlink),b=current.blink(rightBlink);
                if(!current.independentEyes){float both=(a+b)*.5f;a=both;b=both;}
                glb.setExpression(current.head(yaw),current.head(pitch),a,b);
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
        resumed=false;
        if(tracker!=null)tracker.stop();
        if(glb!=null)glb.onPause();
        super.onPause();
    }
}
