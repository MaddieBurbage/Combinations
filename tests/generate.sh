#!/bin/bash

export FUNCT=0
while [ $FUNCT -lt 3 ]; do
    export WIDTH=2
    while [ $WIDTH -lt 5 ]; do
	export WARE=0
	while [ $WARE -lt 2 ]; do
            make timeTests.riscv
            mv timeTests.riscv timeTests-$FUNCT-$WARE-$WIDTH.riscv
            let WARE=$WARE+1
        done
        let WIDTH=$WIDTH*2
    done
    let FUNCT=$FUNCT+1
done

FUNCT=4
while [ $FUNCT -lt 7 ]; do
    WIDTH=2
    while [ $WIDTH -lt 5 ]; do
	WARE=0
	while [ $WARE -lt 2 ]; do
            make timeTests.riscv
            mv timeTests.riscv timeTests-$FUNCT-$WARE-$WIDTH.riscv
            let WARE=$WARE+1
        done
        let WIDTH=$WIDTH*2
    done
    let FUNCT=$FUNCT+1
done

echo Made tests for functions up to $FUNCT-1 and widths up to $WIDTH/2
