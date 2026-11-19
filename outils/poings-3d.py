"""Armes de poing en 3D (Pugiliste de Babel) : un modèle fait de cubes autour du poing, tenu comme le jeu tient un objet.

Repère du modèle (unités de modèle, 1 pixel du dessin = 1/8 d'unité) avec un réglage « en main » neutre : le point de
prise du jeu est en (8, 8, 8), sur la face avant du bout du bras. Le bras du joueur y occupe x 6 à 10, y 4 à 8 (y = 8 :
face avant du bras), z 8 à 20 (z = 8 : bout du poing, l'avant-bras monte vers z = 14). Le même modèle va dans les deux
mains : il est symétrique en x.

Le style suit celui des armes de vorace-item : 5 tons par matière, couleurs du jeu ternes, aplats avec un filet clair
et un filet sombre, quelques taches d'usure, pas de contour noir.

Usage : python3 outils/poings-3d.py [dossier d'aperçu]"""
import json
import math
import os
import random
import sys
from PIL import Image, ImageDraw

ICI = os.path.dirname(os.path.abspath(__file__))
RESSOURCES = os.path.join(ICI, "..", "src", "main", "resources", "exemples", "ressources")
MODELES = os.path.join(RESSOURCES, "modeles", "exemple", "poing")
TEXTURES = os.path.join(RESSOURCES, "textures", "exemple", "poing")

FER = [(68, 68, 72), (108, 110, 116), (150, 152, 158), (194, 196, 202), (236, 238, 242)]
CUIR = [(38, 18, 14), (64, 30, 22), (92, 44, 30), (122, 62, 40), (150, 84, 56)]
PEAU = (198, 150, 118)

ATLAS = 256          # côté de la texture ; 8 pixels par unité de modèle
PX = 8


def boite(de, a, palette, ton, nom=""):
    return {"de": de, "a": a, "palette": palette, "ton": ton, "nom": nom}


def gantelets():
    """Gantelets de fer : jointures, deux lames sur la main, sangle de cuir, canon d'avant-bras évasé et rivé."""
    e = []
    # jointures : quatre bosses sur le bout du poing, alignées d'avant en arrière
    for y0, y1 in ((3.75, 4.75), (4.875, 5.875), (6.125, 7.125), (7.25, 8.25)):
        e.append(boite([6.0, y0, 7.25], [10.0, y1, 8.25], FER, 3, "jointure"))
    # lames de la main, la seconde un peu plus large (elles se recouvrent comme des écailles)
    e.append(boite([5.625, 3.625, 8.0], [10.375, 8.375, 9.5], FER, 2, "lame"))
    e.append(boite([5.5, 3.5, 9.375], [10.5, 8.5, 10.75], FER, 2, "lame"))
    # arête sur le dos de la main
    e.append(boite([7.5, 8.375, 8.25], [8.5, 8.75, 10.625], FER, 3, "arete"))
    # sangle de cuir au poignet
    e.append(boite([5.75, 3.75, 10.625], [10.25, 8.25, 11.75], CUIR, 2, "sangle"))
    # canon d'avant-bras en deux anneaux qui s'évasent, et son rebord
    e.append(boite([5.375, 3.375, 11.625], [10.625, 8.625, 13.5], FER, 2, "canon"))
    e.append(boite([5.125, 3.125, 13.375], [10.875, 8.875, 15.25], FER, 2, "canon"))
    e.append(boite([4.875, 2.875, 15.125], [11.125, 9.125, 15.875], FER, 3, "rebord"))
    # rivets : deux devant, deux derrière, un de chaque côté (symétriques)
    for x0 in (6.375, 9.125):
        e.append(boite([x0, 8.875, 14.0], [x0 + 0.5, 9.125, 14.5], FER, 4, "rivet"))
        e.append(boite([x0, 2.875, 14.0], [x0 + 0.5, 3.125, 14.5], FER, 4, "rivet"))
    e.append(boite([10.875, 5.75, 14.0], [11.125, 6.25, 14.5], FER, 4, "rivet"))
    e.append(boite([4.875, 5.75, 14.0], [5.125, 6.25, 14.5], FER, 4, "rivet"))
    return e


# faces : nom Minecraft → (axe fixe, côté, axes u et v du dessin)
FACES = {
    "north": (2, 0), "south": (2, 1), "west": (0, 0), "east": (0, 1), "down": (1, 0), "up": (1, 1),
}


def dims_face(b, face):
    axe, _ = FACES[face]
    l = [b["a"][i] - b["de"][i] for i in range(3)]
    autres = [i for i in range(3) if i != axe]
    return l[autres[0]], l[autres[1]]


def peindre(img, x0, y0, w, h, palette, ton, hasard):
    """Aplat d'un ton, filet clair en haut, filet sombre en bas, quelques taches verticales d'usure."""
    d = ImageDraw.Draw(img)
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=palette[ton] + (255,))
    if h >= 3:
        d.line([x0, y0, x0 + w - 1, y0], fill=palette[min(4, ton + 1)] + (255,))
        d.line([x0, y0 + h - 1, x0 + w - 1, y0 + h - 1], fill=palette[max(0, ton - 1)] + (255,))
    if w >= 6 and h >= 8:
        for _ in range(w * h // 220):
            tx, ty, n = hasard.randrange(w), hasard.randrange(1, h - 1), hasard.randint(2, 4)
            c = palette[min(4, ton + 1)] if hasard.random() < 0.5 else palette[max(0, ton - 1)]
            d.line([x0 + tx, y0 + ty, x0 + tx, min(y0 + h - 2, y0 + ty + n - 1)], fill=c + (255,))


def construire(nom, elements, affichage):
    hasard = random.Random(nom)
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    # toutes les faces, rangées des plus hautes aux plus basses, puis posées en lignes
    a_poser = []
    for n, b in enumerate(elements):
        for f in FACES:
            fw, fh = dims_face(b, f)
            a_poser.append((max(1, round(fh * PX)), max(1, round(fw * PX)), n, f))
    a_poser.sort(key=lambda t: (-t[0], -t[1]))
    places = {}
    x, y, haut_ligne = 0, 0, 0
    for h, w, n, f in a_poser:
        if x + w > ATLAS:
            x, y, haut_ligne = 0, y + haut_ligne, 0
        if y + h > ATLAS:
            raise SystemExit(nom + " : texture trop petite")
        b = elements[n]
        peindre(img, x, y, w, h, b["palette"], b["ton"], hasard)
        k = 16 / ATLAS
        places[(n, f)] = [x * k, y * k, (x + w) * k, (y + h) * k]
        x += w
        haut_ligne = max(haut_ligne, h)
    sortie = []
    for n, b in enumerate(elements):
        faces = {f: {"uv": places[(n, f)], "texture": "#0"} for f in FACES}
        sortie.append({"from": b["de"], "to": b["a"], "faces": faces, "name": b["nom"]})
    modele = {
        "texture_size": [ATLAS, ATLAS],
        "textures": {"0": "exemple/poing/" + nom + "_3d", "particle": "exemple/poing/" + nom + "_3d"},
        "elements": sortie,
        "display": affichage,
    }
    return modele, img


# Réglages « en main » : neutres à la troisième personne (le modèle est déjà autour du poing) ; à la première
# personne, l'avant-bras part du bas de l'écran vers l'avant.
AFFICHAGE = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [18, -6, 0], "translation": [0, 1, -2], "scale": [1, 1, 1]},
    "firstperson_lefthand": {"rotation": [18, 6, 0], "translation": [0, 1, -2], "scale": [1, 1, 1]},
}


# ------------------------------------------------------------------ aperçu calculé (vue isométrique)

def rot(v, ax, ay):
    x, y, z = v
    c, s = math.cos(ay), math.sin(ay)
    x, z = x * c + z * s, -x * s + z * c
    c, s = math.cos(ax), math.sin(ax)
    y, z = y * c - z * s, y * s + z * c
    return x, y, z


OMBRE = {"up": 1.0, "down": 0.55, "north": 0.8, "south": 0.8, "west": 0.65, "east": 0.65}


def quads(b, tex, uv=None):
    """Un quadrilatère par texel de chaque face : (coins 3D, couleur, face)."""
    out = []
    for f, (axe, cote) in FACES.items():
        autres = [i for i in range(3) if i != axe]
        u, v = autres
        fixe = b["a"][axe] if cote else b["de"][axe]
        if tex is None:
            nu = max(1, round((b["a"][u] - b["de"][u]) * 2))
            nv = max(1, round((b["a"][v] - b["de"][v]) * 2))
        else:
            r = b["faces"][f]["uv"]
            k = ATLAS / 16
            x0, y0, x1, y1 = [round(t * k) for t in r]
            nu, nv = max(1, x1 - x0), max(1, y1 - y0)
        for i in range(nu):
            for j in range(nv):
                a0 = b["de"][u] + (b["a"][u] - b["de"][u]) * i / nu
                a1 = b["de"][u] + (b["a"][u] - b["de"][u]) * (i + 1) / nu
                c0 = b["a"][v] - (b["a"][v] - b["de"][v]) * j / nv
                c1 = b["a"][v] - (b["a"][v] - b["de"][v]) * (j + 1) / nv
                pts = []
                for (p, q) in ((a0, c0), (a1, c0), (a1, c1), (a0, c1)):
                    w = [0, 0, 0]
                    w[axe], w[u], w[v] = fixe, p, q
                    pts.append(w)
                col = PEAU if tex is None else tex.getpixel((x0 + i, y0 + j))[:3]
                out.append((pts, col, f))
    return out


def vue(modele, tex, ax, ay, taille=420, bras=True):
    elems = [dict(de=e["from"], a=e["to"], faces=e["faces"]) for e in modele["elements"]]
    tous = []
    for b in elems:
        tous += quads(b, tex)
    if bras:
        tous += quads(dict(de=[6, 4, 8.01], a=[10, 8, 22]), None)
    img = Image.new("RGB", (taille, taille), (44, 46, 54))
    d = ImageDraw.Draw(img)
    centre = (8, 6, 12)
    ech = taille / 20
    prets = []
    for pts, col, f in tous:
        r = [rot((p[0] - centre[0], p[1] - centre[1], p[2] - centre[2]), ax, ay) for p in pts]
        # normale vue : on ne garde que les faces tournées vers la caméra (z vers l'écran négatif)
        n = [(r[1][k] - r[0][k], r[3][k] - r[0][k]) for k in range(3)]
        nz = n[0][0] * n[1][1] - n[0][1] * n[1][0]
        if (nz > 0) != (f in ("south", "east", "up")) and False:
            pass
        prof = sum(p[2] for p in r) / 4
        # face tournée vers l'arrière : on ne la dessine pas
        normale = [0, 0, 0]
        axe, cote = FACES[f]
        normale[axe] = 1 if cote else -1
        if rot(normale, ax, ay)[2] > 0:
            continue
        prets.append((prof, [(taille / 2 + p[0] * ech, taille / 2 - p[1] * ech) for p in r],
                      tuple(int(c * OMBRE[f]) for c in col)))
    prets.sort(key=lambda t: -t[0])
    for _, poly, col in prets:
        d.polygon(poly, fill=col)
    return img


def apercu(modele, tex, chemin):
    vues = [vue(modele, tex, math.radians(25), math.radians(35)),
            vue(modele, tex, math.radians(-30), math.radians(-40)),
            vue(modele, tex, math.radians(35), math.radians(150)),
            vue(modele, tex, math.radians(15), math.radians(35), bras=False)]
    img = Image.new("RGB", (420 * len(vues), 420), (44, 46, 54))
    for i, v in enumerate(vues):
        img.paste(v, (420 * i, 0))
    img.save(chemin)


def main():
    os.makedirs(MODELES, exist_ok=True)
    os.makedirs(TEXTURES, exist_ok=True)
    modele, tex = construire("gantelets", gantelets(), AFFICHAGE)
    with open(os.path.join(MODELES, "gantelets.json"), "w", encoding="utf-8") as f:
        json.dump(modele, f, indent=1)
    tex.save(os.path.join(TEXTURES, "gantelets_3d.png"))
    print(len(modele["elements"]), "cubes")
    if len(sys.argv) > 1:
        os.makedirs(sys.argv[1], exist_ok=True)
        apercu(modele, tex, os.path.join(sys.argv[1], "gantelets_3d.png"))


if __name__ == "__main__":
    main()
