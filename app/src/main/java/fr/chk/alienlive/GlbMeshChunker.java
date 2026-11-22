package fr.chk.alienlive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Répartit les vrais GLB haute densité en maillages compatibles OpenGL ES 2.0.
 *  Les modèles importés peuvent avoir des indices 32 bits et >200000 sommets :
 *  chaque lot reçoit des indices unsigned short locaux sans détruire les UV.
 */
final class GlbMeshChunker {
    static final int VERTICES_PER_BATCH=54000;
    static final int INDICES_PER_BATCH=162000;
    static final class Chunk {
        final float[] points,uv,lip,blink;
        final short[] indices;
        Chunk(float[] p,float[] u,float[] l,float[] b,short[] i){
            points=p;uv=u;lip=l;blink=b;indices=i;
        }
    }
    private GlbMeshChunker(){}

    static List<Chunk> split(float[] points,float[] uv,int[] triangleIndices,
                             float[] lip,float[] blink){
        if(points==null||points.length%3!=0||points.length<9)
            throw new IllegalArgumentException("Sommets GLB invalides");
        final int n=points.length/3;
        if(uv!=null&&uv.length!=n*2)
            throw new IllegalArgumentException("Coordonnées UV invalides");
        if(lip!=null&&lip.length!=points.length)
            throw new IllegalArgumentException("Animation bouche incohérente");
        if(blink!=null&&blink.length!=points.length)
            throw new IllegalArgumentException("Animation yeux incohérente");
        if(triangleIndices.length<3||triangleIndices.length%3!=0)
            throw new IllegalArgumentException("Indices GLB invalides");
        for(int id:triangleIndices)if(id<0||id>=n)
            throw new IllegalArgumentException("Index GLB hors du maillage");
        ArrayList<Chunk> chunks=new ArrayList<>();
        float[] pBuffer=new float[VERTICES_PER_BATCH*3];
        float[] uBuffer=uv==null?null:new float[VERTICES_PER_BATCH*2];
        float[] lBuffer=lip==null?null:new float[VERTICES_PER_BATCH*3];
        float[] bBuffer=blink==null?null:new float[VERTICES_PER_BATCH*3];
        short[] idxBuffer=new short[INDICES_PER_BATCH];
        int[] local=new int[n],epoch=new int[n];
        int currentEpoch=1,vCount=0,indexCount=0;
        for(int i=0;i<triangleIndices.length;i+=3){
            int needed=0;
            for(int j=0;j<3;j++)if(epoch[triangleIndices[i+j]]!=currentEpoch)needed++;
            if((vCount+needed>VERTICES_PER_BATCH||indexCount+3>INDICES_PER_BATCH)&&indexCount>0){
                chunks.add(snapshot(pBuffer,uBuffer,lBuffer,bBuffer,idxBuffer,vCount,indexCount));
                currentEpoch++;vCount=0;indexCount=0;
            }
            for(int j=0;j<3;j++){
                int original=triangleIndices[i+j];
                if(epoch[original]!=currentEpoch){
                    epoch[original]=currentEpoch;
                    local[original]=vCount;
                    System.arraycopy(points,original*3,pBuffer,vCount*3,3);
                    if(uv!=null)System.arraycopy(uv,original*2,uBuffer,vCount*2,2);
                    if(lip!=null)System.arraycopy(lip,original*3,lBuffer,vCount*3,3);
                    if(blink!=null)System.arraycopy(blink,original*3,bBuffer,vCount*3,3);
                    vCount++;
                }
                idxBuffer[indexCount++]=(short)local[original];
            }
        }
        if(indexCount>0)chunks.add(snapshot(pBuffer,uBuffer,lBuffer,bBuffer,idxBuffer,vCount,indexCount));
        return chunks;
    }
    private static Chunk snapshot(float[] p,float[] uv,float[] lip,float[] blink,short[] i,
                                  int count,int triangles){
        return new Chunk(Arrays.copyOf(p,count*3),
            uv==null?null:Arrays.copyOf(uv,count*2),
            lip==null?null:Arrays.copyOf(lip,count*3),
            blink==null?null:Arrays.copyOf(blink,count*3),
            Arrays.copyOf(i,triangles));
    }
}
