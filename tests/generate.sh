#!
WIDTH=2
FUNCT=0
WARE=0

while [ $FUNCT -lt 3 ]; do
    while [ $WIDTH -lt 5 ]; do
        while [ $WARE -lt 2 ]; do
            make timeTests.riscv
            mv timeTests.riscv timeTests-$FUNCT-$WARE-$WIDTH.riscv
            let WARE=$WARE+1
        done
        let WIDTH=$WIDTH*2
    done
    let FUNCT=$FUNCT+1
done


WIDTH=2
FUNCT=4
WARE=0
while [ $FUNCT -lt 7 ]; do
    while [ $WIDTH -lt 5 ]; do
        while [ $WARE -lt 2 ]; do
            make timeTests.riscv
            mv timeTests.riscv timeTests-$FUNCT-$WARE-$WIDTH.riscv
            let WARE=$WARE+1
        done
        let WIDTH=$WIDTH*2
    done
    let FUNCT=$FUNCT+1
done
