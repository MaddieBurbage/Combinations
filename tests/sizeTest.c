#include "rocc.h"
#include "encoding.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>

int main(void) {
    long tryInt = INT_MAX;
    long int tryLong = LONG_MAX;
    unsigned long tryULong = ULONG_MAX;

    printf("tries: %ld %ld %lu \n", tryInt, tryLong, tryULong);

    long *array = malloc(tryInt * tryInt * sizeof(long));
    array[0] = 1;
    array[1] = 2;
    printf("cool %ld %ld \n", array[0], array[1]);

    long int big[tryInt];
    big[0] = 0;
    big[1] = 1;
    printf("big %ld %ld \n", big[0], big[1]);
}
