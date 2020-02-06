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
 * Generation is computed using Knuth's variant on the cool pattern from
 * The Art of Computer Programming, volume 4, fascicle 3.
 */
int nextWeightedCombination(int n, int last, int *out) {
    int next, temp;
    next = last & (last + 1); //Discards trailing ones

    temp = next ^ (next - 1); //Marks the start of the last "10"

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

/* A function to help generate all binary strings of a certain length and weight range
 * The generation is computed using the cool-est pattern from "The Coolest
 * Way to Generate Binary Strings"
 */
int nextRangedCombination(int n, int last, int min, int max, int *out) {
  unsigned int cut, trimmed, trailed, mask, lastTemp, lastLimit, lastPosition, disposable, count, cap, flipped, valid, first, shifted, rotated, result;
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

static inline int timeHardware(int inputString, int length, int answer) {
    int outputString, outputs;

    outputs = 1;
    #if FUNCT < 3
    ROCC_INSTRUCTION_DSS(0, outputString, length, inputString, FUNCT);
    while(outputString != -1) {
	printf("%d \n", outputString);
	inputString = outputString;
	outputs++;
        ROCC_INSTRUCTION_DSS(0, outputString, length, inputString, FUNCT);
    }
    #else

    #if FUNCT % 3 == 0
    length |= (WIDTH/2) << 5;
    #elif FUNCT % 3 == 2
    length |= (0 << 5) |  ((WIDTH/2) << 10);
    #endif

    long int safe[answer];
    ROCC_INSTRUCTION_DSS(0, outputString, length, &safe[0], FUNCT);
    #endif
    return outputs;
}

static inline int timeSoftware(int inputString, int length, int answer) {
    int outputString, outputs;
    outputs = 1;
    #if FUNCT < 3
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
	printf("%d \n", outputString);
	outputs++;
    }
    #else
    int safe[answer];
    int i = 0;
    while(
	  #if FUNCT % 3 == 0
	  nextWeightedCombination(length, inputString, &safe[i])
	  #elif FUNCT % 3 == 1
	  nextGeneralCombination(length, inputString, &safe[i])
	  #else
	  nextRangedCombination(length, inputString, 0, WIDTH/2, &safe[i])
          #endif
	  != -1) {
	inputString = safe[i];
	printf("%d \n", outputString);
	i++;
    }
    #endif
    return outputs;
}

int main(void) {
    //Set input string and the expected number of combinations
    #if FUNCT % 3 == 1 //General combinations
    int inputString = (1 << WIDTH) - 1;
    int answer = 1 << WIDTH;
    #elif FUNCT % 3 == 0 //Fixed weight combinations
    int inputString = (1 << WIDTH/2) - 1;
    int lookups[] = {0,0, 2, 0, 6, 0,0,0, 70, 0,0,0,0,0,0,0, 12870};
    int answer = lookups[WIDTH];
    #else //Ranged weight combinations
    int inputString = 0;
    int lookups[] = {0,0, 3, 0, 11, 0,0,0, 163, 0,0,0,0,0,0,0, 39203};
    int answer = lookups[WIDTH];
    #endif
    printf("answer %d, input %d \n", answer, inputString);
    //Set the string's length
    int length = WIDTH;
    //Test hardware or software
    //rdcycle() - rdcycle()

    #if WARE == 1
    int testResult = timeHardware(inputString, length, answer);
    #else
    int testResult = timeSoftware(inputString, length, answer);
    #endif

    #if FUNCT < 3
    testResult -= answer;
    #endif
    return testResult;
}
