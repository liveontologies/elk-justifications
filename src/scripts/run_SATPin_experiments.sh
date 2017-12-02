#!/bin/bash

EXE=$1
TIMEOUT=$2
GLOBAL_TIMEOUT=$3
QUERY_FILE=$4
ENCODING_DIR=$5
OUTPUT_DIR=$6

rm -rf $OUTPUT_DIR
mkdir -p $OUTPUT_DIR

echo "query,didTimeOut,time,realTime,cpuTime,nJust" > $OUTPUT_DIR/record.csv

G_START_NANO=`date +%s%N`
TOTAL_TIME_NANOS=0

cat $QUERY_FILE | sed "s/[^a-zA-Z0-9_.-]/_/g" | while read LINE
do
	
	QUERY_DIR=$ENCODING_DIR/$LINE
	if [ ! -s $QUERY_DIR ]
	then
		>&2 echo "No Input Dir: " $QUERY_DIR
		break
	fi
	
	echo -n '"'`cat $QUERY_DIR/encoding.query`'"', >> $OUTPUT_DIR/record.csv
	
	LOG_DIR=$OUTPUT_DIR/$LINE
	mkdir -p $LOG_DIR
	
	START_NANO=`date +%s%N`
	cat $QUERY_DIR/encoding.h $QUERY_DIR/encoding.cnf | timeout -s9 $(($TIMEOUT + 10)) $EXE -assumptions=$QUERY_DIR/encoding.assumptions -question=$QUERY_DIR/encoding.question -cpu-lim=$TIMEOUT -rotate=1 -no-eldbg -reduce=0 -modelClauses -keepSearch -minimal 2>&1 > $LOG_DIR/out.log | tee $LOG_DIR/err.log 1>&2
	END_NANO=`date +%s%N`
	
	RUN_TIME_NANOS=$(($END_NANO - $START_NANO))
	RUN_TIME_MILLIS=`echo "scale=6; $RUN_TIME_NANOS/1000000.0" | bc`
	
	if [ $(( $TIMEOUT * 1000000000 )) -lt $RUN_TIME_NANOS ]
	then
		echo -n TRUE, >> $OUTPUT_DIR/record.csv
	else
		echo -n FALSE, >> $OUTPUT_DIR/record.csv
	fi
	echo -n $RUN_TIME_MILLIS, >> $OUTPUT_DIR/record.csv
	echo -n $RUN_TIME_MILLIS, >> $OUTPUT_DIR/record.csv
	if grep "(unsat:" $LOG_DIR/out.log > /dev/null 2> /dev/null
	then
		PT=`grep "Parse time" $LOG_DIR/out.log | sed "s/[^0-9]*\([0-9]\+\.\?[0-9]*\)[^0-9]*/\1/g"`
        	CT=`grep "CPU time" $LOG_DIR/out.log | sed "s/[^0-9]*\([0-9]\+\.\?[0-9]*\)[^0-9]*/\1/g"`
        	echo -n `echo "scale=6; $CT * 1000 - $PT * 1000" | bc`, >> $OUTPUT_DIR/record.csv
        	JS=`grep "(unsat:" $LOG_DIR/out.log | sed "s/[^(]*(unsat: \([0-9]\+\.\?[0-9]*\))[^)]*/\1/g"`
        	echo $JS >> $OUTPUT_DIR/record.csv
	else
		echo -n $RUN_TIME_MILLIS, >> $OUTPUT_DIR/record.csv
		echo 0 >> $OUTPUT_DIR/record.csv
	fi
	
	TOTAL_TIME_NANOS=$(( $TOTAL_TIME_NANOS + $RUN_TIME_NANOS ))
	if [ $(( $GLOBAL_TIMEOUT * 1000000000 )) -lt $TOTAL_TIME_NANOS ]
	then
		>&2 echo "Global Timeout"
		break
	fi
	
done


