#! /bin/sh

javac -cp .:lib/soot-4.4.1-jar-with-dependencies.jar:lib/z3-turnkey-4.13.0.jar Main.java

java -cp .:lib/soot-4.4.1-jar-with-dependencies.jar:lib/z3-turnkey-4.13.0.jar Main

#javap -c -v -l Main