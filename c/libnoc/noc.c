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
 * Functions to initialize and use the NoC.
 * 
 * Author: Wolfgang Puffitsch (wpuffitsch@gmail.com)
 *
 */


#include <machine/patmos.h>
#include <machine/spm.h>

#include <stdio.h>
#include "bootloader/cmpboot.h"
#include "noc.h"

// Structure to model the network interface
static struct network_interface
{
    volatile unsigned int _IODEV *dma;
    volatile unsigned int _IODEV *dma_p;
    volatile unsigned int _IODEV *st;
} noc_interface = {
  NOC_DMA_BASE, NOC_DMA_P_BASE, NOC_ST_BASE
};

// Configure network interface according to initialization information
void noc_configure(void) {
  unsigned row_size = NOC_TIMESLOTS > NOC_DMAS ? NOC_TIMESLOTS : NOC_DMAS;
  unsigned core_idx = get_cpuid() * NOC_TABLES * row_size;
  for (unsigned i = 0; i < NOC_TIMESLOTS; ++i) {
    *(noc_interface.st+i) = noc_init_array[core_idx + i];
  }
  for (unsigned i = 0; i < NOC_DMAS; ++i) {
    *(noc_interface.dma_p+i) = noc_init_array[core_idx + row_size + i];
  }
}

// Synchronize start-up
static void noc_sync(void) {

  if (get_cpuid() == NOC_MASTER) {
    // Wait until all slaves have configured their network interface
    int done = 0;
    do {
      done = 1;
      for (unsigned i = 0; i < get_cpucnt(); i++) {
        if (boot_info->slave[i].status != STATUS_NULL &&
            boot_info->slave[i].status != STATUS_INITDONE && 
            i != NOC_MASTER) {
          done = 0;
        }
      }
    } while (!done);

    // TODO: start up network

    // Notify slaves that the network is started
    boot_info->master.status = STATUS_INITDONE;

  } else {
    // Notify master that network interface is configured
    boot_info->slave[get_cpuid()].status = STATUS_INITDONE;
    // Wait until master has started the network
    while (boot_info->master.status != STATUS_INITDONE) {
      /* spin */
    }
  }
}

// Initialize the NoC
void noc_init(void) {
  //if (get_cpuid() == NOC_MASTER) puts("noc_configure");
  noc_configure();
  //if (get_cpuid() == NOC_MASTER) puts("noc_sync");
  noc_sync();
  //if (get_cpuid() == NOC_MASTER) puts("noc_done");
}

// Start a NoC transfer
// The addresses and the size are in double-words and relative to the
// communication SPM
int noc_dma(unsigned rcv_id,
            unsigned short write_ptr,
            unsigned short read_ptr,
            unsigned short size) {

    // Only send if previous transfer is done
    if (!noc_done(rcv_id)) {
      return 0;
    }

    // Read pointer and write pointer in the dma table
    *(noc_interface.dma+(rcv_id<<1)+1) = (read_ptr << 16) | write_ptr;
    // DWord count and valid bit, done bit cleared
    *(noc_interface.dma+(rcv_id<<1)) = (size | NOC_VALID_BIT) & ~NOC_DONE_BIT;

    return 1;
}

// Check if a NoC transfer has finished
int noc_done(unsigned rcv_id) {
  unsigned status = *(noc_interface.dma+(rcv_id<<1));
  if ((status & NOC_VALID_BIT) != 0 && (status & NOC_DONE_BIT) == 0) {
      return 0;
  }
  return 1;
}

// Convert from byte address or size to double-word address or size
#define DW(X) (((X)+7)/8)

// Attempt to transfer data via the NoC
// The addresses and the size are in bytes
int noc_nbsend(unsigned rcv_id, volatile void _SPM *dst,
               volatile void _SPM *src, size_t size) {

  unsigned wp = (char *)dst - (char *)NOC_SPM_BASE;
  unsigned rp = (char *)src - (char *)NOC_SPM_BASE;
  return noc_dma(rcv_id, DW(wp), DW(rp), DW(size));
}

// Transfer data via the NoC
// The addresses and the size are in bytes
void noc_send(unsigned rcv_id, volatile void _SPM *dst,
              volatile void _SPM *src, size_t size) {
  _Pragma("loopbound min 1 max 1")
  while(!noc_nbsend(rcv_id, dst, src, size));
}

// Multicast transfer of data via the NoC
// The addresses and the size are in bytes
void noc_multisend(unsigned cnt, unsigned rcv_id [], volatile void _SPM *dst [],
              volatile void _SPM *src, size_t size) {

  int done;
  coreset_t sent;
  coreset_clearall(&sent);
  do {
    done = 1;
    for (unsigned i = 0; i < cnt; i++) {
      if (!coreset_contains(rcv_id[i], &sent)) {
        if (noc_nbsend(rcv_id[i], dst[i], src, size)) {
          coreset_add(rcv_id[i], &sent);
        } else {
          done = 0;
        }
      }
    }
  } while(!done);
}

// Multicast transfer of data via the NoC
// The addresses and the size are in bytes
// The receivers are defined in a coreset
// the coreset must not contain the calling core.
void noc_multisend_cs(coreset_t *receivers, volatile void _SPM *dst[],
                      unsigned offset, volatile void _SPM *src, size_t size) {
  int index = 0;
  unsigned cpuid = get_cpuid();
//  for (unsigned i = 0; i < CORESET_SIZE; ++i) {
  for (unsigned i = 0; i < NOC_CORES; ++i) {
    if (coreset_contains(i,receivers)){
      if (i != cpuid) {
        noc_send(i, (volatile void _SPM *)((unsigned)dst[index]+offset), src, size);
      }
      //DEBUGGER("Transmission address: %x+%x at core %i\n",(unsigned)dst[index],offset,i);
      index++;
    }
  }
}
//void noc_multisend_cs(coreset_t receivers, volatile void _SPM *dst[],
//                                unsigned offset, volatile void _SPM *src, size_t size) {
//  int index;
//  int done;
//  coreset_t sent;
//  coreset_clearall(&sent);
//  do {
//    done = 1;
//    index = 0;
//    for(unsigned i = 0; i < CORESET_SIZE; i++) {
//      if(coreset_contains(i,&receivers) != 0) {
//        if(i != get_cpuid() && coreset_contains(i,&sent) == 0) {
//          if(noc_nbsend(i, (volatile void _SPM *)((unsigned)dst[index]+offset), src, size)) {
//            coreset_add(i,&sent);
//            DEBUGGER("Transmission address: %x+%x at core %i\n",(unsigned)dst[index],offset,i);
//          }
//        }
//        index++;
//      }
//    }
//  } while(!done);
//}

void noc_wait_dma(coreset_t receivers) {
  int index = 0;
  for (unsigned i = 0; i < NOC_CORES; ++i) {
    if (coreset_contains(i,&receivers)){
      if (i != get_cpuid()) {
        while(!noc_done(i));
      }
    }
  } 
}
