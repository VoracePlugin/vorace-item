#!/usr/bin/env python3
"""Dessine les pièces d'exemple de l'épée modulaire (src/main/resources/exemples/ressources).

Trois ensembles, un par matériau : deux lames, deux gardes, une poignée, un pommeau.
  cuivre : lame en feuille ou cimeterre, cuivre qui verdit, manche de bois
  fer    : épée d'armes (lame droite ou large), garde de fer, cuir brun
  acier  : épée longue d'acier sombre, garde et pommeau d'or, cuir noir
Toutes les pièces partagent la même grille : n'importe quelle lame va sur n'importe quelle garde.

Style : celui des objets de Minecraft. Peu de couleurs (5 tons par matière, reprises des lingots et du bâton du jeu),
des aplats en deux bandes avec quelques taches d'usure, pas de contour noir, pas de reflets en biais, pas de grosses
gemmes. Des lames étroites, des gardes fines, un manche simple.

Technique : chaque pièce est un dessin de 128 x 128 pixels (1 pixel = 1/8 d'unité de modèle), découpé en petits
cubes. L'épaisseur change selon la zone (tranchant très fin, plat de lame fin, manche et garde plus épais) : c'est
ce relief par couches qui donne le volume. L'arme tient dans 16 unités, debout, centrée en x = 8 et z = 8, et elle
est agrandie en main par les réglages « display » de objets/exemples.yml (modulaire.affichage).

Grille commune (y en pixels depuis le bas) : pommeau 12 à 18, poignée 18 à 42, garde 40 à 50, lame à partir de 46.

Usage : python3 outils/exemples-pieces.py [aperçu.png]      (demande Pillow : pip install pillow)
"""
import json
import os
import random
import sys

from PIL import Image

RACINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "exemples")
SORTIE = os.path.join(RACINE, "ressources")
T = 128              # pixels de côté de chaque dessin
CX = 64              # colonne du milieu de l'épée
PIED_LAME = 46       # première ligne de la lame

# palettes de 5 tons, du plus sombre au plus clair (couleurs proches de celles du jeu)
CUIVRE = [(109, 52, 33), (156, 69, 41), (193, 90, 54), (231, 124, 86), (252, 163, 130)]
OXYDE = [(47, 110, 96), (61, 138, 116), (79, 171, 144), (79, 171, 144), (110, 196, 168)]
FER = [(68, 68, 72), (108, 110, 116), (150, 152, 158), (194, 196, 202), (236, 238, 242)]
ACIER = [(38, 42, 54), (62, 70, 88), (92, 104, 126), (132, 146, 168), (186, 200, 218)]
OR = [(117, 82, 21), (185, 133, 26), (233, 177, 21), (250, 214, 74), (253, 245, 150)]
BOIS = [(40, 30, 11), (73, 54, 21), (104, 78, 30), (137, 103, 39), (160, 126, 58)]
CUIR = [(38, 18, 14), (64, 30, 22), (92, 44, 30), (122, 62, 40), (150, 84, 56)]
CUIR_NOIR = [(16, 18, 26), (28, 32, 46), (44, 50, 70), (62, 70, 96), (84, 94, 124)]


class Dessin:
    """Une pièce : pour chaque pixel (x, y vers le haut), une couleur et une épaisseur en seizièmes d'unité."""

    def __init__(self):
        self.couleur = {}
        self.epaisseur = {}

    def poser(self, x, y, couleur, epaisseur):
        if 0 <= x < T and 0 <= y < T:
            self.couleur[(x, y)] = couleur
            self.epaisseur[(x, y)] = epaisseur

    def image(self):
        img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
        for (x, y), c in self.couleur.items():
            img.putpixel((x, T - 1 - y), c + (255,))
        return img


def ton(palette, t):
    return palette[max(0, min(4, int(t)))]


def taches(hasard, x0, x1, y0, y1, nombre):
    """Taches d'usure : petits traits verticaux de 2 à 5 pixels, un ton plus clair ou plus sombre."""
    sortie = {}
    for _ in range(nombre):
        x, y, d = hasard.randint(x0, x1), hasard.randint(y0, y1), hasard.choice((-1, -1, 1))
        for j in range(hasard.randint(2, 5)):
            sortie[(x, y + j)] = d
    return sortie


# ------------------------------------------------------------------ lames

def lame(profil, longueur, palette, hasard, fauchon=False, arete=False, gouttiere=0.0, oxyde=None):
    """Lame peinte ligne par ligne. profil(i, longueur) donne (gauche, droite) : la lame va de CX - gauche à CX + droite.

    Deux bandes : la moitié gauche claire, la moitié droite plus sombre. arete : un filet clair au milieu ;
    gouttiere : part de la longueur creusée d'une rainure sombre ; fauchon : dos épais à gauche, tranchant à droite.
    """
    d = Dessin()
    usure = taches(hasard, CX - 6, CX + 6, PIED_LAME, PIED_LAME + longueur, longueur // 4)
    vert = taches(hasard, CX - 6, CX + 6, PIED_LAME, PIED_LAME + longueur, longueur // 5) if oxyde else {}
    for i in range(longueur):
        y = PIED_LAME + i
        g, dr = profil(i, longueur)
        xg, xd = CX - g, CX + dr
        if xg > xd:
            continue
        large = xd - xg + 1
        for x in range(xg, xd + 1):
            o, a, b = x - CX, x - xg, xd - x     # écart au milieu, distance au bord gauche, au bord droit
            if fauchon:
                t = 1 if a <= 1 else 4 if b == 0 else 3 if b <= 2 else 2
                ep = 4 if a <= 1 else 1 if b == 0 else 2 if b <= 2 else 3
            else:
                t, ep = (3 if o < 0 else 2), 3
                if arete and o == 0:
                    t, ep = 4, 4
                if gouttiere and o == 0 and 3 <= i < longueur * gouttiere:
                    t, ep = 1, 2
                if a == 0:
                    t, ep = 4, 1
                elif b == 0:
                    t, ep = 1, 1
                elif large >= 7 and (a == 1 or b == 1):
                    ep = 2
            if large <= 2:
                t, ep = (4 if a == 0 else 2), 1
            elif 0 < a and 0 < b:
                t += usure.get((x, y), 0)
            couleur = ton(palette, t)
            if (x, y) in vert and (i < longueur // 3 or b <= 1):
                couleur = ton(oxyde, t)
            d.poser(x, y, couleur, ep)
    return d


def pointe(w, i, n, fin):
    """Demi-largeur w, qui se resserre sur les « fin » dernières lignes."""
    return w if i < n - fin else round(w * (n - 1 - i) / fin)


def profil_feuille(i, n):
    u = i / (n - 1)
    if u < 0.15:
        w = 2
    elif u < 0.5:
        v = (u - 0.15) / 0.35
        w = 2 + 2 * v * v * (3 - 2 * v)
    elif u < 0.7:
        w = 4
    else:
        w = 4 * (1 - ((u - 0.7) / 0.3) ** 1.6)
    w = round(w)
    return w, w


def profil_fauchon(i, n):
    """Cimeterre : dos à gauche, la lame se courbe vers la gauche, s'élargit, puis le tranchant remonte vers la pointe."""
    u = i / (n - 1)
    gauche = 3 + 7 * u * u
    large = 6 + 2 * min(1, u / 0.7) if u < 0.8 else 8 * ((1 - u) / 0.2) ** 0.7
    return round(gauche), round(large - gauche)


def profil_droite(i, n):
    w = pointe(3, i, n, 10)
    return w, w


def profil_large(i, n):
    w = 4 if i < n * 0.6 else pointe(3, i, n, 9)
    return w, w


def profil_longue(i, n):
    w = 4 if i < 4 else pointe(3, i, n, 18)
    return w, w


def profil_espadon(i, n):
    w = pointe(4 if i < n * 0.45 else 5, i, n, 11)
    return w, w


# ------------------------------------------------------------------ gardes, poignées, pommeaux

def rectangle(dx0, dx1, y0, y1):
    return {(CX + dx, y) for dx in range(dx0, dx1 + 1) for y in range(y0, y1 + 1)}


def bras(hauteur, portee, epais=3):
    """Deux bras symétriques de garde : hauteur(a) = ligne du bas du bras à l'écart a du milieu."""
    return {(CX + s * a, hauteur(a) + j) for a in range(portee + 1) for s in (-1, 1)
            for j in range(epais if a < portee - 1 else epais - 1)}


def aplat(dessin, masque, palette, epaisseur, hasard, centre=None):
    """Aplat à la Minecraft : dessus clair, dessous sombre, le reste uni avec un peu d'usure."""
    for (x, y) in masque:
        t = 2
        if (x, y + 1) not in masque:
            t = 3
        elif (x, y - 1) not in masque:
            t = 1
        elif hasard.random() < 0.10:
            t += hasard.choice((-1, 1))
        if (x, y + 1) not in masque and (x - 1, y) not in masque:
            t = 4
        dessin.poser(x, y, ton(palette, t), epaisseur + (2 if centre and (x, y) in centre else 0))


def garde(masques, palette, hasard, centre=None):
    d = Dessin()
    union = set()
    for m in masques:
        union |= m
    aplat(d, union | (centre or set()), palette, 6, hasard, centre)
    return d


def poignee(palette, pas, bague=None):
    """Manche de 5 pixels de large, avec un trait sombre de ligature tous les « pas » pixels."""
    d = Dessin()
    for y in range(18, 43):
        for k, dx in enumerate(range(-2, 3)):
            t = [1, 3, 2, 2, 1][k] - (1 if (y - 18) % pas == pas - 1 else 0)
            d.poser(CX + dx, y, ton(palette, t), 6 if 0 < k < 4 else 5)
    if bague:
        for y in (18, 19, 41, 42):
            for k, dx in enumerate(range(-3, 4)):
                d.poser(CX + dx, y, ton(bague, [1, 4, 3, 2, 2, 1, 0][k] + (1 if y in (19, 42) else 0)), 7)
    return d


def pommeau(lignes, palette, hasard):
    """lignes : y → demi-largeur."""
    d = Dessin()
    aplat(d, {(CX + dx, y) for y, w in lignes.items() for dx in range(-w, w + 1)}, palette, 8, hasard)
    return d


# ------------------------------------------------------------------ du dessin au modèle

def rectangles(dessin):
    """Regroupe les pixels voisins de même épaisseur en rectangles (un cube par rectangle)."""
    pris, sortie = set(), []
    for y in range(T):
        for x in range(T):
            if (x, y) in pris or (x, y) not in dessin.epaisseur:
                continue
            ep = dessin.epaisseur[(x, y)]
            x2 = x
            while dessin.epaisseur.get((x2 + 1, y)) == ep and (x2 + 1, y) not in pris:
                x2 += 1
            y2 = y
            while all(dessin.epaisseur.get((k, y2 + 1)) == ep and (k, y2 + 1) not in pris for k in range(x, x2 + 1)):
                y2 += 1
            for j in range(y, y2 + 1):
                for k in range(x, x2 + 1):
                    pris.add((k, j))
            sortie.append((x, y, x2, y2, ep))
    return sortie


def elements(dessin):
    """Un cube par rectangle : faces avant et arrière peintes par le dessin, tranches peintes par le pixel du bord."""
    ep_de = dessin.epaisseur
    sortie = []
    for (x, y, x2, y2, ep) in rectangles(dessin):
        u1, u2 = x / 8.0, (x2 + 1) / 8.0
        v1, v2 = (T - 1 - y2) / 8.0, (T - y) / 8.0          # l'image a le haut en premier
        px = 1 / 8.0
        faces = {"north": {"uv": [u2, v1, u1, v2], "texture": "#0"}, "south": {"uv": [u1, v1, u2, v2], "texture": "#0"}}
        if any(ep_de.get((x2 + 1, j), 0) < ep for j in range(y, y2 + 1)):
            faces["east"] = {"uv": [u2 - px, v1, u2, v2], "texture": "#0"}
        if any(ep_de.get((x - 1, j), 0) < ep for j in range(y, y2 + 1)):
            faces["west"] = {"uv": [u1, v1, u1 + px, v2], "texture": "#0"}
        if any(ep_de.get((k, y2 + 1), 0) < ep for k in range(x, x2 + 1)):
            faces["up"] = {"uv": [u1, v1, u2, v1 + px], "texture": "#0"}
        if any(ep_de.get((k, y - 1), 0) < ep for k in range(x, x2 + 1)):
            faces["down"] = {"uv": [u1, v2 - px, u2, v2], "texture": "#0"}
        demi = ep / 32.0
        sortie.append({"from": [x / 8.0, y / 8.0, 8 - demi], "to": [(x2 + 1) / 8.0, (y2 + 1) / 8.0, 8 + demi], "faces": faces})
    return sortie


def ensembles():
    """Les trois ensembles : matériau → pièce → dessin."""
    h = random.Random("vorace-item")
    bloc = rectangle(-3, 3, 42, 46)
    return {
        "cuivre": {
            "lame_droite": lame(profil_feuille, 66, CUIVRE, h, gouttiere=0.5, oxyde=OXYDE),
            "lame_large": lame(profil_fauchon, 64, CUIVRE, h, fauchon=True, oxyde=OXYDE),
            "garde_droite": garde([rectangle(-8, 8, 43, 45)], CUIVRE, h, centre=bloc),
            "garde_croissant": garde([bras(lambda a: 43 + round(3 * (a / 9) ** 2), 9)], CUIVRE, h, centre=bloc),
            "poignee_cuir": poignee(BOIS, 5),
            "pommeau_rond": pommeau({17: 2, 16: 3, 15: 3, 14: 2}, CUIVRE, h),
        },
        "fer": {
            "lame_droite": lame(profil_droite, 72, FER, h, gouttiere=0.45),
            "lame_large": lame(profil_large, 64, FER, h, gouttiere=0.55),
            "garde_droite": garde([rectangle(-10, 10, 43, 45), rectangle(-12, -10, 44, 47), rectangle(10, 12, 44, 47)], FER, h, centre=bloc),
            "garde_croissant": garde([bras(lambda a: 45 - round(4 * a / 11), 11)], FER, h, centre=bloc),
            "poignee_cuir": poignee(CUIR, 3),
            "pommeau_rond": pommeau({17: 2, 16: 3, 15: 3, 14: 2, 13: 1}, FER, h),
        },
        "acier": {
            "lame_droite": lame(profil_longue, 80, ACIER, h, arete=True),
            "lame_large": lame(profil_espadon, 80, ACIER, h, gouttiere=0.4),
            "garde_droite": garde([rectangle(-9, 9, 43, 45), rectangle(-11, 11, 44, 44)], OR, h,
                                  centre={(CX + dx, y) for dx in range(-3, 4) for y in range(41, 48) if abs(dx) + abs(y - 44) <= 3}),
            "garde_croissant": garde([bras(lambda a: 43 + round(5 * a / 10), 10)], OR, h, centre=bloc),
            "poignee_cuir": poignee(CUIR_NOIR, 2, bague=OR),
            "pommeau_rond": pommeau({y: 3 - abs(y - 15) for y in range(12, 19)}, OR, h),
        },
    }


def apercu(tout, chemin):
    """Vue de face de quelques épées assemblées (pour juger le dessin sans lancer le jeu)."""
    choix = [("cuivre", "lame_droite", "cuivre", "garde_droite"), ("cuivre", "lame_large", "cuivre", "garde_croissant"),
             ("fer", "lame_droite", "fer", "garde_droite"), ("fer", "lame_large", "fer", "garde_croissant"),
             ("acier", "lame_droite", "acier", "garde_droite"), ("acier", "lame_large", "acier", "garde_croissant"),
             ("acier", "lame_droite", "fer", "garde_croissant"), ("fer", "lame_large", "cuivre", "garde_droite")]
    k, pas = 7, 40
    img = Image.new("RGBA", (pas * k * len(choix), T * k), (44, 46, 54, 255))
    for n, (ml, l, mg, g) in enumerate(choix):
        pixels = {}
        for d in (tout[ml][l], tout[ml]["poignee_cuir"], tout[ml]["pommeau_rond"], tout[mg][g]):
            for p, c in d.couleur.items():
                if p not in pixels or d.epaisseur[p] >= pixels[p][1]:
                    pixels[p] = (c, d.epaisseur[p])
        for (x, y), (c, _) in pixels.items():
            px, py = (x - CX + pas // 2 + n * pas) * k, (T - 1 - y) * k
            img.paste(c + (255,), (px, py, px + k, py + k))
    img.save(chemin)


def main():
    tout = ensembles()
    fichiers, cubes = [], {}
    for materiau, pieces in tout.items():
        for nom, dessin in pieces.items():
            chemin = "exemple/%s/%s" % (materiau, nom)
            for dossier in ("modeles", "textures"):
                os.makedirs(os.path.dirname(os.path.join(SORTIE, dossier, chemin)), exist_ok=True)
            el = elements(dessin)
            cubes[chemin] = len(el)
            with open(os.path.join(SORTIE, "modeles", chemin + ".json"), "w", encoding="utf-8") as f:
                json.dump({"credit": "vorace-item (exemple)", "textures": {"0": chemin, "particle": chemin}, "elements": el},
                          f, separators=(",", ":"))
            dessin.image().save(os.path.join(SORTIE, "textures", chemin + ".png"), optimize=True)
            fichiers += ["modeles/" + chemin + ".json", "textures/" + chemin + ".png"]
    # liste des fichiers d'exemple, lue par le plugin pour les installer au premier lancement
    with open(os.path.join(RACINE, "ressources.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(sorted(fichiers)) + "\n")
    print("%d fichiers écrits dans %s" % (len(fichiers), os.path.normpath(SORTIE)))
    print("cubes par pièce : " + ", ".join("%s %d" % (c.split("/", 1)[1], n) for c, n in cubes.items()))
    if len(sys.argv) > 1:
        apercu(tout, sys.argv[1])


if __name__ == "__main__":
    main()
