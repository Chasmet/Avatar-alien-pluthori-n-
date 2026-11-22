package fr.chk.alienlive;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/** Lecture HTTPS d'un manifeste statique GitHub Release. Installation demandée à l'utilisateur. */
public final class UpdateManager {
    private static final String META_STABLE="https://raw.githubusercontent.com/Chasmet/Avatar-alien-pluthori-n-/main/update.json";
    private static final String META_PREVIEW="https://raw.githubusercontent.com/Chasmet/Avatar-alien-pluthori-n-/main/update-preview-v2.json";
    private UpdateManager() { }
    public static void check(Activity activity,boolean explicit){
        new Thread(()->{
            try {
                HttpURLConnection c=(HttpURLConnection)new URL(BuildConfig.DEBUG?META_PREVIEW:META_STABLE).openConnection();
                c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setRequestProperty("User-Agent","CHK-Alien-Live");
                int code=c.getResponseCode();
                if(code!=200)throw new Exception("Canal majauto non publié (HTTP "+code+")");
                byte[] bytes=read(c.getInputStream(),128000);c.disconnect();
                JSONObject data=new JSONObject(new String(bytes,"UTF-8"));
                int remote=data.getInt("versionCode");
                String url=data.getString("apkUrl");
                if(!activity.getPackageName().equals(data.optString("packageName","")))
                    throw new Exception("Version incompatible : réinstallation nécessaire");
                String sha256=data.optString("sha256","").toLowerCase(Locale.ROOT);
                if(!sha256.matches("[a-f0-9]{64}"))throw new Exception("Contrôle d'intégrité absent");
                String allowed=BuildConfig.DEBUG
                    ?"https://github.com/Chasmet/Avatar-alien-pluthori-n-/releases/download/chk-pluthor-preview-"
                    :"https://github.com/Chasmet/Avatar-alien-pluthori-n-/releases/download/chk-alien-live-v";
                if(!url.startsWith(allowed))throw new Exception("URL de version invalide");
                activity.runOnUiThread(()->{
                    if(remote>BuildConfig.VERSION_CODE){
                        new AlertDialog.Builder(activity).setTitle(BuildConfig.DEBUG?"Nouvelle préversion disponible":"Nouvelle version disponible")
                            .setMessage("Version "+data.optString("versionName","nouvelle")+". Télécharger et installer sans effacer les données ?")
                            .setNegativeButton("Plus tard",null)
                            .setPositiveButton("Télécharger",(d,w)->download(activity,url,sha256)).show();
                    }else if(explicit)Toast.makeText(activity,"Application à jour",Toast.LENGTH_LONG).show();
                });
            }catch(Exception e){if(explicit)activity.runOnUiThread(()->Toast.makeText(activity,"Mise à jour indisponible : "+e.getMessage(),Toast.LENGTH_LONG).show());}
        },"chk-update-check").start();
    }
    private static void download(Activity activity,String url,String expectedSha){
        Toast.makeText(activity,"Téléchargement de la mise à jour…",Toast.LENGTH_LONG).show();
        new Thread(()->{
            try {
                HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(15000);c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(true);
                if(c.getResponseCode()!=200)throw new Exception("Téléchargement refusé");
                File dir=new File(activity.getCacheDir(),"updates");if(!dir.exists()&&!dir.mkdirs())throw new Exception("Dossier inaccessible");
                File apk=new File(dir,"CHK-Alien-Live-update.apk");
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(apk)){
                    byte[] b=new byte[32768];int n;long total=0;
                    while((n=in.read(b))!=-1){
                        total+=n;
                        if(total>150_000_000L)throw new Exception("APK trop volumineux");
                        digest.update(b,0,n);out.write(b,0,n);
                    }
                }
                c.disconnect();
                StringBuilder checksum=new StringBuilder(64);
                for(byte b:digest.digest())checksum.append(String.format(Locale.ROOT,"%02x",b&255));
                if(!checksum.toString().equals(expectedSha)){
                    apk.delete();throw new Exception("Signature de fichier non conforme (SHA-256)");
                }
                activity.runOnUiThread(()->install(activity,apk));
            }catch(Exception e){activity.runOnUiThread(()->Toast.makeText(activity,"Erreur : "+e.getMessage(),Toast.LENGTH_LONG).show());}
        },"chk-update-download").start();
    }
    private static void install(Activity activity,File apk){
        if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(activity).setTitle("Autoriser l'installation")
                .setMessage("Autorise CHK Alien Live à installer ses mises à jour, puis relance la vérification.")
                .setPositiveButton("Réglages",(d,w)->{
                    Intent it=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName()));
                    activity.startActivity(it);
                }).setNegativeButton("Annuler",null).show();return;
        }
        Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".fileprovider",apk);
        Intent intent=new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri,"application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
        try{activity.startActivity(intent);}catch(ActivityNotFoundException e){Toast.makeText(activity,"Installateur APK introuvable",Toast.LENGTH_LONG).show();}
    }
    private static byte[] read(InputStream in,int cap)throws Exception{
        try(InputStream source=in;java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n,tot=0;
            while((n=source.read(b))!=-1){tot+=n;if(tot>cap)throw new Exception("Fichier trop grand");out.write(b,0,n);}
            return out.toByteArray();
        }
    }
}
