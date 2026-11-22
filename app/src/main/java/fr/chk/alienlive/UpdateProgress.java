package fr.chk.alienlive;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.net.HttpURLConnection;
import java.util.Locale;

/** Fenêtre MAJAUTO visible et pilotée uniquement depuis le thread UI Android. */
final class UpdateProgress {
    private final Activity activity;
    private final AlertDialog dialog;
    private final ProgressBar bar;
    private final TextView state;
    private final TextView details;
    volatile boolean cancelled=false;
    volatile HttpURLConnection connection;
    private volatile boolean finished=false;

    UpdateProgress(Activity a,String version) {
        activity=a;
        View layout=LayoutInflater.from(a).inflate(R.layout.dialog_update_progress,null);
        bar=layout.findViewById(R.id.updateProgress);
        state=layout.findViewById(R.id.updateState);
        details=layout.findViewById(R.id.updateDetails);
        state.setText("Connexion à GitHub…");
        details.setText("0 Mo téléchargé");
        dialog=new AlertDialog.Builder(a)
            .setTitle("CHK Alien Live — version "+version)
            .setView(layout)
            .setNegativeButton("Annuler",(d,w)->cancel())
            .create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private void cancel(){
        cancelled=true;
        HttpURLConnection c=connection;
        if(c!=null)c.disconnect();
    }

    void stage(String message){
        activity.runOnUiThread(()->{
            if(finished||cancelled||activity.isFinishing())return;
            state.setText(message);
        });
    }

    void bytes(long read,long total) {
        activity.runOnUiThread(()->{
            if(finished||cancelled||activity.isFinishing())return;
            if(total>0){
                int percent=(int)Math.min(99L,read*100L/total);
                bar.setIndeterminate(false);
                bar.setProgress(percent);
                state.setText("Téléchargement : "+percent+" %");
                details.setText(String.format(Locale.FRANCE,"%.1f / %.1f Mo",read/1048576.0,total/1048576.0));
            }else{
                bar.setIndeterminate(true);
                state.setText("Téléchargement en cours…");
                details.setText(String.format(Locale.FRANCE,"%.1f Mo reçus — taille totale inconnue",read/1048576.0));
            }
        });
    }

    void complete(){
        activity.runOnUiThread(()->{
            if(finished||cancelled)return;
            bar.setIndeterminate(false);
            bar.setProgress(100);
            state.setText("100 % — fichier vérifié");
            details.setText("Ouverture de l'installateur Android…");
        });
    }

    void close(){
        finished=true;
        activity.runOnUiThread(()->{
            if(dialog.isShowing())dialog.dismiss();
        });
    }

    void fail(String message){
        finished=true;
        activity.runOnUiThread(()->{
            if(cancelled||activity.isFinishing())return;
            bar.setIndeterminate(false);
            state.setText("Mise à jour non installée");
            details.setText(message);
            dialog.setTitle("Erreur de mise à jour");
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setText("Fermer");
        });
    }
}
