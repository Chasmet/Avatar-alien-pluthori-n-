package fr.chk.alienlive;

import java.nio.ByteBuffer;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read named position deltas, including glTF accessors with sparse overrides. */
final class GlbFaceTargets {
    static float[] read(JSONObject mesh,JSONObject primitive,boolean mouth,ByteBuffer source,int base,
                        JSONArray views,JSONArray accessors,int vertexCount) throws Exception {
        JSONObject extras=mesh.optJSONObject("extras");
        JSONArray names=extras==null?null:extras.optJSONArray("targetNames");
        JSONArray targets=primitive.optJSONArray("targets");
        if(names==null||targets==null)return null;
        for(int i=0;i<Math.min(names.length(),targets.length());i++){
            String name=names.optString(i).toLowerCase(Locale.ROOT).replace("_","").replace("-","");
            boolean matches=mouth?(name.equals("jawopen")||name.equals("mouthopen")):name.equals("eyeblink");
            JSONObject target=targets.optJSONObject(i);
            if(!matches||target==null||!target.has("POSITION"))continue;
            JSONObject a=accessors.getJSONObject(target.getInt("POSITION"));
            if(a.getInt("count")!=vertexCount||a.getInt("componentType")!=5126||!"VEC3".equals(a.getString("type")))
                throw new IllegalArgumentException("Morphologie faciale GLB invalide");
            float[] delta=new float[vertexCount*3];
            if(a.has("bufferView")){
                JSONObject v=views.getJSONObject(a.getInt("bufferView"));
                int stride=v.optInt("byteStride",12);
                int p=start(source,base,v,a.optInt("byteOffset",0),vertexCount,stride,12);
                for(int j=0;j<vertexCount;j++)copy(source,p+j*stride,delta,j);
            }
            JSONObject sparse=a.optJSONObject("sparse");
            if(sparse!=null){
                int count=sparse.getInt("count");
                if(count<1||count>vertexCount)throw new IllegalArgumentException("Nombre de sommets sparse invalide");
                JSONObject indices=sparse.getJSONObject("indices"),values=sparse.getJSONObject("values");
                int type=indices.getInt("componentType");
                int size=switch(type){case 5121->1;case 5123->2;case 5125->4;default->throw new IllegalArgumentException("Type d’index sparse invalide");};
                int ix=start(source,base,views.getJSONObject(indices.getInt("bufferView")),indices.optInt("byteOffset",0),count,size,size);
                int val=start(source,base,views.getJSONObject(values.getInt("bufferView")),values.optInt("byteOffset",0),count,12,12);
                int previous=-1;
                for(int j=0;j<count;j++){
                    int p=ix+j*size;
                    int index=size==1?(source.get(p)&255):size==2?(source.getShort(p)&65535):source.getInt(p);
                    if(index<=previous||index>=vertexCount)throw new IllegalArgumentException("Index facial sparse invalide");
                    copy(source,val+j*12,delta,index);
                    previous=index;
                }
            }
            return delta;
        }
        return null;
    }
    private static int start(ByteBuffer source,int base,JSONObject v,int offset,int count,int stride,int size)throws Exception{
        int viewOffset=v.optInt("byteOffset",0), length=v.getInt("byteLength");
        long end=(long)offset+(long)(count-1)*stride+size;
        if(viewOffset<0||offset<0||count<1||stride<size||end>length||(long)base+viewOffset+length>source.limit())
            throw new IllegalArgumentException("Données de morphologie hors limites");
        return base+viewOffset+offset;
    }
    private static void copy(ByteBuffer source,int offset,float[] into,int index){
        for(int axis=0;axis<3;axis++)into[index*3+axis]=source.getFloat(offset+axis*4);
    }
}
