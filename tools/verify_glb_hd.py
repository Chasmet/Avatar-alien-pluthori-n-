#!/usr/bin/env python3
"""Validation structurelle réelle des cinq GLB HD après génération dans Actions."""
from pathlib import Path
import json,struct
BASE=Path(__file__).resolve().parents[1]/'app/src/main/assets'
TAGS=('alpha','sage','guard','shadow','annunaki')

def parse(path):
    b=path.read_bytes()
    assert len(b)>1000 and b[:4]==b'glTF',(path,'en-tête')
    magic,version,size=struct.unpack_from('<III',b,0)
    assert version==2 and size==len(b),(path,'version')
    jlen,jtyp=struct.unpack_from('<II',b,12)
    assert jtyp==0x4e4f534a,(path,'JSON')
    data=json.loads(b[20:20+jlen])
    n,ntyp=struct.unpack_from('<II',b,20+jlen)
    assert ntyp==0x004e4942 and 28+jlen+n==len(b),(path,'BIN')
    return data,b[28+jlen:]

def tex(data,blob):
    image=data['images'][0]
    view=data['bufferViews'][image['bufferView']]
    start=view.get('byteOffset',0)
    t=blob[start:start+view['byteLength']]
    assert t[:4]==b'RIFF' and t[8:12]==b'WEBP'
    return t

def validate(tag):
    source,source_bin=parse(BASE/('pluthorien_'+tag+'_rig.glb'))
    hd,blob=parse(BASE/('pluthorien_'+tag+'_hd.glb'))
    assert tex(source,source_bin)==tex(hd,blob),(tag,'texture source altérée')
    assert hd['extensionsRequired']==['EXT_texture_webp']
    assert 200000<len(blob)<15000000,(tag,'taille binaire')
    names={n['name'] for n in hd['nodes']}
    assert {'Head_Main','Jaw_Lower_Animatable','Torso_Armored',
        'Sculpt_Head_Back','Sculpt_Torso_Back','Sculpt_Mouth_Cavity'}<=names
    assert len(hd['scenes'][0]['nodes'])==len(hd['nodes'])
    for view in hd['bufferViews']:
        offset=view.get('byteOffset',0)
        assert offset%4==0 and offset>=0 and offset+view['byteLength']<=len(blob)
    assert hd['meshes'][0]['extras']['targetNames']==[
       'mouthOpen','mouthFunnel','mouthSmile','eyeBlink']
    assert len(hd['meshes'][0]['primitives'][0]['targets'])==4
    assert len(hd['meshes'][1]['primitives'][0]['targets'])==2
    assert len(hd['meshes'])==(8 if tag in ('sage','guard') else 6)
    vertex_count=0
    for mesh in hd['meshes']:
        for primitive in mesh['primitives']:
            acc=hd['accessors']
            p=acc[primitive['attributes']['POSITION']]
            n=acc[primitive['attributes']['NORMAL']]
            uv=acc[primitive['attributes']['TEXCOORD_0']]
            indices=acc[primitive['indices']]
            assert p['count']==n['count']==uv['count']
            assert p['count']<65536 and indices['count']%3==0
            assert indices['componentType']==5123 and indices['count']<=300000
            for morph in primitive.get('targets',[]):
                assert acc[morph['POSITION']]['count']==p['count']
            vertex_count+=p['count']
    assert vertex_count>10000,(tag,vertex_count)
    print('OK HD GLB',tag,vertex_count,'sommets,',len(hd['meshes']),'maillages, texture préservée')
if __name__=='__main__':
    for tag in TAGS:validate(tag)
