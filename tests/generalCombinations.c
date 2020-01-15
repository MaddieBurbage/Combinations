// Tests for the general binary combinations accelerator
// (c) Maddie Burbage, 2020

#include "rocc.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* A function to help generate all binary strings of a certain length.
 * The generation is computed using the cool-er pattern from "The Coolest
 * Way to Generate Binary Strings"
 */
int nextGeneralCombination(int n, int last, int *out) {
    int next, temp;

    next = last ^ (last + 1);
    mask = (next > 3)? trailed : ~(0);

    lastPosition = (mask >> 1) + 1;
    first = (next > 3)? 1 & previous : 1 & ~(previous);
    shifted = (previous & mask) >> 1;
    rotated = (first == 1) shifted | lastPosition : shifted;
    result = rotated | (~mask & previous);

    if(result ===(1 << length) - 1) {
        return -1;
    }

    *out = result;
    return 1;
}


static inline int testAccelerator(int inputString, int length) {
    int outputString, answer, mismatches;

    mismatches = 0;

    //For each string in the sequence, compare the c output to the accelerator's
    while(nextGeneralCombination(length, inputString, &answer) != -1) {
        ROCC_INSTRUCTION_DSS(0, outputString, length, inputString, 2);
	if(outputString == answer) {
	  printf("Next string: %d, accelerator found %d\n", answer, outputString);
	} else {
	  printf("ERROR: next string: %d, accelerator found %d\n", answer, outputString);
	  mismatches++;
	}
	inputString = answer;
    }
    return mismatches; //Mismatches is 0 for success, otherwise it's positive
}

int main(void) {
    int inputString = 0b000111;
    int length = 6;


    int testResult = testAccelerator(inputString, length);
    return testResult;
}
