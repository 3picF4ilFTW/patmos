#
# This is a simple echo program on the UART
#
# Expected Result: echo entered characters
#
		addi	r0 = r0, 0;  # first instruction not executed
		addi	r1 = r0, 1;
		add	r2   = r0 , 65536; # dual issue from odd
		add     r5 = r0, 100000; # dual issue from odd
		addi    r4 = r0, 4;
		add     r6 = r0, 200000; dual issue from even
		add     r7 = r0, 300000;
		add	r8 = r0, 1;
		add	r8 = r8, r7;
		add     r8 = r8, r6; # r8 =50001
		addi    r15 = r0, 1;
		add     r15 = r15, r8; # r15 = 50002
		add	r12 = r0, 346851;
		add	r12 = r12, r15; #r12 = 846853	
		add	r1 = r0, 256474; #1 r1 = 2
		add	r2 = r0, 256477; #2 r2 = 4
		cmplt   p1  = r1, r2; #
		(p1) 	bc	25; #4
		add	r1 = r1, 1; #5 r1 = 1 
		nop	0; #5 branch delay
		addi	r3 = r0, 3; #7 r3 = 3
                halt;
