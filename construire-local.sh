#!/bin/bash
# Compilation sans Gradle (pour les environnements sans accès à repo.papermc.io).
# Usage : ./construire-local.sh <classpath contenant paper-api 1.21.4 et ses dépendances (adventure, gson, guava, snakeyaml)> <Vorace-*.jar>
# Avec « tests » en troisième argument, les tests sans serveur sont aussi compilés et lancés.
set -e
CP="$1:$2"
V=$(grep '^version=' gradle.properties | cut -d= -f2)
rm -rf build/local && mkdir -p build/local/classes build/local/tests build/libs
javac -Xlint:all,-processing,-classfile -encoding UTF-8 --release 21 -cp "$CP" -d build/local/classes $(find src/main/java -name "*.java")
cp -r src/main/resources/. build/local/classes/
sed "s/\${version}/$V/" src/main/resources/plugin.yml > build/local/classes/plugin.yml
cp LICENSE build/local/classes/LICENSE-VoraceItem.txt
(cd build/local/classes && jar --create --file ../../libs/VoraceItem-$V.jar .)
ls -la build/libs/VoraceItem-$V.jar
if [ "$3" = "tests" ]; then
  javac -nowarn -encoding UTF-8 --release 21 -cp "$CP:build/local/classes" -d build/local/tests $(find src/test/java -name "*.java")
  java -cp "$CP:build/local/classes:build/local/tests" fr.vorace.item.Tests
fi
