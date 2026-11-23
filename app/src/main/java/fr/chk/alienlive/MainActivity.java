package fr.chk.alienlive;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private AlienStageView stage;
    private Alien3DView model3D;
    private boolean model3DReady=false;
    private int activeModel=-1;
    private boolean usingOriginalAlpha=false;
    private boolean previous3DWanted=false;
    private String activeCustomGlbId="";
    private FaceTracker tracker;
    private VoiceEngine voice;
    private Button voiceButton;
    private boolean voiceRequested=false;
    private View controls,header;
    private boolean animateLips=true;
    private volatile FaceCalibration activeCalibration;
    private boolean full=false;
    private TextView status,faceDiagnostics;
    private float lastPitchDegrees=0f,eyeLeftOpen=-1f,eyeRightOpen=-1f,smileConfidence=-1f;
    private long lastFaceDiagnostic=0L;
    private boolean mouthMeasured;
    private float trackedMouth=0f,lastX=.5f,lastY=.5f,lastRoll=0f;
    private long lastFaceMouthAt=0L;
    private final android.os.Handler lipHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable lipTicker=new Runnable(){
        @Override public void run(){
            float audio=voice!=null&&voice.isRunning()?voice.getLevel():0f;
            float audioMouth=Math.max(0f,Math.min(1f,(audio-.018f)*5.5f));
            FaceCalibration cal=activeCalibration;
            float voiceForGlb=cal==null?audioMouth:cal.mouth(audioMouth);
            stage.setVoiceMouth(animateLips?audioMouth:0f);
            boolean recent=android.os.SystemClock.uptimeMillis()-lastFaceMouthAt<450L;
            float mouth=recent?(mouthMeasured?trackedMouth:Math.max(trackedMouth,voiceForGlb*.55f)):voiceForGlb;
            model3D.setPose(lastX,lastY,lastRoll,animateLips?mouth:0f);
            lipHandler.postDelayed(this,33L);
        }
    };
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        setContentView(R.layout.activity_main);
        stage=findViewById(R.id.alienStage);
        model3D=new Alien3DView(this,new Alien3DView.Status(){
            @Override public void onReady(){model3DReady=true;sync3DMode();}
            @Override public void onError(String message){
                model3DReady=false;sync3DMode();
                if(status!=null)status.setText(message+" — retour à l'avatar 2D");
            }
        });
        android.widget.FrameLayout frame=findViewById(R.id.stageFrame);
        frame.addView(model3D,1,new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        model3D.setVisibility(View.VISIBLE);
        controls=findViewById(R.id.controlPanel);
        header=findViewById(R.id.header);
        status=findViewById(R.id.status);
        faceDiagnostics=findViewById(R.id.faceDiagnostics);
        findViewById(R.id.buttonLive).setOnClickListener(v->setLive(true));
        findViewById(R.id.buttonAlien).setOnClickListener(v->openLibrary(false));
        findViewById(R.id.buttonScene).setOnClickListener(v->openLibrary(true));
        voiceButton=findViewById(R.id.buttonVoice);
        voice=new VoiceEngine(message->runOnUiThread(()->{
            if(!isFinishing()) Toast.makeText(this,message,Toast.LENGTH_SHORT).show();
        }));
        voiceButton.setOnClickListener(v->{
            if(voiceRequested){voiceRequested=false;voice.stop();voiceButton.setText("Activer la voix extraterrestre — appui long : réglages");}
            else {voiceRequested=true;startVoice();}
        });
        voiceButton.setOnLongClickListener(v->{startActivity(new Intent(this,VoiceActivity.class));return true;});
        findViewById(R.id.buttonVoiceMenu).setOnClickListener(v->startActivity(new Intent(this,VoiceActivity.class)));
        findViewById(R.id.buttonSettings).setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
        model3D.setOnLongClickListener(v->{
            if(full){setLive(false);return true;}
            return false;
        });
        stage.setOnLongClickListener(v->{
            if(full){setLive(false);return true;}
            return false;
        });
        tracker=new FaceTracker(this,new FaceTracker.Listener(){
            @Override public void onPose(float x,float y,float roll,float mouth){
                runOnUiThread(()->{
                    stage.updatePose(x,y,roll,mouth);
                    lastX=x;lastY=y;lastRoll=roll;
                    FaceCalibration cal=activeCalibration;
                    trackedMouth=cal==null?mouth:cal.mouth(mouth);
                    lastFaceMouthAt=android.os.SystemClock.uptimeMillis();
                    model3D.setPose(x,y,roll,animateLips?trackedMouth:0f);
                    long now=android.os.SystemClock.uptimeMillis();
                    if(now-lastFaceDiagnostic>650L){
                        lastFaceDiagnostic=now;
                        status.setText("Suivi facial actif • lèvres : "+(int)(stage.getMouthLevel()*100)+" %");
                    }
                });
            }
            @Override public void onExpression(float yaw,float pitch,float leftBlink,float rightBlink){
                runOnUiThread(()->{
                    stage.updateExpression(yaw,pitch,leftBlink,rightBlink);
                    lastPitchDegrees=pitch*28f;
                    FaceCalibration cal=activeCalibration;
                    float l=cal==null?leftBlink:cal.blink(leftBlink);
                    float r=cal==null?rightBlink:cal.blink(rightBlink);
                    if(cal!=null&&!cal.independentEyes){float both=(l+r)*.5f;l=both;r=both;}
                    model3D.setExpression(cal==null?yaw:cal.head(yaw),
                        cal==null?pitch:cal.head(pitch),l,r);
                });
            }
            @Override public void onCheeksAndLips(float left,float right,float pucker,boolean lipContours){
                runOnUiThread(()->{
                    mouthMeasured=lipContours;
                    stage.setCheeksAndLips(left,right,pucker,lipContours);
                    model3D.setCheeksAndLips(animateLips?left:0f,animateLips?right:0f,animateLips?pucker:0f);
                });
            }
            @Override public void onFacialMeasurements(float leftOpen,float rightOpen,float mouth,float smile){
                runOnUiThread(()->{
                    eyeLeftOpen=leftOpen;eyeRightOpen=rightOpen;smileConfidence=smile;
                    stage.setSmile(smile);
                    model3D.setSmile(!animateLips||smile<0f?0f:Math.max(0f,(smile-.25f)/.75f));
                    if(faceDiagnostics.getVisibility()==View.VISIBLE){
                        String left=leftOpen<0f?"N/D":Math.round(leftOpen*100f)+"%";
                        String right=rightOpen<0f?"N/D":Math.round(rightOpen*100f)+"%";
                        String smileText=smile<0f?"N/D":Math.round(smile*100f)+"%";
                        faceDiagnostics.setText("VISAGE DÉTECTÉ • Œil G "+left+" • Œil D "+right
                            +" • Bouche "+Math.round(mouth*100f)+"% • Sourire "+smileText
                            +" • Haut/bas "+Math.round(lastPitchDegrees)+"°");
                    }
                });
            }
            @Override public void onStatus(String message){
                runOnUiThread(()->{
                    status.setText(message);
                    if(message.startsWith("Visage non détecté")){
                        model3D.setSmile(0f);
                        model3D.setExpression(0f,0f,0f,0f);
                        if(faceDiagnostics.getVisibility()==View.VISIBLE)
                            faceDiagnostics.setText("Visage non détecté : replace-toi devant la caméra");
                    }
                });
            }
        });
    }
    private void sync3DMode(){
        if(model3D==null||stage==null)return;
        int selected=AlienData.character(this);
        // PRIORITÉ VISUELLE : le rendu de la version stable 0.6.9 reste la référence.
        // Les GLB reconstruits sont disponibles uniquement après activation volontaire.
        boolean experimentalOthers=AlienData.prefs(this).getBoolean("experimental3D",false);
        boolean experimentalAlpha=AlienData.prefs(this).getBoolean("experimentalAlpha",false);
        boolean originalAlpha=!experimentalAlpha;
        GlbCollection.Model imported=selected==AlienData.CUSTOM_CHARACTER?GlbCollection.selected(this):null;
        boolean importedWanted=imported!=null;
        boolean wanted=AlienData.prefs(this).getBoolean("useModel3D",true)
                &&(importedWanted||selected==0||(selected<AlienData.MODEL_FILES.length
                    &&(experimentalOthers||AlienData.prefs(this).getBoolean("model3d_"+selected,false))));
        String nextCustomId=imported==null?"":imported.id;
        if(selected!=activeModel || !nextCustomId.equals(activeCustomGlbId)
                || (selected==0&&usingOriginalAlpha!=originalAlpha)
                || (wanted&&!previous3DWanted)){
            model3DReady=false;
            activeModel=selected;
            activeCustomGlbId=nextCustomId;
            usingOriginalAlpha=originalAlpha;
            if(wanted){
                if(importedWanted)model3D.setCustomModel(imported.file);
                else model3D.setCharacter(selected,originalAlpha);
            }
        }
        previous3DWanted=wanted;
        model3D.setVisibility(wanted?View.VISIBLE:View.GONE);
        stage.set3DMode(wanted&&model3DReady);
        model3D.setHeadTracking(AlienData.prefs(this).getBoolean("followHead",true));
        FaceCalibration cal=activeCalibration;
        model3D.setFaceCalibration(cal);
        model3D.setReverseVertical(cal!=null?cal.reverseVertical:
            AlienData.prefs(this).getBoolean("reverseVertical",false));
        model3D.setEyeAnimations(AlienData.prefs(this).getBoolean("animateEyes",true));
        model3D.setBatterySaver(AlienData.prefs(this).getBoolean("saveBattery",false));
        // Ne jamais supprimer la géométrie du GLB original : même silhouette que 0.6.9.
        model3D.setNaturalModel(selected!=0
                &&AlienData.prefs(this).getBoolean("naturalModel",true));
    }
    private void startVoice(){
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},101);return;
        }
        voice.setPreset(AlienData.prefs(this).getInt("voicePreset",0));
        voice.setDepth(AlienData.prefs(this).getInt("voiceDepth",75)/100f);
        voice.setMix(AlienData.prefs(this).getInt("voiceMix",85)/100f);
        if(voice.start())voiceButton.setText("Arrêter la voix extraterrestre — appui long : réglages");
        else voiceRequested=false;
    }
    private void openLibrary(boolean scenes){
        Intent intent=new Intent(this,LibraryActivity.class);
        intent.putExtra("scenes",scenes);startActivity(intent);
    }
    private void setLive(boolean enabled){
        full=enabled;
        stage.setCleanMode(enabled);
        controls.setVisibility(enabled?View.GONE:View.VISIBLE);
        header.setVisibility(enabled?View.GONE:View.VISIBLE);
        if(enabled){
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }else{
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        applySystemUi();
    }
    private void applySystemUi(){
        int flags = full ? (View.SYSTEM_UI_FLAG_FULLSCREEN
            |View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            |View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            |View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            |View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) : 0;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus)applySystemUi();
    }
    @Override protected void onResume(){
        super.onResume();UpdateManager.resumeInstall(this);stage.reload();
        animateLips=AlienData.prefs(this).getBoolean("animateMouth",true);
        activeCalibration=FaceCalibration.load(this);
        model3D.setFaceCalibration(activeCalibration);
        model3D.refreshFaceRig();
        faceDiagnostics.setVisibility(AlienData.prefs(this).getBoolean("faceDiagnostics",false)?View.VISIBLE:View.GONE);
        model3D.onResume();
        lipHandler.removeCallbacks(lipTicker);
        lipHandler.post(lipTicker);
        sync3DMode();
        tracker.setSaver(AlienData.prefs(this).getBoolean("saveBattery",false));
        if(voiceRequested) startVoice();
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CAMERA},100);
        }else{tracker.start();}
        long now=System.currentTimeMillis();
        long previousCheck=AlienData.prefs(this).getLong("lastUpdateCheck",0L);
        if(AlienData.prefs(this).getBoolean("autoUpdate",true)&&now-previousCheck>900000L){
            AlienData.prefs(this).edit().putLong("lastUpdateCheck",now).apply();
            UpdateManager.check(this,false);
        }
    }
    @Override public void onRequestPermissionsResult(int request,String[] perms,int[] results){
        super.onRequestPermissionsResult(request,perms,results);
        if(request==100){
            if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)tracker.start();
            else status.setText("Autorise la caméra pour animer le personnage");
        }else if(request==101){
            if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED&&voiceRequested)startVoice();
            else {voiceRequested=false;status.setText("Autorise le micro pour modifier la voix");}
        }
    }
    @Override protected void onPause(){lipHandler.removeCallbacks(lipTicker);tracker.stop();model3D.onPause();if(voice!=null)voice.stop();super.onPause();}
    @Override protected void onDestroy(){if(voice!=null)voice.stop();super.onDestroy();}
    @Override public void onBackPressed(){if(full)setLive(false);else super.onBackPressed();}
}
