#!/bin/bash

MAX=6
WIDTHS=(2 4 8 14 16 20 24 32)
export FUNCT=0
while [ $FUNCT -lt 3 ]; do
    WIDTHI=0
    export WIDTH=${WIDTHS[0]}
    while [ $WIDTHI -lt $MAX ]; do
	WIDTH=${WIDTHS[$WIDTHI]}
	export WARE=0
	while [ $WARE -lt 2 ]; do
            tests/timeTests-$FUNCT-$WARE-$WIDTH >> results-$FUNCT-$WARE.out

            let WARE=$WARE+1
        done
        let WIDTHI=$WIDTHI+1
    done
    let FUNCT=$FUNCT+1
done

MAX=4

FUNCT=4
while [ $FUNCT -lt 7 ]; do
    WIDTHI=0
    WIDTH=${WIDTHS[0]}
    while [ $WIDTHI -lt $MAX ]; do
        WIDTH=${WIDTHS[$WIDTHI]}
	WARE=0
	while [ $WARE -lt 2 ]; do
            tests/timeTests-$FUNCT-$WARE-$WIDTH >> results-$FUNCT-$WARE.out

            let WARE=$WARE+1
        done
        let WIDTHI=$WIDTHI+1
    done
    let FUNCT=$FUNCT+1
done

echo Made tests for functions up to $FUNCT-1 and widths up to $WIDTH
