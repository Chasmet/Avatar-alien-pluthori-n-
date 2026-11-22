package fr.chk.alienlive;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class LibraryActivity extends Activity {
    private boolean scene;
    private GridView grid;
    private BaseAdapter adapter;
    private static final int REQUEST_PICK=600;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);setContentView(R.layout.activity_library);
        scene=getIntent().getBooleanExtra("scenes",false);
        ((TextView)findViewById(R.id.title)).setText(scene?"Choisir un décor":"Choisir un extraterrestre");
        Button importButton=findViewById(R.id.importAsset);
        importButton.setText(scene?"Importer un décor depuis le téléphone":"Importer un extraterrestre depuis le téléphone");
        importButton.setOnClickListener(v->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent,REQUEST_PICK);
        });
        Button modelToggle=findViewById(R.id.toggleAvatar3D);
        if(!scene){
            modelToggle.setVisibility(View.VISIBLE);
            refresh3DButton(modelToggle);
            modelToggle.setOnClickListener(v->{
                int character=AlienData.character(this);
                if(character<=0||character>=AlienData.MODEL_FILES.length)return;
                String key="model3d_"+character;
                boolean newValue=!AlienData.prefs(this).getBoolean(key,false);
                AlienData.prefs(this).edit().putBoolean(key,newValue).apply();
                refresh3DButton(modelToggle);
                Toast.makeText(this,newValue?"GLB 3D activé pour ce personnage":"Portrait original restauré",Toast.LENGTH_SHORT).show();
            });
        }
        grid=findViewById(R.id.gallery);
        final String[] names=scene?AlienData.SCENES:AlienData.NAMES;
        final int[] previews=scene?AlienData.BACKGROUNDS:AlienData.PORTRAITS;
        final int selected=scene?AlienData.scene(this):AlienData.character(this);
        adapter=new BaseAdapter(){
            @Override public int getCount(){return names.length;}
            @Override public Object getItem(int pos){return names[pos];}
            @Override public long getItemId(int pos){return pos;}
            @Override public View getView(int pos,View convert,ViewGroup parent){
                LinearLayout box=new LinearLayout(LibraryActivity.this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setBackgroundColor(pos==selected?0xFF413064:0xFF19142C);
                box.setPadding(9,9,9,9);
                ImageView image=new ImageView(LibraryActivity.this);
                boolean custom=pos==(scene?AlienData.CUSTOM_SCENE:AlienData.CUSTOM_CHARACTER);
                java.io.File file=AlienData.file(LibraryActivity.this,scene);
                if(custom&&file.exists()) image.setImageBitmap(BitmapFactory.decodeFile(file.getPath()));
                else image.setImageResource(previews[pos]);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                box.addView(image,new LinearLayout.LayoutParams(-1,(int)(190*getResources().getDisplayMetrics().density)));
                TextView name=new TextView(LibraryActivity.this);
                name.setText(names[pos]+(pos==selected?"  ✓":""));
                name.setTextColor(Color.WHITE);name.setGravity(17);name.setTextSize(15);name.setPadding(0,14,0,14);
                box.addView(name);return box;
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p,v,position,id)->{
            if(position==(scene?AlienData.CUSTOM_SCENE:AlienData.CUSTOM_CHARACTER)&&!AlienData.file(this,scene).exists()){
                Toast.makeText(this,"Importe d'abord une image",Toast.LENGTH_LONG).show();return;
            }
            AlienData.prefs(this).edit().putInt(scene?"scene":"character",position).apply();finish();
        });
    }
    private void refresh3DButton(Button button){
        int character=AlienData.character(this);
        if(character==0){button.setText("Alpha : GLB original conservé");button.setEnabled(false);}
        else if(character>=AlienData.MODEL_FILES.length){button.setText("Image personnalisée : mode 2D");button.setEnabled(false);}
        else{
            boolean active=AlienData.prefs(this).getBoolean("model3d_"+character,false);
            button.setEnabled(true);
            button.setText(active?"Désactiver le GLB 3D — "+AlienData.NAMES[character]
                :"Activer le GLB 3D — "+AlienData.NAMES[character]);
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent intent){
        super.onActivityResult(request,result,intent);
        if(request!=REQUEST_PICK||result!=RESULT_OK||intent==null||intent.getData()==null)return;
        final Uri uri=intent.getData();
        final boolean isScene=scene;
        Toast.makeText(this,"Importation locale…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                AssetImporter.save(getApplicationContext(),uri,isScene);
                AlienData.prefs(this).edit().putInt(isScene?"scene":"character",
                    isScene?AlienData.CUSTOM_SCENE:AlienData.CUSTOM_CHARACTER).apply();
                runOnUiThread(()->{Toast.makeText(this,"Image enregistrée. Active sur la scène !",Toast.LENGTH_LONG).show();finish();});
            }catch(Exception error){runOnUiThread(()->Toast.makeText(this,"Importation impossible : "+error.getMessage(),Toast.LENGTH_LONG).show());}
        },"chk-local-import").start();
    }
}
