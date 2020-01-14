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
int nextWeightedCombination(int n, int last, int *out) {
    int next, temp;
    next = last & (last + 1);

    temp = next ^ (next - 1);

    next = temp + 1;
    temp = temp & last;

    next = (next & last) - 1;
    next = (next > 0)? next : 0;

    next = last + temp - next;

    if(next / (1 << n) > 0) {
        return -1;
    }

    *out = next % (1 << n);
    return 1;
}


static inline int testAccelerator(int inputString, int length) {
    int outputString, answer, mismatches;
    
    mismatches = 0;

    //For each string in the sequence, compare the c output to the accelerator's
    while(nextWeightedCombination(length, inputString, &answer) != -1) {
        ROCC_INSTRUCTION_DSS(0, outputString, length, inputString, 0);
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
