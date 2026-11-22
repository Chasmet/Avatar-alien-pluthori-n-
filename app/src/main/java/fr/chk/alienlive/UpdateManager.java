package fr.chk.alienlive;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import org.json.JSONObject;
import org.json.JSONArray;
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
    private static final String PREVIEW_RELEASES="https://api.github.com/repos/Chasmet/Avatar-alien-pluthori-n-/releases?per_page=25";
    private static final String PREVIEW_TAG="chk-pluthor-preview-v2-";
    private static volatile File pendingApk;
    private UpdateManager() { }
    public static void resumeInstall(Activity activity){
        File file=pendingApk;
        if(file!=null&&file.isFile()&&
            (Build.VERSION.SDK_INT<26||activity.getPackageManager().canRequestPackageInstalls())){
            pendingApk=null;
            install(activity,file);
        }
    }
    public static void check(Activity activity,boolean explicit){
        new Thread(()->{
            try {
                JSONObject data=BuildConfig.DEBUG?latestPreview():readManifest(META_STABLE);
                int remote=data.getInt("versionCode");
                String url=data.getString("apkUrl");
                if(!activity.getPackageName().equals(data.optString("packageName","")))
                    throw new Exception("Version incompatible : réinstallation nécessaire");
                String sha256=data.optString("sha256","").toLowerCase(Locale.ROOT);
                if(!sha256.matches("[a-f0-9]{64}"))throw new Exception("Contrôle d'intégrité absent");
                String allowed=BuildConfig.DEBUG
                    ?"https://github.com/Chasmet/Avatar-alien-pluthori-n-/releases/download/chk-pluthor-preview-v2-"
                    :"https://github.com/Chasmet/Avatar-alien-pluthori-n-/releases/download/chk-alien-live-v";
                if(!url.startsWith(allowed))throw new Exception("URL de version invalide");
                activity.runOnUiThread(()->{
                    if(remote>BuildConfig.VERSION_CODE){
                        new AlertDialog.Builder(activity).setTitle(BuildConfig.DEBUG?"Nouvelle préversion disponible":"Nouvelle version disponible")
                            .setMessage("Version "+data.optString("versionName","nouvelle")+". Télécharger et installer sans effacer les données ?")
                            .setNegativeButton("Plus tard",null)
                            .setPositiveButton("Télécharger",(d,w)->download(activity,url,sha256,remote)).show();
                    }else if(explicit)Toast.makeText(activity,"Application à jour",Toast.LENGTH_LONG).show();
                });
            }catch(Exception e){if(explicit)activity.runOnUiThread(()->Toast.makeText(activity,"Mise à jour indisponible : "+e.getMessage(),Toast.LENGTH_LONG).show());}
        },"chk-update-check").start();
    }
    private static JSONObject readManifest(String url) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent","CHK-Pluthor-Android");
        try {
            if(conn.getResponseCode()!=200)throw new Exception("Canal MAJAUTO HTTP "+conn.getResponseCode());
            return new JSONObject(new String(read(conn.getInputStream(),128000),"UTF-8"));
        }finally{conn.disconnect();}
    }
    /** Canal preview : lecture directe des Releases GitHub, aucune permission d'écriture API. */
    private static JSONObject latestPreview() throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(PREVIEW_RELEASES).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent","CHK-Pluthor-Preview");
        try {
            if(conn.getResponseCode()!=200)throw new Exception("Releases GitHub HTTP "+conn.getResponseCode());
            JSONArray releases=new JSONArray(new String(read(conn.getInputStream(),1500000),"UTF-8"));
            int latest=-1;
            JSONObject best=null;
            for(int i=0;i<releases.length();i++){
                JSONObject release=releases.optJSONObject(i);
                if(release==null||!release.optBoolean("prerelease",false))continue;
                String tag=release.optString("tag_name","");
                if(!tag.startsWith(PREVIEW_TAG))continue;
                String number=tag.substring(PREVIEW_TAG.length());
                if(!number.matches("[0-9]{1,7}"))continue;
                int build=Integer.parseInt(number);
                if(build<=latest)continue;
                JSONArray assets=release.optJSONArray("assets");
                if(assets==null)continue;
                // Honor nécessite API 23. Sur Android 21/22, prendre l'APK compat.
                String apkName=Build.VERSION.SDK_INT>=23?"app-honor-debug.apk":"app-compat-debug.apk";
                for(int n=0;n<assets.length();n++){
                    JSONObject asset=assets.optJSONObject(n);
                    if(asset==null||!apkName.equals(asset.optString("name")))continue;
                    String link=asset.optString("browser_download_url","");
                    String digest=asset.optString("digest","");
                    if(!digest.matches("sha256:[a-fA-F0-9]{64}"))continue;
                    String expected="https://github.com/Chasmet/Avatar-alien-pluthori-n-/releases/download/"+tag+"/"+apkName;
                    if(!expected.equals(link))continue;
                    JSONObject meta=new JSONObject();
                    meta.put("packageName","fr.chk.alienlive.preview");
                    meta.put("versionCode",20000+build);
                    meta.put("versionName","0.6."+build);
                    meta.put("sha256",digest.substring(7));
                    meta.put("apkUrl",link);
                    best=meta;
                    latest=build;
                }
            }
            if(best==null)throw new Exception("Aucune version signée compatible disponible");
            return best;
        }finally{conn.disconnect();}
    }
    private static void download(Activity activity,String url,String expectedSha,int expectedVersion){
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
                    apk.delete();throw new Exception("Intégrité APK incorrecte (SHA-256)");
                }
                // Ne jamais proposer à Android une mise à jour qui changerait
                // de package, reculerait de version ou casserait la signature.
                try { verifyInstallable(activity,apk,expectedVersion); }
                catch(Exception problem){apk.delete();throw problem;}
                activity.runOnUiThread(()->install(activity,apk));
            }catch(Exception e){activity.runOnUiThread(()->Toast.makeText(activity,"Erreur : "+e.getMessage(),Toast.LENGTH_LONG).show());}
        },"chk-update-download").start();
    }
    /** Vérifie localement le package, la version et la correspondance des certificats. */
    private static void verifyInstallable(Activity activity,File apk,int expectedVersion) throws Exception {
        PackageManager pm=activity.getPackageManager();
        int flags=Build.VERSION.SDK_INT>=28
            ?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo incoming=pm.getPackageArchiveInfo(apk.getAbsolutePath(),flags);
        if(incoming==null)throw new Exception("Android ne reconnaît pas cet APK");
        if(!activity.getPackageName().equals(incoming.packageName))
            throw new Exception("Package incompatible : données protégées");
        long code=Build.VERSION.SDK_INT>=28?incoming.getLongVersionCode():incoming.versionCode;
        if(code!=expectedVersion||code<=BuildConfig.VERSION_CODE)
            throw new Exception("Version APK incorrecte ou trop ancienne");
        PackageInfo installed=pm.getPackageInfo(activity.getPackageName(),flags);
        Signature[] oldSignatures=signatures(installed);
        Signature[] newSignatures=signatures(incoming);
        if(oldSignatures==null||newSignatures==null
            ||oldSignatures.length==0||oldSignatures.length!=newSignatures.length)
            throw new Exception("Certificat Android absent ou incompatible");
        for(Signature original:oldSignatures){
            boolean matched=false;
            for(Signature candidate:newSignatures){
                if(original.equals(candidate)){matched=true;break;}
            }
            if(!matched)throw new Exception("Signature différente : mise à jour refusée");
        }
    }
    private static Signature[] signatures(PackageInfo packageInfo){
        if(Build.VERSION.SDK_INT>=28){
            if(packageInfo.signingInfo==null)return null;
            return packageInfo.signingInfo.getApkContentsSigners();
        }
        return packageInfo.signatures;
    }
    private static void install(Activity activity,File apk){
        if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()){
            pendingApk=apk;
            new AlertDialog.Builder(activity).setTitle("Autoriser l'installation")
                .setMessage("Autorise l'installation des mises à jour. L'APK déjà téléchargé sera réutilisé à ton retour.")
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
