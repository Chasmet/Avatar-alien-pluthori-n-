#!/usr/bin/env python3
"""GLB HD offline: cinque textures d'origine, volume arrière sculpté et visèmes.
Reconstruction mono-image approximative, pas scan 360°. Ne modifie jamais les rigs source.
"""
from pathlib import Path
import json,math,struct
BASE=Path(__file__).resolve().parents[1]/'app/src/main/assets'
NAMES=('alpha','sage','guard','shadow','annunaki')
TINT={'alpha':(.49,.42,.41,1.),'sage':(.48,.44,.39,1.),
      'guard':(.28,.26,.40,1.),'shadow':(.20,.20,.31,1.),
      'annunaki':(.40,.30,.23,1.)}

def load(name):
    b=(BASE/('pluthorien_'+name+'_rig.glb')).read_bytes()
    if b[:4]!=b'glTF' or struct.unpack_from('<I',b,4)[0]!=2 or struct.unpack_from('<I',b,8)[0]!=len(b):
        raise ValueError('GLB source invalid: '+name)
    length,kind=struct.unpack_from('<II',b,12)
    if kind!=0x4e4f534a: raise ValueError('JSON chunk absent')
    doc=json.loads(b[20:20+length])
    n,typ=struct.unpack_from('<II',b,20+length)
    if typ!=0x004e4942: raise ValueError('BIN chunk absent')
    return doc,b[28+length:28+length+n]

def read_acc(doc,bin,index):
    a=doc['accessors'][index];v=doc['bufferViews'][a['bufferView']]
    comps={'SCALAR':1,'VEC2':2,'VEC3':3}[a['type']]
    fmt={5126:'f',5123:'H',5121:'B'}[a['componentType']]
    step=struct.calcsize('<'+fmt*comps)
    stride=v.get('byteStride',step)
    off=v.get('byteOffset',0)+a.get('byteOffset',0)
    return [struct.unpack_from('<'+fmt*comps,bin,off+i*stride) for i in range(a['count'])]

def source_mesh(doc,bin,i):
    p=doc['meshes'][i]['primitives'][0]
    coords=read_acc(doc,bin,p['attributes']['POSITION'])
    uv=read_acc(doc,bin,p['attributes']['TEXCOORD_0'])
    index=[x[0] for x in read_acc(doc,bin,p['indices'])]
    if not coords or len(coords)!=len(uv) or len(index)%3 or max(index)>=len(coords):
        raise ValueError('Source mesh invalid')
    return coords,uv,index

def subdivide(p,uv,idx,repeat):
    p=list(p);uv=list(uv);idx=list(idx)
    for _ in range(repeat):
        mid={};tri=[]
        def edge(a,b):
            k=(min(a,b),max(a,b))
            if k not in mid:
                mid[k]=len(p)
                p.append(tuple((p[a][j]+p[b][j])/2 for j in range(3)))
                uv.append(tuple((uv[a][j]+uv[b][j])/2 for j in range(2)))
            return mid[k]
        for i in range(0,len(idx),3):
            a,b,c=idx[i:i+3];ab=edge(a,b);bc=edge(b,c);ca=edge(c,a)
            tri.extend((a,ab,ca,ab,b,bc,ca,bc,c,ab,bc,ca))
        idx=tri
    if len(p)>65535: raise ValueError('OpenGL ES2 vertex limit exceeded')
    return p,uv,idx

def gauss(x,y,a,b,r,s):return math.exp(-((x-a)/r)**2-((y-b)/s)**2)

def sculpt_face(coords):
    p=[]
    for x,y,z in coords:
        z+=.012*gauss(x,y,0,.14,.16,.30)
        z+=.006*(gauss(x,y,-.3,.34,.21,.13)+gauss(x,y,.3,.34,.21,.13))
        p.append((x,y,z))
    return p

def normal_vectors(pos,tri):
    out=[[0.,0.,0.] for _ in pos]
    for i in range(0,len(tri),3):
        a,b,c=tri[i:i+3]
        x,y,z=[pos[b][j]-pos[a][j] for j in range(3)]
        u,v,w=[pos[c][j]-pos[a][j] for j in range(3)]
        n=(y*w-z*v,z*u-x*w,x*v-y*u)
        for k in (a,b,c):
            for j in range(3):out[k][j]+=n[j]
    ans=[]
    for x,y,z in out:
        d=math.sqrt(x*x+y*y+z*z)
        ans.append((x/d,y/d,z/d) if d>1e-10 else (0.,0.,1.))
    return ans

def morphs(p,index):
    all_shapes=[]
    if index==0:
        for name in ('mouthOpen','mouthFunnel','mouthSmile','eyeBlink'):
            ds=[]
            for x,y,z in p:
                lip=gauss(x,y,0.,-.285,.42,.125)
                eye=gauss(x,y,-.30,.39,.22,.11)+gauss(x,y,.30,.39,.22,.11)
                if name=='mouthOpen':delta=(0.,-.028*lip,.013*lip)
                elif name=='mouthFunnel':delta=(-x*.095*lip,.014*lip,.020*lip)
                elif name=='mouthSmile':delta=(.037*(1 if x>0 else -1)*lip,.014*lip,.006*lip)
                else:delta=(0.,-.024*eye,0.)
                ds.append(delta)
            all_shapes.append(ds)
        return ('mouthOpen','mouthFunnel','mouthSmile','eyeBlink'),all_shapes
    if index==1:
        return ('jawLeft','jawRight'),[[(d,0.,.012) for _ in p] for d in (-.034,.034)]
    return (),[]

def hemisphere(rx,cy,ry,front,depth,steps=56,rings=20):
    p=[];uv=[];idx=[]
    for j in range(rings+1):
        theta=.0005+(math.pi/2-.0005)*j/rings
        t=math.sin(theta)
        for k in range(steps+1):
            phi=2*math.pi*k/steps
            p.append((rx*t*math.cos(phi),cy+ry*t*math.sin(phi),front-depth*math.cos(theta)))
            uv.append((k/steps,j/rings))
    for j in range(rings):
        for k in range(steps):
            a=j*(steps+1)+k;b=a+steps+1
            idx.extend((a,b,a+1,a+1,b,b+1))
    return p,uv,idx

def ear(side,rx):
    p=[];uv=[];idx=[];rings=12;steps=24
    for j in range(rings+1):
        lat=-math.pi/2+math.pi*j/rings
        for k in range(steps+1):
            lon=2*math.pi*k/steps;rad=math.cos(lat)
            p.append((side*(rx*.94+.21*rad*math.cos(lon)),.28+.16*math.sin(lat),-.045+.18*rad*math.sin(lon)))
            uv.append((k/steps,j/rings))
    for j in range(rings):
        for k in range(steps):
            a=j*(steps+1)+k;b=a+steps+1;idx.extend((a,b,a+1,a+1,b,b+1))
    return p,uv,idx

def mouth():
    p=[(0.,-.365,.115)];uv=[(.5,.5)];idx=[]
    for i in range(33):
        t=i*2*math.pi/32
        p.append((.255*math.cos(t),-.365+.112*math.sin(t),.115-.020*math.cos(t)**2))
        uv.append((.5+.5*math.cos(t),.5+.5*math.sin(t)))
        if i>0:idx.extend((0,i,i+1))
    return p,uv,idx

class Writer:
    def __init__(self):self.data=bytearray();self.views=[];self.acc=[]
    def raw(self,data):
        while len(self.data)%4:self.data.append(0)
        offset=len(self.data);self.data.extend(data)
        self.views.append({'buffer':0,'byteOffset':offset,'byteLength':len(data)})
        return len(self.views)-1
    def add(self,rows,fmt,kind,extreme=False):
        comps={'SCALAR':1,'VEC2':2,'VEC3':3}[kind]
        payload=b''.join(struct.pack('<'+fmt*comps,*row) for row in rows)
        v=self.raw(payload)
        a={'bufferView':v,'componentType':5126 if fmt=='f' else 5123,'count':len(rows),'type':kind}
        if extreme:
            a['min']=[min(p[k] for p in rows) for k in range(comps)]
            a['max']=[max(p[k] for p in rows) for k in range(comps)]
        self.acc.append(a)
        return len(self.acc)-1
    def mesh(self,name,p,uv,tri,material,shape_names=(),shapes=()):
        if max(tri)>=len(p) or len(p)>65535:raise ValueError(name+' indices out of bounds')
        at={'POSITION':self.add(p,'f','VEC3',True),
            'NORMAL':self.add(normal_vectors(p,tri),'f','VEC3'),
            'TEXCOORD_0':self.add(uv,'f','VEC2')}
        prim={'attributes':at,'indices':self.add([(i,) for i in tri],'H','SCALAR'),'material':material,'mode':4}
        mesh={'name':name,'primitives':[prim]}
        if shapes:
            prim['targets']=[{'POSITION':self.add(shape,'f','VEC3')} for shape in shapes]
            mesh['weights']=[0.]*len(shapes)
            mesh['extras']={'targetNames':list(shape_names)}
        return mesh
    def finish(self,doc):
        while len(self.data)%4:self.data.append(0)
        doc['bufferViews']=self.views;doc['accessors']=self.acc
        doc['buffers']=[{'byteLength':len(self.data)}]
        js=json.dumps(doc,sort_keys=True,separators=(',',':'),ensure_ascii=True).encode()
        js+=b' '*((-len(js))%4)
        binary=bytes(self.data)
        return struct.pack('<III',0x46546c67,2,28+len(js)+len(binary))+struct.pack('<II',len(js),0x4e4f534a)+js+struct.pack('<II',len(binary),0x004e4942)+binary

def generate(name):
    old,bin=load(name)
    im=old['images'][0];v=old['bufferViews'][im['bufferView']]
    offset=v.get('byteOffset',0);texture=bin[offset:offset+v['byteLength']]
    if texture[:4]!=b'RIFF' or texture[8:12]!=b'WEBP':raise ValueError('Missing original image for '+name)
    writer=Writer();meshes=[];nodes=[]
    source=[source_mesh(old,bin,i) for i in range(3)]
    for i,label in enumerate(('Head_Main','Jaw_Lower_Animatable','Torso_Armored')):
        p,uv,tri=subdivide(*source[i],2 if i==0 else 1)
        if i==0:p=sculpt_face(p)
        names,shapes=morphs(p,i)
        meshes.append(writer.mesh(label,p,uv,tri,0,names,shapes))
        nodes.append({'name':label,'mesh':len(meshes)-1})
    head=max(abs(p[0]) for p in source[0][0])
    torso=max(abs(p[0]) for p in source[2][0])
    additions=[('Sculpt_Head_Back',hemisphere(head,.055,.95,.125,.53),1),
      ('Sculpt_Torso_Back',hemisphere(torso,-1.14,.38,-.032,.39),1),
      ('Sculpt_Mouth_Cavity',mouth(),2)]
    if name in ('sage','guard'):
        additions.extend((('Sculpt_Ear_Left',ear(-1,head),1),('Sculpt_Ear_Right',ear(1,head),1)))
    for label,(p,uv,tri),material in additions:
        meshes.append(writer.mesh(label,p,uv,tri,material))
        nodes.append({'name':label,'mesh':len(meshes)-1})
    img=writer.raw(texture)
    doc={'asset':{'version':'2.0','generator':'CHK Alien Live HD GLB v2 (procedural)'},
      'scene':0,'scenes':[{'nodes':list(range(len(nodes)))}],
      'nodes':nodes,'meshes':meshes,
      'materials':[{'name':name+'_portrait','alphaMode':'BLEND','doubleSided':True,
         'pbrMetallicRoughness':{'baseColorTexture':{'index':0},'baseColorFactor':[1,1,1,1],
                               'metallicFactor':0,'roughnessFactor':.91}},
        {'name':name+'_rear','doubleSided':True,'pbrMetallicRoughness':{
            'baseColorFactor':TINT[name],'metallicFactor':.07,'roughnessFactor':.91}},
        {'name':'mouth_cavity','doubleSided':True,'pbrMetallicRoughness':{
            'baseColorFactor':[.055,.018,.027,1.],'metallicFactor':0,'roughnessFactor':1}}],
      'samplers':[{'magFilter':9729,'minFilter':9729,'wrapS':33071,'wrapT':33071}],
      'textures':[{'sampler':0,'extensions':{'EXT_texture_webp':{'source':0}}}],
      'images':[{'bufferView':img,'mimeType':'image/webp','name':'Original_'+name}],
      'extensionsUsed':['EXT_texture_webp'],'extensionsRequired':['EXT_texture_webp'],
      'extras':{'basis':'Original single-view portrait retained as front texture',
        'limitation':'Back and side geometry approximate; not a 360 degree reconstruction',
        'facialTargets':'mouthOpen mouthFunnel mouthSmile eyeBlink jawLeft jawRight'}}
    data=writer.finish(doc)
    dest=BASE/('pluthorien_'+name+'_hd.glb');dest.write_bytes(data)
    print(name,dest.stat().st_size,'bytes;',len(meshes),'meshes;',len(meshes[0]['primitives'][0]['targets']),'facial targets')

if __name__=='__main__':
    for name in NAMES:generate(name)
