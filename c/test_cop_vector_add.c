//#include "include/bootable.h"

int main() {
	while(1){
		int inputs[8] = {1, 2, 3, 4, 5, 6, 7, 8};
		volatile int outputs[4] = {0, 0, 0, 0};

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

		int a = outputs[0];
		int b = outputs[0];
		int c = outputs[0];
		int d = outputs[0];

		asm (	"mov $r1 = %[a]\n\t"
			"mov $r2 = %[b]\n\t"
			"mov $r3 = %[c]\n\t"
			"mov $r4 = %[d]\n\t"
			: 
			: [a] "r" (a), [b] "r" (b), [c] "r" (c), [d] "r" (d)
			: "1", "2", "3", "4");
	}
}


