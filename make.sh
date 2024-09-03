#! /bin/sh

javac samples/Main.java -d samples

javac -cp .:lib/soot-iitmandi-patched.jar:lib/z3-turnkey-4.13.0.jar Main.java

java -cp .:lib/soot-iitmandi-patched.jar:lib/z3-turnkey-4.13.0.jar Main

#javap -c -v -l Main