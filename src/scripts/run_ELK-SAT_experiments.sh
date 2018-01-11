#!/bin/bash

TIMEOUT=$1
shift
GLOBAL_TIMEOUT=$1
shift
ONTOLOGIES_ARCHIVE=$1
shift
EXPERIMENT_DIR=$1
shift
MACHINE_NAME=$1
shift
JAR=$1
shift
SCRIPTS_DIR=$1
shift
WORKSPACE_DIR=$1
shift
RESULTS_ARCHIVE=$1
shift
PLOT_FILE=$1
shift
QUERY_GENERATION_OPTIONS=$1
shift

ONTOLOGIES_DIR=$WORKSPACE_DIR/ontologies
QUERIES_DIR=$WORKSPACE_DIR/queries
ENCODING_DIR=$WORKSPACE_DIR/elk_sat
LOGS_DIR=$WORKSPACE_DIR/logs
RESULTS_DIR=$WORKSPACE_DIR/results



DATE=`date +%y-%m-%d`
TIME_LOG_FORMAT='+%y-%m-%d %H:%M:%S'



# Extract ontologies

rm -rf $ONTOLOGIES_DIR
mkdir -p $ONTOLOGIES_DIR

echo `date "$TIME_LOG_FORMAT"` "extracting the input ontologies"

ABSPLUTE_ONTOLOGIES_ARCHIVE=`realpath $ONTOLOGIES_ARCHIVE`
CURRENT_DIR=`pwd`
cd $ONTOLOGIES_DIR

tar xzf $ABSPLUTE_ONTOLOGIES_ARCHIVE

cd $CURRENT_DIR



# Generate queries

if [ -e $QUERIES_DIR ] && [ ! -d $QUERIES_DIR ]
then
	rm -rf $QUERIES_DIR
fi
mkdir -p $QUERIES_DIR

for ONTOLOGY in $ONTOLOGIES_DIR/*
do
	
	NAME=`basename -s ".owl" $ONTOLOGY`
	echo `date "$TIME_LOG_FORMAT"` "generating queries for $NAME"
	java -Xmx7G -Xms2G -cp $JAR org.liveontologies.pinpointing.ExtractSubsumptions $QUERY_GENERATION_OPTIONS --sort $ONTOLOGY $QUERIES_DIR/$NAME.queries.sorted 2>&1 > $QUERIES_DIR/$NAME.out.log | tee $QUERIES_DIR/$NAME.err.log 1>&2
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
	echo `date "$TIME_LOG_FORMAT"` "encoding $NAME"
	PROPS='-Delk.reasoner.tracing.evictor=NQEvictor(25600,0.75,3500000,0.75)'
	java -Xmx7G -Xms2G $PROPS -cp $JAR org.liveontologies.pinpointing.DirectSatEncodingUsingElkCsvQuery $ONTOLOGY $QUERIES_DIR/$NAME.queries.seed1 $ENCODING_DIR/$NAME.elk_sat 2>&1 > $ENCODING_DIR/$NAME.out.log | tee $ENCODING_DIR/$NAME.err.log 1>&2
	
done



# Run the experiments

rm -rf $RESULTS_DIR
mkdir -p $RESULTS_DIR

for EXPERIMENT in $EXPERIMENT_DIR/*
do
	
	EXPERIMENT_NAME=`basename -s ".sh" $EXPERIMENT`
	echo `date "$TIME_LOG_FORMAT"` "running experiment $EXPERIMENT_NAME ..."
	
	for ONTOLOGY in $ONTOLOGIES_DIR/*
	do
	
		NAME=`basename -s ".owl" $ONTOLOGY`
		echo `date "$TIME_LOG_FORMAT"` "... on $NAME"
		DIR_NAME=$DATE.$NAME.$EXPERIMENT_NAME.$MACHINE_NAME.elk_sat
		rm -rf $LOGS_DIR/$DIR_NAME
		mkdir -p $LOGS_DIR/$DIR_NAME
		./$EXPERIMENT $TIMEOUT $GLOBAL_TIMEOUT $QUERIES_DIR/$NAME.queries.seed1 $ENCODING_DIR/$NAME.elk_sat $SCRIPTS_DIR $LOGS_DIR/$DIR_NAME
		cp $LOGS_DIR/$DIR_NAME/record.csv $RESULTS_DIR/$DIR_NAME.csv
	
	done
	
done



# Pack the results

echo `date "$TIME_LOG_FORMAT"` "packing the results"

CURRENT_DIR=`pwd`
cd $WORKSPACE_DIR

tar czf $CURRENT_DIR/$RESULTS_ARCHIVE `basename $RESULTS_DIR`

cd $CURRENT_DIR



# Plot the plots

echo `date "$TIME_LOG_FORMAT"` "plotting"

PLOT_LEGEND=""
PLOT_ARGS=""
for EXPERIMENT in $EXPERIMENT_DIR/*
do
	
	EXPERIMENT_NAME=`basename -s ".sh" $EXPERIMENT`
	PLOT_ARGS="$PLOT_ARGS $EXPERIMENT_NAME"
	
	PLOT_LEGEND=""
	for ONTOLOGY in $ONTOLOGIES_DIR/*
	do

		NAME=`basename -s ".owl" $ONTOLOGY`
		PLOT_LEGEND="$PLOT_LEGEND $NAME"
	
		DIR_NAME=$DATE.$NAME.$EXPERIMENT_NAME.$MACHINE_NAME.elk_sat
		RECORD=$LOGS_DIR/$DIR_NAME/record.csv
		
		PLOT_ARGS="$PLOT_ARGS $RECORD"
	
	done
	
done

#echo `date "$TIME_LOG_FORMAT"` "./$SCRIPTS_DIR/plot_row.r $PLOT_FILE $PLOT_LEGEND -- $PLOT_ARGS"
./$SCRIPTS_DIR/plot_row.r $PLOT_FILE $PLOT_LEGEND -- $PLOT_ARGS



echo `date "$TIME_LOG_FORMAT"` "Done."



