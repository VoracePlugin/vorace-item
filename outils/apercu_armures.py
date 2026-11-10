"""Aperçu calculé des armures d'exemple : un mannequin les porte (vue de trois quarts, de face et de dos).

Ce n'est pas une capture en jeu : projection isométrique, lumière des entités du jeu approchée.
"""
import math

from PIL import Image, ImageDraw

K = 4


def boite_armure(faces3d, tex, uv, a, b, k):
    u, v, w, h, d = uv
    (x0, y0, z0), (x1, y1, z1) = a, b
    F = {"up": (u + d, v, w, d), "down": (u + d + w, v, w, d), "west": (u, v + d, d, h), "south": (u + d, v + d, w, h),
         "east": (u + d + w, v + d, d, h), "north": (u + d + w + d, v + d, w, h)}
    for n, (fu, fv, fw, fh) in F.items():
        im = tex.crop((fu * k, fv * k, (fu + fw) * k, (fv + fh) * k))
        if im.getbbox() is None:
            continue
        faces3d.append((coins(n, x0, y0, z0, x1, y1, z1), im))


def coins(n, x0, y0, z0, x1, y1, z1):
    return {"south": ((x0, y1, z1), (x1, y1, z1), (x0, y0, z1)), "east": ((x1, y1, z1), (x1, y1, z0), (x1, y0, z1)),
            "up": ((x0, y1, z0), (x1, y1, z0), (x0, y1, z1)), "north": ((x1, y1, z0), (x0, y1, z0), (x1, y0, z0)),
            "west": ((x0, y1, z0), (x0, y1, z1), (x0, y0, z0)), "down": ((x0, y0, z1), (x1, y0, z1), (x0, y0, z0))}[n]


def uni(c, w, h):
    return Image.new("RGBA", (max(1, w), max(1, h)), c + (255,))


def mannequin(faces3d):
    PEAU, CHEMISE, BRAIE, CHEVEUX = (198, 150, 118), (150, 140, 120), (72, 64, 58), (70, 46, 30)
    parts = [((4, 24, 6), (12, 32, 14), PEAU), ((4, 12, 8), (12, 24, 12), CHEMISE), ((0, 12, 8), (4, 24, 12), PEAU),
             ((12, 12, 8), (16, 24, 12), PEAU), ((4, 0, 8), (8, 12, 12), BRAIE), ((8, 0, 8), (12, 12, 12), BRAIE)]
    for a, b, c in parts:
        for n in ("south", "east", "up", "north", "west", "down"):
            im = uni(c, 8, 8)
            if c == PEAU and a[1] == 24 and n == "south":
                dr = ImageDraw.Draw(im)
                dr.rectangle((1, 4, 2, 4), fill=(255, 255, 255)); dr.point((2, 4), fill=(60, 60, 140))
                dr.rectangle((5, 4, 6, 4), fill=(255, 255, 255)); dr.point((5, 4), fill=(60, 60, 140))
                dr.rectangle((0, 0, 7, 1), fill=CHEVEUX)
            if c == PEAU and a[1] == 24 and n in ("up", "north", "east", "west"):
                im = uni(CHEVEUX, 8, 8)
            faces3d.append((coins(n, *a, *b), im))


def tourner(p, axe, angle, o):
    a = math.radians(angle)
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    c, s = math.cos(a), math.sin(a)
    if axe == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axe == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def casque(faces3d, modele):
    """Le modèle du casque posé sur la tête : 16 unités = 10 pixels du joueur, l'avant (nord) devant (+z)."""
    def vers_joueur(p):
        x, y, z = (p[0] - 8) / 1.6, (p[1] - 8) / 1.6, (p[2] - 8) / 1.6
        return (8 - x, 28 + y, 10 - z)
    for c in modele.cubes:
        (x0, y0, z0), (x1, y1, z1) = c["a"], c["b"]
        for n, im in c.get("images", {}).items():
            pts = coins(n, x0, y0, z0, x1, y1, z1)
            if c["rot"]:
                axe, ang, o = c["rot"]
                pts = tuple(tourner(p, axe, ang, o) for p in pts)
            faces3d.append((tuple(vers_joueur(p) for p in pts), im))


def corps(a, b, modele, avec=("tete", "corps", "jambes", "pieds")):
    f = []
    mannequin(f)
    e, e2 = 1.0, 0.5
    if "jambes" in avec:
        boite_armure(f, b, (16, 16, 8, 12, 4), (4 - e2, 12 - e2, 8 - e2), (12 + e2, 24 + e2, 12 + e2), K)
        boite_armure(f, b, (0, 16, 4, 12, 4), (4 - e2, 0 - e2, 8 - e2), (8 + e2, 12 + e2, 12 + e2), K)
        boite_armure(f, b, (0, 16, 4, 12, 4), (8 - e2, 0 - e2, 8 - e2), (12 + e2, 12 + e2, 12 + e2), K)
    if "pieds" in avec:
        boite_armure(f, a, (0, 16, 4, 12, 4), (4 - e, 0 - e, 8 - e), (8 + e, 12 + e, 12 + e), K)
        boite_armure(f, a, (0, 16, 4, 12, 4), (8 - e, 0 - e, 8 - e), (12 + e, 12 + e, 12 + e), K)
    if "corps" in avec:
        boite_armure(f, a, (16, 16, 8, 12, 4), (4 - e, 12 - e, 8 - e), (12 + e, 24 + e, 12 + e), K)
        boite_armure(f, a, (40, 16, 4, 12, 4), (0 - e, 12 - e, 8 - e), (4 + e, 24 + e, 12 + e), K)
        boite_armure(f, a, (40, 16, 4, 12, 4), (12 - e, 12 - e, 8 - e), (16 + e, 24 + e, 12 + e), K)
    if "tete" in avec:
        casque(f, modele)
    return f


def rendre(scenes, E=10, marge=3, fond=(30, 32, 40), dos=False):
    """Rendu à tampon de profondeur (chaque pixel garde la face la plus proche)."""
    import numpy as np
    vue = np.array((0.5, 0.42, 0.75))
    vue = vue / np.linalg.norm(vue)
    R = np.array((0.832, 0.0, -0.555))
    D = np.array((0.231, -0.8975, 0.349))
    faces = []
    for f3, dec in scenes:
        for pts, im in f3:
            P = [np.array(((-p[0] + 16) if dos else p[0], p[1], (-p[2] + 20) if dos else p[2]), float) + np.array(dec, float) for p in pts]
            u, v = P[1] - P[0], P[2] - P[0]
            nrm = -np.cross(u, v)
            if nrm @ vue <= 1e-6:
                continue
            n = nrm / (np.linalg.norm(nrm) or 1)
            lum = min(1.0, 0.55 + 0.45 * max(0, n[1]) + 0.3 * max(0, n[2]) + 0.12 * max(0, n[0]) - 0.12 * max(0, -n[0]))
            faces.append((P, im, lum))
    pr = lambda p: np.array((p @ R, p @ D)) * E
    pts = np.array([pr(c) for P, _, _ in faces for c in (P[0], P[1], P[2], P[1] + P[2] - P[0])])
    o = pts.min(0) - marge * E
    W, H = (pts.max(0) - o + marge * E).astype(int) + 1
    coul = np.zeros((H, W, 3), float) + np.array(fond)
    prof = np.full((H, W), -1e9)
    for P, im, lum in faces:
        a = np.asarray(im.convert("RGBA"), float)
        th, tw = a.shape[:2]
        s0, s1, s2 = pr(P[0]) - o, pr(P[1]) - o, pr(P[2]) - o
        M = np.array([s1 - s0, s2 - s0]).T
        if abs(np.linalg.det(M)) < 1e-9:
            continue
        Mi = np.linalg.inv(M)
        c4 = np.array([s0, s1, s2, s1 + s2 - s0])
        x0, y0 = np.floor(c4.min(0)).astype(int)
        x1, y1 = np.ceil(c4.max(0)).astype(int)
        x0, y0 = max(0, x0), max(0, y0)
        x1, y1 = min(W, x1 + 1), min(H, y1 + 1)
        if x1 <= x0 or y1 <= y0:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        dx, dy = gx - s0[0], gy - s0[1]
        s = Mi[0, 0] * dx + Mi[0, 1] * dy
        t = Mi[1, 0] * dx + Mi[1, 1] * dy
        dedans = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
        ix = np.clip((s * tw).astype(int), 0, tw - 1)
        iy = np.clip((t * th).astype(int), 0, th - 1)
        px = a[iy, ix]
        z = (P[0] @ vue) + s * ((P[1] - P[0]) @ vue) + t * ((P[2] - P[0]) @ vue)
        zone = prof[y0:y1, x0:x1]
        ok = dedans & (px[..., 3] > 0) & (z > zone + 1e-4)
        zone[ok] = z[ok]
        coul[y0:y1, x0:x1][ok] = px[..., :3][ok] * lum
    return Image.fromarray(np.clip(coul, 0, 255).astype("uint8"), "RGB").convert("RGBA")


def dessiner(entrees, chemin, E=16):
    vues = []
    for dos in (False, True):
        sc = [(corps(a, b, m), (i * 26, 0, 0)) for i, (_, a, b, m) in enumerate(entrees)]
        vues.append(rendre(sc, E=E, dos=dos))
    W = max(v.width for v in vues)
    img = Image.new("RGBA", (W, sum(v.height for v in vues)), (30, 32, 40, 255))
    y = 0
    for v in vues:
        img.alpha_composite(v, (0, y))
        y += v.height
    img.convert("RGB").save(chemin)
    # gros plan des casques
    sc = []
    for i, (_, a, b, m) in enumerate(entrees):
        f = []
        casque(f, m)
        mannequin_tete = []
        mannequin(mannequin_tete)
        f = [x for x in mannequin_tete if min(p[1] for p in x[0]) >= 24] + f
        sc.append((f, (i * 16, 0, 0)))
    rendre(sc, E=40).convert("RGB").save(chemin.replace(".png", "_casques.png"))
