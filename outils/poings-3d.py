"""Armes de poing en 3D (Pugiliste de Babel) : un gantelet fait de cubes autour du poing et de l'avant-bras, tenu
comme le jeu tient un objet.

Repère du modèle de la main droite (unités de modèle, 1 pixel de texture = 1/8 d'unité) avec un réglage « en main »
neutre : le point de prise du jeu est en (8, 8, 8), sur la face avant du bout du bras. Le bras du joueur y occupe
x 6 à 10 (x = 10 : dehors, dos de la main ; x = 6 : dedans, paume), y 4 à 8 (y = 8 : devant, côté du pouce), z 8 à 20
(z = 8 : bout du poing, le coude vers z = 14).

La manche d'un plastron du jeu recouvre tout le bras avec 1 unité de marge (x 5 à 11, y 3 à 9, z 7 à 21) : tout ce qui
est en dessous est caché. Le gantelet la dépasse partout d'au moins 1/16 d'unité, sans plus : il reste fin.

La main gauche est le même gantelet retourné (x → 16 - x), dans un second modèle « <nom>_gauche » que le pack choisit
pour la main gauche.

Style des armes de vorace-item : 5 tons par matière, couleurs du jeu ternes, aplats avec un filet clair en haut et un
filet sombre en bas, très peu d'usure, pas de contour noir.

Usage : python3 outils/poings-3d.py [dossier d'aperçu]"""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw

ICI = os.path.dirname(os.path.abspath(__file__))
RESSOURCES = os.path.join(ICI, "..", "src", "main", "resources", "exemples", "ressources")
MODELES = os.path.join(RESSOURCES, "modeles", "exemple", "poing")
TEXTURES = os.path.join(RESSOURCES, "textures", "exemple", "poing")

FER = [(62, 63, 68), (100, 102, 108), (142, 144, 150), (186, 188, 194), (226, 228, 232)]
CUIR = [(40, 22, 16), (66, 36, 24), (94, 54, 34), (124, 76, 48), (152, 100, 66)]
SOMBRE = [(24, 22, 24), (36, 34, 36), (50, 48, 50), (64, 62, 64), (80, 78, 80)]
PEAU = (198, 150, 118)

ATLAS = 512          # côté de la texture ; 8 pixels par unité de modèle
PX = 8


def boite(de, a, palette, ton, nom="", usure=False, filets=True):
    return {"de": list(de), "a": list(a), "palette": palette, "ton": ton, "nom": nom, "usure": usure, "filets": filets}


def autour(m, z0, z1, palette, ton, nom, **k):
    """Boîte qui entoure le bras avec une marge m, de z0 à z1."""
    return boite([6 - m, 4 - m, z0], [10 + m, 8 + m, z1], palette, ton, nom, **k)


def anneau(m, ep, z0, z1, palette, ton, nom):
    """Anneau creux (4 murs d'épaisseur ep) : on voit le bras entrer dedans."""
    x0, x1, y0, y1 = 6 - m, 10 + m, 4 - m, 8 + m
    return [boite([x0, y0, z0], [x1, y0 + ep, z1], palette, ton, nom),
            boite([x0, y1 - ep, z0], [x1, y1, z1], palette, ton, nom),
            boite([x0, y0 + ep, z0], [x0 + ep, y1 - ep, z1], palette, ton, nom),
            boite([x1 - ep, y0 + ep, z0], [x1, y1 - ep, z1], palette, ton, nom)]


def gantelets():
    """Gantelets de fer, main droite, fins : juste au-dessus de la manche d'un plastron (marge 1 1/8), reliefs de
    1/8 à 1/4 d'unité. Main en plaques, jointures, doigts repliés, pouce, sangle, canon court au bord creux."""
    e = []
    # main : bloc de base, plaque du dos de la main (dehors, +x)
    e.append(autour(1.125, 6.875, 10.375, FER, 2, "main"))
    e.append(boite([11.125, 3.75, 7.375], [11.375, 8.25, 10.125], FER, 3, "dos"))
    # jointures : quatre bosses fines sur le bout du poing
    for y0, y1 in ((3.0, 4.25), (4.375, 5.625), (6.375, 7.625), (7.75, 9.0)):
        e.append(boite([6.0, y0, 6.625], [11.125, y1, 7.0], FER, 3, "jointure"))
    # doigts repliés côté paume (dedans, -x) : quatre plaques séparées par des creux
    for y0, y1 in ((3.0, 4.25), (4.375, 5.625), (6.375, 7.625), (7.75, 9.0)):
        e.append(boite([4.625, y0, 7.125], [4.875, y1, 8.875], FER, 2, "doigt"))
    # pouce : sur le devant (+y), côté paume
    e.append(boite([4.875, 9.125, 7.5], [7.25, 9.375, 9.75], FER, 3, "pouce"))
    # poignet : sangle de cuir et boucle
    e.append(autour(1.0625, 10.375, 11.25, CUIR, 2, "sangle"))
    e.append(boite([7.375, 9.0625, 10.5], [8.625, 9.25, 11.125], FER, 4, "boucle"))
    # canon d'avant-bras, court, et son bord creux
    e.append(autour(1.1875, 11.25, 14.375, FER, 2, "canon"))
    e += anneau(1.3125, 0.375, 14.25, 14.875, FER, 3, "rebord")
    # arête sur le dehors du canon, rivets devant et derrière
    e.append(boite([11.1875, 5.5, 11.75], [11.4375, 6.5, 14.0], FER, 3, "arete"))
    for x0 in (5.5, 10.0):
        e.append(boite([x0, 9.1875, 12.5], [x0 + 0.5, 9.3125, 13.0], FER, 4, "rivet", filets=False))
        e.append(boite([x0, 2.6875, 12.5], [x0 + 0.5, 2.8125, 13.0], FER, 4, "rivet", filets=False))
    return e


FACES = ("north", "south", "west", "east", "down", "up")
AXE = {"north": 2, "south": 2, "west": 0, "east": 0, "down": 1, "up": 1}


def dims_face(b, f):
    l = [b["a"][i] - b["de"][i] for i in range(3)]
    if AXE[f] == 1:
        return l[0], l[2]
    if AXE[f] == 0:
        return l[2], l[1]
    return l[0], l[1]


def peindre(img, x0, y0, w, h, b, hasard):
    """Aplat d'un ton, filet clair en haut, filet sombre en bas, une ou deux marques d'usure sur les grandes faces."""
    pal, ton = b["palette"], b["ton"]
    d = ImageDraw.Draw(img)
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=pal[ton] + (255,))
    if b["filets"] and h >= 4:
        d.line([x0, y0, x0 + w - 1, y0], fill=pal[min(4, ton + 1)] + (255,))
        d.line([x0, y0 + h - 1, x0 + w - 1, y0 + h - 1], fill=pal[max(0, ton - 1)] + (255,))
    if b["usure"] and w >= 12 and h >= 12:
        for _ in range(2):
            tx, ty = hasard.randrange(2, w - 2), hasard.randrange(2, h - 5)
            c = pal[max(0, ton - 1)] if hasard.random() < 0.6 else pal[min(4, ton + 1)]
            d.line([x0 + tx, y0 + ty, x0 + tx, y0 + ty + hasard.randint(1, 2)], fill=c + (255,))


def construire(nom, elements, affichage):
    hasard = random.Random(nom)
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
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
        peindre(img, x, y, w, h, elements[n], hasard)
        k = 16 / ATLAS
        places[(n, f)] = [x * k, y * k, (x + w) * k, (y + h) * k]
        x += w
        haut_ligne = max(haut_ligne, h)
    sortie = []
    for n, b in enumerate(elements):
        sortie.append({"name": b["nom"], "from": b["de"], "to": b["a"],
                       "faces": {f: {"uv": places[(n, f)], "texture": "#0"} for f in FACES}})
    tex = "exemple/poing/" + nom + "_3d"
    modele = {"texture_size": [ATLAS, ATLAS], "textures": {"0": tex, "particle": tex}, "elements": sortie,
              "display": affichage}
    return modele, img


def retourner(modele):
    """Même gantelet pour la main gauche : x → 16 - x ; les faces est et ouest s'échangent, les autres ont leur
    texture retournée pour ne pas être vues à l'envers."""
    g = json.loads(json.dumps(modele))
    for e in g["elements"]:
        x0, x1 = e["from"][0], e["to"][0]
        e["from"][0], e["to"][0] = 16 - x1, 16 - x0
        f = e["faces"]
        f["east"], f["west"] = f["west"], f["east"]
        for k in ("north", "south", "up", "down"):
            u = f[k]["uv"]
            f[k]["uv"] = [u[2], u[1], u[0], u[3]]
    return g


# Réglages « en main » : neutres à la troisième personne (le modèle est déjà autour du poing) ; à la première
# personne, l'avant-bras part du bas de l'écran vers l'avant.
AFFICHAGE = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [18, -6, 0], "translation": [0, 1, -2], "scale": [0.85, 0.85, 0.85]},
    "firstperson_lefthand": {"rotation": [18, 6, 0], "translation": [0, 1, -2], "scale": [0.85, 0.85, 0.85]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 0, -4], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, -4], "scale": [0.6, 0.6, 0.6]},
}


# ------------------------------------------------------------------ aperçu calculé (z-buffer, vues isométriques)

OMBRE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "west": 0.62, "east": 0.62}
NORMALE = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "down": (0, -1, 0),
           "up": (0, 1, 0)}


def coins(f, a, b):
    """Coins d'une face dans l'ordre de la texture : haut-gauche, haut-droit, bas-gauche (convention Minecraft)."""
    x0, y0, z0 = a
    x1, y1, z1 = b
    return {"north": [(x1, y1, z0), (x0, y1, z0), (x1, y0, z0)], "south": [(x0, y1, z1), (x1, y1, z1), (x0, y0, z1)],
            "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z0)], "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z1)],
            "up": [(x0, y1, z0), (x1, y1, z0), (x0, y1, z1)], "down": [(x0, y0, z1), (x1, y0, z1), (x0, y0, z0)]}[f]


def matrice(ax, ay):
    ax, ay = math.radians(ax), math.radians(ay)
    ry = np.array([[math.cos(ay), 0, math.sin(ay)], [0, 1, 0], [-math.sin(ay), 0, math.cos(ay)]])
    rx = np.array([[1, 0, 0], [0, math.cos(ax), -math.sin(ax)], [0, math.sin(ax), math.cos(ax)]])
    return rx @ ry


def rendre(elements, tex, M, centre, taille=400, champ=26):
    img = np.zeros((taille, taille, 3))
    img[:] = (44, 46, 54)
    zb = np.full((taille, taille), 1e9)
    ech = taille / champ
    tx = np.array(tex.convert("RGBA"))
    for e in elements:
        for f, fa in e["faces"].items():
            if (M @ np.array(NORMALE[f], float))[2] > 1e-6:
                continue
            p0, p1, p3 = [M @ (np.array(c, float) - centre) for c in coins(f, e["from"], e["to"])]
            nu = max(2, int(np.linalg.norm(p1 - p0) * ech * 1.7))
            nv = max(2, int(np.linalg.norm(p3 - p0) * ech * 1.7))
            S, T = np.meshgrid((np.arange(nu) + 0.5) / nu, (np.arange(nv) + 0.5) / nv, indexing="ij")
            Q = p0 + (p1 - p0) * S[..., None] + (p3 - p0) * T[..., None]
            px = (taille / 2 + Q[..., 0] * ech).astype(int)
            py = (taille / 2 - Q[..., 1] * ech).astype(int)
            if "couleur" in fa:
                C = np.broadcast_to(np.array(fa["couleur"], float), S.shape + (3,))
                A = np.full(S.shape, 255)
            else:
                u0, v0, u1, v1 = fa["uv"]
                U = ((u0 + (u1 - u0) * S) / 16 * tx.shape[1]).astype(int).clip(0, tx.shape[1] - 1)
                V = ((v0 + (v1 - v0) * T) / 16 * tx.shape[0]).astype(int).clip(0, tx.shape[0] - 1)
                C = tx[V, U, :3].astype(float)
                A = tx[V, U, 3]
            for i, j in zip(*np.nonzero(A > 10)):
                x, y, z = px[i, j], py[i, j], Q[i, j, 2]
                if 0 <= x < taille and 0 <= y < taille and z < zb[y, x]:
                    zb[y, x] = z
                    img[y, x] = C[i, j] * OMBRE[f]
    return Image.fromarray(img.astype(np.uint8))


def bras_peau():
    return [{"from": [6, 4, 8.01], "to": [10, 8, 23], "faces": {f: {"couleur": PEAU} for f in FACES}}]


def manche_plastron():
    """Manche d'un plastron du jeu (marge 1), en bleu, pour vérifier que le gantelet la dépasse."""
    return [{"from": [5, 3, 7], "to": [11, 9, 23], "faces": {f: {"couleur": (110, 140, 185)} for f in FACES}}]


def apercu(modele, gauche, tex, chemin):
    # bras pendant : le z du modèle (vers le coude) monte à l'écran, le devant (+y) vient vers nous
    debout = np.array([[1, 0, 0], [0, 0, 1], [0, -1, 0]], float)
    centre = np.array([8.0, 6.0, 12.0])
    vues = [
        (modele["elements"] + bras_peau(), matrice(20, -35) @ debout),     # devant, dehors (dos de la main)
        (modele["elements"] + bras_peau(), matrice(20, 55) @ debout),      # devant, dedans (paume, pouce)
        (modele["elements"] + bras_peau(), matrice(55, -30) @ debout),     # par-dessous : les jointures
        (modele["elements"] + manche_plastron(), matrice(20, -35) @ debout),
        (gauche["elements"] + bras_peau(), matrice(20, 35) @ debout),      # main gauche, dehors
    ]
    img = Image.new("RGB", (400 * len(vues), 400), (44, 46, 54))
    for i, (el, M) in enumerate(vues):
        img.paste(rendre(el, tex, M, centre), (400 * i, 0))
    img.save(chemin)


def main():
    os.makedirs(MODELES, exist_ok=True)
    os.makedirs(TEXTURES, exist_ok=True)
    modele, tex = construire("gantelets", gantelets(), AFFICHAGE)
    gauche = retourner(modele)
    with open(os.path.join(MODELES, "gantelets.json"), "w", encoding="utf-8") as f:
        json.dump(modele, f, indent=1)
    with open(os.path.join(MODELES, "gantelets_gauche.json"), "w", encoding="utf-8") as f:
        json.dump(gauche, f, indent=1)
    tex.save(os.path.join(TEXTURES, "gantelets_3d.png"))
    print(len(modele["elements"]), "cubes")
    if len(sys.argv) > 1:
        os.makedirs(sys.argv[1], exist_ok=True)
        apercu(modele, gauche, tex, os.path.join(sys.argv[1], "gantelets_3d.png"))


if __name__ == "__main__":
    main()
