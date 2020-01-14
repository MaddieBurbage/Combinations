//
//  Fixed_Weight_Combinations.c
//
//  First argument is the length of the binary combination, second is the last combination
//
//  Created by Madeline Burbage on 11/5/19.
//

#include <stdio.h>
#include <stdlib.h>
#include <string.h>


/* A function to generate all binary strings of a certain weight.
 * Input the length of the binary string and the previous combination.
 * The pointer, out, will be loaded with the next combination following
 * the suffix-rotation pattern. -1 is returned when the pattern ends.
 */
int next_weighted_combo(int n, int last, int *out) {
    int next, temp;
    next = last & (last + 1);
    printf("next %d\n", next);
    
    temp = next ^ (next - 1);
    printf("temp %d\n", temp);

    next = temp + 1;
    printf("next %d\n", next);
    temp = temp & last;
    printf("temp %d\n", temp);
    
    next = (next & last) - 1;
    printf("next %d\n", next);    
    next = (next > 0)? next : 0;
    printf("next %d\n", next);   

    next = last + temp - next;
    printf("next %d\n", next);    

    if(next / (1 << n) > 0) {
        return -1;
    }

    *out = next % (1 << n);
    return 1;
}

int main(int argc, char *argv[]) {
    int n, last;
    int result = 0;
    if(argc < 3){
        exit(1);
    }
    n = atoi(argv[1]);
    last = atoi(argv[2]);

    if(next_weighted_combo(n, last, &result) == -1) {
        printf("Final combination \n");
    }

    printf("%d\n \n", result);
}
