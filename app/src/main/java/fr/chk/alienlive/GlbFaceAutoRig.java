package fr.chk.alienlive;

/** Mouvements faciaux de secours pour les GLB générés sans rig ni blendshapes.
 *  Les variations restent petites pour ne pas arracher la texture ou déformer le visage.
 *  Dès qu'un GLB possède ses propres blendshapes, ceux-ci sont utilisés en priorité.
 */
final class GlbFaceAutoRig {
    private GlbFaceAutoRig(){}
    static final class Face {
        final float[] mouth,eyes;
        Face(float[] mouth,float[] eyes){this.mouth=mouth;this.eyes=eyes;}
    }
    private static float gaussian(float x,float y,float cx,float cy,float rx,float ry){
        float dx=(x-cx)/Math.max(.00001f,rx),dy=(y-cy)/Math.max(.00001f,ry);
        return (float)Math.exp(-(dx*dx+dy*dy)*2.2f);
    }
    static Face fromPositions(float[] p){
        if(p.length<300)return new Face(new float[p.length],new float[p.length]);
        float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX;
        float maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(int i=0;i<p.length;i+=3){
            float x=p[i],y=p[i+1],z=p[i+2];
            if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))
                throw new IllegalArgumentException("GLB contient des coordonnées invalides");
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);
            minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
        }
        float width=Math.max(.01f,maxX-minX),height=Math.max(.01f,maxY-minY),
              depth=Math.max(.01f,maxZ-minZ),cx=(minX+maxX)*.5f;
        // Estimation de zones frontales à partir des proportions d'un buste vertical.
        // Pas une reconnaissance anatomique : un export facial riggé est meilleur.
        float mouthY=minY+height*.54f,eyeY=minY+height*.705f;
        float[] lip=new float[p.length],blink=new float[p.length];
        for(int i=0;i<p.length;i+=3){
            float x=p[i],y=p[i+1],z=p[i+2];
            // Exclure la surface arrière et les vêtements.
            float front=Math.max(0f,Math.min(1f,(z-minZ-depth*.47f)/(depth*.34f)));
            float mid=gaussian(x,y,cx,mouthY,width*.205f,height*.045f)*front;
            float l=gaussian(x,y,cx-width*.13f,eyeY,width*.125f,height*.036f);
            float r=gaussian(x,y,cx+width*.13f,eyeY,width*.125f,height*.036f);
            // Sépare très légèrement les lèvres tout en conservant leur contour original.
            float down=y<mouthY?-1f:.40f;
            lip[i+1]=mid*height*.012f*down;
            lip[i+2]=mid*depth*.008f;
            blink[i+1]=-(l+r)*front*height*.009f;
            blink[i+2]=-(l+r)*front*depth*.003f;
        }
        return new Face(lip,blink);
    }
}
