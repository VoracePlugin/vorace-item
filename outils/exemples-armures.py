#!/usr/bin/env python3
"""Dessine les armures d'exemple (src/main/resources/exemples/ressources) : trois ensembles, un par matériau.

Comme les packs d'armures des serveurs : le casque est un vrai modèle 3D (porté sur la tête, sans dessin d'armure),
le reste est le dessin d'armure du jeu, en 16 pixels par bloc, dessiné pixel par pixel comme les armures du jeu.

  cuivre : l'hoplite. Cuirasse musclée, épaulières rondes, ptéryges de cuir sur tunique rouge, cnémides ;
           casque corinthien à joues et nasal, haute crête de crin rouge.
  fer    : le chevalier de la Tour. Haubert de mailles, cotte d'armes rouge à la tour de Babel, épaulières à lames ;
           grand heaume à fentes, croix de laiton, panache rouge.
  acier  : le gardien. Plates d'acier noirci bordées d'or, drap bleu nuit ; heaume à bec et ailes d'acier et d'or.

Pour chaque matériau :
  equipement/exemple/armure_<m>/humanoid.png            plastron, bras, bottes (64 x 32)
  equipement/exemple/armure_<m>/humanoid_leggings.png   jambières (64 x 32)
  modeles/exemple/armure/casque_<m>.json                casque en 3D (porté, en main, dans l'inventaire)
  textures/exemple/armure/casque_<m>.png                sa texture (16 pixels par bloc)
  textures/exemple/armure/<m>_<pièce>.png               icônes (16 x 16) du plastron, des jambières, des bottes

Usage : python3 outils/exemples-armures.py [aperçu.png]
"""
import json
import os
import sys
import zlib

from PIL import Image

RACINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "exemples")
SORTIE = os.path.join(RACINE, "ressources")


def rgb(x):
    return tuple(int(x[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def h(*v):
    return zlib.crc32(repr(v).encode()) & 0xFFFFFFFF


# Palettes : chiffres 0-4 le métal (sombre → clair), a b c le métal des ornements, r s t l'étoffe, k l m le cuir,
# w x y la maille ou la patine, p q le blanc (lin, emblème).
PALETTES = {
    "cuivre": {"0": "#5a2414", "1": "#86391f", "2": "#b0532b", "3": "#d6763c", "4": "#f2a462",
               "a": "#6e5020", "b": "#b8923e", "c": "#efd68a",
               "r": "#5e161a", "s": "#8c2222", "t": "#b8382e",
               "k": "#3a2216", "l": "#5a3620", "m": "#7e5232",
               "w": "#2e6a5a", "x": "#4f9a80", "y": "#9a8a6a", "p": "#c9bb98", "q": "#e8dcc0"},
    "fer": {"0": "#2f3136", "1": "#4e525a", "2": "#737880", "3": "#9da2aa", "4": "#cdd1d7",
            "a": "#6e5020", "b": "#b8923e", "c": "#efd68a",
            "r": "#5a1418", "s": "#8a2024", "t": "#b6362f",
            "k": "#3a2216", "l": "#5a3620", "m": "#7e5232",
            "w": "#23252a", "x": "#3f424a", "y": "#686c74", "p": "#c9bfa8", "q": "#f0e9d6"},
    "acier": {"0": "#15171d", "1": "#232733", "2": "#343c4d", "3": "#4f5b73", "4": "#7f8ead",
              "a": "#7a4c12", "b": "#cc9226", "c": "#f8d870",
              "r": "#14173a", "s": "#252d62", "t": "#3c4894",
              "k": "#2a1a12", "l": "#45291a", "m": "#63402a",
              "w": "#14161c", "x": "#2c313d", "y": "#4c5466", "p": "#c9bfa8", "q": "#f0e9d6"},
}


def miroir(g):
    return [l[::-1] for l in g]


def bande(*faces):
    """Colle des faces de même hauteur côte à côte (la bande qui fait le tour d'un membre)."""
    return ["".join(f[y] for f in faces) for y in range(len(faces[0]))]


def poser(img, pal, grille, x0, y0):
    for y, ligne in enumerate(grille):
        for x, c in enumerate(ligne):
            if c not in ". ":
                img.putpixel((x0 + x, y0 + y), rgb(pal[c]))


# ------------------------------------------------------------------ le dessin d'armure
# Bandes : torse = droite 4 | devant 8 | gauche 4 | dos 8 (x 16-39, y 20-31) ; bras = droite | devant | gauche | dos
# (x 40-55) ; jambe = idem (x 0-15). Dessus et dessous : torse (20,16)/(28,16) 8x4, bras (44,16)/(48,16), jambe
# (4,16)/(8,16) 4x4.
VIDE4 = ["...."] * 12

CUIVRE = dict(
    torse_devant=[
        "34444443",
        "24432442",
        "23421342",
        "12211221",
        "01122110",
        "12322321",
        "11211211",
        "12322321",
        "34444443",
        "mlrmlrml",
        "lkrlkrlk",
        "lk.lk.lk"],
    torse_cote=[
        "3443",
        "2332",
        "lcbl",
        "2221",
        "2221",
        "1221",
        "lcbl",
        "1111",
        "3443",
        "mlrm",
        "lkrl",
        "lk.l"],
    torse_dos=[
        "34444443",
        "23322332",
        "22311321",
        "12211221",
        "12211221",
        "11211211",
        "11211211",
        "01111110",
        "34444443",
        "mlrmlrml",
        "lkrlkrlk",
        "lk.lk.lk"],
    torse_dessus=["34444443", "32222223", "32222223", "33333333"],
    bras_devant=[
        "3443",
        "3442",
        "2332",
        "1001",
        "mlml",
        "lklk",
        "tsst",
        "srrs",
        "4443",
        "2332",
        "1221",
        "kllk"],
    bras_cote=[
        "3443",
        "4cb2",
        "2ab1",
        "1001",
        "mlml",
        "lklk",
        "tsst",
        "srrs",
        "4443",
        "2332",
        "1221",
        "kllk"],
    bras_dessus=["2332", "3443", "3442", "2221"],
    bras_dessous=["kllk", "lmml", "lmml", "kllk"],
    botte_devant=VIDE4[:5] + [
        "3443",
        "2442",
        "2432",
        "2421",
        "1321",
        "mklm",
        "3332"],
    botte_cote=VIDE4[:5] + [
        "3443",
        "2332",
        "2321",
        "2221",
        "1221",
        "mklm",
        "klkl"],
    botte_dos=VIDE4[:5] + [
        "3443",
        "2kl1",
        "2lk1",
        "2kl1",
        "1lk1",
        "mklm",
        "klkl"],
    botte_dessous=["kkkk", "klkk", "kklk", "kkkk"],
    ceinture_devant=["lllcblll", "tsstssts", "srtssrst", "32233223"],
    ceinture_cote=["llll", "tsst", "srts", "3223"],
    cuisse_devant=[
        "tsst",
        "srts",
        "3223",
        "qqqp",
        "pyyy",
        "qqqp",
        "pyyy",
        "qqqp",
        "pyyy",
        "qqqp",
        "pyyy",
        "kllk"],
    cuisse_cote=[
        "tsst",
        "srts",
        "3223",
        "qqpp",
        "pyyy",
        "qqpp",
        "pyyy",
        "qqpp",
        "pyyy",
        "qqpp",
        "pyyy",
        "kllk"],
    cuisse_dessus=["tsst", "stts", "tsst", "srrs"],
)

FER = dict(
    torse_devant=[
        "34444443",
        "ytqttpsx",
        "ytqqppsx",
        "yttqpssx",
        "yttqpssx",
        "yttqpssx",
        "ytqqppsx",
        "ytssssrx",
        "kllcbllk",
        "ytttsssx",
        "ytsttssx",
        "ys.ts.sx"],
    torse_cote=[
        "3443",
        "yxyx",
        "xwxy",
        "yxyx",
        "xwxy",
        "yxyx",
        "xwxy",
        "yxyx",
        "kllk",
        "yxyx",
        "xwxy",
        "yxyw"],
    torse_dos=[
        "34444443",
        "ytttsssx",
        "yttssssx",
        "ytttsssx",
        "yttssssx",
        "ytttsssx",
        "yttssssx",
        "ytssssrx",
        "kllllllk",
        "ytttsssx",
        "ytsttssx",
        "ys.ts.sx"],
    torse_dessus=["34444443", "yx3333xy", "xyxyxyxy", "yxyxyxyx"],
    bras_devant=[
        "3443",
        "2442",
        "1111",
        "3443",
        "2332",
        "yxyx",
        "3443",
        "1221",
        "3443",
        "lmml",
        "2332",
        "2112"],
    bras_cote=[
        "3443",
        "2432",
        "1111",
        "3443",
        "2432",
        "yxyx",
        "3443",
        "2432",
        "3443",
        "lmml",
        "2332",
        "2112"],
    bras_dessus=["3443", "4443", "4432", "3321"],
    bras_dessous=["2112", "1221", "1221", "2112"],
    botte_devant=VIDE4[:7] + [
        "3443",
        "2431",
        "2431",
        "3443",
        "2332"],
    botte_cote=VIDE4[:7] + [
        "3443",
        "2332",
        "2221",
        "3443",
        "2332"],
    botte_dos=VIDE4[:7] + [
        "3443",
        "lmml",
        "yxyx",
        "lmml",
        "kllk"],
    botte_dessous=["kkkk", "klkk", "kklk", "kkkk"],
    ceinture_devant=["kllcbllk", "yxyxyxyx", "xwxyxwxy", "yxyxyxyx"],
    ceinture_cote=["kllk", "yxyx", "xwxy", "yxyx"],
    cuisse_devant=[
        "yxyx",
        "3443",
        "2432",
        "2432",
        "1221",
        "3443",
        "2c32",
        "1221",
        "yxyx",
        "xwxy",
        "yxyx",
        "xwxy"],
    cuisse_cote=[
        "yxyx",
        "xwxy",
        "yxyx",
        "xwxy",
        "yxyx",
        "3443",
        "2332",
        "1221",
        "yxyx",
        "xwxy",
        "yxyx",
        "xwxy"],
    cuisse_dessus=["yxyx", "xyxy", "yxyx", "xyxy"],
)

ACIER = dict(
    torse_devant=[
        "34444443",
        "bccccccb",
        "23342211",
        "233cb221",
        "23342211",
        "12342110",
        "bbbbbbba",
        "34444432",
        "12222221",
        "34btsb32",
        "23bstb21",
        "12btsb21"],
    torse_cote=[
        "3443",
        "bccb",
        "2332",
        "2221",
        "2221",
        "1221",
        "bbba",
        "3443",
        "1221",
        "3443",
        "2221",
        "1110"],
    torse_dos=[
        "34444443",
        "bccccccb",
        "23342211",
        "23342211",
        "22341211",
        "12331110",
        "bbbbbbba",
        "34444432",
        "12222221",
        "34btsb32",
        "23bstb21",
        "12btsb21"],
    torse_dessus=["34444443", "34444443", "33333332", "22222221"],
    bras_devant=[
        "3443",
        "2342",
        "bccb",
        "3442",
        "1221",
        "2332",
        "3443",
        "1221",
        "3443",
        "2332",
        "2332",
        "1221"],
    bras_cote=[
        "3443",
        "3442",
        "bccb",
        "3442",
        "1221",
        "2332",
        "3cb3",
        "1221",
        "3443",
        "2332",
        "2332",
        "1221"],
    bras_dessus=["3443", "3443", "3332", "2221"],
    bras_dessous=["1221", "2332", "2332", "1221"],
    botte_devant=VIDE4[:7] + [
        "3443",
        "2431",
        "2431",
        "3443",
        "3cb2"],
    botte_cote=VIDE4[:7] + [
        "3443",
        "2332",
        "2221",
        "3443",
        "2332"],
    botte_dos=VIDE4[:7] + [
        "3443",
        "2221",
        "1221",
        "3443",
        "2221"],
    botte_dessous=["0110", "1001", "1001", "0110"],
    ceinture_devant=["34444443", "bbbbbbba", "34btsb32", "23bstb21"],
    ceinture_cote=["3443", "bbba", "3443", "2221"],
    cuisse_devant=[
        "3443",
        "2431",
        "2431",
        "2431",
        "bccb",
        "3443",
        "2cb2",
        "1ba1",
        "yxyx",
        "xwxy",
        "yxyx",
        "xwxy"],
    cuisse_cote=[
        "3443",
        "2332",
        "2221",
        "2221",
        "bccb",
        "3443",
        "2332",
        "1221",
        "yxyx",
        "xwxy",
        "yxyx",
        "xwxy"],
    cuisse_dessus=["3443", "3443", "3443", "2332"],
)

DESSINS = {"cuivre": CUIVRE, "fer": FER, "acier": ACIER}


def couche_principale(m):
    d, pal = DESSINS[m], PALETTES[m]
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    poser(img, pal, bande(d["torse_cote"], d["torse_devant"], miroir(d["torse_cote"]), d["torse_dos"]), 16, 20)
    poser(img, pal, d["torse_dessus"], 20, 16)
    bc = d["bras_cote"]
    poser(img, pal, bande(bc, d["bras_devant"], miroir(bc), d.get("bras_dos", d["bras_devant"])), 40, 20)
    poser(img, pal, d["bras_dessus"], 44, 16)
    poser(img, pal, d["bras_dessous"], 48, 16)
    poser(img, pal, bande(d["botte_cote"], d["botte_devant"], miroir(d["botte_cote"]), d["botte_dos"]), 0, 20)
    poser(img, pal, d["botte_dessous"], 8, 16)
    return img


def couche_jambieres(m):
    d, pal = DESSINS[m], PALETTES[m]
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    c = d["ceinture_cote"]
    poser(img, pal, bande(c, d["ceinture_devant"], miroir(c), d["ceinture_devant"]), 16, 28)
    cc = d["cuisse_cote"]
    poser(img, pal, bande(cc, d["cuisse_devant"], miroir(cc), d.get("cuisse_dos", cc)), 0, 20)
    poser(img, pal, d["cuisse_dessus"], 4, 16)
    return img


def icones(a, b):
    """Icônes 16 x 16 tirées des faces avant du dessin porté (comme celles des armures du jeu)."""
    def f(img, x, y, w, hh):
        return img.crop((x, y, x + w, y + hh))
    pl = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pl.alpha_composite(f(a, 20, 20, 8, 11), (4, 3))
    pl.alpha_composite(f(a, 44, 20, 4, 6), (0, 2))
    pl.alpha_composite(f(a, 44, 20, 4, 6).transpose(Image.FLIP_LEFT_RIGHT), (12, 2))
    ja = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    ja.alpha_composite(f(b, 4, 20, 4, 11), (3, 3))
    ja.alpha_composite(f(b, 4, 20, 4, 11).transpose(Image.FLIP_LEFT_RIGHT), (9, 3))
    ja.alpha_composite(f(b, 20, 28, 8, 2), (4, 1))
    bo = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    haut = 0
    while haut < 12 and f(a, 4, 20 + haut, 4, 1).getbbox() is None:
        haut += 1
    jambe = f(a, 4, 20 + haut, 4, 12 - haut)
    hb = jambe.height
    bo.alpha_composite(jambe, (2, 14 - hb))
    bo.alpha_composite(jambe.transpose(Image.FLIP_LEFT_RIGHT), (10, 14 - hb))
    pied = f(a, 4, 31, 4, 1).resize((2, 1), Image.NEAREST)
    bo.alpha_composite(pied, (0, 13))
    bo.alpha_composite(pied, (14, 13))
    return {"plastron": pl, "jambieres": ja, "bottes": bo}


# ------------------------------------------------------------------ casques en 3D (16 pixels par bloc)
def q(pal, L):
    L = max(0.0, min(0.999, L))
    return rgb(pal["01234"[int(L * 5)]])


class Modele:
    """Un modèle d'objet fait de cubes ; chaque face est peinte dans un atlas, 1 texel par unité (16 par bloc)."""

    def __init__(self, pal):
        self.pal = pal
        self.cubes = []

    def cube(self, a, b, peinture, rot=None, sans=()):
        self.cubes.append(dict(a=a, b=b, peinture=peinture, rot=rot, sans=sans))

    def construire(self, taille=64):
        atlas = Image.new("RGBA", (taille, taille), (0, 0, 0, 0))
        x = y = ligne = 0
        elements = []
        for c in self.cubes:
            (x0, y0, z0), (x1, y1, z1) = c["a"], c["b"]
            dims = {"north": (x1 - x0, y1 - y0), "south": (x1 - x0, y1 - y0), "east": (z1 - z0, y1 - y0),
                    "west": (z1 - z0, y1 - y0), "up": (x1 - x0, z1 - z0), "down": (x1 - x0, z1 - z0)}
            fs = {}
            for nom, (fw, fh) in dims.items():
                if nom in c["sans"]:
                    continue
                tw, th = max(1, round(fw)), max(1, round(fh))
                if x + tw > taille:
                    x, y, ligne = 0, y + ligne, 0
                if y + th > taille:
                    raise ValueError("atlas trop petit")
                im = c["peinture"](self.pal, nom, tw, th)
                atlas.paste(im, (x, y))
                fs[nom] = dict(uv=[x * 16 / taille, y * 16 / taille, (x + tw) * 16 / taille, (y + th) * 16 / taille], texture="#0")
                c.setdefault("images", {})[nom] = im
                x += tw
                ligne = max(ligne, th)
            el = {"from": [round(v, 3) for v in c["a"]], "to": [round(v, 3) for v in c["b"]], "faces": fs}
            if c["rot"]:
                axe, angle, origine = c["rot"]
                el["rotation"] = {"angle": angle, "axis": axe, "origin": origine}
            elements.append(el)
        return atlas, elements


def plaque(haut=0.85, bas=0.4, decor=None, pal=None):
    """Plaque de métal : rangée du haut claire, puis plus sombre vers le bas ; le dessus clair, les flancs un ton en dessous."""
    def f(p, nom, tw, th):
        pp = pal or p
        im = Image.new("RGBA", (tw, th))
        for y in range(th):
            for x in range(tw):
                if nom == "up":
                    L = haut + 0.1 - (0.2 if (x == tw - 1 or y == th - 1) and tw > 2 and th > 2 else 0)
                elif nom == "down":
                    L = bas - 0.2
                else:
                    fy = (y + 0.5) / th
                    L = haut + (bas - haut) * fy
                    if y == 0 and th > 2:
                        L = haut + 0.15
                    if y == th - 1 and th > 3:
                        L = bas - 0.15
                    if nom in ("east", "west", "south"):
                        L -= 0.12
                im.putpixel((x, y), q(pp, L))
        if decor:
            decor(pp, im, nom, tw, th)
        return im
    return f


def orn(haut=0.9, bas=0.45, decor=None):
    """Plaque d'ornement (laiton ou or) : lettres a b c de la palette."""
    def f(p, nom, tw, th):
        alt = dict(p)
        alt.update({"0": p["a"], "1": p["a"], "2": p["b"], "3": p["b"], "4": p["c"]})
        return plaque(haut, bas, decor, alt)(p, nom, tw, th)
    return f


def etoffe():
    """Crin ou étoffe rouge : mèches verticales (une valeur par colonne)."""
    def f(p, nom, tw, th):
        im = Image.new("RGBA", (tw, th))
        tons = [p["r"], p["s"], p["t"]]
        for y in range(th):
            for x in range(tw):
                k = (h(x, tw, 7) % 3)
                if nom == "up":
                    c = tons[2]
                elif nom == "down":
                    c = tons[0]
                else:
                    c = tons[max(0, k - (1 if y >= th - 1 and th > 2 else 0) - (1 if nom in ("east", "west", "south") and k == 2 else 0))]
                im.putpixel((x, y), rgb(c))
        return im
    return f


def uni(lettre):
    return lambda p, nom, tw, th: Image.new("RGBA", (tw, th), rgb(p[lettre]))


def rivets(pas=3, rang=None):
    def d(p, im, nom, tw, th):
        if nom in ("up", "down") or th < 2:
            return
        yy = th // 2 if rang is None else rang
        for x in range(1, tw - 1, pas):
            im.putpixel((x, yy), rgb(p["4"]))
    return d


def coque(m, y0, y1, z_avant=1.8, haut=0.8, bas=0.4):
    """La coque : trois boîtes emboîtées qui arrondissent les angles (octogone à gradins). L'avant reste ouvert :
    les boîtes commencent derrière le visage (z_avant), les pièces de face sont posées devant."""
    P = plaque(haut, bas)
    m.cube((0, y0, 2), (16, y1, 14), P)
    m.cube((2, y0, z_avant), (14, y1, 16), P)
    m.cube((1, y0, z_avant), (15, y1, 15), P)


def dome(m, y, gradins, clair=0.95):
    """Calotte en gradins (chaque gradin rentre)."""
    for i, (rx, rz0, rz1, ep) in enumerate(gradins):
        m.cube((8 - rx, y, rz0), (8 + rx, y + ep, rz1), plaque(clair - 0.05 * i, 0.55))
        y += ep
    return y


def casque_cuivre():
    m = Modele(PALETTES["cuivre"])
    coque(m, 1, 15)
    dome(m, 15, [(7, 1, 15, 1), (6, 2, 14, 1), (4, 4, 12, 0.8)])
    # face corinthienne : front, sourcils, joues jusqu'au menton, nasal ; fente en T
    m.cube((0, 9, 0.6), (16, 15, 2), plaque(0.85, 0.5))                               # front
    m.cube((0.5, 8.5, 0), (7.5, 10, 0.6), plaque(0.95, 0.4))                         # sourcil gauche
    m.cube((8.5, 8.5, 0), (15.5, 10, 0.6), plaque(0.95, 0.4))                        # sourcil droit
    m.cube((0, 1, 0.6), (6.5, 6.5, 2), plaque(0.8, 0.35))                            # joue
    m.cube((9.5, 1, 0.6), (16, 6.5, 2), plaque(0.8, 0.35))
    m.cube((0, 6.5, 0.6), (2.5, 9, 2), plaque(0.75, 0.4))                            # tempes (encadrent les yeux)
    m.cube((13.5, 6.5, 0.6), (16, 9, 2), plaque(0.75, 0.4))
    m.cube((7, 5, -0.2), (9, 9, 1.0), plaque(0.95, 0.5))                             # nasal
    m.cube((-0.4, 0.4, 0.3), (6.8, 1.4, 2.2), plaque(0.95, 0.35))                     # bord roulé des joues
    m.cube((9.2, 0.4, 0.3), (16.4, 1.4, 2.2), plaque(0.95, 0.35))
    m.cube((1, 0, 15), (15, 2.5, 17), plaque(0.9, 0.4))                              # couvre-nuque évasé
    m.cube((2, 12, 0.5), (3, 13, 0.6), uni("x"))                                     # vert-de-gris
    m.cube((13, 11, 0.5), (14, 12, 0.6), uni("w"))
    # porte-crête de laiton et haute crête de crin, de l'avant à la nuque
    m.cube((6.5, 17.8, 1.5), (9.5, 19, 15), orn())
    for z, ht in ((0.5, 3), (2.5, 5.5), (4.5, 6.8), (6.5, 7.3), (8.5, 7.3), (10.5, 6.8), (12.5, 6)):
        m.cube((6, 19, z), (10, 19 + ht, z + 2), etoffe())
    m.cube((6, 13, 14.5), (10, 25, 17.5), etoffe())
    m.cube((6.8, 8, 17.5), (9.2, 15, 19), etoffe())
    return m


def souffles(p, im, nom, tw, th):
    """Trous d'aération sur le côté droit du bas du heaume."""
    if nom != "north":
        return
    for (x, y) in ((tw - 4, 2), (tw - 6, 2), (tw - 4, 4), (tw - 6, 4), (tw - 5, 6)):
        if 0 <= x < tw and 0 <= y < th:
            im.putpixel((x, y), rgb(p["w"]))


def casque_fer():
    m = Modele(PALETTES["fer"])
    coque(m, 0.5, 15.5, haut=0.75, bas=0.4)
    dome(m, 15.5, [(6.5, 1.5, 14.5, 1), (4.5, 3.5, 12.5, 0.8)])
    # visage plat du heaume : haut, fentes de vision (vides), bas percé de trous d'aération
    m.cube((0.5, 9, 0.5), (15.5, 15.5, 2), plaque(0.8, 0.5, rivets(3, 1)))
    m.cube((0.5, 0.5, 0.5), (15.5, 7.5, 2), plaque(0.7, 0.35, souffles))
    m.cube((0.5, 7.5, 1.6), (15.5, 9, 2), uni("w"))
    # croix de laiton en relief
    m.cube((7, 0.5, -0.3), (9, 15.5, 0.5), orn(decor=rivets(4)))
    m.cube((0.3, 9, -0.2), (15.7, 10.2, 0.5), orn())
    # cerclages rivetés
    m.cube((-0.2, 14.2, -0.2), (16.2, 15.2, 16.2), plaque(0.9, 0.45, rivets(3)), sans=("up", "down"))
    m.cube((-0.2, 0, -0.2), (16.2, 1, 16.2), plaque(0.85, 0.35), sans=("up",))
    # panache rouge : douille de laiton, plumes qui montent puis partent en arrière
    m.cube((6.5, 17, 6), (9.5, 18.5, 9), orn())
    for (y, z0, z1, x0) in ((18.5, 5.5, 9.5, 6.2), (20.5, 6.5, 11.5, 6.4), (22.3, 8.5, 14, 6.6), (23.6, 11, 16.5, 6.9), (24.4, 14, 19, 7.2)):
        m.cube((x0, y, z0), (16 - x0, y + 2, z1), etoffe())
    return m


def bord_or(p, im, nom, tw, th):
    if nom in ("down", "up"):
        return
    for x in range(tw):
        im.putpixel((x, 0), rgb(p["c"]))


def casque_acier():
    m = Modele(PALETTES["acier"])
    coque(m, 1.5, 15, haut=0.75, bas=0.35)
    dome(m, 15, [(7, 1, 15, 1), (5.5, 2.5, 13.5, 0.8)])
    m.cube((7.3, 15.5, 0.5), (8.7, 18.3, 15.5), orn())                                # crête d'or
    m.cube((-0.2, 14.2, -0.2), (16.2, 15.1, 16.2), orn(), sans=("up", "down"))        # bandeau d'or
    # visière à bec : le front, la fente de vision, puis le bec en trois gradins
    m.cube((0.5, 9.5, 0.5), (15.5, 15, 2), plaque(0.8, 0.45))
    m.cube((0.5, 8, 1.6), (15.5, 9.5, 2), uni("w"))
    m.cube((0.2, 9.2, 0), (15.8, 9.9, 0.6), orn())
    m.cube((0.5, 1.5, 0.5), (15.5, 8, 2), plaque(0.7, 0.35))
    m.cube((2, 2, -1), (14, 7.6, 0.5), plaque(0.8, 0.4))
    m.cube((4.5, 2.6, -2.4), (11.5, 7.2, -1), plaque(0.85, 0.4))
    m.cube((6.8, 3.2, -3.6), (9.2, 6.6, -2.4), orn())
    m.cube((1.8, 7.4, -1.2), (14.2, 8, 0.5), orn(), sans=("down",))
    m.cube((-0.4, 0, -0.4), (16.4, 1.5, 16.4), plaque(0.7, 0.3, rivets(4)), sans=("up",))   # gorgerin
    # ailes : trois plumes d'acier bordées d'or de chaque côté, vers le haut et l'arrière, sur une rosace d'or
    for cote in (-1, 1):
        xa = (-0.8, 0.2) if cote < 0 else (15.8, 16.8)
        for (ang, y0, long_) in ((0, 10, 8), (-22.5, 11.5, 9.5), (-45, 13, 10.5)):
            m.cube((xa[0], y0, 8.5), (xa[1], y0 + 2.5, 8.5 + long_), plaque(0.85, 0.45, bord_or),
                   rot=("x", ang, [8, y0, 8.5]) if ang else None)
        m.cube((xa[0] - (0.4 if cote < 0 else 0), 10, 7), (xa[1] + (0.4 if cote > 0 else 0), 13.4, 10.4), orn())
    return m


CASQUES = {"cuivre": casque_cuivre, "fer": casque_fer, "acier": casque_acier}


def modele_json(m, chemin_texture):
    atlas, elements = m.construire(128)
    mod = {
        "texture_size": [128, 128],
        "textures": {"0": chemin_texture, "particle": chemin_texture},
        "elements": elements,
        "display": {
            "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "gui": {"rotation": [20, 145, 0], "translation": [0, -1.5, 0], "scale": [0.55, 0.55, 0.55]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.35, 0.35, 0.35]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, -1, 0], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_righthand": {"rotation": [10, 45, 0], "translation": [0, 1.5, 1], "scale": [0.36, 0.36, 0.36]},
            "thirdperson_lefthand": {"rotation": [10, 45, 0], "translation": [0, 1.5, 1], "scale": [0.36, 0.36, 0.36]},
            "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, 3, 0], "scale": [0.34, 0.34, 0.34]},
            "firstperson_lefthand": {"rotation": [0, 135, 0], "translation": [0, 3, 0], "scale": [0.34, 0.34, 0.34]},
        },
    }
    return mod, atlas


def main():
    entrees = []
    dt = os.path.join(SORTIE, "textures", "exemple", "armure")
    dm = os.path.join(SORTIE, "modeles", "exemple", "armure")
    os.makedirs(dt, exist_ok=True)
    os.makedirs(dm, exist_ok=True)
    for mat in DESSINS:
        d = os.path.join(SORTIE, "equipement", "exemple", "armure_" + mat)
        os.makedirs(d, exist_ok=True)
        a, b = couche_principale(mat), couche_jambieres(mat)
        a.save(os.path.join(d, "humanoid.png"), optimize=True)
        b.save(os.path.join(d, "humanoid_leggings.png"), optimize=True)
        for piece, ic in icones(a, b).items():
            ic.save(os.path.join(dt, "%s_%s.png" % (mat, piece)), optimize=True)
        m = CASQUES[mat]()
        mod, atlas = modele_json(m, "exemple/armure/casque_" + mat)
        atlas.save(os.path.join(dt, "casque_" + mat + ".png"), optimize=True)
        with open(os.path.join(dm, "casque_" + mat + ".json"), "w", encoding="utf-8") as f:
            json.dump(mod, f, ensure_ascii=False, indent=1)
        vieux = os.path.join(dt, mat + "_casque.png")
        if os.path.exists(vieux):
            os.remove(vieux)
        entrees.append((mat, a, b, m))
    tous = [os.path.relpath(os.path.join(dd, n), SORTIE).replace(os.sep, "/") for dd, _, ns in os.walk(SORTIE) for n in ns]
    with open(os.path.join(RACINE, "ressources.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(sorted(tous)) + "\n")
    print("armures :", ", ".join(DESSINS))
    if len(sys.argv) > 1:
        sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
        import apercu_armures
        apercu_armures.dessiner(entrees, sys.argv[1])


if __name__ == "__main__":
    main()
