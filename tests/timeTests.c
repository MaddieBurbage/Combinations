//Benchmark tests for all combination sequence types
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

static inline int timeHardware(int inputString, int length, int function) {
    int outputString, outputs;

    outputs = 0;
    #if FUNCT < 3
    ROCC_INSTRUCTION_DSS(0, outputString, length, inputString, function);
    while(outputString != -1) {
	inputString = outputString;
	outputs++;
        ROCC_INSTRUCTION_DSS(0, outputString, length, inputString, function);
    }
    #elif
    long int safe[TOTAL];
    ROCC_INSTRUCTION_DSS(0, outputString, length, &safe[0], function);
    #endif
    return outputs;
}

static inline int timeSoftware(int inputString, int length) {
    int outputString, outputs;
    outputs = 0;
    while(
	  #if FUNCT % 3 == 0
	  nextWeightedCombination(length, inputString, &outputString)
	  #elif FUNCT % 3 == 1
	  nextGeneralCombination(length, inputString, &outputString)
	  #else
	  nextRangedCombination(length, inputString, 0, WIDTH/2, &outputString)
	  #endif
	  != -1) {
	inputString = outputString;
	outputs++;
    }
    return outputs;
}

int main(void) {
    #if FUNCT % 3 == 1
    int inputString = (1 << WIDTH) - 1;
    int answer = 1 << WIDTH;
    #else
    int inputString = (1 << WIDTH/2) - 1;
    #if FUNCT % 3 == 0
    int lookups[] = {2, 6, 70, 12870};
    int answer = lookups[log2(WIDTH)-1];
    #else
    int lookups[] = {3, 11, 163, 39203};
    int answer = lookups[log2(WIDTH)-1];
    #endif
    #endif
    
    int length = WIDTH;
    int answer = 69; //general it's 2^length - 1, fixed it's length!/weight!/weight-1!
    #if WARE == 1
    int testResult = timeHardware(inputString, length, FUNCT);
    #else
    int testResult = timeSoftware(inputString, length);
    #endif
    return testResult - answer;
}
