#!/bin/sh

MAIN_CLASS=edu.berkeley.icsi.metaphor.javawikibots.LinguisticMetaphorImporter
DISTDIR=dist

CLASSPATH=$DISTDIR/*:$DISTDIR/lib/*
export CLASSPATH

java $MAIN_CLASS $@
