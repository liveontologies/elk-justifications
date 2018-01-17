#!/bin/bash

TIMEOUT=$1
shift
GLOBAL_TIMEOUT=$1
shift
SOURCE=$1
shift
INPUT=$1
shift
MACHINE_NAME=$1
shift
JAR=$1
shift
SCRIPTS_DIR=$1
shift
WORKSPACE_DIR=$1
shift
QUERY_GENERATION_OPTIONS=$1
shift

INPUT_DIR=$WORKSPACE_DIR/input
ONTOLOGIES_DIR=$WORKSPACE_DIR/ontologies
QUERIES_DIR=$WORKSPACE_DIR/queries
ENCODING_DIR=$WORKSPACE_DIR/elk_sat
EXPERIMENT_DIR=$WORKSPACE_DIR/experiments
LOGS_DIR=$WORKSPACE_DIR/logs
RESULTS_DIR=$WORKSPACE_DIR/results
PLOT_FILE=$WORKSPACE_DIR/plot.svg
RESULTS_ARCHIVE=$WORKSPACE_DIR/results.zip



DATE=`date +%y-%m-%d`
TIME_LOG_FORMAT='+%y-%m-%d %H:%M:%S'



# Obtain the ontologies

INPUT_FILE=""
if [ $SOURCE == "file" ]
then
	
	INPUT_FILE=$INPUT
	
elif [ $SOURCE == "web" ]
then
	
	echo `date "$TIME_LOG_FORMAT"` "downloading the input ontologies"
	
	rm -rf $INPUT_DIR
	mkdir -p $INPUT_DIR
	
	CURRENT_DIR=`pwd`
	cd $INPUT_DIR

	wget -nv $INPUT
	INPUT_FILE=$(realpath $(ls -1 | head -n1))

	cd $CURRENT_DIR
	
else
	
	>&2 echo `date "$TIME_LOG_FORMAT"` "Wrong option for the 3rd argument! Must be one of {file,web}."
	exit 2
	
fi

rm -rf $ONTOLOGIES_DIR
mkdir -p $ONTOLOGIES_DIR

if [[ $INPUT_FILE == *.tar.gz ]] ||  [[ $INPUT_FILE == *.tgz ]]
then
	
	echo `date "$TIME_LOG_FORMAT"` "extracting the input ontologies"
	
	ABSPLUTE_ONTOLOGIES_ARCHIVE=`realpath $INPUT_FILE`
	CURRENT_DIR=`pwd`
	cd $ONTOLOGIES_DIR
	
	tar xzf $ABSPLUTE_ONTOLOGIES_ARCHIVE
	
	cd $CURRENT_DIR
	
elif [[ $INPUT_FILE == *.zip ]]
then
	
	echo `date "$TIME_LOG_FORMAT"` "extracting the input ontologies"
	
	ABSPLUTE_ONTOLOGIES_ARCHIVE=`realpath $INPUT_FILE`
	CURRENT_DIR=`pwd`
	cd $ONTOLOGIES_DIR
	
	unzip -q $ABSPLUTE_ONTOLOGIES_ARCHIVE
	
	cd $CURRENT_DIR
	
else
	
	cp $INPUT_FILE $ONTOLOGIES_DIR
	
fi



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
	PROPS='-Delk.reasoner.tracing.evictor=RecencyEvictor(16896,0.75)'
	java -Xmx7G -Xms2G $PROPS -cp $JAR org.liveontologies.pinpointing.DirectSatEncodingUsingElkCsvQuery $ONTOLOGY $QUERIES_DIR/$NAME.queries.sorted $ENCODING_DIR/$NAME.elk_sat 2>&1 > $ENCODING_DIR/$NAME.out.log | tee $ENCODING_DIR/$NAME.err.log 1>&2
	
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



# Plot the plots

echo `date "$TIME_LOG_FORMAT"` "plotting"

echo "" > $PLOT_FILE

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

./$SCRIPTS_DIR/plot_row.r $PLOT_FILE $PLOT_LEGEND -- $PLOT_ARGS >/dev/null 2>/dev/null

cp $PLOT_FILE $RESULTS_DIR



# Pack the results

echo `date "$TIME_LOG_FORMAT"` "packing the results"

CURRENT_DIR=`pwd`
cd $WORKSPACE_DIR

zip -r -q $CURRENT_DIR/$RESULTS_ARCHIVE `basename $RESULTS_DIR`

cd $CURRENT_DIR



echo `date "$TIME_LOG_FORMAT"` "Done."



