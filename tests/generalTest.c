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
    unsigned long cut, trimmed, trailed, mask, lastTemporary, lastLimit, lastPosition, cap, first, shifted, rotated, result;

    cut = last >> 1;
    trimmed = cut | (cut - 1);
    trailed = trimmed ^ (trimmed + 1);
    mask = (trailed << 1) + 1;

    lastTemporary = trailed + 1;
    lastLimit = 1L << (n-1);
    lastPosition = (lastTemporary == 0 || lastTemporary > lastLimit)? lastLimit : lastTemporary;

    cap = 1L << n;
    first = (mask < cap)? 1 & last : 1 & ~(last);
    shifted = cut & trailed;
    rotated = (first == 1)? shifted | lastPosition : shifted;
    result = rotated | (~mask & last);

    if(result == cap - 1) {
        return -1;
    }

    *out = result;
    return 1;
}



int main(void) {
    int inputString = 0b111111;
    int length = 6;
    int answer;
    long outputs = 0;

    answer=inputString;
    while(nextGeneralCombination(length, answer, &answer) != -1) {
        printf("Next: %d\n", answer);
	outputs++;
    }
    return (2 << length) - outputs;
}
