package fr.chk.alienlive;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.TextView;

public final class SettingsActivity extends Activity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);setContentView(R.layout.activity_settings);
        setup(R.id.autoUpdate,"autoUpdate",true);
        setup(R.id.batterySaver,"saveBattery",false);
        setup(R.id.followHead,"followHead",true);
        setup(R.id.animateEyes,"animateEyes",true);
        setup(R.id.animatedWeather,"animatedWeather",true);
        setup(R.id.animateMouth,"animateMouth",true);
        setup(R.id.useModel3D,"useModel3D",true);
        setup(R.id.naturalModel,"naturalModel",true);
        setup(R.id.originalAlphaModel,"originalAlphaModel",false);
        final SeekBar sensitivity=findViewById(R.id.mouthSensitivity);
        sensitivity.setProgress(AlienData.prefs(this).getInt("mouthSensitivity",75));
        final TextView sensitivityText=findViewById(R.id.mouthSensitivityLabel);
        sensitivityText.setText("Sensibilité de la bouche : "+sensitivity.getProgress()+" %");
        sensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar s,int value,boolean fromUser){
                sensitivityText.setText("Sensibilité de la bouche : "+value+" %");
                if(fromUser)AlienData.prefs(SettingsActivity.this).edit().putInt("mouthSensitivity",value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s){}
            @Override public void onStopTrackingTouch(SeekBar s){}
        });
        final Spinner mode=findViewById(R.id.weatherMode);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,AtmosphereRenderer.WEATHER_NAMES);
        mode.setAdapter(adapter);
        mode.setSelection(AlienData.prefs(this).getInt("weatherMode",0));
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int position,long id){
                AlienData.prefs(SettingsActivity.this).edit().putInt("weatherMode",position).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });
        final SeekBar amount=findViewById(R.id.weatherIntensity);
        amount.setProgress(AlienData.prefs(this).getInt("weatherIntensity",65));
        final TextView amountText=findViewById(R.id.weatherIntensityLabel);
        amountText.setText("Intensité des effets : "+amount.getProgress()+" %");
        amount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int value,boolean fromUser){
                amountText.setText("Intensité des effets : "+value+" %");
                if(fromUser)AlienData.prefs(SettingsActivity.this).edit().putInt("weatherIntensity",value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        findViewById(R.id.checkUpdate).setOnClickListener(v->UpdateManager.check(this,true));
        ((TextView)findViewById(R.id.version)).setText("Version "+BuildConfig.VERSION_NAME+" ("+BuildConfig.VERSION_CODE+")");
    }
    private void setup(int id,String key,boolean fallback){
        Switch toggle=findViewById(id);
        toggle.setChecked(AlienData.prefs(this).getBoolean(key,fallback));
        toggle.setOnCheckedChangeListener((button,enabled)->AlienData.prefs(this).edit().putBoolean(key,enabled).apply());
    }
}
