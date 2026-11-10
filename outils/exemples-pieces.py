#!/usr/bin/env python3
"""Dessine les pièces d'exemple de l'épée modulaire (src/main/resources/exemples/ressources).

Pour chaque métal (cuivre, fer, acier) : deux lames, deux gardes, une poignée, un pommeau. Chaque pièce est un
modèle Minecraft (éléments + UV) avec sa texture de 64 x 64 pixels, peinte à 2 pixels par unité de modèle (32 pixels
par bloc). L'épée est construite debout, centrée en x = 8 et z = 8 ; les réglages « display » communs sont dans
objets/exemples.yml (modulaire.affichage).

Technique : de vrais modèles en volume, dans le style de Minecraft (pas un dessin à plat). Chaque pièce est faite de
cubes dont chaque face est peinte, avec le bord haut éclairé et le bas dans l'ombre : la lame a un plat épais, deux
tranchants plus fins et une pointe en gradins. Une lame reste toujours une seule pièce continue.

Usage : python3 outils/exemples-pieces.py        (demande Pillow : pip install pillow)
"""
import json
import os
import random

from PIL import Image

RACINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "exemples")
SORTIE = os.path.join(RACINE, "ressources")
TAILLE = 64          # pixels de côté de chaque texture
PX = 2               # pixels par unité de modèle

# palettes : du plus sombre au plus clair (contour, ombre, base, clair, reflet)
METAUX = {
    "cuivre": [(74, 38, 24), (134, 72, 44), (184, 106, 64), (222, 146, 96), (250, 198, 150)],
    "fer":    [(52, 56, 64), (104, 110, 120), (150, 156, 166), (196, 202, 210), (238, 242, 246)],
    "acier":  [(34, 44, 62), (76, 96, 126), (118, 144, 178), (170, 196, 224), (226, 240, 252)],
}
CUIR = [(46, 28, 18), (84, 52, 32), (118, 76, 46), (150, 102, 64), (182, 134, 92)]


class Atlas:
    """Texture d'une pièce : les faces y sont rangées par étagères, de haut en bas."""

    def __init__(self):
        self.image = Image.new("RGBA", (TAILLE, TAILLE), (0, 0, 0, 0))
        self.x = 0
        self.y = 0
        self.hauteur = 0

    def place(self, l, h):
        l, h = max(1, l), max(1, h)
        if self.x + l > TAILLE:
            self.x = 0
            self.y += self.hauteur + 1
            self.hauteur = 0
        if self.y + h > TAILLE:
            raise RuntimeError("texture trop petite pour cette pièce")
        rect = (self.x, self.y, l, h)
        self.x += l + 1
        self.hauteur = max(self.hauteur, h)
        return rect


def uv(rect):
    x, y, l, h = rect
    f = 16.0 / TAILLE
    return [round(x * f, 4), round(y * f, 4), round((x + l) * f, 4), round((y + h) * f, 4)]


def peindre_face(atlas, rect, palette, lumiere, hasard, motif=None):
    """Peint une face de cube : teinte selon l'orientation, bord haut éclairé, bas dans l'ombre, contour sombre."""
    x0, y0, l, h = rect
    for j in range(h):
        for i in range(l):
            ton = 2 + lumiere
            if j == 0:
                ton += 1
            if j == h - 1 and h > 1:
                ton -= 1
            if i == 0 or i == l - 1:
                ton -= 0 if l <= 2 else 1 if (i == l - 1) else 0
            if motif:
                ton = motif(i, j, l, h, ton)
            elif hasard.random() < 0.12:
                ton += hasard.choice((-1, 1))
            atlas.image.putpixel((x0 + i, y0 + j), palette[max(0, min(4, ton))] + (255,))


def cube(atlas, de, vers, palette, hasard, rotation=None, motif=None):
    """Un cube peint sur ses six faces. Renvoie l'élément du modèle."""
    lx, ly, lz = (vers[k] - de[k] for k in range(3))
    faces = {}
    for nom, (l, h), lumiere in (("north", (lx, ly), 0), ("south", (lx, ly), 0), ("east", (lz, ly), -1),
                                 ("west", (lz, ly), -1), ("up", (lx, lz), 1), ("down", (lx, lz), -2)):
        rect = atlas.place(round(l * PX), round(h * PX))
        peindre_face(atlas, rect, palette, lumiere, hasard, motif if nom in ("north", "south", "east", "west") else None)
        faces[nom] = {"uv": uv(rect), "texture": "#0"}
    element = {"from": list(de), "to": list(vers), "faces": faces}
    if rotation:
        element["rotation"] = rotation
    return element


def motif_lame(i, j, l, h, ton):
    """Plat de la lame : gouttière sombre au milieu, bords plus clairs vers les tranchants."""
    milieu = (l - 1) / 2.0
    if l >= 4 and abs(i - milieu) <= 0.5 and 1 < j < h - 2:
        return 1
    if i == 0 or i == l - 1:
        return 3
    return 2 if (i + j * 3) % 11 else 3


def motif_tranchant(i, j, l, h, ton):
    """Tranchant : presque blanc, avec quelques reflets."""
    return 4 if j % 7 else 3


def motif_cuir(i, j, l, h, ton):
    """Lanières de cuir enroulées en biais."""
    bande = (i + j) % 4
    return 3 if bande == 0 else 1 if bande == 3 else 2


def motif_rivet(i, j, l, h, ton):
    """Garde : un rivet clair au centre."""
    if h >= 3 and abs(i - (l - 1) / 2.0) <= 0.5 and j == h // 2:
        return 4
    return ton


def modele(elements, texture):
    return {"credit": "vorace-item (exemple)", "texture_size": [TAILLE, TAILLE],
            "textures": {"0": texture, "particle": texture}, "elements": elements}


def lame(metal, forme):
    """Lame en volume, faite de cubes : un plat épais, deux tranchants plus fins, une pointe en gradins."""
    palette, hasard, atlas = METAUX[metal], random.Random(metal + forme), Atlas()
    elements = [cube(atlas, (7.0, 6.5, 7.3), (9.0, 7.5, 8.7), palette, hasard)]   # talon, contre la garde
    if forme == "droite":
        elements += [
            cube(atlas, (6.75, 7.5, 7.6), (9.25, 21.5, 8.4), palette, hasard, motif=motif_lame),           # plat
            cube(atlas, (6.25, 7.5, 7.8), (6.75, 20.5, 8.2), palette, hasard, motif=motif_tranchant),      # tranchants
            cube(atlas, (9.25, 7.5, 7.8), (9.75, 20.5, 8.2), palette, hasard, motif=motif_tranchant),
            cube(atlas, (7.0, 21.5, 7.65), (9.0, 23.0, 8.35), palette, hasard),                            # pointe en gradins
            cube(atlas, (7.5, 23.0, 7.7), (8.5, 24.5, 8.3), palette, hasard),
            cube(atlas, (7.75, 24.5, 7.8), (8.25, 25.5, 8.2), palette, hasard),
        ]
    else:
        # lame large : dos droit à gauche, tranchant à droite, bout taillé en biseau (gradins)
        elements += [
            cube(atlas, (6.0, 7.5, 7.55), (9.75, 19.0, 8.45), palette, hasard, motif=motif_lame),          # plat
            cube(atlas, (9.75, 7.5, 7.8), (10.25, 18.0, 8.2), palette, hasard, motif=motif_tranchant),     # tranchant
            cube(atlas, (6.0, 19.0, 7.6), (9.0, 20.5, 8.4), palette, hasard),                              # biseau en gradins
            cube(atlas, (6.0, 20.5, 7.65), (8.0, 22.0, 8.35), palette, hasard),
            cube(atlas, (6.0, 22.0, 7.7), (7.0, 23.5, 8.3), palette, hasard),
        ]
    return atlas, elements


def garde(metal, forme):
    palette, hasard, atlas = METAUX[metal], random.Random(metal + "garde" + forme), Atlas()
    if forme == "droite":
        elements = [cube(atlas, (3.5, 5.0, 7.2), (12.5, 6.5, 8.8), palette, hasard, motif=motif_rivet),
                    cube(atlas, (3.0, 4.75, 7.4), (4.0, 6.75, 8.6), palette, hasard),
                    cube(atlas, (12.0, 4.75, 7.4), (13.0, 6.75, 8.6), palette, hasard)]
    else:
        # croissant : un bloc central et deux bras relevés de 22,5°
        elements = [cube(atlas, (6.5, 5.0, 7.1), (9.5, 6.6, 8.9), palette, hasard, motif=motif_rivet),
                    cube(atlas, (9.3, 5.1, 7.3), (13.3, 6.4, 8.7), palette, hasard,
                         rotation={"angle": 22.5, "axis": "z", "origin": [9.3, 5.75, 8]}),
                    cube(atlas, (2.7, 5.1, 7.3), (6.7, 6.4, 8.7), palette, hasard,
                         rotation={"angle": -22.5, "axis": "z", "origin": [6.7, 5.75, 8]})]
    return atlas, elements


def poignee(metal):
    palette, hasard, atlas = METAUX[metal], random.Random(metal + "poignee"), Atlas()
    tourne = {"angle": 45, "axis": "y", "origin": [8, 1.5, 8]}
    elements = [cube(atlas, (7.3, -2.0, 7.3), (8.7, 5.0, 8.7), CUIR, hasard, rotation=tourne, motif=motif_cuir),
                cube(atlas, (7.1, 4.3, 7.1), (8.9, 5.0, 8.9), palette, hasard, rotation=tourne),     # bague du haut
                cube(atlas, (7.1, -2.0, 7.1), (8.9, -1.4, 8.9), palette, hasard, rotation=tourne)]   # bague du bas
    return atlas, elements


def pommeau(metal):
    palette, hasard, atlas = METAUX[metal], random.Random(metal + "pommeau"), Atlas()
    # en « + » : vu du dessus, deux cubes croisés donnent un rond en pixel art
    elements = [cube(atlas, (6.8, -4.2, 7.4), (9.2, -2.0, 8.6), palette, hasard),
                cube(atlas, (7.4, -4.2, 6.8), (8.6, -2.0, 9.2), palette, hasard),
                cube(atlas, (7.5, -4.7, 7.5), (8.5, -4.2, 8.5), palette, hasard)]
    return atlas, elements


def ecrire(metal, nom, atlas, elements, fichiers):
    chemin = "exemple/%s/%s" % (metal, nom)
    for dossier, ext in (("modeles", ".json"), ("textures", ".png")):
        os.makedirs(os.path.dirname(os.path.join(SORTIE, dossier, chemin)), exist_ok=True)
    with open(os.path.join(SORTIE, "modeles", chemin + ".json"), "w", encoding="utf-8") as f:
        json.dump(modele(elements, chemin), f, separators=(",", ":"))
    atlas.image.save(os.path.join(SORTIE, "textures", chemin + ".png"), optimize=True)
    fichiers += ["modeles/" + chemin + ".json", "textures/" + chemin + ".png"]


def main():
    fichiers = []
    for metal in METAUX:
        ecrire(metal, "lame_droite", *lame(metal, "droite"), fichiers)
        ecrire(metal, "lame_large", *lame(metal, "large"), fichiers)
        ecrire(metal, "garde_droite", *garde(metal, "droite"), fichiers)
        ecrire(metal, "garde_croissant", *garde(metal, "croissant"), fichiers)
        ecrire(metal, "poignee_cuir", *poignee(metal), fichiers)
        ecrire(metal, "pommeau_rond", *pommeau(metal), fichiers)
    # liste des fichiers d'exemple, lue par le plugin pour les installer au premier lancement
    with open(os.path.join(RACINE, "ressources.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(sorted(fichiers)) + "\n")
    print("%d fichiers écrits dans %s" % (len(fichiers), os.path.normpath(SORTIE)))


if __name__ == "__main__":
    main()
