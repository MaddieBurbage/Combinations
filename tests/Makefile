GCC=riscv64-unknown-elf-gcc
OBJDUMP=riscv64-unknown-elf-objdump
CFLAGS=-mcmodel=medany -std=gnu99 -O2 -fno-common -fno-builtin-printf -Wall
WIDTH=6 #Width of the string, 1-32
FUNCT=0 #The 6 function types, 0-2 and 4-6
WARE=0 #0 for hardware 1 for software
LDFLAGS=-static -nostdlib -nostartfiles -lgcc


# Change this to add tests
PROGRAMS = fixedWeightCombinations generalCombinations timeTests memoryTest

default: $(addsuffix .riscv,$(PROGRAMS))

dumps: $(addsuffix .dump,$(PROGRAMS))

%.o: %.S
	$(GCC) $(CFLAGS) -D__ASSEMBLY__=1 -c $< -o $@

%.o: %.c mmio.h
	$(GCC) $(CFLAGS) -DWIDTH=$(WIDTH) -FUNCT=$(FUNCT) -DWARE=$(WARE) -c $< -o $@

%.S: %.c mmio.h
	$(GCC) $(CFLAGS) -S -c $< -o $@

%.riscv: %.o crt.o syscalls.o link.ld
	$(GCC) -T link.ld $(LDFLAGS) $< crt.o syscalls.o -o $@

%.dump: %.riscv
	$(OBJDUMP) -D $< > $@

clean:
	rm -f *.riscv *.o *.dump
