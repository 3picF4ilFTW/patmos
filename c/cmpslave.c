/*
   Copyright 2014 Technical University of Denmark, DTU Compute. 
   All rights reserved.
   
   This file is part of the time-predictable VLIW processor Patmos.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are met:

      1. Redistributions of source code must retain the above copyright notice,
         this list of conditions and the following disclaimer.

      2. Redistributions in binary form must reproduce the above copyright
         notice, this list of conditions and the following disclaimer in the
         documentation and/or other materials provided with the distribution.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER ``AS IS'' AND ANY EXPRESS
   OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
   OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN
   NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY
   DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
   (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
   ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
   (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
   THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

   The views and conclusions contained in the software and documentation are
   those of the authors and should not be interpreted as representing official
   policies, either expressed or implied, of the copyright holder.
 */

/*
 * Slave for CMP boot loader.
 * 
 * Author: Wolfgang Puffitsch (wpuffitsch@gmail.com)
 *
 */

#include "cmpboot.h"
#include "patio.h"

#define TIM TIMER_USLOW
#define DELAY 1000*1

int main(void)
{
  // setup stack frame and stack cache.
  asm volatile ("mov $r31 = %0;" // initialize shadow stack pointer"
                "mts $ss  = %1;" // initialize the stack cache's spill pointer"
                "mts $st  = %1;" // initialize the stack cache's top pointer"
                : : "r" (&_shadow_stack_base),
                  "r" (&_stack_cache_base));

  // wait a little bit in case of the TU/e memory controller not being ready
  int val = TIM+DELAY;
  while (TIM-val < 0)
    ;
  // overwrite any potential leftovers from previous runs
  boot_info->master.status = STATUS_NULL;
  boot_info->master.entrypoint = NULL;
  boot_info->slave[CORE_ID].status = STATUS_NULL;

  do {
    // make sure the own status is visible
    boot_info->slave[CORE_ID].status = STATUS_BOOT;
    // until master has booted
  } while (boot_info->master.status != STATUS_BOOT);

  // wait until master has downloaded
  while (boot_info->master.status != STATUS_INIT) {
    /* spin */
  }  
  // acknowledge reception of start status
  boot_info->slave[CORE_ID].status = STATUS_INIT;

  // call the application's _start()
  int retval = -1;
  if (boot_info->master.entrypoint != 0) {
    retval = (*boot_info->master.entrypoint)();

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
                    "$r26", "$r27", "$r28", "$r29",
                    "$r30", "$r31");
  }
  
  // TODO: report return value back to master

  // loop back, TODO: replace with a real reset
  main();

  return 0;
}
