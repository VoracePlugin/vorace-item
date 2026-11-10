#!/usr/bin/env python3
"""Dessine les objets de bois d'exemple (ceux de l'Ébéniste) : arcs, bâtons, flèches.

  textures/exemple/bois/arc_court_<essence>.png           arc au repos (32 x 32), puis _tension_0, _1, _2 (tiré)
  textures/exemple/bois/arc_long_if.png                    l'arc long d'if (et ses trois tensions)
  textures/exemple/bois/baton_<essence>.png                bâton (32 x 32, tenu comme une arme)
  textures/exemple/bois/fleche.png                         flèche (16 x 16)

Style des objets du jeu : comme l'arc du jeu, le bois fait un arc de cercle du coin en bas à gauche au coin en haut à
droite, bombé vers le haut à gauche ; la corde est droite au repos, tirée vers le bas à droite en tension, la flèche
pointe vers le haut à gauche. 4 tons par bois, du plus clair (côté lumière) au plus sombre, pas de contour noir.
Usage : python3 outils/exemples-bois.py [aperçu.png]
"""
import math
import os
import sys

from PIL import Image

SORTIE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "exemples", "ressources",
                      "textures", "exemple", "bois")


def rgb(x):
    return tuple(int(x[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


BOIS = {
    "if": [rgb(c) for c in ("#4a2418", "#7c3c24", "#ac5e36", "#d48a56", "#efb47c")],        # if : cœur rouge orangé
    "frene": [rgb(c) for c in ("#5c4a32", "#8c7452", "#b49c74", "#d6c49c", "#efe2c0")],     # frêne : pâle
    "chene": [rgb(c) for c in ("#3e2c1a", "#644a2c", "#8c6a42", "#b08c5c", "#ceac78")],     # chêne : brun
}
CUIR = [rgb(c) for c in ("#2e1a10", "#4e2e1c", "#6e4428", "#8e5c38")]
CORDE = [rgb(c) for c in ("#8a8478", "#c8c2b2", "#ece6d6")]
SILEX = [rgb(c) for c in ("#2c2e34", "#4c5058", "#7a7e88", "#a4a8b0")]
PLUME = [rgb(c) for c in ("#8a1f1a", "#c0392b", "#e86a50", "#f0f0e8")]
LAITON = [rgb(c) for c in ("#6e5020", "#b8923e", "#efd68a")]
AMETHYSTE = [rgb(c) for c in ("#3a2266", "#6a3fae", "#9c6ee0", "#d2b6ff")]


def toile(n=32):
    return Image.new("RGBA", (n, n), (0, 0, 0, 0))


def point(img, x, y, c):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def ligne(img, a, b, c, pas=0.25):
    n = max(1, int(math.hypot(b[0] - a[0], b[1] - a[1]) / pas))
    for i in range(n + 1):
        t = i / n
        point(img, a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, c)


# ------------------------------------------------------------------ arcs
D = (1 / math.sqrt(2), -1 / math.sqrt(2))          # du bout bas-gauche vers le bout haut-droit
N = (-1 / math.sqrt(2), -1 / math.sqrt(2))         # vers le haut à gauche (le dos de l'arc)


def courbe(t, L, b, c=15.5, recourbe=0.0):
    """Point du bois à la position t (-1 bas-gauche, 1 haut-droit). b : flèche de l'arc ; recourbe : bouts retournés."""
    off = b * (1 - t * t) - recourbe * max(0, abs(t) - 0.8) ** 2 * 25
    return (c + t * L * D[0] + off * N[0], c + t * L * D[1] + off * N[1])


def arc(essence, tension, long_=False):
    """tension : -1 au repos, 0, 1, 2 en main tirée."""
    img = toile()
    P = BOIS[essence]
    L = 16.4 if long_ else 14.6
    b = 5.2 if long_ else 5.6
    rec = 0.0 if long_ else (1.0 if essence == "if" else 0.4)
    if tension >= 0:
        b += 1.1 + 0.9 * tension
        L -= 0.7 + 0.6 * tension
    # le bois : on balaie le long de la courbe, épaisseur qui s'affine vers les bouts, poignée plus épaisse
    pts = []
    for i in range(401):
        t = -1 + 2 * i / 400
        x, y = courbe(t, L, b, recourbe=rec)
        ep = 1.35 - 0.55 * abs(t) + (0.45 if abs(t) < 0.16 else 0)
        pts.append((t, x, y, ep))
    for py in range(32):
        for px in range(32):
            best = None
            for t, x, y, ep in pts:
                d = math.hypot(px + 0.5 - x - 0.5, py + 0.5 - y - 0.5)
                if best is None or d < best[0]:
                    best = (d, t, x, y, ep)
            d, t, x, y, ep = best
            if d > ep:
                continue
            # côté : dos (vers le haut à gauche) clair, ventre sombre
            cote = (px - x) * N[0] + (py - y) * N[1]
            if abs(t) < 0.16:                                       # poignée de cuir enroulé
                k = 2 if cote > 0.2 else 1
                if int((t + 1) * 60) % 3 == 0:
                    k -= 1
                img.putpixel((px, py), CUIR[max(0, k)])
                continue
            k = 3 if cote > 0.35 else (2 if cote > -0.35 else 1)
            if abs(t) > 0.93:
                k = max(1, k - 1)                                   # bouts (poupées) plus sombres
            if long_ and abs(t) > 0.9:
                k = 0 if cote < 0 else 1                            # poupées de corne
            img.putpixel((px, py), P[k])
    # garnitures : poupées de corne de l'arc long, nœud de la corde
    bouts = [courbe(-0.97, L, b, recourbe=rec), courbe(0.97, L, b, recourbe=rec)]
    # la corde
    if tension < 0:
        ligne(img, bouts[0], bouts[1], CORDE[1])
    else:
        tir = 2.2 + 2.0 * tension                                    # la corde tirée vers le bas à droite
        cx, cy = 15.5 - N[0] * tir, 15.5 - N[1] * tir
        ligne(img, bouts[0], (cx, cy), CORDE[1])
        ligne(img, bouts[1], (cx, cy), CORDE[1])
        # la flèche encochée : elle pointe vers le haut à gauche
        lf = 15
        a = (cx, cy)
        p = (cx + N[0] * lf, cy + N[1] * lf)
        ligne(img, a, p, BOIS["frene"][2])
        for k, c in enumerate((SILEX[3], SILEX[2], SILEX[1])):
            point(img, p[0] + N[0] * (1 - k * 0.6), p[1] + N[1] * (1 - k * 0.6), c)
        point(img, p[0] + N[0] * 0.2 + D[0] * 0.9, p[1] + N[1] * 0.2 + D[1] * 0.9, SILEX[1])
        point(img, p[0] + N[0] * 0.2 - D[0] * 0.9, p[1] + N[1] * 0.2 - D[1] * 0.9, SILEX[2])
        for k in range(3):                                           # empennage
            q = (cx + N[0] * (1 + k), cy + N[1] * (1 + k))
            point(img, q[0] + D[0] * 1.0, q[1] + D[1] * 1.0, PLUME[1 + (k == 0)])
            point(img, q[0] - D[0] * 1.0, q[1] - D[1] * 1.0, PLUME[0])
    return img


# ------------------------------------------------------------------ bâtons
def baton(essence):
    """Bâton tenu en main : du bas à gauche au haut à droite, tête à droite en haut, cristal d'améthyste."""
    img = toile()
    P = BOIS[essence]
    a, b = (2.5, 29.5), (24.0, 8.0)
    n = 160
    for i in range(n + 1):
        t = i / n
        x, y = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
        if essence == "chene":                                       # chêne : tortueux, nœuds
            ond = 0.6 * math.sin(t * 13)
            x, y = x + ond * N[0], y + ond * N[1]
        for dx, dy, k in ((0, 0, 2), (N[0] * 0.8, N[1] * 0.8, 3), (-N[0] * 0.8, -N[1] * 0.8, 1)):
            kk = k
            if essence == "chene" and int(t * 11) % 4 == 0 and dx == 0:
                kk = 1
            point(img, x + dx, y + dy, P[kk])
    # embout bas
    point(img, 2, 30, P[0]); point(img, 3, 29, P[1])
    # bague (laiton pour le frêne, cuir pour le chêne)
    for k in range(3):
        t = 0.55 + k * 0.02
        x, y = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
        c = LAITON[2 - k] if essence == "frene" else CUIR[3 - k]
        for s in (-1.2, -0.4, 0.4, 1.2):
            point(img, x + N[0] * s, y + N[1] * s, c)
    # tête : deux griffes de bois qui tiennent le cristal
    cx, cy = 25.5, 6.5
    for s in (-1, 1):
        for k in range(5):
            x = b[0] + s * N[0] * (1.6 + k * 0.2) + D[0] * k * 0.9
            y = b[1] + s * N[1] * (1.6 + k * 0.2) + D[1] * k * 0.9
            point(img, x, y, P[3 if s > 0 else 1])
    # le cristal : losange facetté
    for py in range(32):
        for px in range(32):
            u = (px + 0.5 - cx) * D[0] + (py + 0.5 - cy) * D[1]
            v = (px + 0.5 - cx) * N[0] + (py + 0.5 - cy) * N[1]
            if abs(u) / 3.6 + abs(v) / 2.0 <= 1:
                k = 3 if (v > 0.4 and u < 0.8) else (2 if v > -0.3 else 1)
                if abs(u) / 3.6 + abs(v) / 2.0 > 0.82:
                    k = max(0, k - 1)
                img.putpixel((px, py), AMETHYSTE[k])
    point(img, cx - 0.8, cy - 1.2, AMETHYSTE[3])
    return img


# ------------------------------------------------------------------ flèche
def fleche():
    img = toile(16)
    # hampe du bas à gauche au haut à droite (comme la flèche du jeu)
    for i in range(3, 13):
        point(img, i, 15 - i, BOIS["frene"][3 if i % 2 else 2])
    for (x, y, c) in ((13, 2, SILEX[2]), (14, 1, SILEX[3]), (12, 2, SILEX[1]), (13, 3, SILEX[1]), (14, 2, SILEX[2]), (13, 1, SILEX[3])):
        point(img, x, y, c)
    for (x, y, c) in ((1, 13, PLUME[1]), (2, 14, PLUME[0]), (2, 12, PLUME[2]), (3, 13, PLUME[1]), (1, 12, PLUME[2]),
                      (3, 14, PLUME[0]), (4, 12, PLUME[3]), (0, 14, PLUME[1])):
        point(img, x, y, c)
    return img


def main():
    os.makedirs(SORTIE, exist_ok=True)
    faits = []
    for e in BOIS:
        arc(e, -1).save(os.path.join(SORTIE, "arc_court_%s.png" % e), optimize=True)
        for k in range(3):
            arc(e, k).save(os.path.join(SORTIE, "arc_court_%s_tension_%d.png" % (e, k)), optimize=True)
        faits.append("arc_court_" + e)
    arc("if", -1, True).save(os.path.join(SORTIE, "arc_long_if.png"), optimize=True)
    for k in range(3):
        arc("if", k, True).save(os.path.join(SORTIE, "arc_long_if_tension_%d.png" % k), optimize=True)
    for e in ("chene", "frene"):
        baton(e).save(os.path.join(SORTIE, "baton_%s.png" % e), optimize=True)
    fleche().save(os.path.join(SORTIE, "fleche.png"), optimize=True)
    if len(sys.argv) > 1:
        noms = ["arc_court_if", "arc_court_if_tension_2", "arc_court_frene", "arc_court_chene", "arc_long_if", "arc_long_if_tension_1",
                "baton_chene", "baton_frene"]
        ap = Image.new("RGBA", (len(noms) * 34 * 6 + 16 * 6, 34 * 6), (40, 42, 50, 255))
        for i, n in enumerate(noms):
            im = Image.open(os.path.join(SORTIE, n + ".png")).resize((192, 192), Image.NEAREST)
            ap.alpha_composite(im, (i * 204 + 6, 6))
        f = Image.open(os.path.join(SORTIE, "fleche.png")).resize((96, 96), Image.NEAREST)
        ap.alpha_composite(f, (len(noms) * 204 + 6, 50))
        ap.convert("RGB").save(sys.argv[1])
    print("bois :", len(faits) + 3, "objets")


if __name__ == "__main__":
    main()
