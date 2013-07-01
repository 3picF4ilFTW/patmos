#include <machine/patmos.h>
#include <machine/spm.h>

int main() __attribute__((naked,used));

#define _stack_cache_base 0x2f00
#define _shadow_stack_base 0x3f00

#define ISPM        ((volatile _SPM int *) 0x0)

#define UART_STATUS *((volatile _SPM int *) 0xF0000100)
#define UART_DATA   *((volatile _SPM int *) 0xF0000104)
#define LEDS        *((volatile _SPM int *) 0xF0000100)

#define XDIGIT(c) ((c) <= 9 ? '0' + (c) : 'a' + (c) - 10)

#define WRITE(data,len) do { \
  unsigned i; \
  for (i = 0; i < (len); i++) {		   \
    while ((UART_STATUS & 0x01) == 0); \
    UART_DATA = (data)[i];			   \
  } \
} while(0)

int main()
{
	   // setup stack frame and stack cache.
	    asm volatile ("mov $r29 = %0;" // initialize shadow stack pointer"
	                "mts $ss  = %1;" // initialize the stack cache's spill pointer"
	                "mts $st  = %1;" // initialize the stack cache's top pointer"
	                 : : "r" (_shadow_stack_base), "r" (_stack_cache_base));

	int entrypoint = 0;
	int section_number = -1;
	int section_count = 0;
	int section_offset = 0;
	int section_size = 0;
	int integer = 0;
	int section_byte_count = 0;
	enum state {STATE_ENTRYPOINT, STATE_SECTION_NUMBER, STATE_SECTION_SIZE,
		STATE_SECTION_OFFSET, STATE_SECTION_DATA};

	enum state current_state = STATE_ENTRYPOINT;

	//Packet stuff
	int CRC_LENGTH = 4;
	int packet_byte_count = 0;
	int packet_size = 0;
	unsigned int calculated_crc = 0;
	unsigned int received_crc = 0xFFFFFFFF; //Flipped initial value
	unsigned int poly = 0xEDB88320; //Reversed polynomial

	for (;;)
	{
		LEDS = current_state;
		if(UART_STATUS & 0x02)
		{
			int data = UART_DATA;
			if(packet_size == 0)
			{
				//First received byte sets the packet size
				packet_size = data;
				packet_byte_count = 0;
				calculated_crc  = 0xFFFFFFFF;
				received_crc = 0;
			}
			else
			{
				if(packet_byte_count < packet_size)
				{
					calculated_crc = calculated_crc ^ data;
					int i;
					for (i = 0; i < 8; ++i)
					{
						if((calculated_crc & 1) > 0)
						{
							calculated_crc = (calculated_crc >> 1) ^ poly;
						}
						else
						{
							calculated_crc = (calculated_crc >> 1);
						}
					}

					integer |= data << ((3-(section_byte_count%4))*8);
					section_byte_count++;

					if(current_state < STATE_SECTION_DATA)
					{
						if(section_byte_count == 4)
						{
							if (current_state == STATE_ENTRYPOINT)
								entrypoint = integer;
							else if (current_state == STATE_SECTION_NUMBER)
								section_number = integer;
							else if (current_state == STATE_SECTION_SIZE)
								section_size = integer;
							else if (current_state == STATE_SECTION_OFFSET)
								section_offset = integer;

							section_byte_count = 0;
							current_state++;
						}
					}
					else
					{
						//In case of data less than 4 bytes write everytime
						*(ISPM+(section_offset/4)+((section_byte_count-1)/4)) = integer;
						if(section_byte_count == section_size)
						{
							//current_state = STATE_SECTION_START;
							section_byte_count = 0;
							section_count++;
							current_state = STATE_SECTION_SIZE;
						}
					}
					if(section_byte_count%4 == 0)
					{
						integer = 0;
					}

				}
				else if(packet_byte_count < packet_size+CRC_LENGTH)
				{
					received_crc |= data << ((packet_size+CRC_LENGTH-packet_byte_count-1)*8);
				}
				packet_byte_count++;
				if(packet_byte_count == packet_size+CRC_LENGTH)
				{
					calculated_crc = calculated_crc ^ 0xFFFFFFFF; //Flipped final value
					if(calculated_crc == received_crc)
					{
						UART_DATA = 'o';
						if(section_count == section_number)
						{
							//End of program transmission
							//Jump to program execution
							int retval = (*(volatile int (*)())entrypoint)();

							// Compensate off-by-one of return offset with NOP
							// (internal base address is 0 after booting).
							// Return may be "unclean" and leave registers clobbered.
							asm volatile ("nop" : :
										  : "$r2", "$r3", "$r4", "$r5",
											"$r6", "$r7", "$r8", "$r9",
											"$r10", "$r11", "$r12", "$r13",
											"$r14", "$r15", "$r16", "$r17",
											"$r18", "$r19", "$r20", "$r21",
											"$r22", "$r23", "$r24", "$r25",
											"$r26", "$r27", "$r28", "$r29");

							// Print exit magic and return code
							{
							  char msg[10];
							  msg[0] = '\0';
							  msg[1] = 'x';
							  msg[2] = retval & 0xff;
							  WRITE(msg, 3);
							}
							// Start again
							// TODO: replace with a real reset
							main();
						}
					}
					else
					{
						UART_DATA = 'r';
					}
					packet_size = 0;
				}
			}
		}
	}
	return 0;
}
