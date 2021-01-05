//#include "include/bootable.h"

int main() {

	int opa = 240;
	int opb = 255;
	int res = 0;

	asm (	".word   52\n\t"
		"mov $r4 = %[a]\n\t"
		"mov $r3 = %[b]\n\t"
		".word 0x034A4180\n\t"
		"nop\n\t"
		"mov %[d] = $r5\n\t"
		"nop\n\t"
		"nop\n\t"
		"nop\n\t"
		: [d] "=r" (res)
		: [a] "r" (opa), [b] "r" (opb)
		: "3", "4", "5" );
}


