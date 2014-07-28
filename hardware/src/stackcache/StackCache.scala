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
 * Stack cache memory
 *
 * Author: Sahar Abbaspour (sabb@dtu.dk)
 *         Florian Brandner (florian.brandner@ensta-paristech.fr)
 *
 */

package stackcache

import Chisel._
import Node._

import scala.math

import ocp._
import patmos._
import patmos.Constants._

class StackCache() extends Module {
  val io = new StackCacheIO() {
    // slave to cpu
    val fromCPU = new OcpCoreSlavePort(EXTMEM_ADDR_WIDTH, DATA_WIDTH) 
    // master to memory
    val toMemory = new OcpBurstMasterPort(EXTMEM_ADDR_WIDTH, DATA_WIDTH, 
                                          BURST_LENGTH) 
  }

  // number of bits needed to address the bytes of a word
  val wordBits = Chisel.log2Up(BYTES_PER_WORD)

  // number of bits needed to address the bytes of a burst
  val burstBits = Chisel.log2Up(BURST_LENGTH * BYTES_PER_WORD)

  // number of bits to address the stack cache's memory
  val scSizeBits = Chisel.log2Up(SCACHE_SIZE / BYTES_PER_WORD)

  // state machine to manage spilling and filling
  val idleState :: fillState :: waitFillState :: spillState :: holdSpillState :: waitSpillState :: Nil = Enum(UInt(), 6)
  val state = Reg(init = idleState)

  // stack top pointer
  val stackTopReg = Reg(init = UInt(0, DATA_WIDTH))

  // memory top pointer
  val memTopReg = Reg(init = UInt(0, DATA_WIDTH))

  // temporary address used during filling/spilling 
  val transferAddr = Reg(init = UInt(0, DATA_WIDTH))

  // temporary address used during filling
  val requiredMemTop = Reg(init = UInt(0, DATA_WIDTH))

  // the actual memory of the stack cache
  // TODO: convert to array
  val memoryBlock0 = MemBlock(SCACHE_SIZE / BYTES_PER_WORD, BYTE_WIDTH)
  val memoryBlock1 = MemBlock(SCACHE_SIZE / BYTES_PER_WORD, BYTE_WIDTH)
  val memoryBlock2 = MemBlock(SCACHE_SIZE / BYTES_PER_WORD, BYTE_WIDTH)
  val memoryBlock3 = MemBlock(SCACHE_SIZE / BYTES_PER_WORD, BYTE_WIDTH)

  val mb_rdAddr = UInt(width=ADDR_WIDTH)
  val mb_rdData = memoryBlock3.io.rdData ## memoryBlock2.io.rdData ## memoryBlock1.io.rdData ## memoryBlock0.io.rdData
  val mb_wrAddr = UInt(width=ADDR_WIDTH)
  val mb_wrEna = UInt(width=BYTE_WIDTH)
  val mb_wrData = UInt(width=DATA_WIDTH)
  
  // register addr for MemBlock
  val rdAddrReg = Reg(next = memoryBlock0.io.rdAddr)

  // response to CPU for read/write requests
  val responseToCPU = Reg(init = OcpResp.NULL)
  
  // default OCP "request"
  io.stall := Bool(false)
  io.toMemory.M.Cmd := OcpCmd.IDLE
  io.toMemory.M.Addr := UInt(0)
  io.toMemory.M.Data := UInt(0)
  io.toMemory.M.DataValid := UInt(0)
  io.toMemory.M.DataByteEn := UInt("b1111")

  // default signals for the stack cache's memory
  mb_rdAddr := UInt(0)
  mb_wrAddr := UInt(0)
  mb_wrEna := UInt(0)
  mb_wrData := UInt(0)

  // extract current burst counter
  val burstCounter = transferAddr.apply(burstBits - 1, wordBits)

  // reset response to CPU
  responseToCPU := OcpResp.NULL

  //////////////////////////////////////////////////////////////////////////////
  // Stack Control Interface (mfs, sres, sens, sfree)
  //////////////////////////////////////////////////////////////////////////////
  switch(state) {
    is(idleState) {
      // perform operation requested by pipeline
      state := idleState

      switch(io.exsc.op) {
        is(opNone) {
          // don't do anything
        }
        is(opSetStackTop) {
          // assign the operation's operand to the stack top pointer
          stackTopReg := io.exsc.opData
        }
        is(opSetMemTop) {
          // assign the operation's operand to the mem top pointer
          memTopReg := io.exsc.opData
        }
        is(opSRES) {
          // decrement the stack top pointer
          val nextStackTop = stackTopReg - io.exsc.opData
          stackTopReg := nextStackTop

          // start transfer from the current stack pointer + SCACHE_SIZE on
          val nextTransferAddr = (nextStackTop + UInt(SCACHE_SIZE)).apply(
                                       ADDR_WIDTH-1, 
                                       burstBits) ## Fill(burstBits, UInt("b0"))

          // start reading from the stack cache's memory
          mb_rdAddr := nextTransferAddr.apply(scSizeBits + wordBits - 1, wordBits)

          // store transfer address in a register
          transferAddr := nextTransferAddr

          // check if spilling is actually needed
          val needsSpill = (memTopReg - nextStackTop) > UInt(SCACHE_SIZE)
          state := Mux(needsSpill, holdSpillState, idleState)
        }
        is(opSENS) {
          // compute required mem top pointer, and check if filling is needed
          val nextRequiredMemTop = stackTopReg + io.exsc.opData

          // start transfer from the current memory top pointer on
          transferAddr := memTopReg.apply(ADDR_WIDTH - 1, burstBits) ## 
                            Fill(burstBits, UInt("b0"))

          // check if filling is needed
          val needsFill = memTopReg < nextRequiredMemTop
  
          // update memory top pointer if needed
          requiredMemTop := nextRequiredMemTop

          // start actual filling if needed
          state := Mux(needsFill, fillState, idleState)
        }
        is(opSFREE) {
          // move stack top pointer upwards
          val nextStackTop = stackTopReg + io.exsc.opData
          stackTopReg := nextStackTop

          // ensure that mem top pointer is above stack top
          when(nextStackTop > memTopReg) {
            memTopReg := nextStackTop
          }
        }
      }
    }

    // // // // // // // // // // // // // // // // // // // // // // // // //
    // SPILLING
    // // // // // // // // // // // // // // // // // // // // // // // // //
    is (holdSpillState) {
      // stall the pipeline
      io.stall := Bool(true)

      val nextTransferAddr = transferAddr + UInt(BYTES_PER_WORD)

      // only write the data that actually needs spilling
      val writeEnable = ((stackTopReg + UInt(SCACHE_SIZE)) <= transferAddr) & 
                        (transferAddr < memTopReg)

      // generate an OCP write request
      when(io.ena_in) {
        io.toMemory.M.Cmd := OcpCmd.WR
        io.toMemory.M.Addr := transferAddr
        io.toMemory.M.Data := mb_rdData
        io.toMemory.M.DataValid := UInt(1)
        io.toMemory.M.DataByteEn := Fill(4, writeEnable)
      }

      // check if command has been accepted
      val accepted = io.toMemory.S.CmdAccept === UInt(1)

      // read next data element once accepted, otherwise hold
      mb_rdAddr := Mux(accepted, nextTransferAddr.apply(scSizeBits + wordBits - 1, 
                                                        wordBits), 

                                 rdAddrReg)

      // increment transfer address if accepted
      transferAddr := Mux(accepted, nextTransferAddr, transferAddr)

      // advance state if accepted
      state := Mux(accepted, spillState, holdSpillState)
    }

    is(spillState) {
      // stall the pipeline
      io.stall := Bool(true)

      val nextTransferAddr = transferAddr + UInt(BYTES_PER_WORD)

      // only write the data that actually needs spilling
      val writeEnable = ((stackTopReg + UInt(SCACHE_SIZE)) <= transferAddr) & 
                        (transferAddr < memTopReg)

      // read next data element from the stack cache's memory
      mb_rdAddr := nextTransferAddr.apply(scSizeBits + wordBits - 1, wordBits)

      // hand current data element over to the OCP bus
      io.toMemory.M.Data := mb_rdData
      io.toMemory.M.DataValid := UInt(1)
      io.toMemory.M.DataByteEn := Fill(4, writeEnable)

      // increment transfer address and advance state
      transferAddr := nextTransferAddr

      state := Mux(burstCounter === UInt(BURST_LENGTH - 1), 
                   waitSpillState, spillState)
    }

    is(waitSpillState) {
      io.stall := Bool(true)

      // check whether all data has been spilled
      val spillingDone = memTopReg <= transferAddr

      // wait for a response from the memory, if all data has been transfered
      // return to the IDLE state
      state := Mux(io.toMemory.S.Resp === OcpResp.DVA, 
                   Mux(spillingDone, idleState, holdSpillState), 
                   waitSpillState)

      // done? finally compute the new memory top pointer
      memTopReg := Mux(spillingDone, stackTopReg + UInt(SCACHE_SIZE), memTopReg)

      // if more spilling is needed preserve the stack cache's read address
      mb_rdAddr := Mux(spillingDone, UInt(0), rdAddrReg)

    }

    // // // // // // // // // // // // // // // // // // // // // // // // //
    // FILLING
    // // // // // // // // // // // // // // // // // // // // // // // // //
    is(fillState) {
      // stall the pipeline
      io.stall := Bool(true)

      when(io.ena_in) {
        // generate an OCP read request and wait that it is accepted
        io.toMemory.M.Cmd := OcpCmd.RD
        io.toMemory.M.Addr := transferAddr

        // go to next state
        state := Mux(io.toMemory.S.CmdAccept === Bits(1), 
                     waitFillState, fillState)
      }
    }

    is(waitFillState) {
      io.stall := Bool(true)
      io.toMemory.M.Addr := transferAddr

      when(io.toMemory.S.Resp === OcpResp.DVA)
      {
        // check whether all data has been filled
        val fillingDone = requiredMemTop <= transferAddr

        // check whether data should be written to the stack cache's memory
        val writeEnable = !fillingDone && memTopReg <= transferAddr

        // write to the stack cache's memory
        mb_wrEna := Fill(4, writeEnable)
        mb_wrData := io.toMemory.S.Data
        mb_wrAddr := transferAddr.apply(scSizeBits + wordBits - 1, wordBits)

        // increment transfer address
        transferAddr := transferAddr + UInt(BYTES_PER_WORD)

        // go to next state
        state := Mux(burstCounter === UInt(BURST_LENGTH - 1), 
                    Mux(fillingDone, idleState, fillState), 
                    waitFillState)

        // done? finally compute the new memory top pointer
        memTopReg := Mux(fillingDone, requiredMemTop, memTopReg)
      }
    }
  }


  //////////////////////////////////////////////////////////////////////////////
  // Stack Cache Memory Interface (loads/stores)
  //////////////////////////////////////////////////////////////////////////////

  // send response and (potential) read-data to CPU
  io.fromCPU.S.Resp := responseToCPU
  io.fromCPU.S.Data := mb_rdData
  
  // handle read/write requests from CPU
  when(io.fromCPU.M.Cmd === OcpCmd.WR) {
    // write to the stack cache's memory
    mb_wrEna := UInt("b1111") // io.fromCPU.M.ByteEn
    mb_wrData := io.fromCPU.M.Data
    mb_wrAddr := (io.fromCPU.M.Addr + stackTopReg).apply(scSizeBits + wordBits - 1, 
                                                         wordBits)

    // generate response that indicates that the write has completed 
    responseToCPU := OcpResp.DVA
  }
  .elsewhen(io.fromCPU.M.Cmd === OcpCmd.RD) {
    // Read from the stack cache's memory
    mb_rdAddr := (io.fromCPU.M.Addr + stackTopReg).apply(scSizeBits + wordBits - 1, 
                                                         wordBits)

    // generate response that indicates that the write has completed 
    responseToCPU := OcpResp.DVA
  }

  //////////////////////////////////////////////////////////////////////////////
  // Fiddle with signals to/from memory
  //////////////////////////////////////////////////////////////////////////////
  
  // TODO: this should be a for loop
  memoryBlock0.io.rdAddr := mb_rdAddr
  memoryBlock1.io.rdAddr := mb_rdAddr
  memoryBlock2.io.rdAddr := mb_rdAddr
  memoryBlock3.io.rdAddr := mb_rdAddr

  memoryBlock0.io.wrAddr := mb_wrAddr
  memoryBlock1.io.wrAddr := mb_wrAddr
  memoryBlock2.io.wrAddr := mb_wrAddr
  memoryBlock3.io.wrAddr := mb_wrAddr

  memoryBlock0.io.wrEna := mb_wrEna.apply(0)
  memoryBlock1.io.wrEna := mb_wrEna.apply(1)
  memoryBlock2.io.wrEna := mb_wrEna.apply(2)
  memoryBlock3.io.wrEna := mb_wrEna.apply(3)
  
  memoryBlock0.io.wrData := mb_wrData.apply(BYTE_WIDTH - 1, 0)
  memoryBlock1.io.wrData := mb_wrData.apply(2 * BYTE_WIDTH - 1, BYTE_WIDTH)
  memoryBlock2.io.wrData := mb_wrData.apply(3 * BYTE_WIDTH - 1, 2 * BYTE_WIDTH)
  memoryBlock3.io.wrData := mb_wrData.apply(DATA_WIDTH - 1, 3 * BYTE_WIDTH)
  
  //////////////////////////////////////////////////////////////////////////////
  // preserve some signals for debugging
  //////////////////////////////////////////////////////////////////////////////
  
  debug(mb_rdAddr)
  debug(mb_rdData)
  debug(mb_wrAddr)
  debug(mb_wrData)
  debug(mb_wrEna)
}








