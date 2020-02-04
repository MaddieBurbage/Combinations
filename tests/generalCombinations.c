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
  unsigned int cut, trimmed, trailed, mask, lastTemp, lastLimit, lastPosition, cap, first, shifted, rotated, result;

    cut = last >> 1;
    trimmed = cut | (cut - 1); //Discards trailing zeros
    trailed = trimmed ^ (trimmed + 1); //Marks the start of the last "01"
    mask = (trailed << 1) + 1;

    lastTemp = trailed + 1; //Indexes the start of the last "01"
    lastLimit = 1 << (n-1); //Indexes the length of the string
    lastPosition = (lastTemp == 0 || lastTemp > lastLimit)? lastLimit : lastTemp;

    cap = 1 << n;
    first = (mask < cap)? 1 & last : 1 & ~(last); //The bit to be moved
    shifted = cut & trailed;
    rotated = (first == 1)? shifted | lastPosition : shifted;
    result = rotated | (~mask & last);

    if(result == cap - 1) {
        return -1;
    }

    *out = result;
    return 1;
}


static inline int testAccelerator(int length, int inputString) {
    int outputString, answer, mismatches;

    mismatches = 0;

    //For each string in the sequence, compare the c output to the accelerator's
    while(nextGeneralCombination(length, inputString, &answer) != -1) {
        ROCC_INSTRUCTION_DSS(0, outputString, length, inputString, 1);
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
    int inputString = 0b1111;
    int length = 4;


    int testResult = testAccelerator(length, inputString);
    return testResult;
}
