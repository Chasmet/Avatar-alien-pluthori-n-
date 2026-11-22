package fr.chk.alienlive;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Bibliothèque GLB durable : copie locale depuis Documents, sans serveur ni compte.
 *  Les fichiers restent dans le stockage privé de CHK Alien Live lors des mises à jour APK.
 */
public final class GlbCollection {
    private static final int MAX_MODELS=24;
    private static final long MAX_BYTES=65L*1024L*1024L;
    private static final String INDEX="glbCollectionV1",SELECTED="activeCustomGlbId";

    public static final class Model {
        public final String id,name;
        public final File file;
        Model(String id,String name,File file){this.id=id;this.name=name;this.file=file;}
    }
    private GlbCollection(){}
    private static File folder(Context c){return new File(c.getFilesDir(),"alien_models");}
    private static SharedPreferences prefs(Context c){return AlienData.prefs(c);}
    private static JSONArray index(Context c){
        try{return new JSONArray(prefs(c).getString(INDEX,"[]"));}
        catch(Exception ignored){return new JSONArray();}
    }
    public static List<Model> list(Context c){
        JSONArray a=index(c);
        List<Model> models=new ArrayList<>();
        File root=folder(c);
        for(int i=0;i<a.length();i++){
            JSONObject item=a.optJSONObject(i);
            if(item==null)continue;
            String id=item.optString("id","");
            if(!id.matches("[a-f0-9\\-]{36}"))continue;
            File f=new File(root,id+".glb");
            if(f.isFile())models.add(new Model(id,item.optString("name","Personnage GLB"),f));
        }
        return models;
    }
    public static Model selected(Context c){
        String id=prefs(c).getString(SELECTED,"");
        for(Model m:list(c))if(m.id.equals(id))return m;
        return null;
    }
    public static void select(Context c,String id){
        for(Model model:list(c))if(model.id.equals(id)){
            prefs(c).edit().putString(SELECTED,id)
                .putInt("character",AlienData.CUSTOM_CHARACTER).putBoolean("useModel3D",true).apply();
            return;
        }
        throw new IllegalArgumentException("Modèle 3D introuvable");
    }
    public static String activeName(Context c){
        Model m=selected(c);return m==null?"Personnage personnalisé":m.name;
    }
    private static String displayName(Context c,Uri uri){
        String name="Personnage importé";
        try(Cursor cursor=c.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);
        }catch(Exception ignored){}
        if(name==null||name.trim().isEmpty())name="Personnage importé";
        name=name.replaceAll("(?i)\\.glb(?: \\(\\d+\\))?$","").replaceAll("[\\p{Cntrl}\\\\/]+"," ").trim();
        return name.substring(0,Math.min(60,name.length()));
    }
    private static int getInt(byte[] p,int at){
        return (p[at]&255)|((p[at+1]&255)<<8)|((p[at+2]&255)<<16)|((p[at+3]&255)<<24);
    }
    /** Validation de conteneur glTF2 avant publication du modèle à l'application. */
    static void verify(File file)throws Exception{
        if(file.length()<36||file.length()>MAX_BYTES)throw new Exception("Fichier GLB trop volumineux ou vide (65 Mo max)");
        byte[] h=new byte[20];
        try(RandomAccessFile raf=new RandomAccessFile(file,"r")){
            raf.readFully(h);
            if(getInt(h,0)!=0x46546c67||getInt(h,4)!=2||Integer.toUnsignedLong(getInt(h,8))!=file.length())
                throw new Exception("Le fichier n'est pas un GLB 2.0 valide");
            int jsonSize=getInt(h,12);
            if(getInt(h,16)!=0x4e4f534a||jsonSize<10||jsonSize>3*1024*1024
                    ||20L+jsonSize+8>file.length())throw new Exception("JSON glTF incorrect");
            byte[] json=new byte[jsonSize];raf.readFully(json);
            JSONObject gltf=new JSONObject(new String(json,StandardCharsets.UTF_8).trim());
            if(!gltf.has("meshes")||gltf.getJSONArray("meshes").length()==0)
                throw new Exception("Aucun maillage 3D dans le GLB");
            JSONArray required=gltf.optJSONArray("extensionsRequired");
            if(required!=null)for(int i=0;i<required.length();i++){
                String ext=required.optString(i);
                if(!"KHR_materials_unlit".equals(ext)&&!"EXT_texture_webp".equals(ext))
                    throw new Exception("Extension GLB non prise en charge : "+ext);
            }
            JSONArray accessors=gltf.optJSONArray("accessors");
            if(accessors==null)throw new Exception("Aucun sommet GLB");
            for(int i=0;i<accessors.length();i++){
                JSONObject a=accessors.getJSONObject(i);
                if(a.optLong("count",0)>3000000L)throw new Exception("GLB trop dense pour le téléphone");
            }
            if(gltf.getJSONArray("meshes").length()>150)throw new Exception("Trop de maillages dans ce GLB");
            raf.seek(20L+jsonSize);
            byte[] chunk=new byte[8];raf.readFully(chunk);
            long dataSize=Integer.toUnsignedLong(getInt(chunk,0));
            if(getInt(chunk,4)!=0x004e4942||dataSize<8
                    ||28L+jsonSize+dataSize>file.length())throw new Exception("Données 3D manquantes");
        }
    }
    public static Model importFile(Context c,Uri uri)throws Exception{
        List<Model> existing=list(c);
        if(existing.size()>=MAX_MODELS)throw new Exception("24 modèles déjà présents : retire un ancien modèle");
        File dir=folder(c);
        if(!dir.exists()&&!dir.mkdirs())throw new Exception("Stockage local indisponible");
        String id=UUID.randomUUID().toString();
        String title=displayName(c,uri);
        File staging=new File(dir,id+".part"),saved=new File(dir,id+".glb");
        long bytes=0;
        try{
            try(InputStream input=c.getContentResolver().openInputStream(uri);
                FileOutputStream output=new FileOutputStream(staging)){
                if(input==null)throw new Exception("Fichier inaccessible");
                byte[] block=new byte[65536];
                int n;
                while((n=input.read(block))!=-1){
                    bytes+=n;
                    if(bytes>MAX_BYTES)throw new Exception("GLB supérieur à 65 Mo");
                    output.write(block,0,n);
                }
                output.getFD().sync();
            }
            verify(staging);
            if(!staging.renameTo(saved))throw new Exception("Impossible d'enregistrer le GLB");
            JSONArray items=index(c);
            JSONObject item=new JSONObject();
            item.put("id",id);item.put("name",title);
            items.put(item);
            SharedPreferences.Editor edit=prefs(c).edit()
                .putString(INDEX,items.toString()).putString(SELECTED,id)
                .putInt("character",AlienData.CUSTOM_CHARACTER)
                .putBoolean("useModel3D",true);
            // Décor annunaki animé proposé à l'importation, sans écraser un décor personnel.
            if(title.toLowerCase(java.util.Locale.ROOT).contains("annunaki")
                  &&AlienData.scene(c)==0){
                edit.putInt("scene",2).putBoolean("animatedWeather",true)
                    .putInt("weatherMode",0).putInt("weatherIntensity",86);
            }
            edit.apply();
            return new Model(id,title,saved);
        }finally{if(staging.exists())staging.delete();}
    }
    public static void delete(Context c,String id){
        JSONArray items=index(c),remaining=new JSONArray();
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);
            if(item!=null&&!id.equals(item.optString("id")))remaining.put(item);
        }
        for(Model m:list(c))if(m.id.equals(id)){m.file.delete();break;}
        SharedPreferences.Editor edit=prefs(c).edit().putString(INDEX,remaining.toString());
        if(id.equals(prefs(c).getString(SELECTED,"")))
            edit.remove(SELECTED).putInt("character",0);
        edit.apply();
    }
}
