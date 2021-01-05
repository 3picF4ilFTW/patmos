//#include "include/bootable.h"

int main() {

	int opa = 2;
	int opb = 1;
	int res = 0;

	asm (	".word   52\n\t"
		"mov $r4 = %[a]\n\t"
		"mov $r3 = %[b]\n\t"
		".word 0x034A4182\n\t"
		".word 0x034A5182\n\t"
		".word 0x034A5182\n\t"
		"nop\n\t"
		"mov %[d] = $r5\n\t"
		"nop\n\t"
		"nop\n\t"
		"nop\n\t"
		: [d] "=r" (res)
		: [a] "r" (opa), [b] "r" (opb)
		: "3", "4", "5" );
}


