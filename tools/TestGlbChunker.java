package fr.chk.alienlive;
import java.util.List;

/** Vérifie le découpage d'un GLB 221599 sommets, avec index uint32 simulés. */
public final class TestGlbChunker {
    public static void main(String[] args) {
        int vertices=221599;
        float[] positions=new float[vertices*3];
        float[] uv=new float[vertices*2];
        for(int i=0;i<vertices;i++){
            positions[i*3]=i*.001f;
            uv[i*2]=i/(float)vertices;
        }
        int[] tris=new int[300003];
        for(int i=0;i<tris.length;i++)tris[i]=(i*17)%vertices;
        List<GlbMeshChunker.Chunk> parts=GlbMeshChunker.split(
            positions,uv,tris,null,null);
        int count=0;
        if(parts.size()<2)throw new AssertionError("Expected several GLES2 chunks");
        for(GlbMeshChunker.Chunk part:parts){
            int n=part.points.length/3;
            if(n>GlbMeshChunker.VERTICES_PER_BATCH||n<3||part.uv.length!=n*2)
                throw new AssertionError("Batch incompatible");
            for(short raw:part.indices)if((raw&0xffff)>=n)
                throw new AssertionError("Index out of range");
            count+=part.indices.length;
        }
        if(count!=tris.length)throw new AssertionError("Triangle count changed");
        System.out.println("OK : "+parts.size()+" blocs GLES2, "
            +count/3+" triangles et UV conservés");
    }
}
