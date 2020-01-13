#include "rocc.h"

static inline unsigned int fixedWeightCombinations() {
    unsigned int input, length, result;

    //Test idea: input 1, length 2, result 2

    input = 3;
    length = 5;

    ROCC_INSTRUCTION_DSS(0, result, length, input, 0);
    return result;
}

int main(void) {
    unsigned int testResult = fixedWeightCombinations();
    return testResult - 6;
}
