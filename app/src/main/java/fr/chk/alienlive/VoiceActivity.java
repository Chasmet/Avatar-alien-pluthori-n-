package fr.chk.alienlive;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/** Choix des voix et contrôle du microphone en direct, sans serveur. */
public final class VoiceActivity extends Activity {
    private VoiceEngine engine;
    private TextView state,depthText,mixText;
    private Button toggle;
    private ProgressBar audioLevel;
    private boolean requested=false;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable meter=new Runnable(){
        @Override public void run(){
            if(engine!=null&&audioLevel!=null){
                audioLevel.setProgress(Math.min(100,(int)(engine.getLevel()*360f)));
                ui.postDelayed(this,100L);
            }
        }
    };
    @Override public void onCreate(Bundle stateBundle){
        super.onCreate(stateBundle);
        setContentView(R.layout.activity_voice);
        state=findViewById(R.id.voiceState);
        toggle=findViewById(R.id.voiceToggle);
        audioLevel=findViewById(R.id.voiceLevel);
        depthText=findViewById(R.id.voiceDepthLabel);
        mixText=findViewById(R.id.voiceMixLabel);
        engine=new VoiceEngine(message->runOnUiThread(()->state.setText(message)));
        Spinner spinner=findViewById(R.id.voicePresets);
        spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,VoiceEngine.PRESETS));
        spinner.setSelection(AlienData.prefs(this).getInt("voicePreset",0));
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int index,long id){
                engine.setPreset(index);
                AlienData.prefs(VoiceActivity.this).edit().putInt("voicePreset",index).apply();
                state.setText("Voix sélectionnée : "+VoiceEngine.PRESETS[index]);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        SeekBar depth=findViewById(R.id.voiceDepth);
        SeekBar mix=findViewById(R.id.voiceMix);
        depth.setProgress(AlienData.prefs(this).getInt("voiceDepth",75));
        mix.setProgress(AlienData.prefs(this).getInt("voiceMix",85));
        updateFx(depth.getProgress(),mix.getProgress());
        depth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar s,int v,boolean user){if(user){
                AlienData.prefs(VoiceActivity.this).edit().putInt("voiceDepth",v).apply();
                updateFx(v,mix.getProgress());
            }}
            @Override public void onStartTrackingTouch(SeekBar s){}
            @Override public void onStopTrackingTouch(SeekBar s){}
        });
        mix.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar s,int v,boolean user){if(user){
                AlienData.prefs(VoiceActivity.this).edit().putInt("voiceMix",v).apply();
                updateFx(depth.getProgress(),v);
            }}
            @Override public void onStartTrackingTouch(SeekBar s){}
            @Override public void onStopTrackingTouch(SeekBar s){}
        });
        toggle.setOnClickListener(v->{
            if(engine.isRunning()){
                requested=false;engine.stop();toggle.setText("Écouter ma voix transformée");
            }else{requested=true;startAudio();}
        });
        findViewById(R.id.saveVoice).setOnClickListener(v->{
            Toast.makeText(this,"Cette voix est sélectionnée pour le mode LIVE",Toast.LENGTH_SHORT).show();
            finish();
        });
        ui.post(meter);
    }
    private void updateFx(int depth,int mix){
        engine.setDepth(depth/100f);engine.setMix(mix/100f);
        depthText.setText("Profondeur : "+depth+" %");
        mixText.setText("Effet extraterrestre : "+mix+" %");
    }
    private void startAudio(){
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},77);return;
        }
        if(engine.start())toggle.setText("Arrêter le test vocal");else requested=false;
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] results){
        super.onRequestPermissionsResult(r,p,results);
        if(r==77){
            if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED&&requested)startAudio();
            else {requested=false;state.setText("Autorise le micro pour tester ta voix");}
        }
    }
    @Override protected void onPause(){if(engine!=null)engine.stop();super.onPause();}
    @Override protected void onResume(){super.onResume();if(requested&&engine!=null)startAudio();}
    @Override protected void onDestroy(){ui.removeCallbacks(meter);if(engine!=null)engine.stop();super.onDestroy();}
}