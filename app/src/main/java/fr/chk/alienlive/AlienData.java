package fr.chk.alienlive;

import android.content.Context;
import android.content.SharedPreferences;

public final class AlienData {
    public static final String[] NAMES = {"Pluthorien Alpha", "Pluthorien Sage", "Pluthorien Gardien", "Pluthorien Ombre", "Annunaki ancien", "Personnage personnalisé"};
    public static final int[] PORTRAITS = {R.drawable.alien_alpha, R.drawable.alien_sage, R.drawable.alien_guard, R.drawable.alien_shadow, R.drawable.alien_annunaki, R.drawable.alien_alpha};
    /** Modèles GLB animables inclus hors ligne pour les cinq personnages prédéfinis. */
    public static final String[] MODEL_FILES = {
        "pluthorien_alpha_hd.glb", "pluthorien_sage_hd.glb",
        "pluthorien_guard_hd.glb", "pluthorien_shadow_hd.glb",
        "pluthorien_annunaki_hd.glb"
    };
    public static final String[] SCENES = {
        "Passerelle de Pluthor", "Espace profond", "Monde annunaki", "Décor personnalisé",
        "Grotte des cristaux", "Forêt bioluminescente", "Cité orbitale", "Désert aux deux lunes"
    };
    public static final int[] BACKGROUNDS = {
        R.drawable.scene_bridge,R.drawable.scene_space,R.drawable.scene_annunaki,R.drawable.scene_bridge,
        R.drawable.scene_crystal_cavern,R.drawable.scene_bioluminescent_forest,
        R.drawable.scene_orbital_city,R.drawable.scene_two_moons
    };
    public static final int CUSTOM_CHARACTER=5;
    public static final int CUSTOM_SCENE=3;
    public static java.io.File file(Context c,boolean scene){
        return new java.io.File(c.getFilesDir(),scene?"custom-scene.webp":"custom-character.webp");
    }
    private AlienData() { }
    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences("alien_live", Context.MODE_PRIVATE); }
    public static int character(Context c) { return Math.max(0,Math.min(NAMES.length-1,prefs(c).getInt("character",0))); }
    public static int scene(Context c) { return Math.max(0,Math.min(SCENES.length-1,prefs(c).getInt("scene",0))); }
}
