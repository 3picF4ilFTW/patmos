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
 Method Cache for Patmos
 Author: Philipp Degasperi (philipp.degasperi@gmail.com)
 */

package patmos

import Chisel._
import Node._
import MConstants._
import Constants._
import ocp._

import scala.collection.mutable.HashMap
import scala.util.Random
import scala.math

/*
  Method Cache Constants only used internally in MCache.scala
 */
object MConstants {

  val MCACHE_WORD_SIZE = MCACHE_SIZE / 4
  val METHOD_BLOCK_SIZE = MCACHE_WORD_SIZE / METHOD_COUNT
  val METHOD_COUNT_WIDTH = log2Up(METHOD_COUNT)
  val METHOD_BLOCK_SIZE_WIDTH = log2Up(METHOD_BLOCK_SIZE)
  val MCACHE_SIZE_WIDTH = log2Up(MCACHE_WORD_SIZE)

}

/*
  Internal and external connections for the Method Cache
 */
class FeMCache extends Bundle() {
  val addrEven = Bits(width = EXTMEM_ADDR_WIDTH)
  val addrOdd = Bits(width = EXTMEM_ADDR_WIDTH)
}
class ExMCache() extends Bundle() {
  val doCallRet = Bool()
  val callRetBase = UInt(width = EXTMEM_ADDR_WIDTH)
  val callRetAddr = UInt(width = EXTMEM_ADDR_WIDTH)
}
class MCacheFe extends Bundle() {
  val instrEven = Bits(width = INSTR_WIDTH)
  val instrOdd = Bits(width = INSTR_WIDTH)
  // relative base address
  val relBase = UInt(width = MAX_OFF_WIDTH)
  // relative program counter
  val relPc = UInt(width = MAX_OFF_WIDTH+1)
  // offset between relative and absolute program counter
  val reloc = UInt(width = DATA_WIDTH)
  val memSel = Bits(width = 2)
}
class MCacheIO extends Bundle() {
  val ena_out = Bool(OUTPUT)
  val ena_in = Bool(INPUT)
  val invalidate = Bool(INPUT)
  val femcache = new FeMCache().asInput
  val exmcache = new ExMCache().asInput
  val mcachefe = new MCacheFe().asOutput
  val ocp_port = new OcpBurstMasterPort(EXTMEM_ADDR_WIDTH, DATA_WIDTH, BURST_LENGTH)
  val perf = new MethodCachePerf()
}
class MCacheCtrlIO extends Bundle() {
  val ena_in = Bool(INPUT)
  val fetch_ena = Bool(OUTPUT)
  val mcache_ctrlrepl = new MCacheCtrlRepl().asOutput
  val mcache_replctrl = new MCacheReplCtrl().asInput
  val femcache = new FeMCache().asInput
  val exmcache = new ExMCache().asInput
  val ocp_port = new OcpBurstMasterPort(EXTMEM_ADDR_WIDTH, DATA_WIDTH, BURST_LENGTH)
}
class MCacheCtrlRepl extends Bundle() {
  val wEna = Bool()
  val wData = Bits(width = INSTR_WIDTH)
  val wAddr = Bits(width = EXTMEM_ADDR_WIDTH)
  val wTag = Bool()
  val addrEven = Bits(width = MCACHE_SIZE_WIDTH)
  val addrOdd = Bits(width = MCACHE_SIZE_WIDTH)
  val instrStall = Bool()
}
class MCacheReplCtrl extends Bundle() {
  val hit = Bool()
}
class MCacheReplIO extends Bundle() {
  val ena_in = Bool(INPUT)
  val invalidate = Bool(INPUT)
  val hitEna = Bool(OUTPUT)
  val exmcache = new ExMCache().asInput
  val mcachefe = new MCacheFe().asOutput
  val mcache_ctrlrepl = new MCacheCtrlRepl().asInput
  val mcache_replctrl = new MCacheReplCtrl().asOutput
  val mcachemem_in = new MCacheMemIn().asOutput
  val mcachemem_out = new MCacheMemOut().asInput
  val perf = new MethodCachePerf()
}
class MCacheMemIn extends Bundle() {
  val wEven = Bool()
  val wOdd = Bool()
  val wData = Bits(width = DATA_WIDTH)
  val wAddr = Bits(width = (log2Up(MCACHE_WORD_SIZE / 2)))
  val addrEven = Bits(width = (log2Up(MCACHE_WORD_SIZE / 2)))
  val addrOdd = Bits(width = (log2Up(MCACHE_WORD_SIZE / 2)))
}
class MCacheMemOut extends Bundle() {
  val instrEven = Bits(width = INSTR_WIDTH)
  val instrOdd = Bits(width = INSTR_WIDTH)
}
class MCacheMemIO extends Bundle() {
  val mcachemem_in = new MCacheMemIn().asInput
  val mcachemem_out = new MCacheMemOut().asOutput
}

/*
 MCache: Top Level Class for the Method Cache
 */
class MCache() extends Module {
  val io = new MCacheIO()
  val mcachectrl = Module(new MCacheCtrl())
  val mcacherepl = Module(new MCacheReplFifo())
  //Use MCacheReplFifo2 for replacement with fixed block size
  //val mcacherepl = Module(new MCacheReplFifo2())
  val mcachemem = Module(new MCacheMem())
  //connect inputs to method cache ctrl unit
  mcachectrl.io.mcache_ctrlrepl <> mcacherepl.io.mcache_ctrlrepl
  mcachectrl.io.femcache <> io.femcache
  mcachectrl.io.exmcache <> io.exmcache
  mcachectrl.io.ocp_port <> io.ocp_port
  //connect inputs to method cache repl unit
  mcacherepl.io.exmcache <> io.exmcache
  mcacherepl.io.mcachefe <> io.mcachefe
  mcacherepl.io.mcache_replctrl <> mcachectrl.io.mcache_replctrl
  mcacherepl.io.perf <> io.perf
  //connect repl to on chip memory
  mcacherepl.io.mcachemem_in <> mcachemem.io.mcachemem_in
  mcacherepl.io.mcachemem_out <> mcachemem.io.mcachemem_out
  //connect enables
  mcachectrl.io.ena_in <> io.ena_in
  mcacherepl.io.ena_in <> io.ena_in
  //output enable depending on hit/miss/fetch
  io.ena_out := mcachectrl.io.fetch_ena & mcacherepl.io.hitEna
  //connect invalidate signal
  mcacherepl.io.invalidate := io.invalidate
}

/*
 MCacheMem: On-Chip Even/Odd Memory
 */
class MCacheMem() extends Module {
  val io = new MCacheMemIO()

  val mcacheEven = MemBlock(MCACHE_WORD_SIZE / 2, INSTR_WIDTH)
  val mcacheOdd = MemBlock(MCACHE_WORD_SIZE / 2, INSTR_WIDTH)

  mcacheEven.io <= (io.mcachemem_in.wEven, io.mcachemem_in.wAddr,
                    io.mcachemem_in.wData)

  mcacheOdd.io <= (io.mcachemem_in.wOdd, io.mcachemem_in.wAddr,
                   io.mcachemem_in.wData)

  io.mcachemem_out.instrEven := mcacheEven.io(io.mcachemem_in.addrEven)
  io.mcachemem_out.instrOdd := mcacheOdd.io(io.mcachemem_in.addrOdd)
}


/*
 MCacheReplFifo: Class controlls a FIFO replacement strategy
 including tag-memory to keep history of methods in cache.
 A variable block size is used in this replacement.
 */
class MCacheReplFifo() extends Module {
  val io = new MCacheReplIO()

  //tag field tables  for reading tag memory
  val mcacheAddrVec = { Vec.fill(METHOD_COUNT) { Reg(Bits(width = EXTMEM_ADDR_WIDTH)) }}
  val mcacheSizeVec = { Vec.fill(METHOD_COUNT) { Reg(init = Bits(0, width = MCACHE_SIZE_WIDTH+1)) }}
  val mcacheValidVec = { Vec.fill(METHOD_COUNT) { Reg(init = Bool(false)) }}
  val mcachePosVec = { Vec.fill(METHOD_COUNT) { Reg(Bits(width = MCACHE_SIZE_WIDTH)) }}
  //registers to save current replacement status
  val nextIndexReg = Reg(init = Bits(0, width = log2Up(METHOD_COUNT)))
  val nextTagReg = Reg(init = Bits(0, width = log2Up(METHOD_COUNT)))
  val nextPosReg = Reg(init = Bits(0, width = MCACHE_SIZE_WIDTH))
  val freeSpaceReg = Reg(init = SInt(MCACHE_WORD_SIZE, width = MCACHE_SIZE_WIDTH+2))
  //variables when call/return occurs to check tag field
  val posReg = Reg(init = Bits(0, width = MCACHE_SIZE_WIDTH))
  val hitReg = Reg(init = Bool(true))
  val wrPosReg = Reg(init = Bits(0, width = MCACHE_SIZE_WIDTH))
  val callRetBaseReg = Reg(init = UInt(1, DATA_WIDTH))
  val callAddrReg = Reg(init = UInt(1, DATA_WIDTH))
  val selIspmReg = Reg(init = Bool(false))
  val selMCacheReg = Reg(init = Bool(false))

  io.perf.hit := Bool(false)
  io.perf.miss := Bool(false)

  // hit detection
  val hit = Bool()
  val mergePosVec = { Vec.fill(METHOD_COUNT) { Bits(width = MCACHE_SIZE_WIDTH) } }
  hit := Bool(false)
  for (i <- 0 until METHOD_COUNT) {
    mergePosVec(i) := Bits(0)
    when (io.exmcache.callRetBase === mcacheAddrVec(i)
          && mcacheValidVec(i)) {
            hit := Bool(true)
            mergePosVec(i) := mcachePosVec(i)
          }
  }  
  val pos = Mux(hit, mergePosVec.fold(Bits(0))(_|_), nextPosReg)

  //read from tag memory on call/return to check if method is in the cache
  when (io.exmcache.doCallRet && io.ena_in) {

    callRetBaseReg := io.exmcache.callRetBase
    callAddrReg := io.exmcache.callRetAddr
    selIspmReg := io.exmcache.callRetBase(EXTMEM_ADDR_WIDTH-1, ISPM_ONE_BIT-2) === Bits(0x1)
    val selMCache = io.exmcache.callRetBase(EXTMEM_ADDR_WIDTH-1, ISPM_ONE_BIT-1) >= Bits(0x1)
    selMCacheReg := selMCache
    when (selMCache) {
      hitReg := hit
      posReg := pos

      when (hit) {
        io.perf.hit := Bool(true)
      } .otherwise {
        io.perf.miss := Bool(true)
      }
    }
  }

  val relBase = Mux(selMCacheReg,
                    posReg.toUInt,
                    callRetBaseReg(ISPM_ONE_BIT-3, 0))
  val relPc = callAddrReg + relBase

  val reloc = Mux(selMCacheReg,
                  callRetBaseReg - posReg.toUInt,
                  Mux(selIspmReg,
                      UInt(1 << (ISPM_ONE_BIT - 2)),
                      UInt(0)))

  //insert new tags when control unit requests
  when (io.mcache_ctrlrepl.wTag) {
    hitReg := Bool(true) //start fetch, we have again a hit!
    wrPosReg := posReg
    //update free space
    freeSpaceReg := freeSpaceReg - io.mcache_ctrlrepl.wData(MCACHE_SIZE_WIDTH,0) + mcacheSizeVec(nextIndexReg)
    //update tag fields
    mcachePosVec(nextIndexReg) := nextPosReg
    mcacheSizeVec(nextIndexReg) := io.mcache_ctrlrepl.wData(MCACHE_SIZE_WIDTH, 0)
    mcacheAddrVec(nextIndexReg) := io.mcache_ctrlrepl.wAddr
    mcacheValidVec(nextIndexReg) := Bool(true)
    //update pointers
    nextPosReg := nextPosReg + io.mcache_ctrlrepl.wData(MCACHE_SIZE_WIDTH-1,0)
    val nextTag = Mux(nextIndexReg === Bits(METHOD_COUNT - 1), Bits(0), nextIndexReg + Bits(1))
    nextIndexReg := nextTag
    when (nextTagReg === nextIndexReg) {
      nextTagReg := nextTag
    }
  }
  //free new space if still needed -> invalidate next method
  when (freeSpaceReg < SInt(0)) {
    freeSpaceReg := freeSpaceReg + mcacheSizeVec(nextTagReg)
    mcacheSizeVec(nextTagReg) := Bits(0)
    mcacheValidVec(nextTagReg) := Bool(false)
    nextTagReg := Mux(nextTagReg === Bits(METHOD_COUNT - 1), Bits(0), nextTagReg + Bits(1))
  }

  val wParity = io.mcache_ctrlrepl.wAddr(0)
  //adder could be moved to ctrl. unit to operate with rel. addresses here
  val wAddr = (wrPosReg + io.mcache_ctrlrepl.wAddr)(MCACHE_SIZE_WIDTH-1,1)
  val addrEven = (io.mcache_ctrlrepl.addrEven)(MCACHE_SIZE_WIDTH-1,1)
  val addrOdd = (io.mcache_ctrlrepl.addrOdd)(MCACHE_SIZE_WIDTH-1,1)

  io.mcachemem_in.wEven := Mux(wParity, Bool(false), io.mcache_ctrlrepl.wEna)
  io.mcachemem_in.wOdd := Mux(wParity, io.mcache_ctrlrepl.wEna, Bool(false))
  io.mcachemem_in.wData := io.mcache_ctrlrepl.wData
  io.mcachemem_in.wAddr := wAddr
  io.mcachemem_in.addrEven := addrEven
  io.mcachemem_in.addrOdd := addrOdd

  val instrEvenReg = Reg(Bits(width = INSTR_WIDTH))
  val instrOddReg = Reg(Bits(width = INSTR_WIDTH))
  val instrEven = io.mcachemem_out.instrEven
  val instrOdd = io.mcachemem_out.instrOdd
  when (!io.mcache_ctrlrepl.instrStall) {
    instrEvenReg := io.mcachefe.instrEven
    instrOddReg := io.mcachefe.instrOdd
  }
  io.mcachefe.instrEven := Mux(io.mcache_ctrlrepl.instrStall, instrEvenReg, instrEven)
  io.mcachefe.instrOdd := Mux(io.mcache_ctrlrepl.instrStall, instrOddReg, instrOdd)
  io.mcachefe.relBase := relBase
  io.mcachefe.relPc := relPc
  io.mcachefe.reloc := reloc
  io.mcachefe.memSel := Cat(selIspmReg, selMCacheReg)

  io.mcache_replctrl.hit := hitReg

  io.hitEna := hitReg
  
  // reset valid bits
  when (io.invalidate) {
    mcacheValidVec.map(_ := Bool(false))
  }
}


/*
 MCacheCtrl Class: Main Class of Method Cache, implements the State Machine and handles the R/W/Fetch of Cache and External Memory
 */
class MCacheCtrl() extends Module {
  val io = new MCacheCtrlIO()

  //fsm state variables
  val idleState :: sizeState :: transferState :: Nil = Enum(UInt(), 3)
  val mcacheState = Reg(init = idleState)
  //signals for method cache memory (mcache_repl)
  val addrEven = Bits(width = EXTMEM_ADDR_WIDTH)
  val addrOdd = Bits(width = EXTMEM_ADDR_WIDTH)
  val wData = Bits(width = DATA_WIDTH)
  val wTag = Bool() //signalizes the transfer of begin of a write
  val wAddr = Bits(width = EXTMEM_ADDR_WIDTH)
  val wEna = Bool()
  //signals for external memory
  val ocpCmdReg = Reg(init = OcpCmd.IDLE)
  val ocpAddrReg = Reg(Bits(width = EXTMEM_ADDR_WIDTH))
  val fetchEna = Bool()
  val transferSizeReg = Reg(Bits(width = MCACHE_SIZE_WIDTH))
  val fetchCntReg = Reg(Bits(width = MCACHE_SIZE_WIDTH))
  val burstCntReg = Reg(UInt(width = log2Up(BURST_LENGTH)))
  //input/output registers
  val callRetBaseReg = Reg(Bits(width = EXTMEM_ADDR_WIDTH))
  val msizeAddr = callRetBaseReg - Bits(1)
  val addrEvenReg = Reg(Bits())
  val addrOddReg = Reg(Bits())

  val ocpSlaveReg = Reg(next = io.ocp_port.S)

  //init signals
  addrEven := addrEvenReg
  addrOdd := addrOddReg
  wData := Bits(0)
  wTag := Bool(false)
  wEna := Bool(false)
  wAddr := Bits(0)
  fetchEna := Bool(true)

  // reset command when accepted
  when (io.ocp_port.S.CmdAccept === Bits(1)) {
    ocpCmdReg := OcpCmd.IDLE
  }

  //output to external memory
  io.ocp_port.M.Addr := Cat(ocpAddrReg, Bits("b00"))
  io.ocp_port.M.Cmd := ocpCmdReg
  io.ocp_port.M.Data := Bits(0)
  io.ocp_port.M.DataByteEn := Bits("b1111")
  io.ocp_port.M.DataValid := Bits(0)

  when (io.exmcache.doCallRet) {
    callRetBaseReg := io.exmcache.callRetBase // use callret to save base address for next cycle
    addrEvenReg := io.femcache.addrEven
    addrOddReg := io.femcache.addrOdd
  }

  //check if instruction is available
  when (mcacheState === idleState) {
    when(io.mcache_replctrl.hit === Bits(1)) {
      addrEven := io.femcache.addrEven
      addrOdd := io.femcache.addrOdd
    }
    //no hit... fetch from external memory
    .otherwise {
      burstCntReg := UInt(0)

      //aligned read from ssram
      io.ocp_port.M.Cmd := OcpCmd.RD
      when (io.ocp_port.S.CmdAccept === Bits(0)) {
        ocpCmdReg := OcpCmd.RD
      }
      io.ocp_port.M.Addr := Cat(msizeAddr(EXTMEM_ADDR_WIDTH-1,log2Up(BURST_LENGTH)),
                                Bits(0, width=log2Up(BURST_LENGTH)+2))
      ocpAddrReg := Cat(msizeAddr(EXTMEM_ADDR_WIDTH-1,log2Up(BURST_LENGTH)),
                        Bits(0, width=log2Up(BURST_LENGTH)))

      mcacheState := sizeState
    }
  }
  //fetch size of the required method from external memory address - 1
  when (mcacheState === sizeState) {
    fetchEna := Bool(false)
    when (ocpSlaveReg.Resp === OcpResp.DVA) {
      burstCntReg := burstCntReg + Bits(1)
      when (burstCntReg === msizeAddr(log2Up(BURST_LENGTH)-1,0)) {
        val size = ocpSlaveReg.Data(MCACHE_SIZE_WIDTH+2,2)
        //init transfer from external memory
        transferSizeReg := size
        fetchCntReg := Bits(0) //start to write to cache with offset 0
        when (burstCntReg === UInt(BURST_LENGTH - 1)) {
          io.ocp_port.M.Cmd := OcpCmd.RD
          when (io.ocp_port.S.CmdAccept === Bits(0)) {
            ocpCmdReg := OcpCmd.RD
          }
          io.ocp_port.M.Addr := Cat(callRetBaseReg, Bits("b00"))
          ocpAddrReg := callRetBaseReg
          burstCntReg := UInt(0)
        }
        //init transfer to on-chip method cache memory
        wTag := Bool(true)
        //size rounded to next double-word
        wData := size+size(0)
        //write base address to mcachemem for tagfield
        wAddr := callRetBaseReg
        mcacheState := transferState
      }
    }
  }

  //transfer/fetch method to the cache
  when (mcacheState === transferState) {
    fetchEna := Bool(false)
    when (fetchCntReg < transferSizeReg) {
      when (ocpSlaveReg.Resp === OcpResp.DVA) {
        fetchCntReg := fetchCntReg + Bits(1)
        burstCntReg := burstCntReg + Bits(1)
        when(fetchCntReg < transferSizeReg - Bits(1)) {
          //fetch next address from external memory
          when (burstCntReg === UInt(BURST_LENGTH - 1)) {
            io.ocp_port.M.Cmd := OcpCmd.RD
            when (io.ocp_port.S.CmdAccept === Bits(0)) {
              ocpCmdReg := OcpCmd.RD
            }
            io.ocp_port.M.Addr := Cat(callRetBaseReg + fetchCntReg + Bits(1), Bits("b00"))
            ocpAddrReg := callRetBaseReg + fetchCntReg + Bits(1) //need +1 because start fetching with the size of method
            burstCntReg := UInt(0)
          }
        }
        .otherwise {
          //restart to idle state if burst is done now
          when (burstCntReg === UInt(BURST_LENGTH - 1)) {
            fetchEna := Bool(true)
            addrEven := io.femcache.addrEven
            addrOdd := io.femcache.addrOdd
            mcacheState := idleState
          }
        }
        //write current address to mcache memory
        wData := ocpSlaveReg.Data
        wEna := Bool(true)
      }
      wAddr := fetchCntReg
    }
    //restart to idle state after burst is done
    .otherwise {
      when (ocpSlaveReg.Resp === OcpResp.DVA) {
        burstCntReg := burstCntReg + Bits(1)
      }
      when (burstCntReg === UInt(BURST_LENGTH - 1)) {
        fetchEna := Bool(true)
        addrEven := io.femcache.addrEven
        addrOdd := io.femcache.addrOdd
        mcacheState := idleState
      }
    }
  }

  //outputs to mcache memory
  io.mcache_ctrlrepl.addrEven := addrEven
  io.mcache_ctrlrepl.addrOdd := addrOdd
  io.mcache_ctrlrepl.wEna := wEna
  io.mcache_ctrlrepl.wData := wData
  io.mcache_ctrlrepl.wAddr := wAddr
  io.mcache_ctrlrepl.wTag := wTag
  io.mcache_ctrlrepl.instrStall := mcacheState != idleState

  io.fetch_ena := fetchEna
}

