#!/usr/bin/env python3
"""Dessine les pièces d'exemple de l'épée modulaire (src/main/resources/exemples/ressources).

Trois ensembles, un par matériau, chacun avec son style : deux lames, deux gardes, une poignée, un pommeau.
  cuivre : bronze de légionnaire (lame en feuille, fauchon, cuir clair, pommeau en anneau, vert-de-gris)
  fer    : épée de chevalier (lame à gouttière, garde dorée, cuir rouge au fil d'or, pommeau à rubis)
  acier  : lame royale d'acier bleu (arête incrustée, garde ailée d'argent, saphirs, pommeau de cristal)
Toutes les pièces partagent la même grille : n'importe quelle lame va sur n'importe quelle garde.

Technique : chaque pièce est un dessin de 128 x 128 pixels (1 pixel = 1/8 d'unité de modèle), découpé en petits
cubes. L'épaisseur change selon la zone (tranchant très fin, plat de lame fin, garde et pommeau épais) : c'est ce
relief par couches qui donne le volume, dans le style de Minecraft. L'arme tient dans 16 unités, debout, centrée en
x = 8 et z = 8, et elle est agrandie en main par les réglages « display » de objets/exemples.yml
(modulaire.affichage).

Grille commune (y en pixels depuis le bas) : pommeau 1 à 19, poignée 16 à 40, garde 34 à 54, lame à partir de 46.

Usage : python3 outils/exemples-pieces.py [aperçu.png]      (demande Pillow : pip install pillow)
"""
import json
import math
import os
import random
import sys

from PIL import Image

RACINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "exemples")
SORTIE = os.path.join(RACINE, "ressources")
T = 128              # pixels de côté de chaque dessin
CX = 64              # colonne du milieu de l'épée
PIED_LAME = 46       # première ligne de la lame

# palettes de 7 tons, du plus sombre au plus clair
BRONZE = [(58, 30, 20), (98, 54, 30), (142, 84, 42), (184, 120, 58), (214, 158, 84), (240, 198, 124), (255, 236, 180)]
VERT_DE_GRIS = [(26, 62, 60), (40, 92, 84), (56, 116, 102), (70, 140, 120), (96, 164, 140), (120, 186, 160), (160, 214, 190)]
FER = [(38, 42, 54), (72, 80, 96), (112, 122, 140), (152, 162, 178), (190, 198, 210), (222, 228, 236), (250, 252, 255)]
OR = [(74, 44, 16), (128, 82, 22), (180, 124, 30), (224, 168, 48), (246, 206, 84), (255, 232, 140), (255, 250, 208)]
ACIER = [(20, 28, 56), (40, 58, 104), (66, 96, 152), (102, 140, 194), (146, 184, 226), (196, 222, 246), (240, 250, 255)]
ARGENT = [(44, 48, 66), (84, 90, 114), (126, 134, 160), (168, 176, 200), (204, 210, 228), (232, 236, 246), (255, 255, 255)]
CUIR = [(52, 30, 18), (86, 52, 30), (124, 80, 46), (160, 110, 66), (192, 144, 96), (216, 176, 128), (236, 206, 164)]
CUIR_ROUGE = [(48, 10, 16), (88, 18, 26), (128, 28, 36), (164, 44, 48), (192, 70, 66), (216, 104, 92), (236, 150, 130)]
CUIR_NUIT = [(10, 14, 34), (20, 28, 62), (32, 44, 92), (48, 64, 124), (70, 90, 156), (100, 122, 186), (140, 160, 214)]
RUBIS = [(70, 6, 22), (130, 12, 36), (190, 28, 52), (232, 64, 76), (250, 120, 120), (255, 190, 180), (255, 240, 236)]
SAPHIR = [(8, 40, 90), (14, 80, 150), (30, 130, 206), (70, 180, 236), (130, 216, 250), (196, 240, 255), (240, 252, 255)]


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
    return palette[max(0, min(6, int(t)))]


# ------------------------------------------------------------------ formes pleines (gardes, pommeaux, gemmes)

def forme(test, x0=0, x1=T - 1, y0=0, y1=T - 1):
    """Ensemble des pixels dont le centre vérifie test(dx, y), dx étant l'écart au milieu de l'épée."""
    return {(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1) if test(x - CX, y)}


def rectangle(dx0, dx1, y0, y1):
    return {(CX + dx, y) for dx in range(dx0, dx1 + 1) for y in range(y0, y1 + 1)}


def disque(cx, cy, r):
    return forme(lambda dx, y: (dx - cx) ** 2 + (y - cy) ** 2 <= r * r)


def profondeur(masque, maxi=4):
    """Distance de chaque pixel au bord de la forme (1 = sur le bord)."""
    d, reste, niveau = {}, set(masque), 1
    while reste and niveau <= maxi:
        bord = {p for p in reste if any(q not in reste for q in ((p[0] - 1, p[1]), (p[0] + 1, p[1]), (p[0], p[1] - 1), (p[0], p[1] + 1)))}
        for p in bord:
            d[p] = niveau
        reste -= bord
        niveau += 1
    for p in reste:
        d[p] = niveau
    return d


def relief(dessin, masque, palette, ep_bord, ep_milieu, ep_centre, decalage=0):
    """Peint une forme en relief : contour sombre, arête éclairée en haut à gauche, ombre en bas à droite."""
    d = profondeur(masque)
    for (x, y) in masque:
        n = d[(x, y)]
        haut_gauche = d.get((x - 1, y + 1), 0)
        bas_droite = d.get((x + 1, y - 1), 0)
        cote = haut_gauche - bas_droite          # < 0 : côté éclairé ; > 0 : côté à l'ombre
        fin = max(d.get((x + i, y + j), 0) for i in (-1, 0, 1) for j in (-1, 0, 1)) == 1
        if fin:
            t = 4 if cote < 0 else 2 if cote > 0 else 3
        elif n == 1:
            t = 2 if cote < 0 else 0 if cote > 0 else 1
        elif n == 2:
            t = 5 if cote < 0 else 2 if cote > 0 else 4
        else:
            t = 4 if cote < 0 else 3
        ep = ep_bord if n == 1 else ep_milieu if n == 2 else ep_centre
        dessin.poser(x, y, ton(palette, t + decalage), ep)


def gemme(dessin, masque, palette, ep):
    """Pierre taillée : sombre en bas à droite, claire en haut à gauche, un éclat blanc."""
    relief(dessin, masque, palette, ep - 1, ep, ep + 1, decalage=1)
    xs, ys = [p[0] for p in masque], [p[1] for p in masque]
    eclat = (min(xs) + max(1, (max(xs) - min(xs)) // 3), max(ys) - max(1, (max(ys) - min(ys)) // 3))
    if eclat in masque:
        dessin.poser(eclat[0], eclat[1], palette[6], dessin.epaisseur[eclat])


# ------------------------------------------------------------------ lames

def lame(profil, longueur, palette, style, hasard, largeur_gouttiere=1, incrustation=None, patine=None):
    """Lame peinte ligne par ligne. profil(i, longueur) donne (gauche, droite) : la lame va de CX - gauche à CX + droite.

    style : « gouttiere » (rainure au milieu), « arete » (arête claire au milieu), « fauchon » (un seul tranchant, à droite).
    """
    d = Dessin()
    for i in range(longueur):
        y = PIED_LAME + i
        g, dr = profil(i, longueur)
        xg, xd = CX - g, CX + dr
        if xg > xd:
            continue
        large = xd - xg + 1
        corps = 6 <= i < longueur - 20           # zone qui porte la gouttière ou l'incrustation
        for x in range(xg, xd + 1):
            o, a, b = x - CX, x - xg, xd - x     # écart au milieu, distance au bord gauche, au bord droit
            if style == "fauchon":
                t = 1 if a == 0 else 2 if a <= 2 else 5 if b <= 3 else 3
                if b == 0:
                    t = 6
                ep = 4 if a <= 2 else 1 if b == 0 else 2 if b <= 3 else 3
            else:
                t = 4 if o < 0 else 3
                ep = 3
                if style == "gouttiere" and corps and abs(o) <= largeur_gouttiere:
                    t, ep = 2, 2
                    if o == -largeur_gouttiere:
                        t = 1
                    if o == largeur_gouttiere:
                        t = 5
                elif style == "arete" and o == 0:
                    t, ep = 6, 4
                    if incrustation and corps and (i % 9) < 6:
                        d.poser(x, y, ton(incrustation, 4 if (i % 9) in (1, 2) else 3), 4)
                        continue
                elif style == "arete" and o == 1:
                    t = 2
                if a == 1 or b == 1:
                    ep = 2
                if b == 1 and large > 4:
                    t = 2
                if a == 0:
                    t, ep = 6, 1
                if b == 0:
                    t, ep = 1, 1
                if large <= 3:
                    t, ep = (5 if a == 0 else 2), 1
            # reflets en biais et petites irrégularités du métal
            if t in (3, 4) and (x + y) % 27 < 3:
                t += 1
            elif t in (3, 4) and hasard.random() < 0.04:
                t -= 1
            couleur = ton(palette, t)
            if patine and t <= 3 and hasard.random() < (0.30 if i < 10 or b <= 1 else 0.05):
                couleur = ton(patine, t + 1)
            d.poser(x, y, couleur, ep)
    return d


def profil_chevalier(i, n):
    w = 5 if i < n - 16 else round(5 * (n - 1 - i) / 16)
    return w, w


def profil_large(i, n):
    w = 6 if i < 3 else 7 if i < n - 12 else round(7 * (n - 1 - i) / 12)
    return w, w


def profil_feuille(i, n):
    u = i / (n - 1)
    if u < 0.15:
        w = 4 - u / 0.15
    elif u < 0.6:
        v = (u - 0.15) / 0.45
        w = 3 + 3.5 * v * v * (3 - 2 * v)
    else:
        w = 6.5 * (1 - ((u - 0.6) / 0.4) ** 1.7)
    w = round(w)
    return w, w


def profil_fauchon(i, n):
    u = i / (n - 1)
    droite = round(4 + 4.5 * u)
    k = i - (n - 14)
    gauche = 4 if k < 0 else round(4 - (droite + 4) * ((k + 1) / 14) ** 1.3)
    return gauche, droite


def profil_royal(i, n):
    w = 7 if i < 5 else 4 if i < 8 else 5 if i < n - 22 else round(5 * (n - 1 - i) / 22)
    return w, w


def profil_espadon(i, n):
    if i < 4:
        w = 5
    elif i < 9:
        w = 5 + (i - 4)                           # deux ergots au pied de la lame
    elif i < n - 14:
        w = 5 + round(2.4 * (i - 9) / (n - 23))   # la lame s'élargit vers le haut
    else:
        w = round(7.4 * (n - 1 - i) / 14)
    return w, w


# ------------------------------------------------------------------ gardes

def garde(masques, palette, gemme_=None, palette_gemme=None, rivets=()):
    d = Dessin()
    union = set()
    for m in masques:
        union |= m
    relief(d, union, palette, 5, 7, 8)
    for (dx, y) in rivets:
        d.poser(CX + dx, y, palette[6], 9)
        d.poser(CX + dx + 1, y - 1, palette[1], 9)
    if gemme_:
        gemme(d, gemme_, palette_gemme, 9)
    return d


def bras(courbe, demi, portee):
    """Deux bras symétriques : courbe(a) = hauteur du milieu du bras à l'écart a, demi(a) = sa demi-épaisseur."""
    return forme(lambda dx, y: abs(dx) <= portee and abs(y - courbe(abs(dx))) <= demi(abs(dx)), y0=28, y1=60)


def aile(dx, y):
    """Garde ailée : dessus presque droit, dessous en dents de scie (les plumes)."""
    a = abs(dx)
    if a > 20:
        return False
    dessus = 47 + 4 * (a / 20) ** 2
    dessous = 39 + 0.25 * a + (a % 5) * 0.7
    return dessous <= y <= dessus


# ------------------------------------------------------------------ poignées

def poignee(palette, fil=None, bagues=None):
    d = Dessin()
    base = [1, 4, 4, 3, 3, 2, 0]
    for y in range(16, 41):
        for k, dx in enumerate(range(-3, 4)):
            t = base[k]
            if 0 < k < 6:
                bande = (dx + y) % 5
                t += -2 if bande == 0 else 1 if bande == 1 else 0
            couleur = ton(palette, t)
            if fil and 0 < k < 6 and (dx - y) % 8 == 0:
                couleur = ton(fil, 5 if k < 4 else 3)
            d.poser(CX + dx, y, couleur, 4 if k in (0, 6) else 6)
    if bagues:
        for y0 in (19, 27, 35):
            for y in (y0, y0 + 1):
                for k, dx in enumerate(range(-4, 5)):
                    t = [1, 5, 6, 4, 4, 3, 3, 2, 0][k] - (1 if y == y0 else 0)
                    d.poser(CX + dx, y, ton(bagues, t), 7)
    return d


# ------------------------------------------------------------------ pommeaux

def pommeau_anneau(palette):
    d = Dessin()
    anneau = forme(lambda dx, y: 2.2 ** 2 < dx * dx + (y - 9) ** 2 <= 6.6 ** 2)
    relief(d, anneau | rectangle(-2, 2, 15, 18), palette, 5, 7, 8)
    return d


def pommeau_disque(palette, pierre):
    d = Dessin()
    relief(d, disque(0, 10, 6.6) | rectangle(-2, 2, 16, 19), palette, 5, 7, 8)
    gemme(d, disque(0, 10, 2.6), pierre, 9)
    return d


def pommeau_cristal(palette, pierre):
    d = Dessin()
    gemme(d, forme(lambda dx, y: abs(dx) / 5.6 + abs(y - 9) / 8.6 <= 1), pierre, 7)
    relief(d, rectangle(-4, 4, 14, 18) | rectangle(-3, 3, 19, 19), palette, 6, 8, 9)
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
    centre = rectangle(-4, 4, 39, 48) - {(CX + sx * 4, y) for sx in (-1, 1) for y in (39, 48)}
    return {
        "cuivre": {
            "lame_droite": lame(profil_feuille, 60, BRONZE, "gouttiere", h, largeur_gouttiere=0, patine=VERT_DE_GRIS),
            "lame_large": lame(profil_fauchon, 58, BRONZE, "fauchon", h, patine=VERT_DE_GRIS),
            "garde_droite": garde([rectangle(-11, 11, 40, 46), rectangle(-7, 7, 39, 47)], BRONZE, rivets=((-9, 43), (8, 43))),
            "garde_croissant": garde([bras(lambda a: 41 + 7 * (a / 14) ** 2, lambda a: 3.1 - 1.5 * a / 14, 14),
                                      rectangle(-4, 4, 38, 45)], BRONZE, rivets=((0, 42),)),
            "poignee_cuir": poignee(CUIR),
            "pommeau_rond": pommeau_anneau(BRONZE),
        },
        "fer": {
            "lame_droite": lame(profil_chevalier, 78, FER, "gouttiere", h, largeur_gouttiere=1),
            "lame_large": lame(profil_large, 70, FER, "gouttiere", h, largeur_gouttiere=2),
            "garde_droite": garde([rectangle(-17, 17, 41, 45), rectangle(-19, -15, 40, 46), rectangle(15, 19, 40, 46), centre],
                                  OR, gemme_=forme(lambda dx, y: abs(dx) + abs(y - 43) <= 2), palette_gemme=RUBIS),
            "garde_croissant": garde([bras(lambda a: 44 - 6 * (a / 19) ** 2, lambda a: 2.5 if a < 14 else 1.5, 19),
                                      disque(-19, 38, 2.6), disque(19, 38, 2.6), centre], OR),
            "poignee_cuir": poignee(CUIR_ROUGE, fil=OR),
            "pommeau_rond": pommeau_disque(OR, RUBIS),
        },
        "acier": {
            "lame_droite": lame(profil_royal, 80, ACIER, "arete", h, incrustation=SAPHIR),
            "lame_large": lame(profil_espadon, 80, ACIER, "gouttiere", h, largeur_gouttiere=1),
            "garde_droite": garde([forme(aile, y0=36, y1=54), forme(lambda dx, y: abs(dx) + abs(y - 43) <= 6)], ARGENT,
                                  gemme_=forme(lambda dx, y: abs(dx) + abs(y - 43) <= 3), palette_gemme=SAPHIR),
            "garde_croissant": garde([bras(lambda a: 40 + 10 * (a / 16) ** 1.5, lambda a: 2.4 * (1 - a / 16) + 1.3, 16),
                                      disque(0, 42, 5.6)], ARGENT, gemme_=disque(0, 42, 3.1), palette_gemme=SAPHIR),
            "poignee_cuir": poignee(CUIR_NUIT, bagues=ARGENT),
            "pommeau_rond": pommeau_cristal(ARGENT, SAPHIR),
        },
    }


def apercu(tout, chemin):
    """Vue de face de quelques épées assemblées (pour juger le dessin sans lancer le jeu)."""
    choix = [("cuivre", "lame_droite", "cuivre", "garde_droite"), ("cuivre", "lame_large", "cuivre", "garde_croissant"),
             ("fer", "lame_droite", "fer", "garde_droite"), ("fer", "lame_large", "fer", "garde_croissant"),
             ("acier", "lame_droite", "acier", "garde_droite"), ("acier", "lame_large", "acier", "garde_croissant"),
             ("acier", "lame_droite", "fer", "garde_croissant"), ("fer", "lame_large", "cuivre", "garde_droite")]
    k, pas = 6, 56
    img = Image.new("RGBA", (pas * k * len(choix), T * k), (44, 46, 54, 255))
    for n, (ml, l, mg, g) in enumerate(choix):
        pixels = {}
        for d in (tout[ml]["pommeau_rond"], tout[ml]["poignee_cuir"], tout[ml][l], tout[mg][g]):
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
