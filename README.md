# Combinations
Combinations Accelerators by the Williams College Bailey Research Group


Using custom RISCV instructions, the combinations accelerator can be called on to create sequences of binary strings and generate new strings in certain patterns.

## Instructions

This accelerator is called by the custom0 RISCV instruction. The first source register should contain the length of the binary string to be used (in bits) and the second register contains the starting binary string. Lengths up to 32 bits are supported. After a new string is generated, if successful, it will be saved to the destination register supplied by the instruction. However, these binary strings are generated cyclically, so the accelerator is designed to halt generation at a certain string in the cycle. If the endpoint is reached the value -1 will be outputted instead of the next string.


## Functionality

In the custom0 instruction, a function code can be supplied to tell the accelerator which string generation to perform. 

Functions:

**0:** Fixed-Weight Combinations are binary strings of a certain length that will always contain the same number of 1s and 0s. This method is based on cool-lex ordering as discussed in Stevens' and Williams' "The Coolest Way to Generate Binary Strings" and Knuth's *The Art of Computer Programming* Volume 4, Fascicle 3.

**1:** Lexicographic Combinations are the collection of all binary strings of a certain length, in increasing order.

**2:** General Combinations are the collection of all binary strings of a certain length, ordered by the "cooler" successor rule (also from "The Coolest Way to Generate Binary Strings").

**3:** *Still being developed*, this function generates binary strings of a certain length between set minimum and maximum weights. Because the previous string, length of the string, minimum, and maximum weights must be supplied, this function comes in two parts. Using function 4, the minimum and maximum weights of string generation are set, and then function 3 is used like previous functions to generate succeeding strings. Generation is performed by the "coolest" successor rule from "The Coolest Way to Generate Binary Strings".

**4:** Used to set up function 3, the first input is the minimum weight and the second is the maximum weight of strings to be generated. This controls how many 1s strings are allowed to contain. If previous settings are still in use for generation, the new settings will fail to save, with -1 outputted to the destination register. Otherwise, 0 will be sent on success, and function 3 will be ready for use with these minimum and maximum weights.
