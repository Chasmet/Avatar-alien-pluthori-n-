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
    private FaceTracker tracker;
    private VoiceEngine voice;
    private Button voiceButton;
    private boolean voiceRequested=false;
    private View controls,header,hint;
    private boolean full=false;
    private TextView status;
    private long lastFaceDiagnostic=0L;
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
        hint=findViewById(R.id.liveHint);
        status=findViewById(R.id.status);
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
                    model3D.setPose(x,y,roll,stage.getMouthLevel());
                    long now=android.os.SystemClock.uptimeMillis();
                    if(now-lastFaceDiagnostic>250L){
                        lastFaceDiagnostic=now;
                        status.setText("Visage suivi | Bouche : "+(int)(stage.getMouthLevel()*100)+" % | Déplace la tête et parle");
                    }
                });
            }
            @Override public void onExpression(float yaw,float pitch,float leftBlink,float rightBlink){
                runOnUiThread(()->{
                    stage.updateExpression(yaw,pitch,leftBlink,rightBlink);
                    model3D.setExpression(yaw,pitch);
                });
            }
            @Override public void onStatus(String message){runOnUiThread(()->status.setText(message));}
        });
    }
    private void sync3DMode(){
        if(model3D==null||stage==null)return;
        boolean wanted=AlienData.character(this)==0
            &&AlienData.prefs(this).getBoolean("useModel3D",true);
        model3D.setVisibility(wanted?View.VISIBLE:View.GONE);
        stage.set3DMode(wanted&&model3DReady);
        model3D.setHeadTracking(AlienData.prefs(this).getBoolean("followHead",true));
        model3D.setBatterySaver(AlienData.prefs(this).getBoolean("saveBattery",false));
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
        hint.setVisibility(enabled?View.GONE:View.VISIBLE);
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
        super.onResume();stage.reload();
        model3D.onResume();
        sync3DMode();
        tracker.setSaver(AlienData.prefs(this).getBoolean("saveBattery",true));
        if(voiceRequested) startVoice();
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CAMERA},100);
        }else{tracker.start();}
        long now=System.currentTimeMillis();
        long previousCheck=AlienData.prefs(this).getLong("lastUpdateCheck",0L);
        if(AlienData.prefs(this).getBoolean("autoUpdate",true)&&now-previousCheck>86400000L){
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
    @Override protected void onPause(){tracker.stop();model3D.onPause();if(voice!=null)voice.stop();super.onPause();}
    @Override protected void onDestroy(){if(voice!=null)voice.stop();super.onDestroy();}
    @Override public void onBackPressed(){if(full)setLive(false);else super.onBackPressed();}
}
