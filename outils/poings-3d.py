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
LIN = [(98, 84, 64), (140, 124, 98), (184, 168, 138), (212, 200, 174), (234, 226, 206)]
LAITON = [(84, 62, 24), (128, 96, 40), (170, 132, 58), (204, 168, 86), (230, 204, 128)]
ROUGE = [(70, 22, 20), (110, 34, 28), (148, 50, 38), (180, 74, 54), (206, 108, 82)]
PAPIER = [(150, 140, 112), (190, 180, 150), (218, 210, 184), (236, 230, 208), (248, 244, 228)]
PEAU = (198, 150, 118)

ATLAS = 512          # côté de la texture ; 8 pixels par unité de modèle
PX = 8


def boite(de, a, palette, ton, nom="", usure=False, filets=True, glyphes=False):
    return {"de": list(de), "a": list(a), "palette": palette, "ton": ton, "nom": nom, "usure": usure, "filets": filets,
            "glyphes": glyphes}


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


M = 1.125   # marge du corps : juste au-dessus de la manche d'un plastron (1)


def jointures(e, palette, ton, x1=11.125, z0=6.625):
    """Quatre bosses sur le bout du poing, alignées d'avant en arrière."""
    for y0, y1 in ((3.0, 4.25), (4.375, 5.625), (6.375, 7.625), (7.75, 9.0)):
        e.append(boite([6.0, y0, z0], [x1, y1, 7.0], palette, ton, "jointure"))


def anneaux(e, zs, m, palette, ton, nom="anneau", ep=0.5, **k):
    for z in zs:
        e.append(autour(m, z, z + ep, palette, ton, nom, **k))


def gantelets():
    """Gantelets de fer : main en plaques articulées, jointures, doigts repliés, pouce, sangle, canon en deux lames."""
    e = []
    e.append(autour(M, 6.875, 10.375, FER, 1, "main"))
    # dos de la main (dehors, +x) : deux lames articulées et une arête
    e.append(boite([11.125, 3.625, 7.25], [11.375, 8.375, 8.875], FER, 3, "dos"))
    e.append(boite([11.125, 3.875, 8.75], [11.375, 8.125, 10.25], FER, 2, "dos"))
    e.append(boite([11.375, 5.75, 7.5], [11.5, 6.25, 10.0], FER, 4, "arete"))
    jointures(e, FER, 3)
    # doigts repliés côté paume (dedans, -x)
    for y0, y1 in ((3.0, 4.25), (4.375, 5.625), (6.375, 7.625), (7.75, 9.0)):
        e.append(boite([4.625, y0, 7.125], [4.875, y1, 8.875], FER, 2, "doigt"))
    # pouce en deux plaques, sur le devant (+y)
    e.append(boite([4.875, 9.125, 8.375], [7.25, 9.375, 9.875], FER, 2, "pouce"))
    e.append(boite([4.875, 9.125, 7.375], [6.5, 9.375, 8.25], FER, 3, "pouce"))
    # poignet : sangle de cuir et boucle
    e.append(autour(1.0625, 10.375, 11.25, CUIR, 2, "sangle"))
    e.append(boite([7.375, 9.0625, 10.5], [8.625, 9.25, 11.125], FER, 4, "boucle"))
    # canon d'avant-bras : deux lames qui s'évasent, bord creux
    e.append(autour(1.1875, 11.25, 12.875, FER, 2, "canon"))
    e.append(autour(1.25, 12.75, 14.375, FER, 3, "canon"))
    e += anneau(1.375, 0.375, 14.25, 14.875, FER, 2, "rebord")
    # arête sur le dehors du canon, rivets
    e.append(boite([11.25, 5.5, 11.5], [11.5, 6.5, 14.125], FER, 4, "arete"))
    for x0 in (5.5, 10.0):
        e.append(boite([x0, 9.25, 13.25], [x0 + 0.5, 9.375, 13.75], FER, 4, "rivet", filets=False))
        e.append(boite([x0, 2.625, 13.25], [x0 + 0.5, 2.75, 13.75], FER, 4, "rivet", filets=False))
        e.append(boite([x0, 9.1875, 11.75], [x0 + 0.5, 9.3125, 12.25], FER, 4, "rivet", filets=False))
        e.append(boite([x0, 2.6875, 11.75], [x0 + 0.5, 2.8125, 12.25], FER, 4, "rivet", filets=False))
    return e


def bandes_de_combat():
    """Bandes de lin : main et poignet serrés dans des bandes, jointures rembourrées, pouce, bout de bande qui pend."""
    e = []
    e.append(autour(1.0625, 6.875, 13.75, LIN, 2, "bandes"))
    anneaux(e, (7.25, 8.375, 9.5, 10.625, 11.75, 12.875), 1.1875, LIN, 3, "tour", ep=0.625)
    # jointures rembourrées (plusieurs tours de bande)
    e.append(boite([6.0, 3.25, 6.625], [11.25, 8.75, 6.875], LIN, 3, "rembourrage"))
    e.append(boite([6.5, 3.625, 6.375], [11.0, 8.375, 6.625], LIN, 4, "rembourrage"))
    # pouce bandé
    e.append(boite([4.875, 9.0625, 7.5], [7.25, 9.4375, 9.75], LIN, 3, "pouce"))
    # bande croisée sur le dos de la main
    e.append(boite([11.1875, 4.5, 7.75], [11.375, 7.5, 10.5], LIN, 3, "croise"))
    # bout de bande qui pend du poignet (derrière, -y)
    e.append(boite([7.5, 2.625, 10.0], [8.25, 2.75, 13.5], LIN, 1, "bout"))
    return e


def cestes():
    """Cestes de laiton : main et avant-bras serrés de lanières de cuir, quatre anneaux de laiton sur les jointures."""
    e = []
    e.append(autour(1.0625, 6.875, 14.5, CUIR, 2, "lanieres"))
    anneaux(e, (8.0, 9.5, 11.0, 12.25, 13.5), 1.1875, CUIR, 1, "tour", ep=0.5)
    # barre et anneaux de laiton sur les jointures
    e.append(boite([10.75, 2.875, 6.5], [11.625, 9.125, 8.125], LAITON, 2, "barre"))
    for y0, y1 in ((3.0, 4.375), (4.5, 5.875), (6.125, 7.5), (7.625, 9.0)):
        e.append(boite([6.25, y0, 6.0], [11.5, y1, 6.875], LAITON, 3, "anneau"))
        e.append(boite([6.75, y0 + 0.375, 5.875], [11.0, y1 - 0.375, 6.0], LAITON, 1, "creux", filets=False))
    # clous de laiton sur les lanières du poignet, devant et derrière
    for z in (11.0, 12.25, 13.5):
        e.append(boite([7.625, 9.1875, z + 0.0625], [8.375, 9.375, z + 0.4375], LAITON, 4, "clou", filets=False))
        e.append(boite([7.625, 2.625, z + 0.0625], [8.375, 2.8125, z + 0.4375], LAITON, 4, "clou", filets=False))
    # pouce dans une lanière
    e.append(boite([4.875, 9.0625, 7.5], [7.25, 9.375, 9.75], CUIR, 3, "pouce"))
    return e


def brassards_de_garde():
    """Brassards de cuir ferré : gant de cuir, long brassard jusqu'au coude, trois bandes de fer, plaque de garde, lacets."""
    e = []
    e.append(autour(1.0625, 6.875, 10.375, CUIR, 2, "gant"))
    jointures(e, CUIR, 3, x1=11.0)
    e.append(boite([4.875, 9.0625, 7.5], [7.25, 9.3125, 9.75], CUIR, 3, "pouce"))
    # brassard de cuir jusqu'au coude, bord creux
    e.append(autour(1.1875, 10.25, 18.5, CUIR, 2, "brassard"))
    e += anneau(1.3125, 0.375, 18.375, 18.875, CUIR, 3, "bord")
    # bandes de fer
    anneaux(e, (10.5, 14.125, 17.625), 1.3125, FER, 3, "bande", ep=0.625)
    # plaque de garde (dehors, +x) avec son arête
    e.append(boite([11.1875, 4.25, 11.25], [11.4375, 7.75, 17.5], FER, 2, "garde"))
    e.append(boite([11.4375, 5.625, 11.5], [11.5625, 6.375, 17.25], FER, 4, "garde"))
    # lacets sur le devant (+y)
    for z in (11.5, 12.375, 15.0, 15.875, 16.75):
        e.append(boite([7.25, 9.1875, z], [8.75, 9.3125, z + 0.375], CUIR, 0, "lacet", filets=False))
    return e


def bandes_rituelles():
    """Bandes du souffle : bandes de lin couvertes d'inscriptions rouges, cordon rouge, talisman de papier."""
    e = []
    e.append(autour(1.0625, 6.875, 14.0, PAPIER, 2, "bandes", glyphes=True))
    anneaux(e, (7.5, 9.0, 12.5), 1.1875, PAPIER, 3, "tour", ep=0.625)
    e.append(boite([6.0, 3.25, 6.625], [11.25, 8.75, 6.875], PAPIER, 3, "rembourrage", glyphes=True))
    e.append(boite([4.875, 9.0625, 7.5], [7.25, 9.4375, 9.75], PAPIER, 3, "pouce"))
    # cordon rouge noué au poignet
    anneaux(e, (10.5, 11.5), 1.25, ROUGE, 2, "cordon", ep=0.25)
    e.append(boite([7.5, 9.25, 10.375], [8.5, 9.625, 11.875], ROUGE, 3, "noeud"))
    e.append(boite([7.75, 9.375, 11.875], [8.25, 9.5, 13.25], ROUGE, 2, "brin"))
    # talisman de papier sur le dehors (+x), retenu par le cordon
    e.append(boite([11.3125, 4.625, 10.625], [11.4375, 7.375, 13.875], PAPIER, 4, "talisman", glyphes=True))
    return e


ARMES = {"gantelets": gantelets, "bandes_de_combat": bandes_de_combat, "cestes": cestes,
         "brassards_de_garde": brassards_de_garde, "bandes_rituelles": bandes_rituelles}


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
    if b["glyphes"] and w >= 8 and h >= 8:
        # inscriptions : petits signes à l'encre rouge (trait, barre, point), espacés
        encre = ROUGE[1] + (255,)
        for gx in range(2, w - 4, 7):
            gy = 2 + (gx * 5 + h) % max(1, h - 6)
            d.line([x0 + gx + 1, y0 + gy, x0 + gx + 1, y0 + gy + 3], fill=encre)
            d.line([x0 + gx, y0 + gy + 1, x0 + gx + 2, y0 + gy + 1], fill=encre)
            d.point((x0 + gx + 3, y0 + gy + 3), fill=encre)
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


def apercu(modeles, tex, chemin):
    """Une ligne par arme : dehors (dos de la main), dedans (paume, pouce), jointures, sur une manche de plastron."""
    debout = np.array([[1, 0, 0], [0, 0, 1], [0, -1, 0]], float)
    centre = np.array([8.0, 6.0, 12.5])
    vues = [matrice(20, -35) @ debout, matrice(20, 55) @ debout, matrice(55, -30) @ debout, matrice(20, -35) @ debout]
    t = 300
    img = Image.new("RGB", (t * len(vues), t * len(modeles)), (44, 46, 54))
    for r, nom in enumerate(modeles):
        for c, M in enumerate(vues):
            el = modeles[nom]["elements"] + (manche_plastron() if c == 3 else bras_peau())
            img.paste(rendre(el, tex[nom], M, centre, taille=t, champ=24), (t * c, t * r))
    img.save(chemin)


def main():
    os.makedirs(MODELES, exist_ok=True)
    os.makedirs(TEXTURES, exist_ok=True)
    modeles, textures = {}, {}
    for nom, f in ARMES.items():
        modele, tex = construire(nom, f(), AFFICHAGE)
        with open(os.path.join(MODELES, nom + ".json"), "w", encoding="utf-8") as fo:
            json.dump(modele, fo, indent=1)
        with open(os.path.join(MODELES, nom + "_gauche.json"), "w", encoding="utf-8") as fo:
            json.dump(retourner(modele), fo, indent=1)
        tex.save(os.path.join(TEXTURES, nom + "_3d.png"))
        modeles[nom], textures[nom] = modele, tex
        print(nom, len(modele["elements"]), "cubes")
    if len(sys.argv) > 1:
        os.makedirs(sys.argv[1], exist_ok=True)
        apercu(modeles, textures, os.path.join(sys.argv[1], "armes_de_poing_3d.png"))


if __name__ == "__main__":
    main()
