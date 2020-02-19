// Tests for the fixed weight combinations accelerator
// (c) Maddie Burbage, 2020

#include "rocc.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* A function to help generate all binary strings of a certain weight.
 * Input the length of the binary string and the previous combination.
 * The pointer, out, will be loaded with the next combination following
 * the suffix-rotation pattern. -1 is returned when the pattern ends.
 */
int nextWeightedCombination(long n, unsigned long last, unsigned int *out) {
    unsigned long next, temp;
    next = last & (last + 1);
    temp = next ^ (next - 1);

    next = temp + 1;
    temp = temp & last;

    next = (next & last) - 1;
    next = (next < 0x8000000000000000)? next : 0;

    next = last + temp - next;

    if(next / (1L << n) != 0) {
        return -1;
    }

    *out = next % (1L << n);
    return 1;
}


static inline int testAccelerator(int length, unsigned int inputString, int weight) {
    unsigned int outputString, answer, mismatches, constraints;

    mismatches = 0;

    constraints = length | (weight << 6);

    //For each string in the sequence, compare the c output to the accelerator's
    while(nextWeightedCombination(length, inputString, &answer) != -1) {
        ROCC_INSTRUCTION_DSS(0, outputString, constraints, inputString, 0);
	if(outputString == answer) {
	  printf("Next string: %d, accelerator found %d\n", answer, outputString);
	} else {
	  printf("ERROR: next string: %d, accelerator found %d\n", answer, outputString);
	  mismatches++;
	}
	inputString = answer;
    }
    ROCC_INSTRUCTION_DSS(0, outputString, constraints, inputString, 0);
    printf("Final accelerator output %d \n", outputString);
    return mismatches; //Mismatches is 0 for success, otherwise it's positive
}

int main(void) {
    long inputString = 0b11111110000000;
    int length = 14;
    long weight = 7;


    int testResult = testAccelerator(length, inputString, weight);
    return testResult;
}
