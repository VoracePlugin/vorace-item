"""Armes de poing en 3D (Pugiliste de Babel) : chaque arme est un petit modèle fait de cubes autour du poing et de
l'avant-bras, tenu comme le jeu tient un objet.

Repère du modèle de la main droite (unités de modèle, 1 pixel de texture = 1/8 d'unité) avec un réglage « en main »
neutre : le point de prise du jeu est en (8, 8, 8), sur la face avant du bout du bras. Le bras du joueur y occupe
x 6 à 10 (x = 10 : dehors, dos de la main ; x = 6 : dedans, paume), y 4 à 8 (y = 8 : devant, côté du pouce), z 8 à 20
(z = 8 : bout du poing, le coude vers z = 14 et au-delà).

La manche d'un plastron du jeu recouvre tout le bras avec 1 unité de marge (x 5 à 11, y 3 à 9, z 7 à 21) : tout ce qui
est en dessous est caché. Le corps de chaque arme la dépasse de 1/16 à 1/8 d'unité, les reliefs un peu plus.

La main gauche est la même arme retournée (x → 16 - x), dans un second modèle « <nom>_gauche » que le pack choisit
pour la main gauche.

Style des armes de vorace-item : 5 tons par matière, couleurs du jeu ternes, aplats, filet clair du côté du coude
(la lumière vient d'en haut quand le bras pend) et filet sombre de l'autre, peu d'usure, pas de contour noir. Chaque
arme a sa silhouette : plaques évasées des gantelets, anneaux saillants des cestes, attelles et coude des brassards,
spirales et bouts pendants des bandes. Rotations des cubes : 22,5 ou 45 degrés, sur un seul axe (règle du jeu).

Usage : python3 outils/poings-3d.py [dossier d'aperçu]"""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
RESSOURCES = os.path.join(ICI, "..", "src", "main", "resources", "exemples", "ressources")
MODELES = os.path.join(RESSOURCES, "modeles", "exemple", "poing")
TEXTURES = os.path.join(RESSOURCES, "textures", "exemple", "poing")

FER = [(58, 60, 66), (96, 99, 106), (138, 141, 148), (182, 185, 191), (224, 226, 230)]
CUIR = [(44, 24, 16), (72, 40, 26), (102, 60, 38), (134, 84, 54), (164, 112, 76)]
CUIR_CLAIR = [(92, 64, 40), (124, 90, 58), (156, 118, 80), (186, 148, 106), (210, 178, 136)]
LIN = [(104, 92, 72), (146, 132, 106), (186, 172, 144), (214, 202, 178), (234, 226, 208)]
LAITON = [(86, 62, 26), (130, 98, 42), (172, 134, 60), (206, 170, 88), (232, 206, 130)]
ROUGE = [(72, 22, 20), (112, 34, 28), (150, 50, 38), (182, 74, 54), (208, 108, 82)]
PAPIER = [(156, 146, 118), (194, 184, 154), (222, 214, 188), (238, 232, 210), (250, 246, 232)]
BOIS = [(60, 40, 24), (88, 60, 36), (118, 84, 52), (148, 110, 72), (176, 138, 96)]
PEAU = (198, 150, 118)

ATLAS = 512          # côté de la texture ; 8 pixels par unité de modèle
PX = 8


# ================================================================== pièces

def boite(de, a, pal, ton, nom="", motif="metal", rot=None):
    """Un cube : coins, palette (5 tons), ton de base, motif de texture, rotation facultative
    (axe, angle, origine) ; angle 22,5 ou 45 degrés comme le jeu l'accepte."""
    return {"de": [float(v) for v in de], "a": [float(v) for v in a], "pal": pal, "ton": ton, "nom": nom,
            "motif": motif, "rot": rot}


def autour(m, z0, z1, pal, ton, nom, **k):
    """Boîte qui entoure le bras avec une marge m, de z0 à z1."""
    return boite([6 - m, 4 - m, z0], [10 + m, 8 + m, z1], pal, ton, nom, **k)


def anneau(m, ep, z0, z1, pal, ton, nom, **k):
    """Anneau creux (4 murs d'épaisseur ep) : on voit le bras entrer dedans."""
    x0, x1, y0, y1 = 6 - m, 10 + m, 4 - m, 8 + m
    return [boite([x0, y0, z0], [x1, y0 + ep, z1], pal, ton, nom, **k),
            boite([x0, y1 - ep, z0], [x1, y1, z1], pal, ton, nom, **k),
            boite([x0, y0 + ep, z0], [x0 + ep, y1 - ep, z1], pal, ton, nom, **k),
            boite([x1 - ep, y0 + ep, z0], [x1, y1 - ep, z1], pal, ton, nom, **k)]


def spirale(e, z0, z1, pas, m, ep, pal, tons, nom, motif="tissu"):
    """Bande enroulée en spirale : sur chacun des 4 côtés, la bande est décalée d'un quart de pas, comme une bande
    qui tourne autour du bras. Tons alternés d'un tour à l'autre."""
    x0, x1, y0, y1 = 6 - m, 10 + m, 4 - m, 8 + m
    h = pas * 0.62
    z, n = z0, 0
    while z < z1 - 0.01:
        t = tons[n % len(tons)]
        cotes = [((x0 - ep, y1, z), (x1 + ep, y1 + ep, z + h)),                      # devant (+y)
                 ((x1, y0 - ep, z + pas / 4), (x1 + ep, y1 + ep, z + pas / 4 + h)),  # dehors (+x)
                 ((x0 - ep, y0 - ep, z + pas / 2), (x1 + ep, y0, z + pas / 2 + h)),  # derrière (-y)
                 ((x0 - ep, y0 - ep, z + 3 * pas / 4), (x0, y1 + ep, z + 3 * pas / 4 + h))]  # dedans (-x)
        for de, a in cotes:
            if a[2] <= z1 + 0.01:
                e.append(boite(de, a, pal, t, nom, motif=motif))
        z += pas
        n += 1


def doigts(e, pal, ton, motif="metal", ext=11.25, segments=True):
    """Poing fermé : quatre doigts repliés, alignés d'avant en arrière. Chaque doigt : la phalange du bout du poing
    (face qui frappe) et le bout replié contre la paume (dedans, -x)."""
    for y0, y1 in ((3.0, 4.375), (4.5, 5.875), (6.125, 7.5), (7.625, 9.0)):
        e.append(boite([5.0, y0, 6.375], [ext, y1, 7.125], pal, ton, "doigt", motif=motif))
        if segments:
            e.append(boite([4.5, y0, 7.0], [5.0, y1, 8.75], pal, max(0, ton - 1), "doigt", motif=motif))


def pouce(e, pal, ton, motif="metal"):
    """Pouce replié sur le devant du poing (+y), côté paume."""
    e.append(boite([4.75, 9.0, 7.25], [7.5, 9.5, 8.25], pal, ton, "pouce", motif=motif))
    e.append(boite([4.875, 9.0, 8.25], [6.75, 9.375, 10.0], pal, max(0, ton - 1), "pouce", motif=motif))


# ================================================================== les cinq armes

def gantelets():
    """Gantelets de fer : gant de mailles sombre, doigts en lames, jointures saillantes, dos de la main en trois lames
    articulées, poignet sanglé, canon évasé par quatre plaques ouvertes vers le coude, aile au coude."""
    e = []
    e.append(autour(1.0625, 6.875, 11.25, FER, 0, "gant", motif="mailles"))
    doigts(e, FER, 2)
    # jointures : une plaque saillante par doigt, sur l'arête du poing
    for y0, y1 in ((3.0, 4.375), (4.5, 5.875), (6.125, 7.5), (7.625, 9.0)):
        e.append(boite([10.25, y0 + 0.125, 5.875], [11.625, y1 - 0.125, 6.875], FER, 4, "jointure"))
    pouce(e, FER, 3)
    # dos de la main : trois lames qui se recouvrent vers le poignet, arête au milieu
    for i, (z0, z1) in enumerate(((6.875, 8.25), (8.0, 9.375), (9.125, 10.5))):
        e.append(boite([11.125 + 0.0625 * i, 3.5 + 0.125 * i, z0], [11.5 + 0.0625 * i, 8.5 - 0.125 * i, z1], FER, 3 - (i % 2), "dos"))
    e.append(boite([11.6875, 5.75, 7.0], [11.875, 6.25, 10.375], FER, 4, "arete"))
    # poignet : sangle de cuir, boucle
    e.append(autour(1.1875, 10.5, 11.5, CUIR, 2, "sangle", motif="cuir"))
    e.append(boite([7.25, 9.1875, 10.625], [8.75, 9.4375, 11.375], LAITON, 3, "boucle"))
    # canon : base, puis quatre plaques inclinées de 22,5° qui s'ouvrent vers le coude
    e.append(autour(1.1875, 11.5, 13.5, FER, 2, "canon"))
    e.append(boite([4.625, 9.25, 13.25], [11.375, 9.625, 16.5], FER, 3, "evase", rot=("x", -22.5, [8, 9.25, 13.25])))
    e.append(boite([4.625, 2.375, 13.25], [11.375, 2.75, 16.5], FER, 2, "evase", rot=("x", 22.5, [8, 2.75, 13.25])))
    e.append(boite([11.25, 3.0, 13.25], [11.625, 9.0, 16.5], FER, 3, "evase", rot=("y", 22.5, [11.25, 6, 13.25])))
    e.append(boite([4.375, 3.0, 13.25], [4.75, 9.0, 16.5], FER, 2, "evase", rot=("y", -22.5, [4.75, 6, 13.25])))
    # rivets du canon
    for x0 in (5.25, 10.25):
        e.append(boite([x0, 9.3125, 12.0], [x0 + 0.5, 9.4375, 12.5], FER, 4, "rivet", motif="plein"))
        e.append(boite([x0, 2.5625, 12.0], [x0 + 0.5, 2.6875, 12.5], FER, 4, "rivet", motif="plein"))
    return e


def bandes_de_combat():
    """Bandes de lin : bandes enroulées en spirale de la main au milieu de l'avant-bras, jointures rembourrées par des
    tours croisés, pouce bandé, deux bouts de bande qui pendent du poignet."""
    e = []
    e.append(autour(1.0625, 6.875, 14.5, LIN, 1, "dessous", motif="tissu"))
    spirale(e, 7.0, 14.25, 1.125, 1.0625, 0.125, LIN, (3, 2), "tour")
    # jointures rembourrées : tours serrés en travers du poing
    e.append(boite([5.5, 3.0, 6.375], [11.375, 9.0, 6.875], LIN, 3, "rembourrage", motif="tissu"))
    e.append(boite([6.0, 3.375, 6.125], [11.125, 8.625, 6.375], LIN, 4, "rembourrage", motif="tissu"))
    # croix sur le dos de la main
    e.append(boite([11.1875, 3.5, 8.0], [11.3125, 4.25, 11.5], LIN, 4, "croix", motif="tissu", rot=("x", 45, [11.25, 6, 9.75])))
    e.append(boite([11.1875, 7.75, 8.0], [11.3125, 8.5, 11.5], LIN, 4, "croix", motif="tissu", rot=("x", -45, [11.25, 6, 9.75])))
    pouce(e, LIN, 3, motif="tissu")
    # bouts de bande qui pendent du poignet (derrière, -y), légèrement écartés
    e.append(boite([6.75, 2.5, 11.0], [7.5, 2.625, 14.0], LIN, 2, "bout", motif="tissu", rot=("y", 22.5, [7.125, 2.5, 14.0])))
    e.append(boite([7.875, 2.5, 10.5], [8.625, 2.625, 14.0], LIN, 3, "bout", motif="tissu"))
    return e


def cestes():
    """Cestes de laiton : un vrai poing américain, rien que le laiton ; le poing du joueur reste visible. Une plaque
    plate en travers des doigts (plan y-z, au milieu du poing) : quatre anneaux soudés côte à côte, leurs trous vides
    sous la barre de frappe, une bosse sur chaque anneau, les bords qui descendent vers la poignée dans la paume.
    La barre et les bosses dépassent la manche d'un plastron (qui couvre la main jusqu'à 1 unité du poing)."""
    e = []
    x0, x1 = 7.625, 8.625     # épaisseur de la plaque, au milieu du poing
    # barre de frappe : le haut des quatre anneaux, devant le bout du poing
    e.append(boite([x0, 3.25, 6.25], [x1, 8.75, 6.875], LAITON, 3, "barre"))
    # une bosse arrondie sur chaque anneau (deux marches)
    for c in (4.125, 5.375, 6.625, 7.875):
        e.append(boite([x0 + 0.125, c - 0.5, 5.875], [x1 - 0.125, c + 0.5, 6.25], LAITON, 4, "bosse"))
        e.append(boite([x0 + 0.25, c - 0.25, 5.625], [x1 - 0.25, c + 0.25, 5.875], LAITON, 4, "bosse", motif="plein"))
    # parois des anneaux : entre les trous (où passent les doigts) et sur les bords
    for y0, y1 in ((3.25, 3.625), (4.625, 4.875), (5.875, 6.125), (7.125, 7.375), (8.375, 8.75)):
        e.append(boite([x0, y0, 6.875], [x1, y1, 8.125], LAITON, 2, "paroi"))
    # dessous des anneaux, juste contre le poing : les trous restent ouverts
    e.append(boite([x0, 3.25, 7.875], [x1, 8.75, 8.125], LAITON, 1, "dessous"))
    # trous arrondis : un coin de laiton dans chaque angle (trous à huit côtés)
    for h0, h1 in ((3.625, 4.625), (4.875, 5.875), (6.125, 7.125), (7.375, 8.375)):
        for yy in (h0, h1 - 0.25):
            for zz in (6.875, 7.625):
                e.append(boite([x0, yy, zz], [x1, yy + 0.25, zz + 0.25], LAITON, 2, "coin", motif="plein"))
    # bouts arrondis de la barre
    e.append(boite([x0, 3.0, 6.5], [x1, 3.25, 7.75], LAITON, 2, "bout"))
    e.append(boite([x0, 8.75, 6.5], [x1, 9.0, 7.75], LAITON, 2, "bout"))
    # bords qui descendent de chaque côté vers la poignée (dans la paume), en se refermant
    e.append(boite([x0, 3.25, 8.125], [x1, 3.625, 9.25], LAITON, 2, "bord"))
    e.append(boite([x0, 8.375, 8.125], [x1, 8.75, 9.25], LAITON, 2, "bord"))
    e.append(boite([x0, 3.25, 9.0], [x1, 3.625, 10.0], LAITON, 3, "bord", rot=("x", 22.5, [8, 3.625, 9.0])))
    e.append(boite([x0, 8.375, 9.0], [x1, 8.75, 10.0], LAITON, 3, "bord", rot=("x", -22.5, [8, 8.375, 9.0])))
    return e


def brassards_de_garde():
    """Brassards de cuir ferré : gant de cuir, long brassard jusqu'au coude avec trois attelles de fer sur le dehors,
    lacets croisés sur le dedans, bandes de fer, garde de coude évasée."""
    e = []
    e.append(autour(1.0625, 6.875, 10.25, CUIR_CLAIR, 2, "gant", motif="cuir"))
    doigts(e, CUIR_CLAIR, 3, motif="cuir", ext=11.125)
    pouce(e, CUIR_CLAIR, 3, motif="cuir")
    # brassard de cuir
    e.append(autour(1.1875, 10.125, 18.75, CUIR, 2, "brassard", motif="cuir"))
    # attelles de fer sur le dehors (+x) et le devant
    for y0, y1 in ((3.25, 4.75), (5.25, 6.75), (7.25, 8.75)):
        e.append(boite([11.1875, y0, 10.75], [11.5, y1, 18.0], FER, 3, "attelle"))
    for x0, x1 in ((5.5, 7.25), (8.75, 10.5)):
        e.append(boite([x0, 9.1875, 11.0], [x1, 9.5, 17.75], FER, 2, "attelle"))
    # bandes de fer
    for z in (10.25, 14.0, 17.75):
        e.append(autour(1.375, z, z + 0.625, FER, 4 if z == 14.0 else 3, "bande"))
    # lacets croisés sur le dedans (-x)
    for z in (11.75, 13.5, 15.25):
        e.append(boite([4.6875, 4.25, z], [4.8125, 7.75, z + 0.375], CUIR_CLAIR, 3, "lacet", motif="plein", rot=("x", 22.5, [4.75, 6, z + 0.1875])))
        e.append(boite([4.6875, 4.25, z], [4.8125, 7.75, z + 0.375], CUIR_CLAIR, 2, "lacet", motif="plein", rot=("x", -22.5, [4.75, 6, z + 0.1875])))
    # garde de coude évasée
    e.append(boite([11.25, 3.0, 18.25], [11.625, 9.0, 20.5], FER, 3, "coude", rot=("y", 22.5, [11.25, 6, 18.25])))
    e += anneau(1.3125, 0.375, 18.625, 19.125, CUIR, 3, "bord", motif="cuir")
    return e


def bandes_rituelles():
    """Bandes du souffle : bandes de papier de riz couvertes d'inscriptions rouges, en spirale, cordon rouge noué au
    poignet avec un gland, deux talismans qui pendent, bracelet de perles de bois."""
    e = []
    e.append(autour(1.0625, 6.875, 14.5, PAPIER, 1, "dessous", motif="tissu"))
    spirale(e, 7.0, 14.25, 1.25, 1.0625, 0.125, PAPIER, (3, 2), "tour", motif="glyphes")
    e.append(boite([5.5, 3.0, 6.375], [11.375, 9.0, 6.875], PAPIER, 3, "rembourrage", motif="tissu"))
    pouce(e, PAPIER, 3, motif="tissu")
    # cordon rouge, nœud et gland
    e.append(autour(1.25, 10.75, 11.0, ROUGE, 2, "cordon", motif="plein"))
    e.append(boite([7.5, 9.25, 10.5], [8.5, 9.75, 11.25], ROUGE, 3, "noeud"))
    e.append(boite([7.875, 9.375, 11.25], [8.125, 9.625, 13.0], ROUGE, 2, "brin", motif="plein"))
    e.append(boite([7.625, 9.25, 13.0], [8.375, 9.75, 14.0], ROUGE, 3, "gland", motif="tissu"))
    # deux talismans de papier qui pendent du cordon, sur le dehors (+x)
    e.append(boite([11.3125, 4.25, 11.0], [11.4375, 6.0, 14.5], PAPIER, 4, "talisman", motif="glyphes", rot=("x", 22.5, [11.375, 5, 11.0])))
    e.append(boite([11.3125, 6.5, 11.0], [11.4375, 8.0, 13.75], PAPIER, 3, "talisman", motif="glyphes"))
    # perles de bois autour du poignet
    for (x0, y0), (x1, y1) in (((5.0, 2.5), (5.5, 3.0)), ((7.75, 2.375), (8.25, 2.875)), ((10.5, 2.5), (11.0, 3.0)),
                              ((11.0, 5.75), (11.5, 6.25)), ((4.5, 5.75), (5.0, 6.25))):
        e.append(boite([x0, y0, 11.875], [x1, y1, 12.375], BOIS, 3, "perle", motif="plein"))
    return e


ARMES = {"gantelets": gantelets, "bandes_de_combat": bandes_de_combat, "cestes": cestes,
         "brassards_de_garde": brassards_de_garde, "bandes_rituelles": bandes_rituelles}


# ================================================================== texture

FACES = ("north", "south", "west", "east", "down", "up")
AXE = {"north": 2, "south": 2, "west": 0, "east": 0, "down": 1, "up": 1}


def dims_face(b, f):
    l = [b["a"][i] - b["de"][i] for i in range(3)]
    if AXE[f] == 1:
        return l[0], l[2]
    if AXE[f] == 0:
        return l[2], l[1]
    return l[0], l[1]


def coins(f, a, b):
    """Coins d'une face dans l'ordre de la texture : haut-gauche, haut-droit, bas-gauche (convention du jeu)."""
    x0, y0, z0 = a
    x1, y1, z1 = b
    return {"north": [(x1, y1, z0), (x0, y1, z0), (x1, y0, z0)], "south": [(x0, y1, z1), (x1, y1, z1), (x0, y0, z1)],
            "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z0)], "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z1)],
            "up": [(x0, y1, z0), (x1, y1, z0), (x0, y1, z1)], "down": [(x0, y0, z1), (x1, y0, z1), (x0, y0, z0)]}[f]


def peindre(px, x0, y0, w, h, b, f, hasard):
    """Peint une face texel par texel d'après sa place sur le cube : aplat, filet clair du côté du coude (+z, en haut
    quand le bras pend) et filet sombre du côté du poing, motif de la matière."""
    pal, ton = b["pal"], b["ton"]
    c = coins(f, b["de"], b["a"])
    p0, p1, p3 = (np.array(v, float) for v in c)
    zmin, zmax = b["de"][2], b["a"][2]
    cap = AXE[f] == 2   # faces des bouts (vers le poing ou le coude) : filets d'après y
    ymin, ymax = b["de"][1], b["a"][1]
    for j in range(h):
        for i in range(w):
            q = p0 + (p1 - p0) * (i + 0.5) / w + (p3 - p0) * (j + 0.5) / h
            t = ton
            motif = b["motif"]
            if motif != "plein":
                if not cap:
                    if q[2] > zmax - 1.0 / PX and zmax - zmin > 2.0 / PX:
                        t = ton + 1
                    elif q[2] < zmin + 1.0 / PX and zmax - zmin > 2.0 / PX:
                        t = ton - 1
                else:
                    if q[1] > ymax - 1.0 / PX and ymax - ymin > 2.0 / PX:
                        t = ton + 1
                    elif q[1] < ymin + 1.0 / PX and ymax - ymin > 2.0 / PX:
                        t = ton - 1
                if motif == "mailles" and t == ton:
                    # mailles : petits anneaux en quinconce (un texel clair sur quatre)
                    k = int(round(q[2] * PX)) + int(round((q[0] + q[1]) * PX))
                    if k % 2 == 0 and int(round(q[2] * PX)) % 2 == 0:
                        t = ton + 1
                elif motif == "tissu" and t == ton and not cap:
                    # trame : une ligne plus sombre tous les 3 texels le long du bras
                    if int(math.floor(q[2] * PX)) % 4 == 3:
                        t = ton - 1
                elif motif == "cuir" and not cap and t == ton:
                    # coutures : points clairs à 2 texels des bords du côté du coude et du poing
                    zz = q[2]
                    if (abs(zz - (zmax - 2.5 / PX)) < 0.5 / PX or abs(zz - (zmin + 2.5 / PX)) < 0.5 / PX) and zmax - zmin > 6.0 / PX:
                        if int(math.floor((q[0] + q[1]) * PX)) % 3 == 0:
                            t = ton + 1
            t = max(0, min(4, t))
            col = pal[t]
            if motif == "glyphes" and w >= 6 and h >= 6:
                # inscriptions à l'encre rouge : petits signes réguliers
                gx, gy = (i + 3 * (j // 13)) % 11, j % 13
                if (gx == 2 and 2 <= gy <= 6) or (gy == 3 and 1 <= gx <= 3) or (gx == 4 and gy == 7) or (gy == 5 and gx == 3):
                    col = ROUGE[1]
            px[x0 + i, y0 + j] = col + (255,)
    # usure : deux petits traits le long du bras sur les grandes faces de métal
    if b["motif"] == "metal" and w >= 14 and h >= 14:
        for _ in range(2):
            i, j = hasard.randrange(2, w - 2), hasard.randrange(2, h - 5)
            for k in range(hasard.randint(2, 3)):
                px[x0 + i, y0 + j + k] = pal[max(0, ton - 1)] + (255,)


def construire(nom, elements, affichage):
    hasard = random.Random(nom)
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    px = img.load()
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
        peindre(px, x, y, w, h, elements[n], f, hasard)
        k = 16 / ATLAS
        places[(n, f)] = [x * k, y * k, (x + w) * k, (y + h) * k]
        x += w
        haut_ligne = max(haut_ligne, h)
    sortie = []
    for n, b in enumerate(elements):
        el = {"name": b["nom"], "from": b["de"], "to": b["a"]}
        if b["rot"]:
            axe, angle, origine = b["rot"]
            el["rotation"] = {"angle": angle, "axis": axe, "origin": [float(v) for v in origine]}
        el["faces"] = {f: {"uv": places[(n, f)], "texture": "#0"} for f in FACES}
        sortie.append(el)
    tex = "exemple/poing/" + nom + "_3d"
    modele = {"texture_size": [ATLAS, ATLAS], "textures": {"0": tex, "particle": tex}, "elements": sortie,
              "display": affichage}
    return modele, img


def retourner(modele):
    """Même arme pour la main gauche : x → 16 - x ; les faces est et ouest s'échangent, les autres ont leur texture
    retournée ; les rotations autour de y et de z changent de sens."""
    g = json.loads(json.dumps(modele))
    for e in g["elements"]:
        x0, x1 = e["from"][0], e["to"][0]
        e["from"][0], e["to"][0] = 16 - x1, 16 - x0
        if "rotation" in e:
            r = e["rotation"]
            r["origin"][0] = 16 - r["origin"][0]
            if r["axis"] in ("y", "z"):
                r["angle"] = -r["angle"]
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


# ================================================================== aperçu calculé (z-buffer)

OMBRE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "west": 0.62, "east": 0.62}
NORMALE = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "down": (0, -1, 0),
           "up": (0, 1, 0)}


def mat_rot(r):
    if not r:
        return np.eye(3), np.zeros(3)
    axe, angle, origine = r["axis"], math.radians(r["angle"]), np.array(r["origin"], float)
    c, s = math.cos(angle), math.sin(angle)
    if axe == "x":
        R = np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    elif axe == "y":
        R = np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    else:
        R = np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
    return R, origine


def matrice(ax, ay):
    ax, ay = math.radians(ax), math.radians(ay)
    ry = np.array([[math.cos(ay), 0, math.sin(ay)], [0, 1, 0], [-math.sin(ay), 0, math.cos(ay)]])
    rx = np.array([[1, 0, 0], [0, math.cos(ax), -math.sin(ax)], [0, math.sin(ax), math.cos(ax)]])
    return rx @ ry


def rendre(elements, tex, M, centre, taille=300, champ=24):
    img = np.zeros((taille, taille, 3))
    img[:] = (44, 46, 54)
    zb = np.full((taille, taille), 1e9)
    ech = taille / champ
    tx = np.array(tex.convert("RGBA"))
    for e in elements:
        R, o = mat_rot(e.get("rotation"))
        for f, fa in e["faces"].items():
            n = M @ (R @ np.array(NORMALE[f], float))
            if n[2] > 1e-6:
                continue
            p0, p1, p3 = [M @ (R @ (np.array(c, float) - o) + o - centre) for c in coins(f, e["from"], e["to"])]
            nu = max(2, int(np.linalg.norm(p1 - p0) * ech * 1.7))
            nv = max(2, int(np.linalg.norm(p3 - p0) * ech * 1.7))
            S, T = np.meshgrid((np.arange(nu) + 0.5) / nu, (np.arange(nv) + 0.5) / nv, indexing="ij")
            Q = p0 + (p1 - p0) * S[..., None] + (p3 - p0) * T[..., None]
            X = (taille / 2 + Q[..., 0] * ech).astype(int)
            Y = (taille / 2 - Q[..., 1] * ech).astype(int)
            if "couleur" in fa:
                C = np.broadcast_to(np.array(fa["couleur"], float), S.shape + (3,))
                A = np.full(S.shape, 255)
            else:
                u0, v0, u1, v1 = fa["uv"]
                U = ((u0 + (u1 - u0) * S) / 16 * tx.shape[1]).astype(int).clip(0, tx.shape[1] - 1)
                V = ((v0 + (v1 - v0) * T) / 16 * tx.shape[0]).astype(int).clip(0, tx.shape[0] - 1)
                C = tx[V, U, :3].astype(float)
                A = tx[V, U, 3]
            # lumière : celle du jeu par face, plus un peu d'après la vraie orientation (faces tournées)
            lum = OMBRE[f] if not e.get("rotation") else 0.55 + 0.45 * max(0.0, -n[2]) ** 0.5
            for i, j in zip(*np.nonzero(A > 10)):
                x, y, z = X[i, j], Y[i, j], Q[i, j, 2]
                if 0 <= x < taille and 0 <= y < taille and z < zb[y, x]:
                    zb[y, x] = z
                    img[y, x] = C[i, j] * lum
    return Image.fromarray(img.astype(np.uint8))


def bras_peau():
    return [{"from": [6, 4, 8.01], "to": [10, 8, 23], "faces": {f: {"couleur": PEAU} for f in FACES}}]


def manche_plastron():
    """Manche d'un plastron du jeu (marge 1), en bleu, pour vérifier que l'arme la dépasse."""
    return [{"from": [5, 3, 7], "to": [11, 9, 23], "faces": {f: {"couleur": (110, 140, 185)} for f in FACES}}]


def apercu(modeles, textures, chemin):
    """Une ligne par arme : dehors (dos de la main), dedans (paume, pouce), de face (jointures), sur une manche de
    plastron, main gauche."""
    debout = np.array([[1, 0, 0], [0, 0, 1], [0, -1, 0]], float)
    centre = np.array([8.0, 6.0, 12.5])
    vues = [("d", matrice(15, -40) @ debout), ("d", matrice(15, 60) @ debout), ("d", matrice(70, -20) @ debout),
            ("p", matrice(15, -40) @ debout), ("g", matrice(15, 40) @ debout)]
    t = 300
    img = Image.new("RGB", (t * len(vues), t * len(modeles)), (44, 46, 54))
    for r, nom in enumerate(modeles):
        for c, (genre, M) in enumerate(vues):
            el = (retourner(modeles[nom]) if genre == "g" else modeles[nom])["elements"]
            el = el + (manche_plastron() if genre == "p" else bras_peau())
            img.paste(rendre(el, textures[nom], M, centre, taille=t), (t * c, t * r))
    img.save(chemin)


def main():
    os.makedirs(MODELES, exist_ok=True)
    os.makedirs(TEXTURES, exist_ok=True)
    modeles, textures = {}, {}
    seules = [a for a in sys.argv[2:]] or list(ARMES)
    for nom in seules:
        modele, tex = construire(nom, ARMES[nom](), AFFICHAGE)
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
