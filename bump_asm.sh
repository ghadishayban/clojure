#!/bin/bash

NIGHTLY_URI=http://forge.ow2.org/svnsnapshots/asm-svn-latest.tar.gz

ASM_BASEDIR=asm-master
mkdir $ASM_BASEDIR
curl $NIGHTLY_URI | tar --extract -z --strip-components=1 -C $ASM_BASEDIR

CLJASM=src/jvm/clojure/asm

git rm -r $CLJASM
mkdir $CLJASM

rsync -rt \
      $ASM_BASEDIR/asm/src/org/objectweb/asm/ \
      --exclude '*Remapper.java' \
      --exclude 'Remapping*.java' \
      --exclude 'JSRInlinerAdapter.java' \
      --exclude 'TryCatchBlockSorter.java' \
      --include '*.java' \
      --include commons \
      --exclude '*' \
      $CLJASM

find $CLJASM -name '*.java' -print0 | xargs -0 sed -i 's/org.objectweb.asm/clojure.asm/g'
git add $CLJASM
git commit -m "vendoring asm to $(date '+%F') svn snapshot"

rm -rf $ASM_BASEDIR
