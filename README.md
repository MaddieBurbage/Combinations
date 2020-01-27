# Combinations
Combinations Accelerators by the Williams College Bailey Research Group


Using custom RISCV instructions, the combinations accelerator can be called on to create sequences of binary strings and generate new strings in certain patterns. This accelerator supports operations on strings up to 32 bits long.

## Instructions

This accelerator is called by the custom0 RISCV instruction. There are three combination types it can generate, and it can either return the following string of a cycle or save a full cycle of strings to memory. The operation performed is specified by the function code within the instruction. The first source register always contains parameters for the combinations. For memory instructions, the second source register contains the address to use for stores, and all valid binary strings for the combination type will be stored as 64-bit values starting from that location. For return instructions, the second source register contains a string in the cycle and the following string will be saved in the destination register, unless the cycle is complete and -1 is outputted instead.

Register 1 is 64 bits wide, and in the bottom 5 bits should hold the length of the string. For ranged combinations, bits 9-5 should contain the minimum weight and bits 14-10 should contain the maximum weight. For the memory version of fixed-weight combinations, bits 9-5 should contain the string's weight.

Register 2 contains the previous string for functions 0-2 or the memory store address for functions 4-6.

## Functions
*Non-memory functions:*

**0:** Fixed-Weight Combinations are binary strings of a certain length that will always contain the same number of 1s and 0s. This method is based on cool-lex ordering as discussed in Stevens' and Williams' "The Coolest Way to Generate Binary Strings" and Knuth's *The Art of Computer Programming* Volume 4, Fascicle 3.

**1:** General Combinations are the collection of all binary strings of a certain length, ordered by the "cooler" successor rule (also from "The Coolest Way to Generate Binary Strings").

**2:** Ranged Combinations are all the binary strings of a certain length with the amount of 1s between minimum and maximum weights. Generation is performed by the "coolest" successor rule from "The Coolest Way to Generate Binary Strings".

*Memory functions:*

**4:** Here, fixed weight combinations are stored in memory. The first string has the lowest valid bits set, and the cycle follows the same pattern as function 0 from there.

**5:** This function stores all general combinations in memory, starting from the string with all bits set.

**6:** Finally, ranged combinations are stored in memory starting from the minimum amount of 1s set as the lowest bits and continuing by the pattern of function 2.
