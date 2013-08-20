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
 * Definitions for OCP ports that support Bursts
 * 
 * Authors: Wolfgang Puffitsch (wpuffitsch@gmail.com)
 * 
 */

package ocp

import Chisel._
import Node._

// Burst masters provide handshake signals
class OcpBurstMasterSignals(addrWidth : Int, dataWidth : Int)
  extends OcpMasterSignals(addrWidth, dataWidth) {
  val DataValid = Bits(width = 1)
  val DataByteEn = Bits(width = dataWidth/8)

  // This does not really clone, but Data.clone doesn't either
  override def clone() = {
    val res = new OcpBurstMasterSignals(addrWidth, dataWidth)
  	res.asInstanceOf[this.type]
  }
}

// Burst slaves provide handshake signal
class OcpBurstSlaveSignals(dataWidth : Int)
  extends OcpSlaveSignals(dataWidth) {
  val DataAccept = Bits(width = 1)

  // This does not really clone, but Data.clone doesn't either
  override def clone() = {
    val res = new OcpBurstSlaveSignals(dataWidth)
  	res.asInstanceOf[this.type]
  }
}

// Master port
class OcpBurstMasterPort(addrWidth : Int, dataWidth : Int, burstLen : Int) extends Bundle() {
  val burstLength = burstLen
  // Clk is implicit in Chisel
  val M = new OcpBurstMasterSignals(addrWidth, dataWidth).asOutput
  val S = new OcpBurstSlaveSignals(dataWidth).asInput 
}

// Slave port is reverse of master port
class OcpBurstSlavePort(addrWidth : Int, dataWidth : Int, burstLen : Int) extends Bundle() {
  val burstLength = burstLen
  // Clk is implicit in Chisel
  val M = new OcpBurstMasterSignals(addrWidth, dataWidth).asInput
  val S = new OcpBurstSlaveSignals(dataWidth).asOutput
}

// Bridge between word-oriented port and burst port
class OcpBurstBridge(master : OcpCoreMasterPort, slave : OcpBurstSlavePort) {
  val addrWidth = master.M.Addr.width
  val dataWidth = master.M.Data.width
  val burstLength = slave.burstLength
  val burstAddrBits = log2Up(burstLength)

  // State of transmission
  val idle :: read :: readResp :: write :: writeResp :: Nil = Enum(5){ Bits() }
  val state = Reg(resetVal = idle)
  val burstCnt = Reg(resetVal = UFix(0, burstAddrBits))
  val cmdPos = Reg(resetVal = Bits(0, burstAddrBits))

  // Register signals that come from master
  val masterReg = Reg(resetVal = OcpMasterSignals.resetVal(master.M))

  // Register to delay response
  val slaveReg = Reg(resetVal = OcpSlaveSignals.resetVal(slave.S))

  masterReg.Cmd := master.M.Cmd
  masterReg.Addr := master.M.Addr
  when(master.M.Cmd === OcpCmd.RD) {
	state := read
	cmdPos := master.M.Addr(burstAddrBits+log2Up(dataWidth/8)-1, log2Up(dataWidth/8))
  }
  when(master.M.Cmd === OcpCmd.WRNP) {
	state := write
	cmdPos := master.M.Addr(burstAddrBits+log2Up(dataWidth/8)-1, log2Up(dataWidth/8))
	masterReg.Data := master.M.Data
	masterReg.ByteEn := master.M.ByteEn
  }

  // Default values
  slave.M.Cmd := masterReg.Cmd
  slave.M.Addr := Cat(masterReg.Addr(addrWidth-1, burstAddrBits+log2Up(dataWidth/8)),
						 Fill(Bits(0), burstAddrBits+log2Up(dataWidth/8)))
  slave.M.Data := Bits(0)
  slave.M.DataByteEn := Bits(0)
  slave.M.DataValid := Bits(0)
  master.S := slave.S
  
  // Read burst
  when(state === read) {
	when(slave.S.Resp === OcpResp.DVA) {
	  when(burstCnt === cmdPos) {
		slaveReg := slave.S
	  }
	  when(burstCnt === UFix(burstLength - 1)) {
		state := readResp
	  }
	  burstCnt := burstCnt + UFix(1)
	}
	master.S.Resp := OcpResp.NULL
	master.S.Data := Bits(0)
  }
  when(state === readResp) {
	state := idle
	master.S := slaveReg
  }
  
  // Write burst
  when(state === write) {
	slave.M.DataValid := Bits(1)
	when(burstCnt === cmdPos) {
	  slave.M.Data := masterReg.Data
	  slave.M.DataByteEn := masterReg.ByteEn
	}
	when(burstCnt === UFix(burstLength - 1)) {
	  state := idle
	}
	when(slave.S.DataAccept === Bits(1)) {
	  burstCnt := burstCnt + UFix(1)
	}
  }

}
