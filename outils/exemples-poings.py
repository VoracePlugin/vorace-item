"""Icônes des armes de poing d'exemple (Pugiliste de Babel) : 16 × 16, couleurs du jeu ternes, aplats, sans contour
noir. En main, ces armes sont invisibles (les poings du joueur portent le coup) : l'icône ne sert que dans
l'inventaire, posée par terre et dans un cadre.

Usage : python3 outils/exemples-poings.py [dossier d'aperçu]"""
import os
import sys
from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
SORTIE = os.path.join(ICI, "..", "src", "main", "resources", "exemples", "ressources", "textures", "exemple", "poing")

# 5 tons par matière, du plus sombre au plus clair
P = {
    # lin des bandes
    "a": (96, 82, 60), "b": (140, 122, 90), "c": (182, 164, 126), "d": (214, 200, 166), "e": (236, 228, 206),
    # fer
    "1": (66, 66, 70), "2": (108, 108, 112), "3": (150, 150, 154), "4": (196, 196, 198), "5": (228, 228, 228),
    # cuir
    "l": (84, 46, 28), "m": (118, 66, 38), "n": (150, 88, 52), "o": (178, 112, 70), "p": (204, 140, 96),
    # laiton terne
    "q": (110, 80, 30), "r": (150, 110, 44), "s": (190, 148, 64), "t": (222, 184, 92), "u": (244, 218, 140),
    # encre rouge des bandes rituelles
    "x": (120, 32, 30), "y": (166, 52, 42),
}

ICONES = {
    # rouleau de bandes de lin, l'extrémité qui pend
    "bandes_de_combat": [
        "................",
        "................",
        "......bccb......",
        "....bcdddccb....",
        "...bcdeeeddcb...",
        "...cdeebbeedc...",
        "..bcdeb..bedcb..",
        "..bcdeb..bedcb..",
        "..bcddebbeddcb..",
        "...bcddeeddcba..",
        "...abccddccbaa..",
        "....aabbbbaab...",
        "......aaaa.bc...",
        "...........bd...",
        "............cb..",
        ".............b..",
    ],
    # gant de fer : doigts articulés en haut, plaque du dos, manchette de cuir
    "gantelets": [
        "................",
        ".....4.4.4......",
        "....4535354.....",
        "....3434343.....",
        "....2323232.....",
        "...455555543....",
        "...45444443.....",
        "...44222223.....",
        "...3433333322...",
        "...33222222334..",
        "....3222222333..",
        "....mnnnnnnm....",
        "....mooooonm....",
        "....lmnnnnml....",
        ".....lllllll....",
        "................",
    ],
    # ceste : quatre anneaux de laiton serrés côte à côte et la barre tenue dans la paume
    "cestes": [
        "................",
        "................",
        "................",
        "................",
        ".ut..ut..ut..ut.",
        "t..st..st..st..s",
        "t..st..st..st..s",
        "s..rs..rs..rs..r",
        ".rsssrrssrrssrr.",
        ".tuuuuuuuuuuuts.",
        ".tsssssssssssssr",
        "..rrrrrrrrrrrrq.",
        "................",
        "................",
        "................",
        "................",
    ],
    # brassard de garde : manchon de cuir, deux plaques de fer, sangles de cuir (dessiné plus bas, en biais)
    "brassards_de_garde": None,
    # bandes rituelles : une bande à demi déroulée couverte d'inscriptions, un talisman de papier
    "bandes_rituelles": None,
}


def biais(depart, arrivee, rayon, couleur):
    """Grille 16 × 16 d'une forme en biais (de bas-gauche à haut-droite) : couleur(t, d) donne la lettre d'un pixel
    selon sa place le long de la forme (t de 0 à 1) et de part et d'autre de son axe (d de -1 à 1), ou None."""
    (x0, y0), (x1, y1) = depart, arrivee
    vx, vy = x1 - x0, y1 - y0
    long2 = vx * vx + vy * vy
    g = [["."] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - x0, y + 0.5 - y0
            t = (px * vx + py * vy) / long2
            if t < 0 or t > 1:
                continue
            d = (px * vy - py * vx) / (long2 ** 0.5) / rayon
            if abs(d) > 1:
                continue
            c = couleur(t, d)
            if c:
                g[y][x] = c
    return ["".join(r) for r in g]


def brassard(t, d):
    """Plaques de fer (claires du côté éclairé), sangles et bords de cuir."""
    if t < 0.1 or t > 0.92:
        return "n" if d < -0.2 else "m" if d < 0.4 else "l"
    if 0.3 < t < 0.38 or 0.64 < t < 0.72:
        return "o" if d < -0.2 else "n" if d < 0.4 else "m"
    if 0.45 < t < 0.5:
        return "2"  # jointure entre les deux plaques
    return "5" if d < -0.45 else "4" if d < 0.15 else "3" if d < 0.6 else "2"


def rituelle(t, d):
    """Bande de lin : un ton clair et un sombre, inscriptions rouges tous les quelques pixels."""
    if 0.18 < t < 0.9 and abs(d) < 0.5 and int(t * 22) % 3 == 0:
        return "x" if d > 0 else "y"
    return "e" if d < -0.3 else "d" if d < 0.3 else "c"


def image(nom):
    lignes = ICONES[nom]
    assert len(lignes) == 16 and all(len(l) == 16 for l in lignes), nom
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, l in enumerate(lignes):
        for x, c in enumerate(l):
            if c != ".":
                im.putpixel((x, y), P[c] + (255,))
    return im


def completer():
    ICONES["brassards_de_garde"] = biais((2.5, 13.5), (13.5, 2.5), 2.6, brassard)
    r = [list(l) for l in biais((2.0, 14.0), (10.5, 5.5), 1.6, rituelle)]
    # le rouleau en haut à droite, et le talisman qui pend de la bande
    rouleau = ["..bccb.", ".bcddcb", "bcdaadc", "bcdaadc", ".bcddcb", "..bcca."]
    for j, l in enumerate(rouleau):
        for i, c in enumerate(l):
            if c != ".":
                r[2 + j][9 + i] = c
    talisman = ["e", "y", "e", "d"]
    for j, c in enumerate(talisman):
        r[10 + j][7] = c
    r[9][7] = "a"
    ICONES["bandes_rituelles"] = ["".join(l) for l in r]


def main():
    completer()
    os.makedirs(SORTIE, exist_ok=True)
    apercu = sys.argv[1] if len(sys.argv) > 1 else None
    planche = Image.new("RGBA", (16 * 12 * len(ICONES) + 12 * (len(ICONES) + 1), 16 * 12 + 24), (139, 139, 139, 255))
    for i, nom in enumerate(ICONES):
        im = image(nom)
        couleurs = len({im.getpixel((x, y)) for x in range(16) for y in range(16)} - {(0, 0, 0, 0)})
        assert couleurs <= 12, f"{nom} : {couleurs} couleurs"
        im.save(os.path.join(SORTIE, nom + ".png"))
        planche.alpha_composite(im.resize((192, 192), Image.NEAREST), (12 + i * 204, 12))
    if apercu:
        planche.save(os.path.join(apercu, "poings.png"))
    print(len(ICONES), "icônes écrites dans", os.path.normpath(SORTIE))


if __name__ == "__main__":
    main()
