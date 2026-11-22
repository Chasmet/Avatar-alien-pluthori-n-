#!/usr/bin/env python3
"""Four original, deterministic, offline animated-scene background illustrations."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFilter
import random,math
W,H=720,1280
OUT=Path(__file__).resolve().parents[1]/'app/src/main/res/drawable-nodpi'
SCENES={
    'scene_crystal_cavern':((9,8,29),(63,38,86),(22,28,54),14),
    'scene_bioluminescent_forest':((3,22,31),(23,92,82),(9,33,34),31),
    'scene_orbital_city':((4,11,40),(49,44,112),(7,18,43),53),
    'scene_two_moons':((23,10,29),(190,81,75),(48,22,34),71),
}
def glow(base,x,y,r,color,strength=115):
    m=Image.new('RGBA',base.size,(0,0,0,0));d=ImageDraw.Draw(m)
    d.ellipse((x-r,y-r,x+r,y+r),fill=(*color,strength))
    base.alpha_composite(m.filter(ImageFilter.GaussianBlur(max(4,r*.6))))
def bg(top,bottom,seed):
    rand=random.Random(seed)
    b=Image.new('RGB',(W,H));d=ImageDraw.Draw(b)
    for y in range(H):
        t=y/max(1,H-1);k=.5-.5*math.cos(t*math.pi)
        noise=rand.randrange(-4,5)
        c=tuple(max(0,min(255,int(a*(1-k)+z*k)+noise)) for a,z in zip(top,bottom))
        d.line((0,y,W,y),fill=c)
    for ix,amount in [(0,25),(1,11),(2,6)]:
        size=(max(6,W//(12*(ix+1))),max(6,H//(12*(ix+1))))
        n=Image.new('L',size)
        n.putdata([rand.randrange(256) for _ in range(size[0]*size[1])])
        n=n.resize((W,H),Image.Resampling.BICUBIC).filter(ImageFilter.GaussianBlur(8+ix*2))
        f=Image.new('RGBA',(W,H),(210,205,231,0))
        f.putalpha(n.point(lambda v,s=amount:int(v*s/255)))
        tmp=b.convert('RGBA');tmp.alpha_composite(f);b=tmp.convert('RGB')
    return b.convert('RGBA')
def mountains(b,seed,ground):
    rand=random.Random(seed);d=ImageDraw.Draw(b,'RGBA')
    for layer in range(4):
        y0=int(H*(.50+layer*.13));height=H*(.14-layer*.019)
        pts=[(-10,H)]
        for i in range(-10,W+30,15):
            wave=(math.sin(i*.011+layer*2.4)+.5*math.sin(i*.037+layer))*height*.42
            pts.append((i,int(y0-wave+rand.randrange(-12,13))))
        pts.append((W+10,H))
        shade=tuple(min(255,int(c*(1+.15*layer))) for c in ground)
        d.polygon(pts,fill=(*shade,int(140+layer*22)))
        for _ in range(24):
            x=rand.randrange(W);y=rand.randrange(max(0,y0-60),H)
            d.line((x,y,x+rand.randrange(1,22),y-rand.randrange(0,8)),
                   fill=(166,148,167,rand.randrange(10,32)),width=1)
def stars(b,seed,count=150):
    r=random.Random(seed);d=ImageDraw.Draw(b,'RGBA')
    for _ in range(count):
        x=r.randrange(W);y=r.randrange(int(H*.58))
        rad=r.choices([1,2,3],[.82,.15,.03])[0]
        d.ellipse((x-rad,y-rad,x+rad,y+rad),fill=(218,225,255,r.randrange(36,180)))
def planet(b,x,y,r,color,seed,ring=False):
    glow(b,x,y,r*2,color,90)
    d=ImageDraw.Draw(b,'RGBA')
    d.ellipse((x-r,y-r,x+r,y+r),fill=(*color,255))
    rd=random.Random(seed)
    for j in range(65):
        ox=rd.uniform(-.92,.92)*r;oy=rd.uniform(-.92,.92)*r
        if ox*ox+oy*oy>r*r*.86:continue
        rad=rd.randint(2,20)
        d.ellipse((x+ox-rad,y+oy-rad,x+ox+rad,y+oy+rad),
                  fill=(16,15,44,rd.randint(12,44)))
    d.arc((x-r,y-r,x+r,y+r),195,332,fill=(250,220,230,180),width=3)
    if ring:d.ellipse((x-r*1.6,y-r*.35,x+r*1.6,y+r*.35),
                      outline=(176,213,245,110),width=3)
def crystal_scene(b,seed):
    rand=random.Random(seed);d=ImageDraw.Draw(b,'RGBA')
    mountains(b,seed,(17,23,48))
    for side in [-1,1]:
        for i in range(13):
            x=side*rand.randrange(80,300)+(0 if side==-1 else W)
            h=rand.randint(130,470);w=rand.randint(18,65)
            d.polygon([(x-w,H),(x-w*.55,H-h*.28),(x,H-h),
                       (x+w*.6,H-h*.34),(x+w,H)],fill=(12,19,45,230))
            d.line([(x,H-h),(x+w*.6,H-h*.34),(x+w,H)],
                   fill=(53,169,226,110),width=2)
    for i in range(34):
        x=rand.randrange(50,W-50);y=rand.randrange(int(H*.45),H);r=rand.randrange(13,48)
        glow(b,x,y,r,(61,130,255),50)
        d=ImageDraw.Draw(b,'RGBA')
        d.polygon([(x-r*.4,y+r),(x-r*.35,y-r*.3),(x,y-r*1.7),
                   (x+r*.35,y-r*.2),(x+r*.4,y+r)],fill=(38,87,173,177))
        d.line([(x,y-r*1.7),(x-r*.35,y-r*.3)],fill=(166,231,255,140),width=2)
    d=ImageDraw.Draw(b,'RGBA')
    d.arc((-90,-300,W+90,700),0,180,fill=(10,12,33,225),width=115)
    stars(b,seed,30)
def forest_scene(b,seed):
    rand=random.Random(seed);d=ImageDraw.Draw(b,'RGBA')
    planet(b,W*.77,H*.2,80,(116,237,204),seed)
    mountains(b,seed,(11,45,44))
    for layer in range(3):
        for j in range(10):
            x=rand.randrange(-50,W+50);w=rand.randrange(18,60)
            color=(6+layer*5,30+layer*8,28+layer*7,225)
            d.polygon([(x-w,H),(x-w*.5,H*.33),(x-w*.1,H*.08),
                       (x+w*.24,H*.10),(x+w*.6,H*.35),(x+w,H)],fill=color)
            d.line([(x,H*.4),(x+rand.randrange(-80,85),H*.27)],
                   fill=(27,79,72,155),width=w//4)
            for h in range(3):
                by=rand.randrange(200,850);bx=x+rand.randrange(-70,70)
                glow(b,bx,by,rand.randrange(8,24),(20,240,193),35)
    d=ImageDraw.Draw(b,'RGBA')
    for i in range(220):
        x=rand.randrange(W);y=rand.randrange(H)
        d.ellipse((x,y,x+2,y+2),fill=(40,244,196,rand.randrange(40,170)))
def city_scene(b,seed):
    rand=random.Random(seed)
    planet(b,W*.72,H*.18,156,(103,92,180),seed,ring=True)
    mountains(b,seed,(8,23,54))
    d=ImageDraw.Draw(b,'RGBA')
    for i in range(40):
        x=i*19+rand.randrange(-3,4)
        height=rand.randrange(80,410);y=int(H*.81)-height
        d.polygon([(x-8,H*.81),(x-4,y+40),(x,y),(x+8,y+50),
                   (x+15,H*.81)],fill=(13,29,68,240))
        for k in range(4):
            d.line((x+k*2,y+50,x+k*2,int(H*.81)),
                   fill=(52,171,243,rand.randrange(45,115)),width=1)
    d.arc((-210,int(H*.31),W+200,int(H*1.30)),195,345,
          fill=(56,94,161,240),width=34)
    d.arc((-180,int(H*.32),W+170,int(H*1.29)),195,345,
          fill=(98,188,255,146),width=3)
    for k in range(5):
        x=rand.randrange(W);y=rand.randrange(180,750)
        d.ellipse((x-28,y-4,x+28,y+4),fill=(118,171,233,100))
        d.ellipse((x-9,y-7,x+9,y+4),fill=(17,21,45,200))
    stars(b,seed,190)
def moons_scene(b,seed):
    rand=random.Random(seed)
    planet(b,W*.73,H*.17,115,(215,104,113),seed)
    planet(b,W*.24,H*.28,48,(160,173,215),seed+2)
    mountains(b,seed,(75,33,45))
    d=ImageDraw.Draw(b,'RGBA')
    for i in range(4):
        x=int((i+.25)*W/4);height=rand.randrange(165,320);base=int(H*.78)
        d.polygon([(x-85,base),(x-62,base-height*.5),
                   (x-30,base-height*.5),(x-23,base-height),
                   (x+23,base-height),(x+30,base-height*.5),
                   (x+62,base-height*.5),(x+85,base)],fill=(30,18,36,238))
        d.line([(x,base-height),(x,base)],fill=(237,151,111,94),width=4)
    for i in range(9):
        x=rand.randrange(W);h=rand.randrange(50,220)
        d.polygon([(x-12,H*.87),(x-6,H*.87-h*.75),
                   (x,H*.87-h),(x+6,H*.87-h*.75),
                   (x+12,H*.87)],fill=(32,19,37,212))
    stars(b,seed,75)
def make(name,colors):
    top,bottom,ground,seed=colors
    b=bg(top,bottom,seed)
    if name.endswith('cavern'):crystal_scene(b,seed)
    elif name.endswith('forest'):forest_scene(b,seed)
    elif name.endswith('city'):city_scene(b,seed)
    else:moons_scene(b,seed)
    # Vignette; leave the centre clear for the 3D performer.
    v=Image.new('L',(W,H),0);px=v.load()
    for y in range(H):
        for x in range(W):
            nx=(x-W*.5)/(W*.5);ny=(y-H*.48)/(H*.58)
            px[x,y]=min(170,int((nx*nx+ny*ny)*64))
    overlay=Image.new('RGBA',(W,H),(3,3,12,0));overlay.putalpha(v)
    b.alpha_composite(overlay)
    OUT.mkdir(parents=True,exist_ok=True)
    target=OUT/(name+'.jpg')
    b.convert('RGB').save(target,quality=87,subsampling=0,optimize=True)
    print(name,target.stat().st_size,W,H)
if __name__=='__main__':
    for name,colors in SCENES.items():make(name,colors)
