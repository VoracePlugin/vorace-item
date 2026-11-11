#!/bin/bash
# Essai automatique sur un vrai serveur Paper (CI) : vorace-core et vorace-item démarrent, un faux joueur reçoit une
# épée modulaire et frappe un cochon (l'épée doit s'user par vorace-item, pas par le jeu), les pièces données une à une
# sont montées en épée, puis « /vitem essai » vérifie les objets, le pack, l'équipement porté et l'usure.
# Usage : outils/essai-serveur.sh <version de Paper> <dossier contenant Vorace-*.jar et VoraceItem-*.jar>
set -uo pipefail
VERSION=${1:-1.21.4}
JARS=$(cd "$2" && pwd)
OUTILS=$(cd "$(dirname "$0")" && pwd)
mkdir -p essai-serveur/plugins && cd essai-serveur
cp "$JARS"/Vorace-*.jar "$JARS"/VoraceItem-*.jar plugins/ || { echo "Il faut Vorace-*.jar et VoraceItem-*.jar dans $JARS"; exit 1; }
URL=$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/$VERSION/builds/latest" | python3 -c 'import json,sys; print(json.load(sys.stdin)["downloads"]["server:default"]["url"])')
echo "Paper : $URL"
curl -fsSL -o paper.jar "$URL"
echo "eula=true" > eula.txt
cat > server.properties <<PROPS
online-mode=false
level-type=minecraft\:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
generate-structures=false
spawn-protection=0
spawn-monsters=false
spawn-animals=false
view-distance=4
simulation-distance=4
PROPS
rm -f console && mkfifo console
exec 3<>console
java -Xmx2G -jar paper.jar --nogui < console > journal.txt 2>&1 &
SERVEUR=$!
cmd() { echo ">>> $1" >> commandes.txt; echo "$1" >&3; }
attendre() { # attendre <motif> <fichier> <secondes>
  for _ in $(seq 1 "$3"); do grep -aq "$1" "$2" 2>/dev/null && return 0; sleep 1; done; return 1
}
if ! attendre 'Done (' journal.txt 400; then echo "Le serveur n'a pas démarré"; tail -80 journal.txt; exit 1; fi
sleep 3
cmd "vitem recharger"
sleep 8
node "$OUTILS/essai-bot.mjs" 35 > bot.txt 2>&1 &
BOT=$!
attendre 'BOT CONNECTE' bot.txt 60 || { echo "Le faux joueur ne s'est pas connecté"; cat bot.txt; }
sleep 2
cmd "vitem assembler Testeur epee fer/lame_droite,fer/garde_droite,fer/poignee_cuir,fer/pommeau_rond"
# le monde a ses propres cochons : on les retire, et celui de l'essai porte une étiquette
cmd "kill @e[type=pig]"
cmd "execute at Testeur run summon pig ^ ^ ^2 {NoAI:1b,Tags:[\"essai\"]}"
attendre 'BOT ATTAQUE' bot.txt 30 || echo "Le faux joueur n'a pas attaqué"
attendre 'BOT APRES' bot.txt 10 || echo "Le faux joueur n'a pas regardé son épée après le coup"
cmd "kill @e[type=pig]"
# les pièces en objets : quatre pièces données une à une, puis montées en épée
for piece in fer/lame_large acier/garde_droite cuivre/poignee_cuir acier/pommeau_rond; do cmd "vitem piece Testeur $piece 80"; done
cmd "vitem monter Testeur epee"
cmd "vitem essai"
attendre 'ESSAI VORACE-ITEM' journal.txt 30 || echo "L'essai n'a pas fini"
wait $BOT
cmd "stop"
for _ in $(seq 1 60); do kill -0 $SERVEUR 2>/dev/null || break; sleep 1; done
kill $SERVEUR 2>/dev/null

echo "===== Faux joueur"; cat bot.txt
echo "===== Console (vorace-item)"; grep -aiE 'voraceitem|vorace-item|ESSAI|exception|error|at fr\.' journal.txt | grep -av 'Loading server plugin' | head -150
echo "===== Commandes envoyées, dans l'ordre"; cat commandes.txt
echo "===== Fin du journal"; tail -25 journal.txt

# verdict
grep -aq 'ESSAI VORACE-ITEM : [0-9]* réussi(s), 0 échec(s)' journal.txt || { echo "ECHEC : /vitem essai"; exit 1; }
if grep -aq 'équipement non essayé' journal.txt; then echo "ECHEC : l'équipement n'a pas été essayé (pas de joueur)"; exit 1; fi
grep -a 'BOT OBJET' bot.txt | grep -q 'voraceitem:kit/epee' || { echo "ECHEC : le joueur n'a pas reçu l'épée modulaire (modèle composé)"; exit 1; }
grep -a 'BOT OBJET' bot.txt | grep -q 'fer/lame_droite' || { echo "ECHEC : l'épée reçue par le joueur ne porte pas ses pièces"; exit 1; }
# l'épée frappée a perdu un point de durabilité de vorace-item : la barre de l'objet a bougé
AVANT=$(grep -a 'BOT AVANT' bot.txt | grep -oE '"type":"damage","data":[0-9]+' | grep -oE '[0-9]+$')
APRES=$(grep -a 'BOT APRES' bot.txt | grep -oE '"type":"damage","data":[0-9]+' | grep -oE '[0-9]+$')
echo "usure de l'épée vue par le joueur : ${AVANT:-0} -> ${APRES:-0}"
[ "${APRES:-0}" -gt "${AVANT:-0}" ] || { echo "ECHEC : l'épée ne s'est pas usée par vorace-item après le coup"; exit 1; }
grep -aq 'Monté pour Testeur : fer/lame_large, acier/garde_droite, cuivre/poignee_cuir, acier/pommeau_rond (qualité Excellente, score 80)' journal.txt || { echo "ECHEC : les quatre pièces données au joueur n'ont pas été montées en épée"; exit 1; }
ERREURS=$(grep -acE 'ERROR\]: \[VoraceItem\]|at fr\.vorace\.item\.|WARN\]: \[VoraceItem\]' journal.txt)
echo "erreurs et alertes de vorace-item dans la console : $ERREURS"
if [ "$ERREURS" -gt 0 ]; then echo "ECHEC : erreurs ou alertes de vorace-item dans la console"; exit 1; fi
echo "ESSAI REUSSI"
