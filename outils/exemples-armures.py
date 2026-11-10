#!/usr/bin/env python3
"""Dessine les armures d'exemple (src/main/resources/exemples/ressources) : trois ensembles, un par matériau.

  cuivre : l'hoplite de cuivre. Cuirasse musclée, épaulières rondes, ptéryges de cuir sur tunique rouge, cnémides,
           casque corinthien à joues et nasal, haute crête de crin rouge.
  fer    : le chevalier de la Tour. Haubert de mailles, cotte d'armes rouge à la tour de Babel, épaulières à lames,
           cubitières et genouillères à ailette, grand heaume à fentes, croix de renfort et panache rouge.
  acier  : le gardien d'acier noir. Plates d'acier bruni bordées d'or, grandes épaulières étagées, braconnière,
           drap bleu nuit, heaume à bec et ailes d'acier et d'or.

Comme sur les serveurs : le casque est un vrai modèle 3D porté sur la tête (objet de la tête sans dessin d'armure),
le reste est le dessin d'armure du jeu, en haute définition (4 fois le jeu : 256 x 128).

Pour chaque matériau :
  equipement/exemple/armure_<m>/humanoid.png            plastron, bras, bottes (256 x 128)
  equipement/exemple/armure_<m>/humanoid_leggings.png   jambières (256 x 128)
  modeles/exemple/armure/casque_<m>.json                casque en 3D (porté, en main, dans l'inventaire)
  textures/exemple/armure/casque_<m>.png                sa texture
  textures/exemple/armure/<m>_<pièce>.png               icônes (32 x 32) du plastron, des jambières, des bottes

Peinture : quelques tons par matière (pas de bruit), lumière en haut à gauche, relief calculé sur la forme (cuirasse
musclée, lames qui se recouvrent, rivets), pas de contour noir.
Usage : python3 outils/exemples-armures.py [aperçu.png]
"""
import json
import math
import os
import sys
import zlib

from PIL import Image

RACINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "exemples")
SORTIE = os.path.join(RACINE, "ressources")
K = 4                                     # texels par pixel du jeu


def rgb(x):
    return tuple(int(x[i:i + 2], 16) for i in (1, 3, 5))


def rampe(*c):
    return [rgb(x) for x in c]


# palettes, du plus sombre au plus clair
CUIVRE = rampe("#3e1a12", "#62291a", "#8a3d22", "#b4552c", "#d6743c", "#ee9a5a", "#ffc58c")
VERT = rampe("#1f4a42", "#2e6a5a", "#438a72", "#62a98c")
ROUGE = rampe("#3c0f12", "#5e161a", "#821f22", "#a62c2a", "#c84236", "#e2634c")
CUIR = rampe("#24140d", "#3a2216", "#53321f", "#6e452a", "#8c5c38", "#a8784c")
LIN = rampe("#6e6252", "#958772", "#b9ab90", "#d8ccb0", "#efe6cc")
FER = rampe("#2c2e34", "#44474f", "#62666f", "#858a94", "#a9aeb8", "#cdd2da", "#eef1f5")
MAILLE = rampe("#1c1d21", "#34363c", "#53565e", "#767a83", "#9da1aa", "#c4c8cf")
LAITON = rampe("#4a3414", "#6e5020", "#94702e", "#b8923e", "#d6b45a", "#efd68a")
ACIER = rampe("#111319", "#1b1e27", "#272c39", "#363d4f", "#4a5368", "#646f88", "#8a96b0", "#b8c2d8")
OR = rampe("#4e300c", "#7a4c12", "#a56d1a", "#cc9226", "#e8b840", "#f8d870", "#fff2b4")
NUIT = rampe("#0e0f22", "#171a38", "#22284f", "#2f3868", "#414d86", "#5a68a4")
BLANC = rampe("#8a8070", "#b0a690", "#d2c9b0", "#ece4cc", "#fbf6e6")


def q(pal, L):
    L = max(0.0, min(1.0, L))
    return pal[min(len(pal) - 1, int(L * len(pal)))] + (255,)


def h(*v):
    return zlib.crc32(repr(v).encode()) & 0xFFFFFFFF


LUMIERE = (-0.55, -0.62, 0.56)            # vient d'en haut à gauche, vers l'avant


def eclairage(nx, ny, nz=1.0):
    n = math.sqrt(nx * nx + ny * ny + nz * nz)
    d = (nx * LUMIERE[0] + ny * LUMIERE[1] + nz * LUMIERE[2]) / n
    return max(0.0, d)


def relief(fn, X, Y, e=0.06, amb=0.18):
    """Lumière d'un relief z = fn(X, Y) : pente calculée, puis éclairée d'en haut à gauche."""
    dx = (fn(X + e, Y) - fn(X - e, Y)) / (2 * e)
    dy = (fn(X, Y + e) - fn(X, Y - e)) / (2 * e)
    return amb + (1 - amb) * eclairage(-dx, -dy, 1.0)


def bosse(X, Y, cx, cy, rx, ry, a=1.0):
    d = ((X - cx) / rx) ** 2 + ((Y - cy) / ry) ** 2
    return a * math.exp(-d * 2.2)


# ------------------------------------------------------------------ le dessin d'armure (disposition du jeu)
BOITES = {"tete": (0, 0, 8, 8, 8), "corps": (16, 16, 8, 12, 4), "bras": (40, 16, 4, 12, 4), "jambe": (0, 16, 4, 12, 4)}


def faces(b):
    u, v, w, hh, d = b
    return {"dessus": (u + d, v, w, d), "dessous": (u + d + w, v, w, d), "droite": (u, v + d, d, hh),
            "avant": (u + d, v + d, w, hh), "gauche": (u + d + w, v + d, d, hh), "arriere": (u + 2 * d + w, v + d, w, hh)}


class P:
    """Un texel à peindre : face, position (X, Y en pixels du jeu, à virgule), taille de la face, angle autour."""
    __slots__ = ("face", "X", "Y", "W", "H", "x", "y", "t", "cote")

    def __init__(self, face, x, y, W, H):
        self.face, self.x, self.y, self.W, self.H = face, x, y, W, H
        self.X, self.Y = (x + 0.5) / K, (y + 0.5) / K
        # t : position autour de la boîte, de -1 (bord droit de la face avant) à 1 (bord gauche) ; ±2 : de dos
        if face == "avant":
            self.t = (self.X / W) * 2 - 1
        elif face == "arriere":
            self.t = 2.0
        elif face == "droite":
            self.t = -1.4
        elif face == "gauche":
            self.t = 1.4
        else:
            self.t = 0.0
        self.cote = face in ("droite", "gauche")


def peindre(img, boite, fn, faces_ok=None, lignes=None):
    for nom, (u, v, w, hh) in faces(BOITES[boite]).items():
        if faces_ok and nom not in faces_ok:
            continue
        for y in range(hh * K):
            for x in range(w * K):
                p = P(nom, x, y, w, hh)
                if lignes and nom not in ("dessus", "dessous") and not (lignes[0] <= p.Y < lignes[1]):
                    continue
                c = fn(p)
                if c is not None:
                    img.putpixel((u * K + x, v * K + y), c)


def cylindre(p, fort=0.22):
    """Lumière d'une surface ronde autour de la boîte (claire à gauche de l'avant, sombre au dos)."""
    t = p.t
    if abs(t) <= 1:
        return 1.0 - fort * (0.5 + 0.5 * math.sin((t + 0.35) * 1.4)) * 1.0
    return 1.0 - fort * (1.2 if p.face == "arriere" else (1.0 if t < 0 else 1.3))


def rivet(p, cx, cy, r=0.32):
    """Tête de rivet : None si on n'est pas dessus, sinon une lumière (claire en haut à gauche)."""
    dx, dy = p.X - cx, p.Y - cy
    if dx * dx + dy * dy > r * r:
        return None
    return 0.95 if dx + dy < -0.1 else (0.25 if dx + dy > 0.15 else 0.65)


def lame(p, y0, y1, L0=0.82, L1=0.38):
    """Une lame (plaque horizontale) entre y0 et y1 : bord haut clair, puis plus sombre vers le bas (elle s'incline)."""
    f = (p.Y - y0) / max(0.01, y1 - y0)
    if f < 0.08 or p.Y - y0 < 0.26:
        return L0 + 0.15
    if y1 - p.Y < 0.26:
        return 0.12                       # ombre sous la lame, sur la suivante
    return L0 + (L1 - L0) * f


def maille(p, assomb=0):
    """Cotte de mailles : rangs d'anneaux de 4 texels, décalés d'un rang à l'autre."""
    rang = p.y // 2
    xx = (p.x + (rang % 2) * 2) % 4
    yy = p.y % 2
    tons = ((1, 4, 3, 1), (3, 2, 1, 2))[yy][xx]
    return MAILLE[max(0, min(5, tons - (1 if p.cote else 0) - assomb - (1 if p.face == "arriere" else 0)))] + (255,)


def drap(pal, p, plis=1.0, base=0.6):
    """Étoffe : plis verticaux doux (bandes de lumière), sans bruit."""
    L = base + 0.16 * math.sin(p.X * 2.3 + (0.9 if p.face == "arriere" else 0)) * plis + 0.08 * math.sin(p.X * 5.1) * plis
    if p.cote:
        L -= 0.12
    return q(pal, L - 0.04 * p.Y / 12)


def bordure(p, y, ep=0.5, pal=OR):
    """Filet de métal de ep pixels sous la ligne y : clair en haut, sombre en bas."""
    if y <= p.Y < y + ep:
        f = (p.Y - y) / ep
        return q(pal, 0.95 - 0.7 * f - (0.12 if p.cote else 0))
    return None


# ---------------------------------------------------------------- cuivre : l'hoplite
def cuirasse_musclee(X, Y):
    z = 0.0
    z += bosse(X, Y, 2.55, 2.55, 2.1, 1.7, 0.9) + bosse(X, Y, 5.45, 2.55, 2.1, 1.7, 0.9)    # pectoraux
    for cy in (4.7, 5.85, 7.0):
        for cx in (3.15, 4.85):
            z += bosse(X, Y, cx, cy, 0.75, 0.55, 0.35)                                     # abdominaux
    z -= 0.35 * math.exp(-((X - 4) / 0.35) ** 2) * (1 if Y > 1.5 else 0.3)                  # ligne du milieu
    z += 0.6 * (1 - ((X - 4) / 4.4) ** 2)                                                    # le torse s'arrondit
    return z


def cuivre_corps(p):
    if p.face == "dessus":
        L = 0.75 if p.Y > 3 or p.Y < 0.6 else 0.55
        return q(CUIVRE, L - (0.15 if p.X < 1 or p.X > 7 else 0))
    if p.Y < 0.6:                                                    # bord roulé de l'encolure
        return q(CUIVRE, 0.92 if p.Y < 0.3 else 0.35)
    if p.Y < 8.3:
        if p.face == "avant":
            L = relief(cuirasse_musclee, p.X, p.Y) * cylindre(p, 0.12)
            if (h(p.x // 3, p.y // 3, 7) % 37 == 0) and L < 0.45:
                return q(VERT, 0.5)                                  # vert-de-gris dans les creux
            return q(CUIVRE, L)
        if p.face == "arriere":
            z = lambda X, Y: bosse(X, Y, 2.4, 3.2, 1.8, 2.4, 0.6) + bosse(X, Y, 5.6, 3.2, 1.8, 2.4, 0.6) + 0.5 * (1 - ((X - 4) / 4.4) ** 2)
            return q(CUIVRE, relief(z, p.X, p.Y) * 0.82)
        # flancs : la charnière et deux courroies
        if 2.2 < p.Y < 3.0 or 6.0 < p.Y < 6.8:
            r = rivet(p, 2.0, (2.6 if p.Y < 4 else 6.4), 0.38)
            if r is not None:
                return q(LAITON, r)
            return q(CUIR, 0.55 if p.Y % 3 < 2.6 else 0.3)
        return q(CUIVRE, 0.45 - 0.1 * (p.X / 4))
    if p.Y < 8.9:                                                    # bord roulé du bas
        return q(CUIVRE, 0.9 if p.Y < 8.55 else 0.3)
    # ptéryges : lanières de cuir sur la tunique rouge, un clou de cuivre en haut de chacune
    larg = 1.0
    i = int(p.X // larg) if not p.cote else int(p.X // larg) + 9
    fx = (p.X % larg) / larg
    bout = 11.6 + (0.25 if i % 2 else 0)
    if fx < 0.18 or p.Y > bout:
        return drap(ROUGE, p, base=0.45) if p.Y < 12 else None
    if 9.0 < p.Y < 9.6 and 0.3 < fx < 0.8:
        return q(CUIVRE, 0.9 if fx < 0.55 and p.Y < 9.3 else 0.5)
    L = 0.62 - 0.25 * abs(fx - 0.5) * 2 - (0.1 if p.cote else 0) - (0.08 if p.face == "arriere" else 0)
    if bout - p.Y < 0.3:
        L -= 0.22
    return q(CUIR, L)


def cuivre_bras(p):
    # épaulière ronde (un disque bombé vu de côté), avec un bouton au centre
    if p.face == "dessus":
        d = math.hypot(p.X - 2, p.Y - 2) / 2.2
        return q(CUIVRE, 0.95 - 0.55 * d) if not (d < 0.22) else q(LAITON, 0.9)
    if p.Y < 3.4:
        X = p.X if not p.cote else p.X
        prof = (1 - (p.Y / 3.4) ** 1.6)
        L = 0.35 + 0.55 * prof * cylindre(p, 0.3)
        if 3.0 < p.Y:
            L = 0.22
        if p.Y < 0.35:
            L = 0.95
        if p.face == "gauche" or p.face == "droite":
            r = rivet(p, 2.0, 1.4, 0.4)
            if r is not None:
                return q(LAITON, r)
        return q(CUIVRE, L)
    if p.Y < 5.4:                                                    # ptéryges d'épaule
        fx = (p.X % 1.0)
        if fx < 0.2:
            return drap(ROUGE, p, base=0.42)
        return q(CUIR, 0.6 - 0.25 * abs(fx - 0.55) * 2 - (p.Y - 3.4) * 0.06 - (0.1 if p.cote else 0))
    if p.Y < 7.6:
        return drap(ROUGE, p, base=0.62)                             # manche de la tunique
    if p.Y < 8.0:
        return q(CUIR, 0.5)
    if p.Y < 11.3:                                                   # brassard de cuivre gravé
        L = 0.78 * cylindre(p, 0.35)
        if abs(p.Y - 9.65) < 0.14:
            L -= 0.3
        if p.Y < 8.25:
            L = 0.95
        if p.Y > 11.05:
            L = 0.3
        return q(CUIVRE, L)
    return q(CUIR, 0.45 - (0.1 if p.cote else 0))


def meandre(X, Y):
    """Grecque (méandre) dans une bande de 1 pixel de haut : vrai si le texel est sur le filet."""
    xx = int((X * K) % 8)
    yy = int((Y * K) % 4)
    motif = ("########", "#.....#.", "#.###.#.", "###.###.")
    return motif[yy][xx] == "#"


def cuivre_jambe_bas(p):                     # jambières (calque des jambières) : tunique et bandes de lin
    if p.face == "dessus":
        return drap(ROUGE, p, base=0.5)
    if p.Y < 2.6:
        if 1.6 < p.Y < 2.6:
            return q(CUIVRE, 0.75 if meandre(p.X + (2 if p.cote else 0), p.Y - 1.6) else 0.15) if p.Y < 2.6 else None
        return drap(ROUGE, p, base=0.6)
    # bandes de lin enroulées en biais
    k = (p.X * 0.8 + p.Y * 1.6 + (1.3 if p.face == "arriere" else 0)) % 1.6
    L = 0.7 - 0.35 * (k / 1.6) - (0.12 if p.cote else 0)
    return q(LIN, L)


def cuivre_ceinture(p):
    if p.Y < 9.8:
        return q(CUIR, 0.55 if p.Y > 8.3 else 0.3)
    return drap(ROUGE, p, base=0.55)


def cnemide(X, Y):
    return bosse(X, Y, 1.6, 7.6, 1.6, 1.4, 0.7) + 0.4 * (1 - ((X - 2) / 2.3) ** 2) + bosse(X, Y, 2.0, 5.9, 1.3, 0.6, 0.4)


def cuivre_botte(p):
    if p.face == "dessous":
        return q(CUIR, 0.2)
    if p.Y < 5.3:
        return None
    if p.Y < 10.3:                                                   # cnémide : mollet et genou modelés
        if p.Y < 5.6:
            return q(CUIVRE, 0.95)
        if p.face == "avant":
            return q(CUIVRE, relief(cnemide, p.X, p.Y) * 0.98)
        if p.face == "arriere":                                      # lacée derrière
            if abs(p.X - 2) < 0.9 and int(p.Y * 2) % 2 == 0:
                return q(CUIR, 0.6 if abs(p.X - 2) < 0.5 else 0.3)
            return q(CUIVRE, 0.38)
        return q(CUIVRE, 0.5 - 0.06 * (p.Y - 6))
    if p.Y < 10.6:
        return q(CUIVRE, 0.25)
    # sandale-botte : lanières croisées, bout de cuivre
    if p.face == "avant" and p.Y > 11.2:
        return q(CUIVRE, 0.85 if p.Y < 11.5 else 0.5)
    k = (p.X + p.Y * 1.0) % 1.3 if p.face != "arriere" else (p.X - p.Y) % 1.3
    return q(CUIR, (0.62 if k < 0.65 else 0.36) - (0.1 if p.cote else 0))


# ---------------------------------------------------------------- fer : le chevalier de la Tour
TOUR = [                                  # la tour de Babel, 12 x 16 texels
    "....#..#....",
    "....####....",
    "...######...",
    "...##..##...",
    "...######...",
    "..########..",
    "..##.##.##..",
    "..########..",
    ".##########.",
    ".###.##.###.",
    ".##########.",
    "############",
    "####....####",
    "###......###",
    "###......###",
    "############"]


def fer_corps(p):
    if p.face == "dessus":
        if p.Y < 1.0 or p.Y > 3.0:
            return maille(p)
        return q(FER, 0.75 if p.X < 4 else 0.55)
    # col de fer riveté
    if p.Y < 1.1 and p.face in ("avant", "arriere"):
        if p.Y > 0.85:
            return q(FER, 0.15)
        r = rivet(p, 1.2, 0.5, 0.3) or rivet(p, 6.8, 0.5, 0.3)
        if r is not None:
            return q(FER, r)
        return q(FER, (0.9 if p.Y < 0.3 else 0.62) * cylindre(p, 0.2))
    # cotte d'armes rouge devant et derrière
    if p.face in ("avant", "arriere") and 1.0 <= p.X < 7.0:
        if 8.0 <= p.Y < 9.0:                                         # ceinture
            if p.face == "avant" and 3.4 <= p.X < 4.6:
                inner = 3.75 <= p.X < 4.25 and 8.25 <= p.Y < 8.75
                return q(FER, 0.3 if inner else (0.95 if p.X < 4 and p.Y < 8.5 else 0.6))
            return q(CUIR, 0.62 if p.Y < 8.25 else (0.25 if p.Y > 8.75 else 0.45))
        dent = p.Y > 11.3 and int(p.X * 2) % 2 == 1                  # bas découpé
        if dent:
            return maille(p)
        bord = p.X < 1.25 or p.X >= 6.75
        if p.face == "avant" and 1.5 <= p.Y < 5.5:
            tx, ty = int((p.X - 2.5) * K), int((p.Y - 1.5) * K)
            if 0 <= tx < 12 and 0 <= ty < 16 and TOUR[ty][tx] == "#":
                L = 0.85 - 0.3 * (tx / 12) - 0.15 * (ty / 16)
                return q(BLANC, L)
        if bord:
            return q(ROUGE, 0.25)
        if p.face == "avant" and p.Y >= 9:
            return drap(ROUGE, p, plis=1.3, base=0.55)
        return drap(ROUGE, p, base=0.62 if p.face == "avant" else 0.48)
    return maille(p)


def fer_bras(p):
    if p.face == "dessus":
        d = math.hypot(p.X - 2, p.Y - 2) / 2.3
        return q(FER, 0.92 - 0.45 * d)
    if p.Y < 4.0:                                                    # épaulière : trois lames
        for (y0, y1) in ((0, 1.6), (1.4, 2.8), (2.6, 4.0)):
            if y0 <= p.Y < y1 and not (p.Y >= y1 - 0.2 and y1 < 4.0 and False):
                pass
        if p.Y < 1.6:
            L = lame(p, 0, 1.6, 0.9, 0.55)
        elif p.Y < 2.8:
            L = lame(p, 1.6, 2.8, 0.82, 0.48)
        else:
            L = lame(p, 2.8, 4.0, 0.75, 0.42)
        r = None
        if p.cote:
            r = rivet(p, 0.7, 0.8) or rivet(p, 3.3, 0.8) or rivet(p, 2.0, 2.1) or rivet(p, 2.0, 3.35)
        if r is not None:
            return q(FER, r)
        return q(FER, L * cylindre(p, 0.3))
    if p.Y < 6.3:
        return maille(p)
    if p.Y < 7.8:                                                    # cubitière, ailette en dehors
        if p.face == "gauche":
            d = math.hypot(p.X - 2, p.Y - 7.05) / 1.25
            if d < 1:
                return q(FER, 0.95 - 0.6 * d if d > 0.25 else 0.4)
        L = 0.85 if p.Y < 6.6 else (0.25 if p.Y > 7.55 else 0.6)
        return q(FER, L * cylindre(p, 0.25))
    if p.Y < 11.0:                                                   # canon d'avant-bras, sangles de cuir
        if abs(p.Y - 9.4) < 0.3:
            if p.face == "avant" and abs(p.X - 2) < 0.45:
                return q(FER, 0.9)
            return q(CUIR, 0.5)
        L = 0.78 * cylindre(p, 0.35)
        if p.Y < 8.05:
            L = 0.95
        if p.Y > 10.75:
            L = 0.28
        return q(FER, L)
    # gantelet : petites lames
    return q(FER, lame(p, 11.0 + (0.5 if p.Y >= 11.5 else 0), 11.5 + (0.5 if p.Y >= 11.5 else 0), 0.75, 0.4) * cylindre(p, 0.3))


def fer_ceinture(p):
    if 8.2 <= p.Y < 9.0:
        return q(CUIR, 0.5)
    return maille(p)


def fer_jambe_bas(p):
    if p.face == "dessus":
        return maille(p)
    # cuissot devant la cuisse, genouillère à ailette
    if p.face == "avant" and 1.0 <= p.Y < 5.6 and 0.4 <= p.X < 3.6:
        if p.Y < 1.35:
            return q(FER, 0.92)
        r = rivet(p, 0.85, 1.8) or rivet(p, 3.15, 1.8)
        if r is not None:
            return q(FER, r)
        z = lambda X, Y: 0.8 * (1 - ((X - 2) / 2.2) ** 2)
        return q(FER, relief(z, p.X, p.Y) * (0.95 - 0.04 * (p.Y - 1)))
    if 5.6 <= p.Y < 7.3:
        if p.face == "droite":
            d = math.hypot(p.X - 2, p.Y - 6.45) / 1.2
            if d < 1:
                return q(FER, 0.95 - 0.6 * d if d > 0.25 else 0.4)
        z = lambda X, Y: bosse(X, Y, 2, 6.45, 1.6, 0.9, 1.0)
        L = relief(z, p.X if not p.cote else 2, p.Y) if p.face == "avant" else 0.5
        if p.Y > 7.05:
            L = 0.2
        return q(FER, L)
    return maille(p)


def fer_botte(p):
    if p.face == "dessous":
        return q(CUIR, 0.2)
    if p.Y < 7.2:
        return None
    if p.Y < 10.4:                                                   # grève à arête
        if p.Y < 7.5:
            return q(FER, 0.95)
        if p.face == "avant":
            d = p.X - 2
            L = 0.9 if -0.25 < d < 0 else (0.42 if 0 <= d < 0.25 else 0.72 - 0.12 * abs(d))
            return q(FER, L)
        if p.face == "arriere":
            if abs(p.Y - 8.6) < 0.25 or abs(p.Y - 9.8) < 0.25:
                return q(CUIR, 0.45)
            return maille(p)
        return q(FER, 0.55 - 0.05 * (p.Y - 7.5))
    # soleret à lames
    k = (p.Y - 10.4) % 0.55
    L = 0.85 if k < 0.14 else (0.25 if k > 0.45 else 0.55)
    if p.face == "arriere":
        return q(CUIR, 0.4)
    return q(FER, L - (0.1 if p.cote else 0))


# ---------------------------------------------------------------- acier : le gardien
def filigrane(p, x0, y0, x1, y1):
    """Rinceau d'or gravé dans un cadre : une vague et ses volutes."""
    if not (x0 <= p.X < x1 and y0 <= p.Y < y1):
        return False
    u = (p.X - x0) / (x1 - x0)
    v = (p.Y - y0) / (y1 - y0)
    vague = 0.5 + 0.32 * math.sin(u * math.pi * 3)
    if abs(v - vague) < 0.09:
        return True
    for k in range(3):
        cx = (k + 0.25) / 3
        cy = 0.5 + 0.32 * math.sin(cx * math.pi * 3) + (-0.28 if k % 2 == 0 else 0.28)
        d = math.hypot((u - cx) * (x1 - x0), (v - cy) * (y1 - y0))
        if 0.28 < d < 0.48 and (u - cx) * (1 if k % 2 == 0 else -1) < 0.15:
            return True
    return False


def plaque_acier(p, L):
    return q(ACIER, L)


def acier_corps(p):
    if p.face == "dessus":
        if p.Y < 0.5 or p.Y > 3.5:
            return q(OR, 0.75)
        return q(ACIER, 0.72 if p.X < 4 else 0.55)
    # gorgerin : deux lames bordées d'or
    if p.Y < 1.3:
        b = bordure(p, 0.0, 0.3) or bordure(p, 0.85, 0.25)
        if b:
            return b
        return q(ACIER, (0.8 if p.Y < 0.6 else 0.6) * cylindre(p, 0.25))
    if p.Y < 7.3:
        if p.face == "avant":
            if 6.95 <= p.Y:
                return q(OR, 0.8 if p.Y < 7.12 else 0.4)
            if p.X < 0.35 or p.X > 7.65:
                return q(OR, 0.7 if p.X < 1 else 0.45)
            d = p.X - 4
            if -0.3 < d < 0:                                         # arête
                return q(ACIER, 0.95)
            if 0 <= d < 0.3:
                return q(ACIER, 0.35)
            # soleil gravé sous le gorgerin
            r = math.hypot(p.X - 4, p.Y - 2.6)
            if r < 1.25 and abs(d) > 0.3:
                a = math.atan2(p.Y - 2.6, p.X - 4)
                if r < 0.55 or (int((a + math.pi) / (math.pi / 6)) % 2 == 0 and r < 1.25 and p.Y < 3.4):
                    return q(OR, 0.9 - 0.4 * r)
            if filigrane(p, 0.6, 4.4, 3.6, 6.6) or filigrane(p, 4.4, 4.4, 7.4, 6.6):
                return q(OR, 0.75 - 0.08 * (p.Y - 4.4))
            z = lambda X, Y: 1.2 * (1 - ((X - 4) / 4.6) ** 2) - 0.25 * abs(X - 4) + bosse(X, Y, 4, 3.8, 3.6, 3.2, 0.4)
            return q(ACIER, relief(z, p.X, p.Y, amb=0.12) * (1.0 - 0.03 * p.Y))
        if p.face == "arriere":
            if p.Y > 6.95:
                return q(OR, 0.5)
            if abs(p.X - 4) < 0.25:
                return q(ACIER, 0.62 if p.X < 4 else 0.3)
            return q(ACIER, 0.45 - 0.03 * p.Y)
        return q(ACIER, 0.42 - 0.02 * p.Y)
    # braconnière (lames) puis tassettes et drap bleu nuit
    if p.Y < 9.4:
        k = (p.Y - 7.3) % 0.7
        if k < 0.16:
            return q(OR, 0.82 if not p.cote else 0.6)
        return q(ACIER, (0.62 - 0.4 * (k / 0.7)) * cylindre(p, 0.25))
    if p.face in ("avant", "arriere") and 2.4 <= p.X < 5.6:
        if p.X < 2.65 or p.X >= 5.35 or p.Y > 11.65:
            return q(OR, 0.7 if p.X < 4 else 0.5)
        if p.face == "avant" and 10.1 < p.Y < 11.1 and abs(p.X - 4) < 0.5:
            return q(OR, 0.85 if p.Y < 10.6 else 0.6)
        return drap(NUIT, p, plis=0.8, base=0.6)
    # tassettes
    if p.Y < 9.6 or (11.7 < p.Y):
        return q(OR, 0.75 if p.Y < 9.6 else 0.45)
    k = (p.Y - 9.6) % 1.05
    return q(ACIER, (0.72 - 0.42 * (k / 1.05)) * (0.85 if p.cote else 1.0))


def acier_bras(p):
    if p.face == "dessus":
        d = math.hypot(p.X - 2, p.Y - 2) / 2.3
        if d > 0.86:
            return q(OR, 0.8)
        return q(ACIER, 0.95 - 0.5 * d)
    if p.Y < 4.6:                                                    # grande épaulière : dôme et trois lames
        bornes = ((0, 2.1), (2.1, 2.95), (2.95, 3.8), (3.8, 4.6))
        for i, (y0, y1) in enumerate(bornes):
            if y0 <= p.Y < y1:
                if y1 - p.Y < 0.22:
                    return q(OR, 0.82 if y1 - p.Y > 0.11 else 0.45)
                L = (0.9 - 0.12 * i) - 0.42 * (p.Y - y0) / (y1 - y0)
                if i == 0:
                    L = 0.95 - 0.55 * (p.Y / 2.1) ** 1.3
                    if p.cote and filigrane(p, 0.5, 0.3, 3.5, 1.7):
                        return q(OR, 0.82)
                return q(ACIER, L * cylindre(p, 0.3))
    if p.Y < 6.2:
        return q(ACIER, 0.62 * cylindre(p, 0.35) - (0.2 if p.Y > 5.95 else 0))
    if p.Y < 7.7:                                                    # cubitière à ailette, bord d'or
        if p.face == "gauche":
            d = math.hypot(p.X - 2, p.Y - 6.95) / 1.3
            if d < 1:
                if d > 0.82:
                    return q(OR, 0.75)
                return q(ACIER, 0.95 - 0.6 * d if d > 0.25 else 0.5)
        if p.Y < 6.42:
            return q(OR, 0.8)
        return q(ACIER, (0.85 if p.Y < 6.8 else 0.5) * cylindre(p, 0.3))
    if p.Y < 10.8:                                                   # canon d'avant-bras, bande d'or
        if 8.9 < p.Y < 9.4:
            return q(OR, 0.85 if p.Y < 9.12 else 0.5)
        L = 0.75 * cylindre(p, 0.4)
        if p.Y < 7.95:
            L = 0.92
        return q(ACIER, L)
    # gantelet évasé
    if p.Y < 11.1:
        return q(OR, 0.75)
    return q(ACIER, lame(p, 11.1, 12, 0.78, 0.35))


def acier_ceinture(p):
    if p.face in ("avant", "arriere") and 2.4 <= p.X < 5.6 and p.Y >= 9.4:
        return drap(NUIT, p, base=0.55)
    k = (p.Y - 8) % 0.8
    if k < 0.16:
        return q(OR, 0.75)
    return q(ACIER, 0.62 - 0.35 * k / 0.8)


def acier_jambe_bas(p):
    if p.face == "dessus":
        return q(ACIER, 0.5)
    if p.Y < 5.6:                                                    # cuissot
        if p.Y > 5.32:
            return q(OR, 0.75)
        if p.face == "avant":
            d = p.X - 2
            L = 0.88 if -0.22 < d < 0 else (0.38 if 0 <= d < 0.22 else 0.74 - 0.18 * abs(d) - 0.03 * p.Y)
            return q(ACIER, L)
        return q(ACIER, 0.5 - 0.03 * p.Y)
    if p.Y < 7.4:                                                    # genouillère d'or et d'acier
        if p.face == "droite":
            d = math.hypot(p.X - 2, p.Y - 6.5) / 1.35
            if d < 1:
                if d > 0.82:
                    return q(OR, 0.8)
                return q(ACIER, 0.95 - 0.6 * d if d > 0.22 else 0.45)
        if p.face == "avant":
            r = math.hypot(p.X - 2, p.Y - 6.5)
            if r < 0.42:
                return q(OR, 0.95 if p.X < 2 else 0.6)
            z = lambda X, Y: bosse(X, Y, 2, 6.5, 1.8, 1.0, 1.0)
            return q(ACIER, relief(z, p.X, p.Y, amb=0.12))
        return q(ACIER, 0.5)
    return maille(p, 1)


def acier_botte(p):
    if p.face == "dessous":
        return q(ACIER, 0.1)
    if p.Y < 7.2:
        return None
    if p.Y < 10.6:
        if p.Y < 7.55:
            return q(OR, 0.85 if p.Y < 7.38 else 0.5)
        if p.face == "avant":
            d = p.X - 2
            L = 0.9 if -0.22 < d < 0 else (0.35 if 0 <= d < 0.22 else 0.72 - 0.15 * abs(d) - 0.03 * (p.Y - 7.5))
            return q(ACIER, L)
        return q(ACIER, 0.48 - 0.03 * (p.Y - 7.5))
    if p.Y < 10.85:
        return q(OR, 0.8)
    k = (p.Y - 10.85) % 0.58
    if p.face == "avant" and p.Y > 11.55 and abs(p.X - 2) < 0.7:
        return q(OR, 0.9 if p.X < 2 else 0.6)
    return q(ACIER, (0.75 if k < 0.15 else (0.2 if k > 0.46 else 0.5)) - (0.1 if p.cote else 0))


ENSEMBLES = {
    "cuivre": dict(corps=cuivre_corps, bras=cuivre_bras, botte=cuivre_botte, ceinture=cuivre_ceinture, cuisse=cuivre_jambe_bas),
    "fer": dict(corps=fer_corps, bras=fer_bras, botte=fer_botte, ceinture=fer_ceinture, cuisse=fer_jambe_bas),
    "acier": dict(corps=acier_corps, bras=acier_bras, botte=acier_botte, ceinture=acier_ceinture, cuisse=acier_jambe_bas),
}


def couche_principale(e):
    img = Image.new("RGBA", (64 * K, 32 * K), (0, 0, 0, 0))
    peindre(img, "corps", e["corps"], faces_ok=("dessus", "droite", "avant", "gauche", "arriere"))
    peindre(img, "bras", e["bras"], faces_ok=("dessus", "droite", "avant", "gauche", "arriere", "dessous"))
    peindre(img, "jambe", e["botte"], faces_ok=("droite", "avant", "gauche", "arriere", "dessous"))
    return img


def couche_jambieres(e):
    img = Image.new("RGBA", (64 * K, 32 * K), (0, 0, 0, 0))
    peindre(img, "corps", e["ceinture"], faces_ok=("droite", "avant", "gauche", "arriere", "dessous"), lignes=(8, 12))
    peindre(img, "jambe", e["cuisse"], faces_ok=("dessus", "droite", "avant", "gauche", "arriere"))
    return img


# ------------------------------------------------------------------ casques en 3D
class Modele:
    """Un modèle d'objet fait de cubes, chaque face peinte à part dans un atlas (2 texels par unité)."""
    D = 2

    def __init__(self):
        self.cubes = []

    def cube(self, a, b, peinture, rot=None, sans=()):
        self.cubes.append(dict(a=a, b=b, peinture=peinture, rot=rot, sans=sans))

    def construire(self, taille=128):
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
                tw, th = max(1, round(fw * self.D)), max(1, round(fh * self.D))
                if x + tw > taille:
                    x, y, ligne = 0, y + ligne, 0
                if y + th > taille:
                    raise ValueError("atlas trop petit")
                im = c["peinture"](nom, tw, th)
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


def tons_face(pal, nom, tw, th, haut=0.85, bas=0.45, bord=True, cote_sombre=0.12):
    """Une plaque de métal : claire en haut, plus sombre vers le bas ; filet clair au bord haut, sombre au bas."""
    im = Image.new("RGBA", (tw, th))
    for y in range(th):
        for x in range(tw):
            if nom == "up":
                L = haut + 0.08 - 0.1 * (x / max(1, tw)) - 0.06 * (y / max(1, th))
            elif nom == "down":
                L = bas - 0.2
            else:
                f = (y + 0.5) / th
                L = haut + (bas - haut) * f
                if nom in ("east", "west"):
                    L -= cote_sombre
                if nom == "south":
                    L -= cote_sombre * 1.5
                if bord and y == 0 and th > 2:
                    L += 0.12
                if bord and y == th - 1 and th > 2:
                    L -= 0.18
            im.putpixel((x, y), q(pal, L))
    return im


def plaque(pal, haut=0.85, bas=0.45, bord=True, decor=None):
    def f(nom, tw, th):
        im = tons_face(pal, nom, tw, th, haut, bas, bord)
        if decor:
            decor(im, nom, tw, th)
        return im
    return f


def uni(pal, L):
    return lambda nom, tw, th: Image.new("RGBA", (tw, th), q(pal, L - (0.1 if nom in ("east", "west", "south") else 0)))


def crin(pal, sens="y"):
    """Crin de cheval : mèches en longues traînées (une valeur par colonne), plus sombres dessous."""
    def f(nom, tw, th):
        im = Image.new("RGBA", (tw, th))
        for y in range(th):
            for x in range(tw):
                if nom in ("up", "down"):
                    k = x
                else:
                    k = x if sens == "y" else y
                v = (h(k, 3) % 5) / 4
                L = 0.35 + 0.45 * v
                if nom == "down":
                    L = 0.2
                if nom in ("east", "west", "north", "south") and sens == "y":
                    L -= 0.25 * (y / max(1, th))
                if nom == "up":
                    L += 0.15
                im.putpixel((x, y), q(pal, L))
        return im
    return f


def rivets_sur(pal, pas=4, rang=None):
    def decor(im, nom, tw, th):
        if nom in ("up", "down") or th < 3:
            return
        yy = th // 2 if rang is None else rang
        for x in range(1, tw - 1, pas):
            im.putpixel((x, yy), q(pal, 0.98))
            if x + 1 < tw:
                im.putpixel((x + 1, yy), q(pal, 0.5))
            if yy + 1 < th:
                im.putpixel((x, yy + 1), q(pal, 0.25))
    return decor


INTERIEUR = uni(CUIR, 0.25)


def coque(m, pal, y0, y1, ep=0.8, decor_avant=None, avant=True, x0=0.0, x1=16.0, z0=0.0, z1=16.0, haut=0.85, bas=0.5):
    """Coque de casque : quatre parois et un dessus (l'intérieur est du cuir sombre)."""
    def paroi(nom_ext, decor=None):
        ext = plaque(pal, haut, bas, decor=decor)

        def f(nom, tw, th):
            return ext(nom, tw, th) if nom in nom_ext else INTERIEUR(nom, tw, th)
        return f
    m.cube((x0, y0, z0 + ep), (x0 + ep, y1, z1 - ep), paroi(("west", "up", "down")), sans=())
    m.cube((x1 - ep, y0, z0 + ep), (x1, y1, z1 - ep), paroi(("east", "up", "down")))
    m.cube((x0, y0, z1 - ep), (x1, y1, z1), paroi(("south", "east", "west", "up", "down")))
    if avant:
        m.cube((x0, y0, z0), (x1, y1, z0 + ep), paroi(("north", "east", "west", "up", "down"), decor_avant))


def casque_cuivre():
    m = Modele()
    C = CUIVRE
    # calotte : front, côtés, nuque ; puis deux gradins pour l'arrondi
    coque(m, C, 8.0, 16.0)
    m.cube((0.0, 16.0, 0.0), (16.0, 16.8, 16.0), plaque(C, 0.95, 0.6))
    m.cube((1.2, 16.8, 1.2), (14.8, 17.6, 14.8), plaque(C, 0.98, 0.65))
    m.cube((3.0, 17.6, 3.0), (13.0, 18.2, 13.0), plaque(C, 1.0, 0.7))
    # sourcils en relief au-dessus des yeux
    m.cube((0.8, 8.4, -0.5), (7.4, 9.6, 0.0), plaque(C, 0.95, 0.4))
    m.cube((8.6, 8.4, -0.5), (15.2, 9.6, 0.0), plaque(C, 0.95, 0.4))
    # joues qui descendent jusqu'au menton (fente en T au milieu), nasal
    m.cube((0.0, 1.2, 0.0), (6.0, 8.0, 0.9), plaque(C, 0.82, 0.4), sans=())
    m.cube((10.0, 1.2, 0.0), (16.0, 8.0, 0.9), plaque(C, 0.82, 0.4))
    m.cube((0.0, 1.2, 0.9), (0.9, 8.0, 15.1), plaque(C, 0.75, 0.38))
    m.cube((15.1, 1.2, 0.9), (16.0, 8.0, 15.1), plaque(C, 0.75, 0.38))
    m.cube((7.3, 5.2, -0.4), (8.7, 8.4, 0.6), plaque(C, 0.95, 0.5))
    # couvre-nuque évasé
    m.cube((0.0, 1.2, 15.1), (16.0, 8.0, 16.0), plaque(C, 0.7, 0.38))
    m.cube((-0.4, 0.2, 15.4), (16.4, 1.6, 17.2), plaque(C, 0.9, 0.4))
    # bord roulé autour du visage et du bas des joues
    m.cube((-0.3, 0.6, -0.3), (6.3, 1.4, 1.0), plaque(C, 0.95, 0.35))
    m.cube((9.7, 0.6, -0.3), (16.3, 1.4, 1.0), plaque(C, 0.95, 0.35))
    # vert-de-gris : deux taches sur le front
    m.cube((2.2, 12.2, -0.05), (3.2, 12.8, 0.0), uni(VERT, 0.6))
    m.cube((12.6, 10.6, -0.05), (13.4, 11.2, 0.0), uni(VERT, 0.45))
    # porte-crête de cuivre, puis la haute crête de crin rouge, de l'avant à la nuque
    m.cube((7.0, 18.2, 2.0), (9.0, 19.4, 15.0), plaque(LAITON, 0.95, 0.5))
    profil = [(0.8, 3.0), (2.6, 5.2), (4.6, 6.2), (6.6, 6.6), (8.6, 6.6), (10.6, 6.2), (12.6, 5.4), (14.6, 4.0)]
    for i, (z, ht) in enumerate(profil):
        m.cube((7.1, 19.4, z), (8.9, 19.4 + ht, z + 2.0), crin(ROUGE))
    # queue de crin qui retombe dans le dos
    m.cube((7.1, 12.0, 16.6), (8.9, 22.4, 18.4), crin(ROUGE))
    m.cube((7.1, 8.0, 18.4), (8.9, 14.4, 19.6), crin(ROUGE))
    return m


def casque_fer():
    m = Modele()
    F = FER

    def croix(im, nom, tw, th):
        if nom != "north":
            return
    coque(m, F, 1.0, 16.0, avant=False)
    # face : deux plaques au-dessus et en dessous des fentes de vision
    m.cube((0.0, 8.0, 0.0), (16.0, 16.0, 0.8), plaque(F, 0.85, 0.55, decor=rivets_sur(F, 5, 2)))
    m.cube((0.0, 1.0, 0.0), (16.0, 6.4, 0.8), plaque(F, 0.75, 0.4, decor=souffles))
    m.cube((0.0, 6.4, 0.6), (16.0, 8.0, 0.8), INTERIEUR)                          # le fond des fentes
    # croix de renfort en laiton : barre verticale et bandeau des yeux
    m.cube((7.1, 1.0, -0.6), (8.9, 16.0, 0.0), plaque(LAITON, 0.95, 0.45, decor=rivets_sur(LAITON, 6)))
    m.cube((0.0, 8.0, -0.4), (16.0, 9.2, 0.0), plaque(LAITON, 0.95, 0.5))
    m.cube((0.0, 5.4, -0.3), (7.1, 6.4, 0.0), plaque(F, 0.9, 0.5))
    m.cube((8.9, 5.4, -0.3), (16.0, 6.4, 0.0), plaque(F, 0.9, 0.5))
    # dessus plat bordé, puis cerclage riveté
    m.cube((0.0, 16.0, 0.0), (16.0, 16.8, 16.0), plaque(F, 0.92, 0.6))
    m.cube((0.6, 16.8, 0.6), (15.4, 17.3, 15.4), plaque(F, 0.98, 0.7))
    m.cube((-0.3, 14.4, -0.3), (16.3, 15.4, 16.3), plaque(F, 0.92, 0.45, decor=rivets_sur(F, 4)), sans=("up", "down"))
    m.cube((-0.3, 0.4, -0.3), (16.3, 1.4, 16.3), plaque(F, 0.85, 0.35), sans=("up",))
    # panache rouge : une touffe qui monte puis part en arrière
    m.cube((6.8, 17.3, 5.0), (9.2, 19.5, 9.0), plaque(LAITON, 0.95, 0.5))
    for (y, z0, z1, x0) in ((19.5, 4.6, 9.6, 6.2), (21.5, 5.4, 11.0, 6.4), (23.3, 7.0, 13.0, 6.6), (24.6, 9.4, 15.4, 6.9), (25.4, 12.4, 17.6, 7.2)):
        m.cube((x0, y, z0), (16 - x0, y + 2.0, z1), crin(ROUGE))
    return m


def souffles(im, nom, tw, th):
    """Trous d'aération sur le côté droit du bas du heaume."""
    if nom != "north":
        return
    for (x, y) in ((tw - 6, 3), (tw - 9, 3), (tw - 6, 6), (tw - 9, 6), (tw - 7, 9)):
        if 0 <= x < tw - 1 and 0 <= y < th - 1:
            im.putpixel((x, y), q(CUIR, 0.05))
            im.putpixel((x + 1, y), q(CUIR, 0.05))


def casque_acier():
    m = Modele()
    A = ACIER

    def filet_or(im, nom, tw, th):
        if nom in ("down",):
            return
        for x in range(tw):
            im.putpixel((x, th - 1), q(OR, 0.55))
            if th > 3:
                im.putpixel((x, th - 2), q(OR, 0.85))

    coque(m, A, 1.6, 16.0, avant=False, haut=0.72, bas=0.42)
    # calotte et sa crête, bordure d'or
    m.cube((0.0, 16.0, 0.0), (16.0, 16.8, 16.0), plaque(A, 0.8, 0.55))
    m.cube((1.0, 16.8, 1.0), (15.0, 17.6, 15.0), plaque(A, 0.86, 0.6))
    m.cube((7.2, 17.6, 0.6), (8.8, 18.6, 15.4), plaque(OR, 0.95, 0.5))
    m.cube((-0.3, 15.2, -0.3), (16.3, 16.1, 16.3), plaque(OR, 0.92, 0.45), sans=("up", "down"))
    # visière à bec : trois gradins vers l'avant, fente de vision au-dessus
    m.cube((0.0, 9.6, 0.0), (16.0, 15.2, 0.8), plaque(A, 0.8, 0.5))
    m.cube((0.0, 7.8, 0.4), (16.0, 9.6, 0.8), INTERIEUR)
    m.cube((-0.2, 9.4, -0.5), (16.2, 10.0, 0.4), plaque(OR, 0.95, 0.5))
    m.cube((0.0, 1.6, 0.0), (16.0, 7.8, 0.8), plaque(A, 0.75, 0.4, decor=filet_or))
    m.cube((2.0, 2.2, -1.4), (14.0, 7.6, 0.0), plaque(A, 0.82, 0.42))
    m.cube((4.6, 2.8, -2.8), (11.4, 7.2, -1.4), plaque(A, 0.88, 0.45))
    m.cube((6.8, 3.4, -3.8), (9.2, 6.6, -2.8), plaque(OR, 0.95, 0.5))
    m.cube((-0.2, 7.2, -1.6), (16.2, 7.8, 0.4), plaque(OR, 0.92, 0.5), sans=("down",))
    # gorgerin
    m.cube((-0.4, 0.2, -0.4), (16.4, 1.6, 16.4), plaque(A, 0.7, 0.35, decor=filet_or), sans=("up",))
    # ailes d'acier et d'or, de chaque côté, vers le haut et l'arrière
    for cote in (-1, 1):
        xa = (-0.7, 0.0) if cote < 0 else (16.0, 16.7)
        for i, (ang, y0, long_) in enumerate(((0, 11.0, 7.0), (-22.5, 12.4, 8.5), (-45, 13.8, 9.5))):
            m.cube((xa[0], y0, 9.0), (xa[1], y0 + 1.5, 9.0 + long_), plaque(A, 0.85, 0.45, decor=filet_or),
                   rot=("x", ang, [8, y0, 9.0]) if ang else None)
        m.cube((xa[0] - (0.3 if cote < 0 else 0), 10.4, 7.6), (xa[1] + (0.3 if cote > 0 else 0), 13.6, 10.4), plaque(OR, 0.95, 0.5))
    return m


CASQUES = {"cuivre": casque_cuivre, "fer": casque_fer, "acier": casque_acier}


def modele_json(m, chemin_texture, gui_echelle):
    atlas, elements = m.construire(256)
    mod = {
        "texture_size": [256, 256],
        "textures": {"0": chemin_texture, "particle": chemin_texture},
        "elements": elements,
        "display": {
            "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "gui": {"rotation": [25, 145, 0], "translation": [0, -1.0, 0], "scale": [gui_echelle] * 3},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.35] * 3},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, -1, 0], "scale": [0.6] * 3},
            "thirdperson_righthand": {"rotation": [10, 45, 0], "translation": [0, 1.5, 1], "scale": [0.36] * 3},
            "thirdperson_lefthand": {"rotation": [10, 45, 0], "translation": [0, 1.5, 1], "scale": [0.36] * 3},
            "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, 3, 0], "scale": [0.34] * 3},
            "firstperson_lefthand": {"rotation": [0, 135, 0], "translation": [0, 3, 0], "scale": [0.34] * 3},
        },
    }
    return mod, atlas


# ------------------------------------------------------------------ icônes 32 x 32, tirées du dessin porté
def reduire(img):
    """Divise par deux en gardant les tons de la palette (le ton le plus présent de chaque carré de 2 x 2)."""
    w, hh = img.size
    out = Image.new("RGBA", (w // 2, hh // 2), (0, 0, 0, 0))
    for y in range(hh // 2):
        for x in range(w // 2):
            px = [img.getpixel((2 * x + i, 2 * y + j)) for j in (0, 1) for i in (0, 1)]
            pleins = [c for c in px if c[3] > 0]
            if len(pleins) < 2:
                continue
            compte = {}
            for c in pleins:
                compte[c] = compte.get(c, 0) + 1
            out.putpixel((x, y), max(compte.items(), key=lambda kv: (kv[1], sum(kv[0][:3])))[0])
    return out


def face(img, boite, nom):
    u, v, w, hh = faces(BOITES[boite])[nom]
    return img.crop((u * K, v * K, (u + w) * K, (v + hh) * K))


def icone(piece, a, b):
    t = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    if piece == "plastron":
        corps = face(a, "corps", "avant").crop((0, 0, 32, 46))
        bras = face(a, "bras", "avant").crop((0, 0, 16, 26))
        t.alpha_composite(corps, (16, 6))
        t.alpha_composite(bras, (0, 4))
        t.alpha_composite(bras.transpose(Image.FLIP_LEFT_RIGHT), (48, 4))
    elif piece == "jambieres":
        ceint = face(b, "corps", "avant").crop((0, 32, 32, 48))
        jambe = face(b, "jambe", "avant").crop((0, 0, 16, 46))
        t.alpha_composite(jambe, (14, 14))
        t.alpha_composite(jambe.transpose(Image.FLIP_LEFT_RIGHT), (34, 14))
        t.alpha_composite(ceint, (16, 2))
    else:
        botte = face(a, "jambe", "avant").crop((0, 28, 16, 48))
        cote = face(a, "jambe", "droite").crop((0, 28, 16, 48))
        for i, x0 in enumerate((6, 36)):
            t.alpha_composite(botte, (x0 + (0 if i else 6), 24))
            t.alpha_composite(cote, (x0 + (16 if i else -10) if False else x0 + (6 if i == 0 else 0) - 0, 24)) if False else None
            pied = botte.crop((0, 12, 16, 20))
            t.alpha_composite(pied, (x0 + (-4 if i == 0 else 4) + (6 if i == 0 else 0), 36))
    return reduire(t)


def icone_casque(m):
    """L'icône d'inventaire du casque : son modèle 3D vu de trois quarts, réduit."""
    return None


# ------------------------------------------------------------------ aperçu calculé (mannequin portant l'armure)
def main():
    apercu_entrees = []
    for mat, e in ENSEMBLES.items():
        d = os.path.join(SORTIE, "equipement", "exemple", "armure_" + mat)
        os.makedirs(d, exist_ok=True)
        a, b = couche_principale(e), couche_jambieres(e)
        a.save(os.path.join(d, "humanoid.png"), optimize=True)
        b.save(os.path.join(d, "humanoid_leggings.png"), optimize=True)
        dt = os.path.join(SORTIE, "textures", "exemple", "armure")
        os.makedirs(dt, exist_ok=True)
        for piece in ("plastron", "jambieres", "bottes"):
            icone(piece, a, b).save(os.path.join(dt, "%s_%s.png" % (mat, piece)), optimize=True)
        m = CASQUES[mat]()
        mod, atlas = modele_json(m, "exemple/armure/casque_" + mat, 0.62)
        atlas.save(os.path.join(dt, "casque_" + mat + ".png"), optimize=True)
        dm = os.path.join(SORTIE, "modeles", "exemple", "armure")
        os.makedirs(dm, exist_ok=True)
        with open(os.path.join(dm, "casque_" + mat + ".json"), "w", encoding="utf-8") as f:
            json.dump(mod, f, ensure_ascii=False, indent=1)
        apercu_entrees.append((mat, a, b, m))
    # l'ancienne icône 2D du casque n'existe plus
    for mat in ENSEMBLES:
        vieux = os.path.join(SORTIE, "textures", "exemple", "armure", mat + "_casque.png")
        if os.path.exists(vieux):
            os.remove(vieux)
    tous = [os.path.relpath(os.path.join(dd, n), SORTIE).replace(os.sep, "/") for dd, _, ns in os.walk(SORTIE) for n in ns]
    with open(os.path.join(RACINE, "ressources.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(sorted(tous)) + "\n")
    print("armures :", ", ".join(ENSEMBLES))
    if len(sys.argv) > 1:
        import apercu_armures
        apercu_armures.dessiner(apercu_entrees, sys.argv[1])


if __name__ == "__main__":
    main()
