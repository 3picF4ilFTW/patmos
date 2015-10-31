/*
   Copyright 2015 Technical University of Denmark, DTU Compute.
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
  Instruction Cache with Prefetcher for Patmos
  Authors: Philipp Degasperi (philipp.degasperi@gmail.com)
           Wolfgang Puffitsch (wpuffitsch@gmail.com)
	   Bekim Cilku (bcilku@gmail.com)
 */

package patmos

import Chisel._
import Node._
import PIConstants._
import Constants._
import ocp._

import scala.collection.mutable.HashMap
import scala.util.Random
import scala.math

/*
  Instruction cache constants only used internally
 */
object PIConstants {

  val ICACHE_WORD_SIZE = ICACHE_SIZE / 4
  val ICACHE_SIZE_WIDTH = log2Up(ICACHE_WORD_SIZE)

  val LINE_WORD_SIZE = BURST_LENGTH
  val LINE_WORD_SIZE_WIDTH = log2Up(LINE_WORD_SIZE)

  val LINE_COUNT = ICACHE_WORD_SIZE / LINE_WORD_SIZE
  val LINE_COUNT_WIDTH = log2Up(LINE_COUNT)

  val TAG_HIGH = EXTMEM_ADDR_WIDTH - 1
  val TAG_LOW = LINE_COUNT_WIDTH + LINE_WORD_SIZE_WIDTH
  val TAG_SIZE = TAG_HIGH - TAG_LOW + 1

  val INDEX_HIGH = TAG_LOW - 1
  val INDEX_LOW = LINE_WORD_SIZE_WIDTH
  val INDEX_SIZE = INDEX_HIGH - INDEX_LOW + 1
}

/*
  Internal connections for the instruction cache
 */
class PICacheCtrlIO extends Bundle() {
  val ena_in = Bool(INPUT)
  val fetch_ena = Bool(OUTPUT)
  val ctrlrepl = new PICacheCtrlRepl().asOutput
  val replctrl = new PICacheReplCtrl().asInput
  val ocp_port = new OcpBurstMasterPort(EXTMEM_ADDR_WIDTH, DATA_WIDTH, BURST_LENGTH)
  val perf = new InstructionCachePerf()
}
class PICacheCtrlRepl extends Bundle() {
  val wEna = Bool()
  val wData = Bits(width = INSTR_WIDTH)
  val wAddr = Bits(width = ADDR_WIDTH)
  val wTag = Bool()
}
class PICachePrefRepl extends Bundle() {
  val pref_en = Bool()
  val prefAddr = Bits(INPUT, width= EXTMEM_ADDR_WIDTH)
}
class PICacheReplCtrl extends Bundle() {
  val hit = Bool() 
  val hitPref = Bool()
  val fetchAddr = Bits(width = EXTMEM_ADDR_WIDTH)
  val selCache = Bool()
}
class PICacheReplIO extends Bundle() {
  val ena_in = Bool(INPUT)
  val invalidate = Bool(INPUT)
  val exicache = new ExICache().asInput
  val feicache = new FeICache().asInput
  val icachefe = new ICacheFe().asOutput
  val ctrlrepl = new PICacheCtrlRepl().asInput
  val replctrl = new PICacheReplCtrl().asOutput
  val prefrepl = new PICachePrefRepl().asInput
  val memIn = new PICacheMemIn().asOutput
  val memOut = new PICacheMemOut().asInput
}

class PICacheMemIn extends Bundle() {
  val wEven = Bool()
  val wOdd = Bool()
  val wData = Bits(width = DATA_WIDTH)
  val wAddr = Bits(width = INDEX_SIZE + LINE_WORD_SIZE_WIDTH)
  val addrOdd = Bits(width = INDEX_SIZE + LINE_WORD_SIZE_WIDTH)
  val addrEven = Bits(width = INDEX_SIZE + LINE_WORD_SIZE_WIDTH)
}
class PICacheMemOut extends Bundle() {
  val instrEven = Bits(width = INSTR_WIDTH)
  val instrOdd = Bits(width = INSTR_WIDTH)
}
class PICacheMemIO extends Bundle() {
  val memIn = new ICacheMemIn().asInput
  val memOut = new ICacheMemOut().asOutput
}
class PrefetcherIO extends Bundle() {
  val ena_in = Bool(INPUT)
  val feicache = new FeICache().asInput
  val prefrepl = new PICachePrefRepl().asOutput
}



/*
 ICache: Top Level Class for the Instruction Cache
 */
class PICache() extends Module {
  val io = new ICacheIO()
  // Generate submodules of instruction cache
  val ctrl = Module(new PICacheCtrl())
  val repl = Module(new PICacheReplDm())
  val mem = Module(new PICacheMem())
  val pref = Module(new PFSMDM())
  // Connect control unit
  ctrl.io.ctrlrepl <> repl.io.ctrlrepl
  ctrl.io.ocp_port <> io.ocp_port
  ctrl.io.perf <> io.perf
  // Connect replacement unit
  repl.io.exicache <> io.exicache
  repl.io.feicache <> io.feicache
  repl.io.icachefe <> io.icachefe
  repl.io.replctrl <> ctrl.io.replctrl
  repl.io.prefrepl <> pref.io.prefrepl
  pref.io.feicache <> io.feicache
  // Connect replacement unit to on-chip memory
  repl.io.memIn <> mem.io.memIn
  repl.io.memOut <> mem.io.memOut
  // Connect enable signal
  ctrl.io.ena_in <> io.ena_in
  repl.io.ena_in <> io.ena_in
  pref.io.ena_in <> io.ena_in
  // Output enable depending on hit/miss/fetch
  io.ena_out := ctrl.io.fetch_ena
  // Connect invalidate signal
  repl.io.invalidate := io.invalidate
}

/*
 ICacheMem Class: On-Chip Instruction Cache Memory
 */
class PICacheMem extends Module {
  val io = new PICacheMemIO()

  val icacheEven = MemBlock(ICACHE_WORD_SIZE / 2, INSTR_WIDTH)
  val icacheOdd = MemBlock(ICACHE_WORD_SIZE / 2, INSTR_WIDTH)

  //Write port on on-chip memory ?
  icacheEven.io <= (io.memIn.wEven, io.memIn.wAddr, io.memIn.wData)
  icacheOdd.io <= (io.memIn.wOdd, io.memIn.wAddr, io.memIn.wData)

  //Read port from on-chip memory
  io.memOut.instrEven := icacheEven.io(io.memIn.addrEven)
  io.memOut.instrOdd := icacheOdd.io(io.memIn.addrOdd)
}

/*
 Direct-Mapped Replacement Class
 */
class PICacheReplDm() extends Module {
  val io = new PICacheReplIO()

  // Tag memory and vector for valid bits
  val tagMemEven = MemBlock(LINE_COUNT / 2, TAG_SIZE)
  val tagMemOdd = MemBlock(LINE_COUNT / 2, TAG_SIZE)
  val tagMemEvenPref = MemBlock(LINE_COUNT / 2, TAG_SIZE)
  val tagMemOddPref = MemBlock(LINE_COUNT / 2, TAG_SIZE) 
  val validVec = Vec.fill(LINE_COUNT) { Reg(init = Bool(false)) }
  
  // Variables for call/return
  val callRetBaseReg = Reg(init = UInt(1, DATA_WIDTH))
  val callAddrReg = Reg(init = UInt(1, DATA_WIDTH))
  val selSpmReg = Reg(init = Bool(false))
  val selCacheReg = Reg(init = Bool(false))

  val fetchAddr = Bits(width = EXTMEM_ADDR_WIDTH)
  val hitEven = Bool()
  val hitOdd = Bool()
  val hitPref = Bool() 

  val relBase = Mux(selCacheReg,
                    callRetBaseReg(EXTMEM_ADDR_WIDTH-1, 0),
                    callRetBaseReg(ISPM_ONE_BIT-3, 0))
  val relPc = callAddrReg + relBase

  val reloc = Mux(selCacheReg,
                  UInt(0),
                  Mux(selSpmReg,
                      UInt(1 << (ISPM_ONE_BIT - 2)),
                      UInt(0)))

  when (io.exicache.doCallRet && io.ena_in) {
    callRetBaseReg := io.exicache.callRetBase
    callAddrReg := io.exicache.callRetAddr
    selSpmReg := io.exicache.callRetBase(EXTMEM_ADDR_WIDTH-1, ISPM_ONE_BIT-2) === Bits(0x1)
    selCacheReg := io.exicache.callRetBase(EXTMEM_ADDR_WIDTH-1, ISPM_ONE_BIT-1) >= Bits(0x1)
  }

  // Register addresses
  val addrEvenReg = Reg(next = io.feicache.addrEven)
  val addrOddReg = Reg(next = io.feicache.addrOdd)
  val addrPrefReg = Reg(next = io.prefrepl.prefAddr)

  // Addresses for tag memory
  val indexEven = io.feicache.addrEven(INDEX_HIGH, INDEX_LOW+1)
  val indexOdd = io.feicache.addrOdd(INDEX_HIGH, INDEX_LOW+1)
  val indexPref = io.prefrepl.prefAddr(INDEX_HIGH, INDEX_LOW+1)
  val parityEven = io.feicache.addrEven(INDEX_LOW)
  val parityPref = io.prefrepl.prefAddr(INDEX_LOW)
  val tagAddrEven = Mux(parityEven, indexOdd, indexEven)
  val tagAddrOdd = Mux(parityEven, indexEven, indexOdd)
  val tagAddrPref = indexPref
  
  // Read from tag memory
  val toutEven = tagMemEven.io(tagAddrEven)
  val toutOdd = tagMemOdd.io(tagAddrOdd)
  val toutEvenPref = tagMemEvenPref.io(tagAddrPref)
  val toutOddPref = tagMemOddPref.io(tagAddrPref)

  // Multiplex tag memory output
  val tagEven = Mux(addrEvenReg(INDEX_LOW), toutOdd, toutEven)
  val tagOdd = Mux(addrOddReg(INDEX_LOW), toutOdd, toutEven)
  val tagPref = Mux(addrPrefReg(INDEX_LOW), toutOddPref, toutEvenPref)

  // Check if line is valid
  val validEven = validVec(addrEvenReg(INDEX_HIGH, INDEX_LOW))
  val validOdd = validVec(addrOddReg(INDEX_HIGH, INDEX_LOW))
  val validPref = validVec(addrPrefReg(INDEX_HIGH, INDEX_LOW))
  val valid = validEven && validOdd

  // Check for a hit of both instructions in the address bundle
  hitEven := Bool(true)
  hitOdd := Bool(true)
  hitPref := Bool(true)

  fetchAddr := addrEvenReg

  when ((tagEven != addrEvenReg(TAG_HIGH, TAG_LOW)) || (!validEven)) {
    hitEven := Bool(false)
//	fetchAddr := addrEvenReg
  }
  .elsewhen ((tagOdd != addrOddReg(TAG_HIGH, TAG_LOW)) || (!validOdd)) {
    hitOdd := Bool(false)
    fetchAddr := addrOddReg
  }
  .elsewhen ((tagPref != addrPrefReg(TAG_HIGH, TAG_LOW)) || (!validPref)) { 
    // Avoiding cache line collision between fetch and prefetch 
    when (((addrEvenReg(INDEX_HIGH, INDEX_LOW+1)) != addrPrefReg(INDEX_HIGH, INDEX_LOW+1)) || ((addrOddReg(INDEX_HIGH, INDEX_LOW+1)) != addrPrefReg(INDEX_HIGH, INDEX_LOW+1))) {	
        hitPref := Bool(false)
        fetchAddr := addrPrefReg
    }
  }

  // Keep signals alive for emulator
  debug(hitEven)
  debug(hitOdd)

  val wrAddrTag = io.ctrlrepl.wAddr(TAG_HIGH,TAG_LOW)
  // Index for vector of valid bits
  val wrValidIndex = io.ctrlrepl.wAddr(INDEX_HIGH, INDEX_LOW)
  // Index for tag memory even/odd
  val wrAddrIndex = io.ctrlrepl.wAddr(INDEX_HIGH, INDEX_LOW+1)
  val wrAddrParity = io.ctrlrepl.wAddr(INDEX_LOW)
  // Update tag field when new write occurs
  tagMemEven.io <= (io.ctrlrepl.wTag && !wrAddrParity, wrAddrIndex, wrAddrTag)
  tagMemOdd.io <= (io.ctrlrepl.wTag && wrAddrParity, wrAddrIndex, wrAddrTag)
  tagMemEvenPref.io <= (io.ctrlrepl.wTag && !wrAddrParity, wrAddrIndex, wrAddrTag)
  tagMemOddPref.io <= (io.ctrlrepl.wTag && wrAddrParity, wrAddrIndex, wrAddrTag)
  when (io.ctrlrepl.wTag) {
    validVec(wrValidIndex) := Bool(true)
  }

  val wrParity = io.ctrlrepl.wAddr(0)

  // Outputs to cache memory
  io.memIn.wEven := Mux(wrParity, Bool(false), io.ctrlrepl.wEna)
  io.memIn.wOdd := Mux(wrParity, io.ctrlrepl.wEna, Bool(false))
  io.memIn.wData := io.ctrlrepl.wData
  io.memIn.wAddr := io.ctrlrepl.wAddr(INDEX_HIGH,1)
  io.memIn.addrOdd := io.feicache.addrOdd(INDEX_HIGH,1)
  io.memIn.addrEven := io.feicache.addrEven(INDEX_HIGH,1)

  // Outputs to fetch stage
  io.icachefe.instrEven := io.memOut.instrEven
  io.icachefe.instrOdd := io.memOut.instrOdd

  io.icachefe.relBase := relBase
  io.icachefe.relPc := relPc
  io.icachefe.reloc := reloc
  io.icachefe.memSel := Cat(selSpmReg, selCacheReg)

  // Hit/miss to control module
  io.replctrl.fetchAddr := fetchAddr
  io.replctrl.hitPref := hitPref
  io.replctrl.hit := hitEven && hitOdd  
  io.replctrl.selCache := selCacheReg

  //invalidate Valid and Prefetch bits
  when (io.invalidate) {
    validVec.map(_ := Bool(false))
  }

}

/*
 Instruction Cache Control Class: handles block transfer from external Memory to the I-Cache
 */
class PICacheCtrl() extends Module {
  val io = new PICacheCtrlIO()

  // States of the state machine
  val initState :: idleState :: transferState :: waitState :: Nil = Enum(UInt(), 4)
  val stateReg = Reg(init = initState)
  
  val fetch = (!io.replctrl.hit) || (!io.replctrl.hitPref) 
  // Signal for replacement unit
  val wData = Bits(width = DATA_WIDTH)
  val wTag = Bool()
  val wAddr = Bits(width = ADDR_WIDTH)
  val wEna = Bool()
  // Signals for external memory
  val ocpCmd = Bits(width = 3)
  val ocpAddr = Bits(width = EXTMEM_ADDR_WIDTH)
  val fetchCnt = Reg(init = Bits(0, width = ICACHE_SIZE_WIDTH))
  val burstCnt = Reg(init = UInt(0, width = log2Up(BURST_LENGTH)))
  val fetchEna = Bool()
  // Input/output registers
  val addrReg = Reg(init = Bits(0, width = EXTMEM_ADDR_WIDTH - LINE_WORD_SIZE_WIDTH))
  val ocpSlaveReg = Reg(next = io.ocp_port.S)

  // Initialize signals
  wData := Bits(0)
  wTag := Bool(false)
  wEna := Bool(false)
  wAddr := Bits(0)
  ocpCmd := OcpCmd.IDLE
  ocpAddr := Bits(0)
  fetchEna := Bool(true)

  // Wait till ICache is the selected source of instructions
  when (stateReg === initState) {
    when (io.replctrl.selCache) {
      stateReg := idleState
    }
  }
  when (stateReg === idleState) {
    when (!io.replctrl.selCache) {
      stateReg := initState
    } .otherwise {
	when (fetch) {    
         when (!io.replctrl.hit) {
	   fetchEna := Bool(false)
	 }
        val addr = io.replctrl.fetchAddr(EXTMEM_ADDR_WIDTH-1, LINE_WORD_SIZE_WIDTH)
        addrReg := addr
        burstCnt := UInt(0)
        fetchCnt := UInt(0)
        // Check if command is accepted by the memory controller
        ocpAddr := Cat(addr, Bits(0, width =  LINE_WORD_SIZE_WIDTH))
        ocpCmd := OcpCmd.RD
        when (io.ocp_port.S.CmdAccept === Bits(1)) {
          stateReg := transferState
        } .otherwise {
          stateReg := waitState
        }
      }
    }
  }
  when (stateReg === waitState) {
    when (!io.replctrl.hit) {
	  fetchEna := Bool(false)
    }
    ocpAddr := Cat(addrReg, Bits(0, width = LINE_WORD_SIZE_WIDTH))
    ocpCmd := OcpCmd.RD
    when (io.ocp_port.S.CmdAccept === Bits(1)) {
      stateReg := transferState
    }
  }
  // Transfer/fetch cache block
  when (stateReg === transferState) {
    when (!io.replctrl.hit) {
      fetchEna := Bool(false)
    }
    when (fetchCnt < UInt(LINE_WORD_SIZE)) {
      when (fetchCnt === UInt(LINE_WORD_SIZE - 1)) {
        fetchEna := Bool(false)
	// Write new tag field memory
        wTag := Bool(true)
      }
      when (ocpSlaveReg.Resp === OcpResp.DVA) {
        fetchCnt := fetchCnt + Bits(1)
        burstCnt := burstCnt + Bits(1)
        when(fetchCnt < UInt(LINE_WORD_SIZE-1)) {
          // Fetch next address from external memory
          when (burstCnt >= UInt(BURST_LENGTH - 1)) {
            ocpCmd := OcpCmd.RD
            ocpAddr := Cat(addrReg, Bits(0, width = LINE_WORD_SIZE_WIDTH)) + fetchCnt + Bits(1)
            burstCnt := UInt(0)
          }
        }
        // Write current address to icache memory
        wData := ocpSlaveReg.Data
        wEna := Bool(true)
      }
      wAddr := Cat(addrReg, Bits(0, width = LINE_WORD_SIZE_WIDTH)) + fetchCnt
    }
    // Restart to idle state
    .otherwise {
 //     fetchEna := Bool(false)
      stateReg := idleState
    }
  }

  // Outputs to cache memory
  io.ctrlrepl.wEna := wEna
  io.ctrlrepl.wData := wData
  io.ctrlrepl.wAddr := wAddr
  io.ctrlrepl.wTag := wTag

  io.fetch_ena := fetchEna

  // Outputs to external memory
  io.ocp_port.M.Addr := Cat(ocpAddr, Bits("b00"))
  io.ocp_port.M.Cmd := ocpCmd
  io.ocp_port.M.Data := Bits(0) //read-only
  io.ocp_port.M.DataByteEn := Bits("b1111") //read-only
  io.ocp_port.M.DataValid := Bits(0) //read-only

  // Output to performance counters
  io.perf.hit := Bool(false)
  io.perf.miss := Bool(false)
  when (io.ena_in && io.replctrl.selCache && stateReg === idleState) {
    when (io.replctrl.hit) {
      io.perf.hit := Bool(true)
    } .otherwise {
      io.perf.miss := Bool(true)
    }
  }
}        
