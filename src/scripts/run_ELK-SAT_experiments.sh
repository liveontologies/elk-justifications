#!/bin/bash

TIMEOUT=$1
shift
GLOBAL_TIMEOUT=$1
shift
ONTOLOGIES_DIR=$1
shift
QUERIES_DIR=$1
shift
ENCODING_DIR=$1
shift
EXPERIMENT_DIR=$1
shift
MACHINE_NAME=$1
shift
JAR=$1
shift
SCRIPTS_DIR=$1
shift
OUTPUT_DIR=$1
shift
OUTPUT_FILE=$1
shift



DATE=`date +%y-%m-%d`



# Generate queries

if [ -e $QUERIES_DIR ] && [ ! -d $QUERIES_DIR ]
then
	rm -rf $QUERIES_DIR
fi
mkdir -p $QUERIES_DIR

for ONTOLOGY in $ONTOLOGIES_DIR/*
do
	
	NAME=`basename -s ".owl" $ONTOLOGY`
	echo "generating queries for $NAME"
	java -Xmx7G -Xms2G -cp $JAR org.liveontologies.pinpointing.ExtractSubsumptions --direct --nobottom --sort $ONTOLOGY $QUERIES_DIR/$NAME.queries.sorted 2>&1 > $QUERIES_DIR/$NAME.out.log | tee $QUERIES_DIR/$NAME.err.log 1>&2
	java -Xmx7G -Xms2G -cp $JAR org.liveontologies.pinpointing.Shuffler 1 < $QUERIES_DIR/$NAME.queries.sorted > $QUERIES_DIR/$NAME.queries.seed1
	
done



# Encode ELK inferences to SAT

if [ -e $ENCODING_DIR ] && [ ! -d $ENCODING_DIR ]
then
	rm -rf $ENCODING_DIR
fi
mkdir -p $ENCODING_DIR

for ONTOLOGY in $ONTOLOGIES_DIR/*
do
	
	NAME=`basename -s ".owl" $ONTOLOGY`
	echo "encoding $NAME"
	java -Xmx7G -Xms2G -cp $JAR org.liveontologies.pinpointing.DirectSatEncodingUsingElkCsvQuery $ONTOLOGY $QUERIES_DIR/$NAME.queries.seed1 $ENCODING_DIR/$NAME.elk_sat 2>&1 > $ENCODING_DIR/$NAME.out.log | tee $ENCODING_DIR/$NAME.err.log 1>&2
	
done



# Run the experiments

for EXPERIMENT in $EXPERIMENT_DIR/*
do
	
	EXPERIMENT_NAME=`basename -s ".sh" $EXPERIMENT`
	echo "running experiment $EXPERIMENT_NAME ..."
	
	for ONTOLOGY in $ONTOLOGIES_DIR/*
	do
	
		NAME=`basename -s ".owl" $ONTOLOGY`
		echo "... on $NAME"
		DIR_NAME=$DATE.$NAME.$EXPERIMENT_NAME.$MACHINE_NAME.elk_sat
		rm -rf $OUTPUT_DIR/$DIR_NAME
		mkdir -p $OUTPUT_DIR/$DIR_NAME
		./$EXPERIMENT $TIMEOUT $GLOBAL_TIMEOUT $QUERIES_DIR/$NAME.queries.seed1 $ENCODING_DIR/$NAME.elk_sat $SCRIPTS_DIR $OUTPUT_DIR/$DIR_NAME
	
	done
	
done



echo "packing the results"
tar czf $OUTPUT_FILE $OUTPUT_DIR



echo "Done."



