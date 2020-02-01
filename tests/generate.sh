#!
WIDTH=2
FUNCT=0
WARE=0

while [ $WIDTH -lt 5 ]; do
    make timeTests.riscv
    mv timeTests.riscv timeTests-$FUNCT-$WARE-$WIDTH.riscv
    let WIDTH=$WIDTH*2
done
