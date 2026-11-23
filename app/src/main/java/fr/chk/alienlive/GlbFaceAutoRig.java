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
    static Face fromPositions(float[] p){return fromPositions(p,null);}
    static Face fromPositions(float[] p,FaceCalibration cal){
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
        float[] left=cal==null?new float[]{.375f,.705f}:cal.effectivePoint(0);
        float[] right=cal==null?new float[]{.625f,.705f}:cal.effectivePoint(1);
        float[] mouth=cal==null?new float[]{.5f,.54f}:cal.effectivePoint(2);
        float mouthY=minY+height*mouth[1],mouthX=minX+width*mouth[0];
        float[] lip=new float[p.length],blink=new float[p.length];
        for(int i=0;i<p.length;i+=3){
            float x=p[i],y=p[i+1],z=p[i+2];
            // Exclure la surface arrière et les vêtements.
            float front=Math.max(0f,Math.min(1f,(z-minZ-depth*.47f)/(depth*.34f)));
            float mid=gaussian(x,y,mouthX,mouthY,width*.205f,height*.045f)*front;
            float l=gaussian(x,y,minX+width*left[0],minY+height*left[1],width*.125f,height*.036f);
            float r=gaussian(x,y,minX+width*right[0],minY+height*right[1],width*.125f,height*.036f);
            // Sépare très légèrement les lèvres tout en conservant leur contour original.
            float down=y<mouthY?-1f:.40f;
            lip[i+1]=mid*height*.012f*down;
            lip[i+2]=mid*depth*.008f;
            float offsetLeft=y-(minY+height*left[1]);
            float offsetRight=y-(minY+height*right[1]);
            blink[i+1]=-(l*offsetLeft+r*offsetRight)*front*1.45f;
            blink[i+2]=-(l+r)*front*depth*.003f;
        }
        return new Face(lip,blink);
    }
}
