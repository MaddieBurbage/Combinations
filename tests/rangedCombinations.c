// Tests for the ranged-weight binary combinations accelerator
// (c) Maddie Burbage, 2020


#include "rocc.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* A function to help generate all binary strings of a certain length and weight range
 * The generation is computed using the cool-est pattern from "The Coolest
 * Way to Generate Binary Strings"
 */
int nextRangedCombination(int n, int last, int min, int max, unsigned int *out) {
  unsigned long cut, trimmed, trailed, mask, lastTemp, lastLimit, lastPosition, disposable, count, cap, flipped, valid, first, shifted, rotated, result;
    cut = last >> 1;
    trimmed = cut | (cut - 1); //Discards trailing zeros
    trailed = trimmed ^ (trimmed + 1); //Marks the start of the last "01"
    mask = (trailed << 1) + 1;

    lastTemp = trailed + 1; //Indexes the start of the last "01"
    lastLimit = 1 << (n-1); //Indexes the length of the string
    lastPosition = (lastTemp == 0 || lastTemp > lastLimit)? lastLimit : lastTemp;

    disposable = last; //Prepare to count bits set in the string
    for(count = 0; disposable; count++) {
        disposable = disposable & (disposable - 1); //Discard the last bit set
    }

    cap = 1 << n;
    flipped = 1 & ~last;
    valid = (flipped == 0)? count > min : count < max;
    first = (mask < cap || !valid)? 1 & last : flipped; //The bit to be moved
    shifted = cut & trailed;
    rotated = (first == 1)? shifted | lastPosition : shifted;
    result = rotated | (~mask & last);

    cap = (1 << min) - 1;
    if(result == cap) {
        return -1;
    }

    *out = result;
    return 1;
}


static inline int testAccelerator(int length, int inputString, int min, int max) {
    int outputString, answer, mismatches, constraints;

    mismatches = 0;

    constraints = length | (min << 6) | (max << 12);

    //For each string in the sequence, compare the c output to the accelerator's
    while(nextRangedCombination(length, inputString, min, max, &answer) != -1) {
        ROCC_INSTRUCTION_DSS(0, outputString, constraints, inputString, 2);
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
    int inputString = 0b1;
    int length = 4;
    int min = 1;
    int max = 3;


    int testResult = testAccelerator(length, inputString, min, max);
    return testResult;
}
