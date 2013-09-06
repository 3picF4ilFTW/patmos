/*
   Copyright 2013 Technical University of Denmark, DTU Compute. 
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
 * Simple I/O module for timer
 * 
 * Authors: Wolfgang Puffitsch (wpuffitsch@gmail.com)
 * 
 */


package io

import Chisel._
import Node._

import patmos.TimerIO
import patmos.Constants._

import ocp._

class Timer(clk_freq: Int) extends Component {
  val io = new TimerIO()

  val masterReg = Reg(io.ocp.M)

  // Register for cycle counter
  val cycleReg   = Reg(resetVal = UFix(0, 2*DATA_WIDTH))

  // Registers for usec counter
  val cycPerUSec = UFix(clk_freq/1000000)
  val usecSubReg = Reg(resetVal = UFix(0))
  val usecReg    = Reg(resetVal = UFix(0, 2*DATA_WIDTH))

  // Registers for data to read
  val cycleHiReg = Reg(resetVal = Bits(0, DATA_WIDTH))
  val usecHiReg  = Reg(resetVal = Bits(0, DATA_WIDTH))

  // Default response
  val resp = Bits()
  val data = Bits(width = DATA_WIDTH)
  resp := OcpResp.NULL
  data := Bits(0)

  // Read current state of timer
  when(masterReg.Cmd === OcpCmd.RD) {
	resp := OcpResp.DVA

	// Read cycle counter
	// Must read word at higher address first!
	when(masterReg.Addr(3, 2) === Bits("b01")) {
	  data := cycleReg(DATA_WIDTH-1, 0)
	  cycleHiReg := cycleReg(2*DATA_WIDTH-1, DATA_WIDTH)
	}
	when(masterReg.Addr(3, 2) === Bits("b00")) {
	  data := cycleHiReg
	}

	// Read usec counter
	// Must read word at higher address first!
	when(masterReg.Addr(3, 2) === Bits("b11")) {
	  data := usecReg(DATA_WIDTH-1, 0)
	  usecHiReg := usecReg(2*DATA_WIDTH-1, DATA_WIDTH)
	}
	when(masterReg.Addr(3, 2) === Bits("b10")) {
	  data := usecHiReg
	}
  }

  // Connections to master
  io.ocp.S.Resp := resp
  io.ocp.S.Data := data

  // Increment cycle counter
  cycleReg := cycleReg + UFix(1)
  // Increment usec counter
  usecSubReg := usecSubReg + UFix(1)
  when(usecSubReg === cycPerUSec) {
	usecSubReg := UFix(0)
	usecReg := usecReg + UFix(1)
  }
}
