#include <stdio.h>

typedef struct {
    uint32_t a;
} Test1

void main(){
    Test1* a = {.a = 123};
}