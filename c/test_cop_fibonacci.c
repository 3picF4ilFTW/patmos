//#include "include/bootable.h"

int main() {

	int opa = 5;
	int res = 0;

	asm (	".word   52\n\t"
		"mov $r3 = %[a]\n\t"
		".word 0x03443011\n\t"
		"nop\n\t"
		"nop\n\t"
		"nop\n\t"
		"nop\n\t"
		"nop\n\t"
		"nop\n\t"

		".word 0x03480113\n\t"
		"mov %[d] = $r4\n\t"

		"nop\n\t"

		".word 0x03443011\n\t"
		"nop\n\t"
		".word 0x03480113\n\t"
		"mov %[d] = $r4\n\t"

		: [d] "=r" (res)
		: [a] "r" (opa)
		: "3", "4", "5" );
}


