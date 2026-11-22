from PIL import Image, ImageDraw, ImageFilter
from pathlib import Path
import random, math
random.seed(41)
out=Path(__file__).resolve().parents[1]/"app/src/main/res/drawable-nodpi"
out.mkdir(parents=True,exist_ok=True)
W,H=360,640
def scene(name):
    colors={"scene_bridge":(13,21,43),"scene_space":(10,8,30),"scene_annunaki":(52,29,34)}
    b=colors[name];im=Image.new("RGB",(W,H));px=im.load()
    for y in range(H):
        for x in range(W):
            f=y/H;glow=max(0,1-math.hypot((x-170)/290,(y-240)/460))
            wave=math.sin(x*.013+f*11)*math.sin(y*.012+x*.019)*5
            px[x,y]=tuple(max(0,min(255,int(v+glow*38+f*12+wave))) for v in b)
    d=ImageDraw.Draw(im,"RGBA")
    if name=="scene_bridge":
        d.rounded_rectangle((22,32,338,602),25,fill=(7,18,32,130),outline=(100,200,242,140),width=6)
        d.ellipse((59,39,306,284),fill=(55,78,151,100),outline=(158,172,250,170),width=3)
        for row in (340,415,490):
            for l,r in ((10,100),(260,350)):
                d.rounded_rectangle((l,row,r,row+34),8,fill=(23,60,84,180),outline=(77,197,238,120))
    elif name=="scene_annunaki":
        d.ellipse((160,50,365,248),fill=(175,103,71,105),outline=(227,160,104,140),width=3)
        for i in range(7):
            x=24+i*50;yy=410+i%3*26
            d.polygon([(x-28,600),(x,yy-190),(x+28,600)],fill=(41,27,42,245))
            d.line((x,yy-190,x,599),fill=(227,165,69,85),width=2)
    else:
        d.ellipse((76,60,390,371),fill=(82,73,164,92),outline=(166,151,225,95),width=4)
    for i in range(200):
        x=random.randrange(W);y=random.randrange(H);r=random.choice((1,1,1,2))
        d.ellipse((x-r,y-r,x+r,y+r),fill=(200,206,247,random.randrange(10,105)))
    im.save(out/(name+".png"),optimize=True)
def alien(name,skin,iris):
    im=Image.new("RGB",(W,H),(13,14,29));d=ImageDraw.Draw(im,"RGBA")
    for i in range(230):
        x=random.randrange(W);y=random.randrange(H);d.point((x,y),fill=(104,153,204,random.randrange(8,70)))
    d.ellipse((-90,449,450,1025),fill=skin)
    d.rounded_rectangle((128,339,236,590),46,fill=skin)
    d.ellipse((59,83,304,466),fill=skin,outline=(197,167,151,130),width=5)
    d.polygon(((65,207),(8,250),(77,305)),fill=skin)
    d.polygon(((294,207),(355,250),(289,305)),fill=skin)
    for x in (84,197):
        d.ellipse((x,235,x+84,300),fill=(23,18,34,255))
        d.ellipse((x+17,242,x+69,288),fill=iris)
        d.ellipse((x+34,242,x+57,282),fill=(12,12,26,255))
        d.ellipse((x+37,247,x+44,256),fill=(230,245,254,255))
    d.line((163,323,158,361,200,367),fill=(54,35,40,155),width=6)
    d.arc((127,384,238,439),0,180,fill=(22,13,30,230),width=8)
    for i in range(1600):
        x=random.randrange(73,295);y=random.randrange(89,453)
        if ((x-183)/120)**2+((y-272)/194)**2<1:
            d.point((x,y),fill=(237,209,201,random.randrange(7,66)))
    d.polygon(((30,605),(89,485),(145,511),(182,600),(218,511),(276,485),(341,605)),fill=(22,38,57,255),outline=(123,147,193,160),width=4)
    im.filter(ImageFilter.GaussianBlur(.25)).save(out/(name+".png"),optimize=True)
for n in ("scene_bridge","scene_space","scene_annunaki"):scene(n)
for a,b,c in [
    ("alien_alpha",(112,101,145),(119,139,222)),("alien_sage",(157,142,120),(108,165,183)),
    ("alien_guard",(92,111,146),(99,174,222)),("alien_shadow",(92,80,126),(167,111,217)),
    ("alien_annunaki",(154,127,97),(173,145,87))]:alien(a,b,c)
im=Image.new("RGBA",(240,110));pix=im.load()
for y in range(110):
    for x in range(240):
        z=((x-120)/140)**2+((y-55)/44)**2
        pix[x,y]=(223,229,239,max(0,int((1-z)*62)))
im.save(out/"cloud_volume.png",optimize=True)
