#!/usr/bin/env python3
"""Dessine les armures d'exemple (src/main/resources/exemples/ressources).

Trois ensembles, assortis aux épées : cuivre (légionnaire : plaques de cuivre, lanières de cuir), fer (chevalier :
plates et mailles), acier (acier sombre bordé d'or). Chaque ensemble a quatre pièces : casque, plastron, jambières,
bottes.

Pour chaque ensemble, le script écrit :
  textures/exemple/armure/<ensemble>_<pièce>.png   icône d'inventaire, 16 x 16, à plat comme les armures du jeu
  armures/exemple/<ensemble>.png                   armure portée : tête, torse, bras, pieds (64 x 32)
  armures/exemple/<ensemble>_jambes.png            armure portée : jambes et ceinture (64 x 32)

Une armure portée n'est pas un modèle 3D : Minecraft l'affiche sur le corps du joueur, à partir d'une texture au
format des armures du jeu. Même style que les épées : 5 tons par matière, aplats, pas de contour noir.

Usage : python3 outils/exemples-armures.py [aperçu.png]      (demande Pillow : pip install pillow)
"""
import os
import random
import sys

from PIL import Image

RACINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "exemples")
SORTIE = os.path.join(RACINE, "ressources")

# palettes de 5 tons, du plus sombre au plus clair (les mêmes que celles des épées)
CUIVRE = [(109, 52, 33), (156, 69, 41), (193, 90, 54), (231, 124, 86), (252, 163, 130)]
FER = [(68, 68, 72), (108, 110, 116), (150, 152, 158), (194, 196, 202), (236, 238, 242)]
MAILLE = [(40, 42, 48), (62, 64, 72), (86, 88, 98), (112, 114, 124), (140, 142, 152)]
ACIER = [(38, 42, 54), (62, 70, 88), (92, 104, 126), (132, 146, 168), (186, 200, 218)]
OR = [(117, 82, 21), (185, 133, 26), (233, 177, 21), (250, 214, 74), (253, 245, 150)]
CUIR = [(38, 18, 14), (64, 30, 22), (92, 44, 30), (122, 62, 40), (150, 84, 56)]
CUIR_NOIR = [(16, 18, 26), (28, 32, 46), (44, 50, 70), (62, 70, 96), (84, 94, 124)]

# un ensemble : métal principal, matière des bordures, matière du dessous (lanières, mailles, cuir)
ENSEMBLES = {
    "cuivre": {"metal": CUIVRE, "bord": CUIR, "dessous": CUIR, "style": "legionnaire"},
    "fer": {"metal": FER, "bord": FER, "dessous": MAILLE, "style": "chevalier"},
    "acier": {"metal": ACIER, "bord": OR, "dessous": CUIR_NOIR, "style": "royal"},
}

# ------------------------------------------------------------------ icônes d'inventaire (16 x 16)
# chiffres 0 à 4 : tons du métal ; lettres a à e : tons de la bordure ; point : vide
ICONES = {
    "casque": [
        "................",
        "................",
        "....33333322....",
        "...3444333221...",
        "..344433332221..",
        "..344333322211..",
        "..343333222211..",
        "..cdccccccccba..",
        "..332......211..",
        "..332......211..",
        "..331......210..",
        "..31........10..",
        "................",
        "................",
        "................",
        "................",
    ],
    "plastron": [
        "................",
        ".333........222.",
        "34443......32221",
        "344433cddc322211",
        "3443333333322211",
        ".33333333332211.",
        "..343333332221..",
        "..343333322221..",
        "..343333322211..",
        "..333333222211..",
        "..333332222211..",
        "..cdccccccccba..",
        "..332222221110..",
        "................",
        "................",
        "................",
    ],
    "jambieres": [
        "................",
        "..cdccccccccba..",
        "..344333322211..",
        "..344333322211..",
        "..3443333.2211..",
        "..3433....2211..",
        "..3433....2211..",
        "..3443....3211..",
        "..3443....3211..",
        "..3433....2211..",
        "..3433....2211..",
        "..3432....2210..",
        "..3322....2110..",
        "................",
        "................",
        "................",
    ],
    "bottes": [
        "................",
        "................",
        "................",
        "..cdcb....cdba..",
        "..3432....3321..",
        "..3432....3321..",
        "..3432....3321..",
        "..3432....3321..",
        ".34432...33321..",
        ".344322..333211.",
        ".333221..322110.",
        ".111100..111000.",
        "................",
        "................",
        "................",
        "................",
    ],
}


def icone(nom, ensemble):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, ligne in enumerate(ICONES[nom]):
        assert len(ligne) == 16, (nom, y)
        for x, c in enumerate(ligne):
            if c == ".":
                continue
            palette = ensemble["metal"] if c.isdigit() else ensemble["bord"]
            img.putpixel((x, y), palette[int(c) if c.isdigit() else ord(c) - ord("a")] + (255,))
    return img


# ------------------------------------------------------------------ armure portée (64 x 32, format des armures du jeu)

class Boite:
    """Une partie du corps dans la texture : coin (x, y), largeur, hauteur et profondeur en pixels."""

    def __init__(self, x, y, largeur, hauteur, profondeur):
        l, h, p = largeur, hauteur, profondeur
        self.faces = {
            "dessus": (x + p, y, l, p), "dessous": (x + p + l, y, l, p),
            "droite": (x, y + p, p, h), "devant": (x + p, y + p, l, h),
            "gauche": (x + p + l, y + p, p, h), "derriere": (x + p + l + p, y + p, l, h),
        }


TETE = Boite(0, 0, 8, 8, 8)
TORSE = Boite(16, 16, 8, 12, 4)
BRAS = Boite(40, 16, 4, 12, 4)
JAMBE = Boite(0, 16, 4, 12, 4)


def peindre(img, face, palette, hasard, lignes=None, colonnes=None, trous=(), bord_bas=None, traits=()):
    """Peint une face en aplat : rangée du haut claire, rangée du bas sombre, un peu d'usure.

    lignes / colonnes : rangées et colonnes à peindre (toutes par défaut) ; trous : pixels (colonne, rangée) laissés
    vides ; bord_bas : palette de la dernière rangée peinte ; traits : rangées marquées d'un trait sombre (jointure).
    """
    x0, y0, l, h = face
    lignes = range(h) if lignes is None else lignes
    colonnes = range(l) if colonnes is None else colonnes
    premiere, derniere = min(lignes), max(lignes)
    for j in lignes:
        for i in colonnes:
            if (i, j) in trous:
                continue
            t = 2
            if j == premiere:
                t = 3
            elif j in traits:
                t = 1
            elif i == min(colonnes):
                t = 3
            elif l * h > 40 and hasard.random() < 0.05:
                t += hasard.choice((-1, 1))
            p = palette
            if j == derniere:
                t = 1
                if bord_bas:
                    p, t = bord_bas, 2 if i % 2 else 3
            img.putpixel((x0 + i, y0 + j), p[max(0, min(4, t))] + (255,))


def portee(ensemble):
    """Les deux textures d'un ensemble : (tête, torse, bras, pieds) et (jambes)."""
    h = random.Random("armure-" + ensemble["style"])
    metal, bord, dessous, style = ensemble["metal"], ensemble["bord"], ensemble["dessous"], ensemble["style"]
    haut = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    bas = Image.new("RGBA", (64, 32), (0, 0, 0, 0))

    # --- casque
    peindre(haut, TETE.faces["dessus"], metal, h)
    for cote in ("droite", "gauche", "derriere"):
        peindre(haut, TETE.faces[cote], metal, h, lignes=range(0, 7), bord_bas=bord)
    if style == "royal":
        # heaume fermé : une fente pour les yeux
        peindre(haut, TETE.faces["devant"], metal, h, lignes=range(0, 8), bord_bas=bord,
                trous={(i, 4) for i in range(1, 7) if i not in (3, 4)})
    else:
        # visage ouvert, protège-joues ; le chevalier a un nasal
        visage = {(i, j) for i in range(2, 6) for j in range(3, 8)}
        if style == "chevalier":
            visage -= {(3, 3), (3, 4), (3, 5), (4, 3), (4, 4), (4, 5)}
        visage |= {(i, 7) for i in range(0, 8)}
        peindre(haut, TETE.faces["devant"], metal, h, trous=visage)
    if style == "legionnaire":
        # cimier : une bande de cuir sur le dessus du casque
        x0, y0, _, _ = TETE.faces["dessus"]
        for j in range(8):
            for i in (3, 4):
                haut.putpixel((x0 + i, y0 + j), bord[3 if i == 3 else 2] + (255,))

    # --- plastron : torse entier, épaulières, bras selon le style
    col = {(i, 0) for i in range(2, 6)}
    peindre(haut, TORSE.faces["devant"], metal, h, trous=col, bord_bas=bord, traits=(6, 9))
    peindre(haut, TORSE.faces["derriere"], metal, h, bord_bas=bord, traits=(6, 9))
    for cote in ("droite", "gauche"):
        peindre(haut, TORSE.faces[cote], metal, h, bord_bas=bord, traits=(6, 9))
    x0, y0, _, _ = TORSE.faces["devant"]
    for j in range(1, 11):
        # arête au milieu du plastron
        haut.putpixel((x0 + 3, y0 + j), metal[3] + (255,))
    peindre(haut, BRAS.faces["dessus"], metal, h)
    for cote in ("droite", "devant", "gauche", "derriere"):
        peindre(haut, BRAS.faces[cote], metal, h, lignes=range(0, 5), bord_bas=bord)
        if style == "chevalier":
            peindre(haut, BRAS.faces[cote], dessous, h, lignes=range(5, 11))
        elif style == "royal":
            peindre(haut, BRAS.faces[cote], metal, h, lignes=range(5, 11), bord_bas=bord, traits=(7,))

    # --- bottes : le bas des jambes
    for cote in ("droite", "devant", "gauche", "derriere"):
        x0, y0, l, _ = JAMBE.faces[cote]
        peindre(haut, JAMBE.faces[cote], metal, h, lignes=range(7, 12))
        for i in range(l):
            haut.putpixel((x0 + i, y0 + 7), bord[2 if i % 2 else 3] + (255,))
    peindre(haut, JAMBE.faces["dessous"], metal, h)

    # --- jambières : ceinture sur le bas du torse, puis les jambes
    for cote in ("droite", "devant", "gauche", "derriere"):
        peindre(bas, TORSE.faces[cote], bord, h, lignes=range(7, 9))
        if style == "legionnaire":
            # lanières de cuir : une colonne sur deux, sur le bas du torse et le haut des jambes
            x0, y0, l, _ = TORSE.faces[cote]
            for i in range(l):
                for j in range(9, 12):
                    bas.putpixel((x0 + i, y0 + j), dessous[3 if i % 2 else 1] + (255,))
            x0, y0, l, _ = JAMBE.faces[cote]
            for i in range(l):
                for j in range(0, 5):
                    bas.putpixel((x0 + i, y0 + j), dessous[3 if i % 2 else 1] + (255,))
                bas.putpixel((x0 + i, y0 + 4), metal[2 if i % 2 else 3] + (255,))
        else:
            peindre(bas, TORSE.faces[cote], dessous if style == "chevalier" else metal, h, lignes=range(9, 12))
            peindre(bas, JAMBE.faces[cote], metal, h, lignes=range(0, 9), traits=(4,), bord_bas=bord if style == "royal" else None)
    x0, y0, _, _ = TORSE.faces["devant"]
    for i in (3, 4):
        # boucle de ceinture
        bas.putpixel((x0 + i, y0 + 7), metal[4] + (255,))
        bas.putpixel((x0 + i, y0 + 8), metal[2] + (255,))
    return haut, bas


def mannequin(haut, bas, k):
    """Vue de face d'un joueur qui porte l'ensemble (corps gris, armure par-dessus)."""
    img = Image.new("RGBA", (16 * k, 32 * k), (0, 0, 0, 0))
    corps = (120, 104, 96, 255)
    parties = [  # (boîte, x, y dans la vue, miroir)
        (TETE, 4, 0, False), (TORSE, 4, 8, False), (BRAS, 0, 8, False), (BRAS, 12, 8, True),
        (JAMBE, 4, 20, False), (JAMBE, 8, 20, True),
    ]
    for boite, px, py, miroir in parties:
        x0, y0, l, h = boite.faces["devant"]
        for j in range(h):
            for i in range(l):
                c = corps
                for couche in (bas, haut):
                    p = couche.getpixel((x0 + (l - 1 - i if miroir else i), y0 + j))
                    if p[3]:
                        c = p
                img.paste(c, ((px + i) * k, (py + j) * k, (px + i + 1) * k, (py + j + 1) * k))
    return img


def main():
    fichiers = []
    apercu = Image.new("RGBA", (3 * 420, 360), (44, 46, 54, 255))
    for n, (nom, ensemble) in enumerate(ENSEMBLES.items()):
        for dossier in ("textures/exemple/armure", "armures/exemple"):
            os.makedirs(os.path.join(SORTIE, dossier), exist_ok=True)
        for k, piece in enumerate(ICONES):
            img = icone(piece, ensemble)
            img.save(os.path.join(SORTIE, "textures/exemple/armure/%s_%s.png" % (nom, piece)), optimize=True)
            apercu.alpha_composite(img.resize((80, 80), Image.NEAREST), (n * 420 + 20 + (k % 2) * 90, 60 + (k // 2) * 100))
        haut, bas = portee(ensemble)
        haut.save(os.path.join(SORTIE, "armures/exemple/%s.png" % nom), optimize=True)
        bas.save(os.path.join(SORTIE, "armures/exemple/%s_jambes.png" % nom), optimize=True)
        apercu.alpha_composite(mannequin(haut, bas, 10), (n * 420 + 230, 20))
    # liste de tous les fichiers d'exemple, lue par le plugin pour les installer au premier lancement
    for dossier, _, noms in os.walk(SORTIE):
        for f in noms:
            fichiers.append(os.path.relpath(os.path.join(dossier, f), SORTIE).replace(os.sep, "/"))
    with open(os.path.join(RACINE, "ressources.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(sorted(fichiers)) + "\n")
    print("armures écrites dans %s (%d fichiers d'exemple en tout)" % (os.path.normpath(SORTIE), len(fichiers)))
    if len(sys.argv) > 1:
        apercu.save(sys.argv[1])


if __name__ == "__main__":
    main()
