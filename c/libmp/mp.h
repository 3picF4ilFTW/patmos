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

/**
 * \file mp.h Definitions for libmp.
 * 
 * \author Rasmus Bo Soerensen <rasmus@rbscloud.dk>
 *
 */

#ifndef _MP_H_
#define _MP_H_

#include <machine/patmos.h>
#include <machine/spm.h>
#include "libnoc/noc.h"

////////////////////////////////////////////////////////////////////////////
// Data structures for storing state information
// of the message passing channels
////////////////////////////////////////////////////////////////////////////

typedef struct {
  volatile void _SPM * remote_addr; /**< The address of the remote buffer structure */
  volatile void _SPM * local_addr;  /**< The address of the local buffer structure */
  size_t buf_size;                  /**< The size of a buffer in bytes */
  size_t num_buf;                   /**< The number of buffers at the receiver */
  volatile size_t _SPM * rcv_count;   /**< The number of messages received by the receiver */
  union {
    struct {
      int rcv_id;                   /**< The ID of the receiver, only present at the sender */
      size_t sent_count;            /**< The number of messages sent by the sender, only present at the sender */
    };
    struct {
      int send_id;                  /**< The ID of the sender, only present at the receiver */
      volatile void _SPM * remote_rcv_count; /**< The address of the rcv_count at the sender, only present at the receiver */
    };
  };
} MP_T;

////////////////////////////////////////////////////////////////////////////
// Functions for initializing the message passing API
////////////////////////////////////////////////////////////////////////////

/// \brief Initialize the state of the send function
///
/// \param rcv_id The core id of the receiving processor
/// \param remote_addr A pointer to the remote address, where the receiving
/// buffer structure should start. The size of the buffer structure is the
/// message buffer size multiplied by the number of buffers plus 16 bytes.
/// \param local_addr A pointer to the local address, where the sending
/// buffer structure should start. The size of the buffer structure is the
/// message buffer size multiplied by the number of buffers plus 16 bytes.
/// \param size The size of the message buffer
void mp_send_init(MP_T* mp_ptr, int rcv_id, volatile void _SPM *remote_addr,
              volatile void _SPM *local_addr, size_t size, size_t num_buf);

/// \brief Initialize the state of the receive function
///
/// \param send_id The core id of the sending processor
/// \param remote_addr A pointer to the remote address, where the receiving
/// buffer structure should start. The size of the buffer structure is the
/// message buffer size multiplied by the number of buffers plus 16 bytes.
/// \param local_addr A pointer to the local address, where the sending
/// buffer structure should start. The size of the buffer structure is the
/// message buffer size multiplied by the number of buffers plus 16 bytes.
/// \param size The size of the message buffer
void mp_rcv_init(MP_T* mp_ptr, int send_id, volatile void _SPM *remote_addr,
              volatile void _SPM *local_addr, size_t size, size_t num_buf);

////////////////////////////////////////////////////////////////////////////
// Functions for transmitting data
////////////////////////////////////////////////////////////////////////////

/// \brief A function for passing a message to a remote processor under
/// flow control. The data to be passed by the function should be in the
/// local buffer in the communication scratch pad before the function
/// is called.
///
/// \param mp_ptr A pointer to the message passing data structure
/// for the given message passing channel.
void mp_send(MP_T* mp_ptr) __attribute__((noinline));

/// \brief A function for receiving a message from a remote processor under
/// flow control. The data that is received is placed in a message buffer
/// in the communication scratch pad, when the received message is no
/// longer used the reception of the message should be acknowledged with
/// the #mp_ack()
///
/// \param mp_ptr A pointer to the message passing data structure
/// for the given message passing channel.
void mp_rcv(MP_T* mp_ptr);

/// \brief A function for acknowledging the reception of a message.
/// This function shall be called to release space in the receiving
/// buffer when the received data is no longer used.
/// It is not necessary to call #mp_ack() after each #mp_rcv() call.
/// It is possible to work on 2 or more incomming messages at the same
/// time with out them being overwritten.
///
/// \param mp_ptr A pointer to the message passing data structure
/// for the given message passing channel.
void mp_ack(MP_T* mp_ptr);

#endif /* _MP_H_ */
