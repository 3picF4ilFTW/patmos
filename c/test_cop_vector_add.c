//#include "include/bootable.h"

int main() {
	while(1) {
		int inputs[8] = {1, 2, 3, 4, 5, 6, 7, 8};
		int outputs[4] = {0, 0, 0, 0};

		asm (	"mov $r3 = %[a]\n\t"
			"mov $r4 = %[b]\n\t"
			".word 0x03443201\n\t"
			: 
			: [a] "r" (inputs), [b] "r" (outputs)
			: "3", "4");

		int flag = 0;
		while(!flag)
		{
			asm (	".word 0x034A0103\n\t"
				"mov %[a] = $r5\n\t"
				: [a] "=r" (flag)
				: 
				: "5");
		}

		asm (	//"nop\n\t"
			"lwm $r1 = [%[a]]\n\t"
			"lwm $r2 = [%[a] + 1]\n\t"
			"lwm $r3 = [%[a] + 2]\n\t"
			"lwm $r4 = [%[a] + 3]\n\t"
			: 
			: [a] "r" (outputs)
			: "1", "2", "3", "4");
	}
}


