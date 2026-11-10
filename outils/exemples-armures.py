#!/usr/bin/env python3
"""Dessine les armures d'exemple (src/main/resources/exemples/ressources) : trois ensembles, un par matériau.

  cuivre : cuivre martelé qui verdit par endroits, courroies de cuir, casque ouvert à nasal
  fer    : lames de fer rivetées, cotte de mailles aux jointures, ceinture de cuir, chapel à fentes
  acier  : plates d'acier sombre bordées d'or, arête au milieu du torse, heaume à visière

Pour chaque matériau :
  equipement/exemple/armure_<m>/humanoid.png          casque, plastron, bras, bottes (64 x 32, disposition du jeu)
  equipement/exemple/armure_<m>/humanoid_leggings.png jambières (64 x 32)
  textures/exemple/armure/<m>_<pièce>.png             icônes d'inventaire (16 x 16, comme les armures du jeu)

Style des objets de Minecraft (skill babel-armes-style) : 5 tons par matière, aplats, lumière en haut à gauche, détails
au pixel (rivets, joints, bordures), pas de contour noir. Usage : python3 outils/exemples-armures.py [aperçu.png]
"""
import os
import sys

from PIL import Image

RACINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "exemples")
SORTIE = os.path.join(RACINE, "ressources")

# du plus sombre au plus clair (mêmes palettes que les pièces d'armes)
CUIVRE = [(109, 52, 33), (156, 69, 41), (193, 90, 54), (231, 124, 86), (252, 163, 130)]
OXYDE = [(47, 110, 96), (61, 138, 116), (79, 171, 144), (79, 171, 144), (110, 196, 168)]
FER = [(68, 68, 72), (108, 110, 116), (150, 152, 158), (194, 196, 202), (236, 238, 242)]
ACIER = [(38, 42, 54), (62, 70, 88), (92, 104, 126), (132, 146, 168), (186, 200, 218)]
OR = [(117, 82, 21), (185, 133, 26), (233, 177, 21), (250, 214, 74), (253, 245, 150)]
CUIR = [(38, 18, 14), (64, 30, 22), (92, 44, 30), (122, 62, 40), (150, 84, 56)]
MAILLE = [(40, 40, 44), (70, 72, 78), (110, 112, 118), (150, 152, 158), (190, 192, 198)]

ENSEMBLES = {
    "cuivre": dict(metal=CUIVRE, bord=CUIVRE, sangle=CUIR, oxyde=OXYDE, style="martele"),
    "fer": dict(metal=FER, bord=FER, sangle=CUIR, oxyde=None, style="lames"),
    "acier": dict(metal=ACIER, bord=OR, sangle=CUIR, oxyde=None, style="plates"),
}

# faces des cubes dans la texture d'armure du jeu (x, y, largeur, hauteur)
TETE = dict(dessus=(8, 0, 8, 8), dessous=(16, 0, 8, 8), droite=(0, 8, 8, 8), avant=(8, 8, 8, 8), gauche=(16, 8, 8, 8), arriere=(24, 8, 8, 8))
CORPS = dict(dessus=(20, 16, 8, 4), dessous=(28, 16, 8, 4), droite=(16, 20, 4, 12), avant=(20, 20, 8, 12), gauche=(28, 20, 4, 12), arriere=(32, 20, 8, 12))
BRAS = dict(dessus=(44, 16, 4, 4), dessous=(48, 16, 4, 4), droite=(40, 20, 4, 12), avant=(44, 20, 4, 12), gauche=(48, 20, 4, 12), arriere=(52, 20, 4, 12))
JAMBE = dict(dessus=(4, 16, 4, 4), dessous=(8, 16, 4, 4), droite=(0, 20, 4, 12), avant=(4, 20, 4, 12), gauche=(8, 20, 4, 12), arriere=(12, 20, 4, 12))


def ton(p, i):
    return p[max(0, min(4, i))] + (255,)


LIN = [(92, 78, 58), (128, 110, 82), (164, 144, 108), (196, 178, 140), (222, 208, 172)]
DRAP = [(58, 22, 22), (88, 32, 30), (118, 44, 40), (150, 62, 54), (178, 86, 74)]   # drap de la brigandine
VIDE = None
OMBRE = {"avant": 0, "gauche": 0, "dessus": -1, "droite": 1, "arriere": 1, "dessous": 2}


def maille(x, y, o=0):
    """Cotte de mailles : anneaux en quinconce, comme la maille du jeu."""
    return ton(MAILLE, 2 + (1 if (x + y) % 2 == 0 else 0) - (1 if (x % 2 and y % 2) else 0) - o)


def matelasse(p, x, y, o=0):
    """Gambison matelassé : coutures verticales tous les 2 pixels, piqûres en losange."""
    t = 3 if x % 2 == 0 else 2
    if (x + y) % 4 == 0:
        t = 1
    return ton(p, t - o)


def bombe(p, x, w, o=0):
    """Plaque bombée : claire au milieu-gauche (lumière en haut à gauche), sombre sur les bords."""
    if w >= 8:
        t = [2, 3, 3, 4, 3, 3, 2, 1][x * 8 // w]
    else:
        t = [3, 4, 2, 1][x * 4 // w]
    return ton(p, t - o)


def ecailles(p, x, y, o=0):
    """Lamelles de cuivre : rangées de 2 pixels décalées, haut de chaque écaille éclairé."""
    rang = y // 2
    xx = (x + rang) % 2
    t = (3 if y % 2 == 0 else 2) - (1 if xx else 0)
    return ton(p, t - o)


def pixel(m, partie, face, x, y, w, h):
    """Couleur d'un pixel de l'armure (ou None : transparent). x, y dans la face, w x h sa taille."""
    o = OMBRE[face]
    if m == "cuivre":
        if partie == "tete":
            if face == "dessus":
                return ton(CUIVRE, 4 if x in (3, 4) or y in (3, 4) else 3) if (x + y) % 5 else ton(CUIVRE, 2)
            if y <= 3:                                             # calotte de cuivre en côtes, bord sombre
                if y == 3:
                    return ton(CUIVRE, 1 - min(o, 0))
                return ton(CUIVRE, (4 if x in (3, 4) else 3 if x % 3 == 0 else 2) - o)
            if face == "avant":
                if x in (3, 4) and y <= 6:
                    return ton(CUIVRE, 3 if x == 3 else 1)         # nasal
                if x in (0, 7) or y == 7:
                    return matelasse(CUIR, x, y)                    # coiffe de cuir autour du visage
                return VIDE
            return matelasse(CUIR, x, y, o)
        if partie == "corps":
            if face == "dessus":
                return matelasse(CUIR, x, y, 0)
            if y == 8:
                return ton(CUIR, 1) if x not in (3, 4) else ton(OR, 3 if x == 3 else 2)   # ceinture et boucle
            if face in ("avant", "arriere") and 1 <= y <= 7 and 0 < x < w - 1:
                return ecailles(CUIVRE, x, y, o)                    # plastron de lamelles
            if y == 0:
                return ton(CUIR, 3 - o)
            return matelasse(LIN, x, y, o)                          # gambison
        if partie == "bras":
            if y <= 3:
                return ecailles(CUIVRE, x, y, o) if y > 0 else ton(CUIVRE, 3 - o)
            if 4 <= y <= 7:
                return matelasse(LIN, x, y, o)
            if y == 9:
                return ton(CUIR, 2)
            return bombe(CUIVRE, x, w, o)                           # bracelet de cuivre
        if partie == "botte":
            if y == 7:
                return ton(CUIR, 3 - o)
            if y >= 10 and face == "avant":
                return ton(CUIVRE, 3 if y == 10 else 2)             # bout ferré
            return ton(CUIR, 2 - o + (1 if x == 0 else 0))
        if partie == "ceinture":
            return matelasse(LIN, x, y, o) if y > 8 else ton(CUIR, 1)
        if partie == "cuisse":
            if y in (5, 6) and face == "avant":
                return ton(CUIVRE, 3 if y == 5 else 2)              # genouillère
            return matelasse(CUIR, x, y, o)
    if m == "fer":
        if partie == "tete":
            if face == "dessus":
                return ton(FER, 4 if (x, y) in ((2, 2), (3, 2), (2, 3)) else 3 if x < 5 else 2)
            if y <= 2:
                return ton(FER, (4 if y == 0 and x < 4 else 3 if x < 4 else 2) - o)   # cloche du chapel
            if y == 3:
                return ton(FER, 4 if x % 3 == 0 else 3)             # large bord riveté
            if face == "avant":
                if x in (0, 1, 6, 7) or y == 7:
                    return maille(x, y)                             # camail de mailles
                return VIDE
            return maille(x, y, o)
        if partie == "corps":
            if face == "dessus":
                return maille(x, y, 0)
            if y == 8:
                return ton(CUIR, 1) if x not in (3, 4) else ton(FER, 4)
            if face in ("avant", "arriere") and 1 <= y <= 7 and 0 < x < w - 1:
                if (x + (y // 2)) % 2 == 0 and y % 2 == 0:
                    return ton(FER, 4)                              # têtes de rivets de la brigandine
                return ton(DRAP, (3 if y == 1 else 2) - o + (1 if x == 1 else 0))
            return maille(x, y, o)
        if partie == "bras":
            if y <= 2:
                return ton(FER, (4 if y == 0 else 3 - y) - o + (1 if x == 0 else 0))  # épaulière à lames
            if 3 <= y <= 7:
                return maille(x, y, o)
            if y == 8 or y == 11:
                return ton(FER, 4 if x == 1 else 2)
            return bombe(FER, x, w, o)                              # canon d'avant-bras
        if partie == "botte":
            if face == "avant" and y <= 9:
                return bombe(FER, x, w, 0) if y > 6 else ton(FER, 4 if x == 1 else 3)   # grève
            if y == 7:
                return ton(CUIR, 3 - o)
            return ton(CUIR, 2 - o)
        if partie == "ceinture":
            return maille(x, y, o) if y > 8 else ton(CUIR, 1)
        if partie == "cuisse":
            if face == "avant" and y in (5, 6):
                return ton(FER, 4 if (y == 5 and x == 1) else 3 if y == 5 else 2)   # genouillère
            return maille(x, y, o)
    if m == "acier":
        if partie == "tete":
            if face == "dessus":
                return ton(OR, 3 if x == 3 else 2) if x in (3, 4) else bombe(ACIER, x, w, -1)   # crête dorée
            if face == "avant":
                if y == 3:
                    return ton(ACIER, 0) if 1 <= x <= 6 else ton(OR, 2)   # fente de vision
                if y == 2:
                    return ton(OR, 3 if x < 4 else 2)              # bord doré de la visière
                if y == 5 and x in (2, 5):
                    return ton(ACIER, 0)                            # trous d'aération
                if y >= 6:
                    return ton(ACIER, 4 if x in (3, 4) and y == 6 else 3 if x in (3, 4) else 2)   # bavière à arête
                return bombe(ACIER, x, w)
            if y == 7:
                return ton(OR, 2 - min(o, 1))                      # bord doré
            if face in ("droite", "gauche") and (x, y) == (3, 4):
                return ton(OR, 3)                                   # pivot de la visière
            return bombe(ACIER, x, w, o)
        if partie == "corps":
            if face == "dessus":
                return ton(OR, 3) if y == 0 else bombe(ACIER, x, w, -1)
            if y == 0:
                return ton(OR, 3 - o)                               # gorgerin
            if face == "avant" and y <= 7:
                if x in (3, 4):
                    return ton(ACIER, 4 if x == 3 else 2)           # arête du plastron
                if y == 7:
                    return ton(OR, 2)
                return bombe(ACIER, x, w)
            if y >= 8:                                              # braconnière en lames
                return ton(ACIER, 0) if y % 2 == 1 and y < 11 else ton(ACIER, 3 - o if y % 2 == 0 else 2 - o)
            return bombe(ACIER, x, w, o)
        if partie == "bras":
            if y <= 3:                                              # épaulière à trois lames, bord d'or
                return ton(OR, 3 - o) if y == 3 else ton(ACIER, (4 if y == 0 else 3) - o + (0 if x else 1))
            if y in (6, 7):
                return ton(ACIER, 4 if (y == 6 and x == 1) else 3 - o)   # cubitière qui brille
            if y == 11:
                return ton(ACIER, 1 - min(o, 1))                   # gantelet
            return bombe(ACIER, x, w, o)
        if partie == "botte":
            if y >= 10:
                return ton(ACIER, 0) if y == 10 and face == "avant" else ton(ACIER, 2 - o)   # solerets
            if y == 9:
                return ton(OR, 2)
            return bombe(ACIER, x, w, o)                            # grèves
        if partie == "ceinture":
            return ton(ACIER, 0) if y % 2 == 1 and y < 11 else ton(ACIER, 3 - o)
        if partie == "cuisse":
            if y in (5, 6):
                return ton(OR, 3) if (y == 5 and x == 1 and face == "avant") else ton(ACIER, 4 if y == 5 else 3)   # genouillère
            return bombe(ACIER, x, w, o)
    return VIDE


def peindre(img, m, partie, cube, lignes=None, faces=None):
    for nom, (x0, y0, w, h) in cube.items():
        if faces and nom not in faces:
            continue
        for y in range(h):
            if lignes is not None and nom not in ("dessus", "dessous") and y not in lignes:
                continue
            for x in range(w):
                c = pixel(m, partie, nom, x, y, w, h)
                if c is not None:
                    img.putpixel((x0 + x, y0 + y), c)


def couche_principale(m, e):
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    peindre(img, m, "tete", TETE, faces=("dessus", "droite", "avant", "gauche", "arriere"))
    peindre(img, m, "corps", CORPS, faces=("dessus", "droite", "avant", "gauche", "arriere"))
    peindre(img, m, "bras", BRAS, faces=("dessus", "droite", "avant", "gauche", "arriere"))
    peindre(img, m, "botte", JAMBE, lignes=range(6, 12), faces=("droite", "avant", "gauche", "arriere", "dessous"))
    return img


def couche_jambieres(m, e):
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    peindre(img, m, "ceinture", CORPS, lignes=range(8, 12), faces=("droite", "avant", "gauche", "arriere", "dessous"))
    peindre(img, m, "cuisse", JAMBE, lignes=range(0, 7), faces=("dessus", "droite", "avant", "gauche", "arriere"))
    return img


# ------------------------------------------------------------------ icônes 16 x 16
FORMES = {
    "casque": [
        "................",
        "................",
        ".....######.....",
        "....########....",
        "...##########...",
        "...##########...",
        "...##########...",
        "...###....###...",
        "...##......##...",
        "...##......##...",
        "...##......##...",
        "................",
        "................", "................", "................", "................"],
    "plastron": [
        "................",
        "...###....###...",
        "..####....####..",
        ".##############.",
        ".##############.",
        ".##############.",
        "..############..",
        "...##########...",
        "...##########...",
        "...##########...",
        "...##########...",
        "...##########...",
        "...##########...",
        "................", "................", "................"],
    "jambieres": [
        "................",
        "...##########...",
        "...##########...",
        "...##########...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "................", "................", "................", "................"],
    "bottes": [
        "................", "................", "................", "................",
        "................",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "..#####..#####..",
        ".######..######.",
        ".######..######.",
        "................", "................", "................", "................"],
}


CASQUES = {
    "cuivre": ["................", "................", "......####......", "....########....", "...##########...", "...##########...",
               "...##########...", "...###.##.###...", "...##..##..##...", "...##..##..##...", "...##......##...",
               "................", "................", "................", "................", "................"],
    "fer": ["................", "................", "................", ".....######.....", "....########....", "....########....",
            ".##############.", "..############..", "....##....##....", "....##....##....", "....########....",
            "................", "................", "................", "................", "................"],
    "acier": ["................", "......####......", "....########....", "...##########...", "...##########...", "...##########...",
              "...##########...", "...##########...", "...##########...", "....########....", "....########....",
              ".....######.....", "................", "................", "................", "................"],
}


def icone(piece, m, e):
    f = CASQUES[m] if piece == "casque" else FORMES[piece]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pleins = {(x, y) for y in range(16) for x in range(16) if f[y][x] == "#"}
    for (x, y) in pleins:
        bord_haut = (x, y - 1) not in pleins
        bord_gauche = (x - 1, y) not in pleins
        bord_bas = (x, y + 1) not in pleins
        bord_droit = (x + 1, y) not in pleins
        t = 2
        if bord_haut or bord_gauche:
            t = 3
        if bord_bas or bord_droit:
            t = 1
        if bord_haut and bord_gauche:
            t = 4
        c = ton(e["metal"], t)
        if e["style"] == "lames" and y % 3 == 0 and not (bord_haut or bord_bas):
            c = ton(FER, 0 if not (bord_gauche or bord_droit) else 1)
        if e["style"] == "plates" and (bord_haut or bord_bas):
            c = ton(OR, 3 if bord_haut else 1)
        if e["oxyde"] and (x * 5 + y * 3) % 19 == 0 and not (bord_haut or bord_gauche):
            c = ton(OXYDE, 2)
        img.putpixel((x, y), c)
    # détails propres à chaque matériau
    if piece == "casque":
        if m == "fer":
            for x in range(1, 15):
                if (x, 6) in pleins: img.putpixel((x, 6), ton(FER, 4 if x % 3 == 1 else 3))   # bord du chapel
        if m == "acier":
            for x in range(4, 12):
                img.putpixel((x, 6), ton(ACIER, 0))                      # fente de vision
            for x in range(3, 13):
                img.putpixel((x, 5), ton(OR, 3 if x < 8 else 2))
            img.putpixel((6, 8), ton(ACIER, 0)); img.putpixel((9, 8), ton(ACIER, 0))
            for y in range(1, 5):
                img.putpixel((7, y), ton(OR, 4)); img.putpixel((8, y), ton(OR, 2))
    if piece == "plastron":
        for x in range(3, 13):
            img.putpixel((x, 9), ton(CUIR, 2 if x % 3 else 3))          # ceinture
        img.putpixel((7, 9), ton(OR, 3))
        if m == "acier":
            for y in range(4, 9):
                img.putpixel((7, y), ton(ACIER, 4)); img.putpixel((8, y), ton(ACIER, 2))
    if piece == "bottes":
        for x in list(range(3, 7)) + list(range(9, 13)):
            img.putpixel((x, 7), ton(CUIR, 2))
    return img


def main():
    ecrits = []
    apercu = Image.new("RGBA", (64 * 3 * 4 + 40, 32 * 4 * 2 + 100), (44, 46, 54, 255))
    for i, (m, e) in enumerate(ENSEMBLES.items()):
        d = os.path.join(SORTIE, "equipement", "exemple", "armure_" + m)
        os.makedirs(d, exist_ok=True)
        a, b = couche_principale(m, e), couche_jambieres(m, e)
        a.save(os.path.join(d, "humanoid.png"), optimize=True)
        b.save(os.path.join(d, "humanoid_leggings.png"), optimize=True)
        apercu.paste(a.resize((256, 128), Image.NEAREST), (i * 264, 0), a.resize((256, 128), Image.NEAREST))
        apercu.paste(b.resize((256, 128), Image.NEAREST), (i * 264, 132), b.resize((256, 128), Image.NEAREST))
        di = os.path.join(SORTIE, "textures", "exemple", "armure")
        os.makedirs(di, exist_ok=True)
        for k, piece in enumerate(FORMES):
            ic = icone(piece, m, e)
            ic.save(os.path.join(di, "%s_%s.png" % (m, piece)), optimize=True)
            g = ic.resize((64, 64), Image.NEAREST)
            apercu.paste(g, (i * 264 + (k % 4) * 64, 268), g)
        ecrits.append(m)
    tous = [os.path.relpath(os.path.join(dd, n), SORTIE).replace(os.sep, "/") for dd, _, ns in os.walk(SORTIE) for n in ns]
    with open(os.path.join(RACINE, "ressources.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(sorted(tous)) + "\n")
    print("armures :", ", ".join(ecrits))
    if len(sys.argv) > 1:
        apercu.crop((0, 0, apercu.width, 340)).save(sys.argv[1])


if __name__ == "__main__":
    main()
