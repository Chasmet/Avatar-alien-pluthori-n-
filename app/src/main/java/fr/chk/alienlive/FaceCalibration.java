package fr.chk.alienlive;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** Réglages faciaux indépendants pour chaque GLB, conservés lors des MAJ APK.
 * Toutes les coordonnées sont normalisées de 0 à 1 sur la silhouette avant.
 */
public final class FaceCalibration {
    public static final int AUTO=0, MANUAL=1, HYBRID=2;
    public int mode=HYBRID;
    public int mouthSensitivity=75,mouthMax=65,eyeSensitivity=80,gazeRange=55,smoothing=70,headRange=65;
    public boolean independentEyes=true,reverseVertical=false;
    public float neutralMouth=0f,openMouthReference=1f;
    // Les positions sont normalisées X gauche->droite et Y bas->haut.
    public float leftX=.375f,leftY=.705f,rightX=.625f,rightY=.705f,mouthX=.50f,mouthY=.54f;
    private final String key;
    private FaceCalibration(String key){this.key=key;}
    public static String keyFor(Context context){
        int character=AlienData.character(context);
        if(character==AlienData.CUSTOM_CHARACTER){
            GlbCollection.Model custom=GlbCollection.selected(context);
            if(custom!=null)return "import_"+custom.id;
        }
        return "preset_"+character;
    }
    public static FaceCalibration load(Context context){return loadFor(context,keyFor(context));}
    static FaceCalibration loadFor(Context context,String modelKey){
        FaceCalibration f=new FaceCalibration("face_cal_v1_"+modelKey);
        SharedPreferences prefs=AlienData.prefs(context);
        f.reverseVertical=prefs.getBoolean("reverseVertical",false);
        f.mouthSensitivity=prefs.getInt("mouthSensitivity",75);
        try{
            JSONObject j=new JSONObject(prefs.getString(f.key,"{}"));
            f.mode=limit(j.optInt("mode",HYBRID),0,2);
            f.mouthSensitivity=limit(j.optInt("mouthSensitivity",f.mouthSensitivity),0,100);
            f.mouthMax=limit(j.optInt("mouthMax",65),0,100);
            f.eyeSensitivity=limit(j.optInt("eyeSensitivity",80),0,100);
            f.gazeRange=limit(j.optInt("gazeRange",55),0,100);
            f.smoothing=limit(j.optInt("smoothing",70),0,100);
            f.headRange=limit(j.optInt("headRange",65),0,100);
            f.independentEyes=j.optBoolean("independentEyes",true);
            f.reverseVertical=j.optBoolean("reverseVertical",f.reverseVertical);
            f.leftX=clamp((float)j.optDouble("leftX",.375));
            f.leftY=clamp((float)j.optDouble("leftY",.705));
            f.rightX=clamp((float)j.optDouble("rightX",.625));
            f.rightY=clamp((float)j.optDouble("rightY",.705));
            f.mouthX=clamp((float)j.optDouble("mouthX",.50));
            f.neutralMouth=clamp((float)j.optDouble("neutralMouth",0));
            f.openMouthReference=clamp((float)j.optDouble("openMouthReference",1));
            f.mouthY=clamp((float)j.optDouble("mouthY",.54));
        }catch(Exception ignored){}
        return f;
    }
    public void save(Context context){
        try{
            JSONObject j=new JSONObject();
            j.put("mode",mode).put("mouthSensitivity",mouthSensitivity).put("mouthMax",mouthMax)
                .put("eyeSensitivity",eyeSensitivity).put("gazeRange",gazeRange)
                .put("smoothing",smoothing).put("headRange",headRange)
                .put("independentEyes",independentEyes).put("reverseVertical",reverseVertical)
                .put("leftX",leftX).put("leftY",leftY)
                .put("rightX",rightX).put("rightY",rightY)
                .put("mouthX",mouthX).put("mouthY",mouthY)
                .put("neutralMouth",neutralMouth).put("openMouthReference",openMouthReference);
            AlienData.prefs(context).edit().putString(key,j.toString()).apply();
        }catch(Exception e){throw new IllegalStateException("Impossible de sauvegarder la calibration",e);}
    }
    public static FaceCalibration reset(Context c){
        AlienData.prefs(c).edit().remove("face_cal_v1_"+keyFor(c)).apply();
        return load(c);
    }
    public float mouth(float detected){
        float openRange=Math.max(.10f,openMouthReference-neutralMouth);
        return clamp(Math.max(0f,detected-neutralMouth)/openRange
            *(.35f+mouthSensitivity/80f)*mouthMax/100f);
    }
    public float blink(float closure){
        return clamp(closure*eyeSensitivity/80f);
    }
    public float head(float angle){
        return Math.max(-1f,Math.min(1f,angle*(headRange/65f)));
    }
    public float gaze(float value){return value*gazeRange/55f;}
    public float smoothingFactor(){
        // Plus de fluidité = interpolation lente, jamais figée.
        return Math.max(.07f,Math.min(.65f,.65f-smoothing*.0054f));
    }
    public float[] point(int which){
        if(which==0)return new float[]{leftX,leftY};
        if(which==1)return new float[]{rightX,rightY};
        return new float[]{mouthX,mouthY};
    }
    public void setPoint(int which,float x,float y){
        x=clamp(x);y=clamp(y);
        if(which==0){leftX=x;leftY=y;}
        else if(which==1){rightX=x;rightY=y;}
        else {mouthX=x;mouthY=y;}
    }
    public static int limit(int value,int min,int max){return Math.max(min,Math.min(max,value));}
    public static float clamp(float value){return Math.max(0f,Math.min(1f,value));}
}
