package fr.chk.alienlive;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Stockage privé des références importées : aucune API web. */
public final class AssetImporter {
    private AssetImporter(){}
    public static void save(Context context,Uri uri,boolean scene)throws Exception {
        BitmapFactory.Options opt=new BitmapFactory.Options();
        opt.inJustDecodeBounds=true;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){
            if(in==null)throw new Exception("Impossible d'ouvrir l'image");
            BitmapFactory.decodeStream(in,null,opt);
        }
        if(opt.outWidth<32||opt.outHeight<32||opt.outWidth>20000||opt.outHeight>20000)
            throw new Exception("Dimensions de fichier non valides");
        opt.inJustDecodeBounds=false;
        opt.inPreferredConfig=Bitmap.Config.ARGB_8888;
        opt.inSampleSize=1;
        while((opt.outWidth/opt.inSampleSize)>1600 || (opt.outHeight/opt.inSampleSize)>1600)
            opt.inSampleSize*=2;
        Bitmap loaded;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){
            if(in==null)throw new Exception("Fichier non accessible");
            loaded=BitmapFactory.decodeStream(in,null,opt);
        }
        if(loaded==null)throw new Exception("Image invalide ou non prise en charge");
        File dest=AlienData.file(context,scene), temp=new File(dest.getParentFile(),dest.getName()+".tmp");
        try(FileOutputStream out=new FileOutputStream(temp)){
            if(!loaded.compress(Bitmap.CompressFormat.WEBP,84,out))
                throw new Exception("Impossible de convertir l'image");
        }finally{loaded.recycle();}
        if(!temp.renameTo(dest)){
            if(dest.exists()&&!dest.delete())throw new Exception("Impossible de remplacer l'image");
            if(!temp.renameTo(dest))throw new Exception("Impossible d'enregistrer l'image");
        }
    }
}
