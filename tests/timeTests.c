//Benchmark tests for all combination sequence types
// (c) Maddie Burbage, 2020

#include "rocc.h"
#include "encoding.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define LONGTOP 0x8000000000000000
/* A function to help generate all binary strings of a certain weight.
 * Input the length of the binary string and the previous combination.
 * The pointer, out, will be loaded with the next combination following
 * the suffix-rotation pattern. -1 is returned when the pattern ends.
 * Generation is computed using Knuth's variant on the cool pattern from
 * The Art of Computer Programming, volume 4, fascicle 3.
 */
int nextWeightedCombination(long n, unsigned long last, unsigned int *out) {
    unsigned long next, temp, result;
    next = last & (last + 1); //Discards trailing ones
    temp = next ^ (next - 1); //Marks the start of the last "10"

    next = temp + 1;
    temp = temp & last;

    next = (next & last) - 1;

    next = (next < LONGTOP)? next : 0;

    result = last + temp - next;

    if(result / (1L << n) > 0) {
        return -1;
    }

    *out = result % (1L << n);
    return 1;
}

/* A function to help generate all binary strings of a certain length.
 * The generation is computed using the cool-er pattern from "The Coolest
 * Way to Generate Binary Strings"
 */
int nextGeneralCombination(long n, unsigned long last, unsigned int *out) {
  unsigned long cut, trimmed, trailed, mask, lastTemp, lastLimit, lastPosition, cap, first, shifted, rotated, result;

    cut = last >> 1;
    trimmed = cut | (cut - 1); //Discards trailing zeros
    trailed = trimmed ^ (trimmed + 1); //Marks the start of the last "01"
    mask = (trailed << 1) + 1;

    lastTemp = trailed + 1; //Indexes the start of the last "01"
    lastLimit = 1L << (n-1); //Indexes the length of the string
    lastPosition = (lastTemp == 0 || lastTemp > lastLimit)? lastLimit : lastTemp;

    cap = 1L << n;
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
int nextRangedCombination(long n, unsigned long last, long min, long max, unsigned int *out) {
  unsigned long cut, trimmed, trailed, mask, lastTemp, lastLimit, lastPosition, disposable, count, cap, flipped, valid, first, shifted, rotated, result;
    cut = last >> 1;
    trimmed = cut | (cut - 1); //Discards trailing zeros
    trailed = trimmed ^ (trimmed + 1); //Marks the start of the last "01"
    mask = (trailed << 1) + 1;

    lastTemp = trailed + 1; //Indexes the start of the last "01"
    lastLimit = 1L << (n-1); //Indexes the length of the string
    lastPosition = (lastTemp == 0 || lastTemp > lastLimit)? lastLimit : lastTemp;

    disposable = last; //Prepare to count bits set in the string
    for(count = 0; disposable; count++) {
        disposable = disposable & (disposable - 1); //Discard the last bit set
    }

    cap = 1L << n;
    flipped = 1 & ~last;
    valid = (flipped == 0)? count > min : count < max;
    first = (mask < cap || !valid)? 1 & last : flipped; //The bit to be moved
    shifted = cut & trailed;
    rotated = (first == 1)? shifted | lastPosition : shifted;
    result = rotated | (~mask & last);

    cap = (1L << min) - 1;
    if(result == cap) {
        return -1;
    }

    *out = result;
    return 1;
}

static inline int timeHardware(unsigned int inputString, int length, long answer) {
    unsigned int outputString, outputs;

    outputs = 1;

    #if FUNCT % 4 == 0
    length |= (WIDTH/2) << 6;
    #elif FUNCT % 4 == 2
    length |= (0 << 6) |  ((WIDTH/2) << 12);
    #endif
    #if FUNCT < 3
    ROCC_INSTRUCTION_DSS(0, outputString, length, inputString, FUNCT);
    while(outputString != -1) {
	//printf("%d \n", outputString);
	inputString = outputString;
	outputs++;
        ROCC_INSTRUCTION_DSS(0, outputString, length, inputString, FUNCT);
    }
    #else
    unsigned long streamOut[answer/4];
    //    long i;
    //for(i = 0; i < answer; i++) {
    //	streamOut[i] = i;
    //}
    ROCC_INSTRUCTION_DSS(0, outputString, length, &streamOut[0], FUNCT);
    outputs = outputString;
    #endif
    return outputs;
}

static inline int timeSoftware(unsigned int inputString, int length, long answer) {
    unsigned int outputString, outputs;
    outputs = 1;
    #if FUNCT < 3
    while(
	  #if FUNCT % 4 == 0
	  nextWeightedCombination(length, inputString, &outputString)
	  #elif FUNCT % 4 == 1
	  nextGeneralCombination(length, inputString, &outputString)
	  #else
	  nextRangedCombination(length, inputString, 0, WIDTH/2, &outputString)
          #endif
	  != -1) {
	inputString = outputString;
	//printf("%d \n", outputString);
	outputs++;
    }
    #else
    unsigned int streamOut[answer];
    int i = 0;
    while(
	  #if FUNCT % 4 == 0
	  nextWeightedCombination(length, inputString, &streamOut[i])
	  #elif FUNCT % 4 == 1
	  nextGeneralCombination(length, inputString, &streamOut[i])
	  #else
	  nextRangedCombination(length, inputString, 0, WIDTH/2, &streamOut[i])
          #endif
	  != -1) {
	inputString = streamOut[i];
	//printf("%d \n", inputString);
	i++;
    }
    outputs = (i+1 == answer)? 0 : -1;
    #endif
    return outputs;
}

int main(void) {
    long startCycle, endCycle;
    //Set input string and the expected number of combinations
    #if FUNCT % 4 == 1 //General combinations
    unsigned long inputString = (1L << WIDTH) - 1;
    long answer = 1L << WIDTH;
    #elif FUNCT % 4 == 0 //Fixed weight combinations
    unsigned long inputString = (1L << WIDTH/2) - 1;
    long lookups[] = {0,0, 2, 0, 6, 0, 20, 0, 70, 0,0,0,0,0, 3432,0, 12870,0,0,0,0,0,0,0, 2704156,0,0,0,0,0,0,0,601080390};
    long answer = lookups[WIDTH];
    #else //Ranged weight combinations
    unsigned long inputString = 0;
    long lookups[] = {0,0, 3, 0, 11, 0, 42, 0, 163, 0,0,0,0,0, 9908,0, 39203,0,0,0,0,0,0,0, 9740686,0,0,0,0,0,0,0, 2448023843};
    long answer = lookups[WIDTH];
    #endif
    
    printf("answer %lu, input %lu \n", answer, inputString);
    //Set the string's length
    int length = WIDTH;

    asm volatile ("fence");
    startCycle = rdcycle();
    #if WARE == 1
    int testResult = timeHardware(inputString, length, answer);
    #else
    int testResult = timeSoftware(inputString, length, answer);
    #endif
    asm volatile ("fence");
    endCycle = rdcycle();
    printf("%d, %lu \n", WIDTH, endCycle-startCycle);

    #if FUNCT < 3
    testResult -= answer;
    #endif
    return testResult;
}
