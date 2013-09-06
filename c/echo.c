/*
	Echo characters received from the UART to the UART.
	Toggles LEDs on every received character.

	TODO: IO is defined via ld/st local, but the compiler generates
	a different ld/st type here.

	Author: Martin Schoeberl
	Copyright: DTU, BSD License
*/

#include <machine/spm.h>

int main() {

	volatile _SPM int *led_ptr = (volatile _SPM int *) 0xF0000900;
	volatile _SPM int *uart_status = (volatile _SPM int *) 0xF0000800;
	volatile _SPM int *uart_data = (volatile _SPM int *) 0xF0000804;
	int status;
	int val;

	for (;;) {
		status = *uart_status;
		if (status & 0x02) {
			val = *uart_data;
			*uart_data = val;
			*led_ptr = ~val;
		}
	}
}
