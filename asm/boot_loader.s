#
# Expected Result: ...
# this echos wrong characters
# MS: what does this program? Looks very out of date: UART at wrong address, bne,...
# SA: should I continue with assembly boot loader? 
# MS: I think a boot loader shall be done in C if possible
# and we shall drop unused out-of-date code
		.word	264;
		addi    r16  = r16 , 64;
		addi    r7 = r7 , 511;
		addi	r1   = r0 , 2;
		lwm     r10  = [r5 + 0];
                nop;
                and     r11  = r10 , r1;
		bne     r1 != r11 , 4;
		addi	r0  = r0 , 1;
                addi    r0  = r0 , 1;
                lwm     r15  = [r5 + 1];
                lwm     r15  = [r5 + 1];
		addi    r17  = r17 , 24;
		sl	r15 = r15 , r17;
		lwm     r10  = [r5 + 0];
		addi    r0  = r0 , 1;
                and     r11  = r10 , r1;
		bne     r1 != r11 , 4;
		addi	r0  = r0 , 1;
                addi    r0  = r0 , 1;
                lwm     r18  = [r5 + 1];
                lwm     r18  = [r5 + 1];
		addi	r19 = r19 , 16;
		sl      r18 = r18 , r19;
		or	r15 = r15 , r18;
		lwm     r10  = [r5 + 0];
		nop;
                and     r11  = r10 , r1;
		bne     r1 != r11 , 4;
		addi	r0  = r0 , 1;
                addi    r0  = r0 , 1;
                lwm     r20  = [r5 + 1];
                lwm     r20  = [r5 + 1];
		addi	r21 = r21 , 8;
		sl      r20 = r20 , r21;
		or	r15 = r15 , r20;
		lwm     r10  = [r5 + 0];
		nop;
                and     r11  = r10 , r1;
		bne     r1 != r11 , 4;
		addi	r0  = r0 , 1;
                addi    r0  = r0 , 1;
                lwm     r22  = [r5 + 1];
                lwm     r22  = [r5 + 1];
		addi    r0  = r0 , 1;
		or	r15 = r15 , r22;
                swm     [r7 + 1] = r15; 
		addi    r27 = r27 , 1;
		andi	r0 = r0 , 0;
		andi	r1 = r1 , 0;
		andi    r5 = r5 , 0;
		andi    r10 = r10 , 0;
		andi    r11 = r11 , 0;
		andi	r15 = r15 , 0;
		andi    r17 = r17 , 0;
		andi    r18 = r18 , 0;
		andi    r19 = r19 , 0;
		andi    r20 = r20 , 0;
		andi    r21 = r21 , 0;
		andi    r22 = r22 , 0;
		addi    r7 = r7 , 1;
		bne	r27 != r16 , 59;
		addi    r9 = r9 , 1;
		andi	r9 = r9 , 0;
		andi    r27 = r27 , 0;
		andi    r7 = r7 , 0;
		andi    r16 = r16 , 0;
		andi    r0 = r0 , 0;
		andi    r0 = r0 , 0;
		andi    r0 = r0 , 0;
                halt;
