#include "rocc.h"

static inline unsigned int fixedWeightCombinations() {
    unsigned int input, length, result;

    input = 1;
    length = 2;

    ROCC_INSTRUCTION_DSS(0, result, length, input, 0);
    return result;
}

int main(void) {
    unsigned int testResult = fixedWeightCombinations();
    return testResult - 2;
}
