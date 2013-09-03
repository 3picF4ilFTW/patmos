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
 * Execution stage of Patmos.
 * 
 * Authors: Martin Schoeberl (martin@jopdesign.com)
 *          Wolfgang Puffitsch (wpuffitsch@gmail.com)
 * 
 * Current Fmax on the DE2-70 is 84 MHz
 *   from forward address comparison to EXMEM register rd
 *   Removing the not so nice ALU instrcutions gives 96 MHz
 *   Drop just rotate: 90 MHz
 * 
 */

package patmos

import Chisel._
import Node._

import Constants._

class Execute() extends Component {
  val io = new ExecuteIO()

  val exReg = Reg(new DecEx(), resetVal = DecExResetVal)
  when(io.ena) {
    exReg := io.decex
  }
  // no access to io.decex after this point!!!

  def alu(func: Bits, op1: UFix, op2: UFix): Bits = {
    val result = UFix(width = DATA_WIDTH)
    val sum = op1 + op2
    result := sum // some default 0 default biggest, fastest. sum default slower smallest
    val shamt = op2(4, 0).toUFix
    // This kind of decoding of the ALU op in the EX stage is not efficient,
    // but we keep it for now to get something going soon.
    switch(func) {
      is(FUNC_ADD)    { result := sum }
      is(FUNC_SUB)    { result := op1 - op2 }
      is(FUNC_XOR)    { result := (op1 ^ op2).toUFix }
      is(FUNC_SL)     { result := (op1 << shamt).toUFix }
      is(FUNC_SR)     { result := (op1 >> shamt).toUFix }
      is(FUNC_SRA)    { result := (op1.toFix >> shamt).toUFix }
      is(FUNC_OR)     { result := (op1 | op2).toUFix }
      is(FUNC_AND)    { result := (op1 & op2).toUFix }
      is(FUNC_NOR)    { result := (~(op1 | op2)).toUFix }
      // TODO: shadd shift shall be in it's own operand MUX
      is(FUNC_SHADD)  { result := (op1 << UFix(1)) + op2 }
      is(FUNC_SHADD2) { result := (op1 << UFix(2)) + op2 }
    }
    result
  }

  def comp(func: Bits, op1: UFix, op2: UFix): Bool = {
    val op1s = op1.toFix
    val op2s = op2.toFix
    val shamt = op2(4, 0).toUFix
    // Is this nicer than the switch?
    // Some of the comparison function (equ, subtract) could be shared
    MuxLookup(func, Bool(false), Array(
      (CFUNC_EQ,    (op1 === op2)),
      (CFUNC_NEQ,   (op1 != op2)),
      (CFUNC_LT,    (op1s < op2s)),
      (CFUNC_LE,    (op1s <= op2s)),
      (CFUNC_ULT,   (op1 < op2)),
      (CFUNC_ULE,   (op1 <= op2)),
      (CFUNC_BTEST, ((op1 & (Bits(1) << shamt)) != UFix(0)))))
  }

  def pred(func: Bits, op1: Bool, op2: Bool): Bool = {
    MuxLookup(func, Bool(false), Array(
      (PFUNC_OR, op1 | op2),
      (PFUNC_AND, op1 & op2),
      (PFUNC_XOR, op1 ^ op2),
      (PFUNC_NOR, ~(op1 | op2))))
  }

  // data forwarding
  val fwMemReg = Vec(2*PIPE_COUNT) { Vec(PIPE_COUNT) { Reg(resetVal = Bool(false)) } }
  val fwExReg  = Vec(2*PIPE_COUNT) { Vec(PIPE_COUNT) { Reg(resetVal = Bool(false)) } }
  val memResultDataReg = Vec(PIPE_COUNT) { Reg(Bits(width = DATA_WIDTH)) }
  val exResultDataReg  = Vec(PIPE_COUNT) { Reg(Bits(width = DATA_WIDTH)) }
  val op = Vec(2*PIPE_COUNT) { Bits(width = 32) }

  // precompute forwarding
  when (io.ena) {
	for (i <- 0 until 2*PIPE_COUNT) { 
	  for (k <- 0 until PIPE_COUNT) {
		fwMemReg(i)(k) := Bool(false)
		when(io.decex.rsAddr(i) === io.memResult(k).addr && io.memResult(k).valid) {
		  fwMemReg(i)(k) := Bool(true)
		}
		fwExReg(i)(k) := Bool(false)
		when(io.decex.rsAddr(i) === io.exResult(k).addr && io.exResult(k).valid) {
		  fwExReg(i)(k) := Bool(true)
		}
	  }
	}
	for (k <- 0 until PIPE_COUNT) {
	  memResultDataReg(k) := io.memResult(k).data
	  exResultDataReg(k) := io.exResult(k).data
	}
  }
  // forwarding multiplexers
  for (i <- 0 until 2*PIPE_COUNT) { 
	op(i) := exReg.rsData(i)
	for (k <- 0 until PIPE_COUNT) {
	  when(fwMemReg(i)(k)) { 
		op(i) := memResultDataReg(k)
	  }
	}
	for (k <- 0 until PIPE_COUNT) {
	  when(fwExReg(i)(k)) { 
		op(i) := exResultDataReg(k)
	  }
	}
  }

  for (i <- 0 until PIPE_COUNT) { 
	when(exReg.immOp(i)) {
	  op(2*i+1) := exReg.immVal(i)
	}
  }

  // predicates
  val predReg = Vec(PRED_COUNT) { Reg(resetVal = Bool(false)) }

  val doExecute = Vec(PIPE_COUNT) { Bool() }
  for (i <- 0 until PIPE_COUNT) {
	doExecute(i) := predReg(exReg.pred(i)(PRED_BITS-1, 0)) ^ exReg.pred(i)(PRED_BITS)
  }

  // stack registers
  val stackTopReg = Reg(resetVal = UFix(0, DATA_WIDTH))
  val stackSpillReg = Reg(resetVal = UFix(0, DATA_WIDTH))
  io.exdec.sp := stackTopReg

  // MS: maybe the multiplication should be in a local component?
  
  // multiplication result registers
  val mulLoReg = Reg(resetVal = UFix(0, DATA_WIDTH))
  val mulHiReg = Reg(resetVal = UFix(0, DATA_WIDTH))

  // multiplication pipeline registers
  val mulLLReg    = Reg(resetVal = UFix(0, DATA_WIDTH))
  val mulLHReg    = Reg(resetVal = UFix(0, DATA_WIDTH))
  val mulHLReg    = Reg(resetVal = UFix(0, DATA_WIDTH))
  val mulHHReg    = Reg(resetVal = UFix(0, DATA_WIDTH))

  val mulBufReg = Reg(resetVal = UFix(0, 2*DATA_WIDTH))
  
  val mulPipeReg = Vec(3) { Reg(resetVal = Bool(false)) }

  // multiplication only in first pipeline
  when(io.ena) {
	mulPipeReg(0) := exReg.aluOp(0).isMul && doExecute(0)
	mulPipeReg(1) := mulPipeReg(0)
	mulPipeReg(2) := mulPipeReg(1)

	val signed = exReg.aluOp(0).func === MFUNC_MUL

	val op1H = op(0)(DATA_WIDTH-1, DATA_WIDTH/2)
	val op1L = op(0)(DATA_WIDTH/2-1, 0)
	val op2H = op(1)(DATA_WIDTH-1, DATA_WIDTH/2)
	val op2L = op(1)(DATA_WIDTH/2-1, 0)

	mulLLReg := op1L.toUFix * op2L.toUFix
	mulLHReg := op1L.toUFix * op2H.toUFix
	mulHLReg := op1H.toUFix * op2L.toUFix
	mulHHReg := op1H.toUFix * op2H.toUFix

	when(signed) {
	  mulLLReg := (op1L.toUFix * op2L.toUFix).toUFix
	  mulLHReg := (op1L.toUFix * op2H.toFix).toUFix
	  mulHLReg := (op1H.toFix * op2L.toUFix).toUFix
	  mulHHReg := (op1H.toFix * op2H.toFix).toUFix
	}

	mulBufReg := (Cat(mulHHReg, mulLLReg)
			   + Cat(Fill(DATA_WIDTH/2, mulHLReg(DATA_WIDTH-1)),
					 mulHLReg, UFix(0, width = DATA_WIDTH/2))
			   + Cat(Fill(DATA_WIDTH/2, mulLHReg(DATA_WIDTH-1)),
					 mulLHReg, UFix(0, width = DATA_WIDTH/2)))

	when(mulPipeReg(1)) {
	  mulHiReg := mulBufReg(2*DATA_WIDTH-1, DATA_WIDTH)
	  mulLoReg := mulBufReg(DATA_WIDTH-1, 0)
	}
  }

  // dual-issue operations
  for (i <- 0 until PIPE_COUNT) {

	val aluResult = alu(exReg.aluOp(i).func, op(2*i), op(2*i+1))
	val compResult = comp(exReg.aluOp(i).func, op(2*i), op(2*i+1))

	// predicate operations
	val ps1 = predReg(exReg.predOp(i).s1Addr(PRED_BITS-1,0)) ^ exReg.predOp(i).s1Addr(PRED_BITS)
	val ps2 = predReg(exReg.predOp(i).s2Addr(PRED_BITS-1,0)) ^ exReg.predOp(i).s2Addr(PRED_BITS)
	val predResult = pred(exReg.predOp(i).func, ps1, ps2)

	when((exReg.aluOp(i).isCmp || exReg.aluOp(i).isPred) && doExecute(i) && io.ena) {
      predReg(exReg.predOp(i).dest) := Mux(exReg.aluOp(i).isCmp, compResult, predResult)
	}
	predReg(0) := Bool(true)

	// stack register handling
	when(exReg.aluOp(i).isSTC && doExecute(i) && io.ena) {
	  io.exdec.sp := op(2*i+1).toUFix()
	  stackTopReg := op(2*i+1).toUFix()
	}

	// special registers
	when(exReg.aluOp(i).isMTS && doExecute(i) && io.ena) {
	  switch(exReg.aluOp(i).func) {
		is(SPEC_FL) {
		  predReg := op(2*i)(PRED_COUNT-1, 0).toBits()
		  predReg(0) := Bool(true)
		}
		is(SPEC_SL) {
		  mulLoReg := op(2*i).toUFix()
		}
		is(SPEC_SH) {
		  mulHiReg := op(2*i).toUFix()
		}
		is(SPEC_ST) {
		  io.exdec.sp := op(2*i).toUFix()
		  stackTopReg := op(2*i).toUFix()
		}
		is(SPEC_SS) {
		  stackSpillReg := op(2*i).toUFix()
		}
	  }
	}
	val mfsResult = UFix();
	mfsResult := UFix(0, DATA_WIDTH)
	switch(exReg.aluOp(i).func) {
	  is(SPEC_FL) {
		mfsResult := Cat(Bits(0, DATA_WIDTH-PRED_COUNT), predReg.toBits()).toUFix()
	  }
	  is(SPEC_SL) {
		mfsResult := mulLoReg
	  }
	  is(SPEC_SH) {
		mfsResult := mulHiReg
	  }
	  is(SPEC_ST) {
		mfsResult := stackTopReg
	  }
	  is(SPEC_SS) {
		mfsResult := stackSpillReg
	  }
	}

	// result
	io.exmem.rd(i).addr := exReg.rdAddr(i)
	io.exmem.rd(i).valid := exReg.wrRd(i) && doExecute(i)
	io.exmem.rd(i).data := Mux(exReg.aluOp(i).isMFS, mfsResult, aluResult)
  }

  // load/store
  io.exmem.mem.load := exReg.memOp.load && doExecute(0)
  io.exmem.mem.store := exReg.memOp.store && doExecute(0)
  io.exmem.mem.hword := exReg.memOp.hword
  io.exmem.mem.byte := exReg.memOp.byte
  io.exmem.mem.zext := exReg.memOp.zext
  io.exmem.mem.typ := exReg.memOp.typ
  io.exmem.mem.addr := op(0) + exReg.immVal(0)
  io.exmem.mem.data := op(1)
  io.exmem.mem.call := exReg.call && doExecute(0)
  io.exmem.mem.ret  := exReg.ret && doExecute(0)
  io.exmem.mem.brcf := exReg.brcf && doExecute(0)
  // call/return
  val callAddr = Mux(exReg.immOp(0), exReg.callAddr, op(0).toUFix)
  val brcfAddr = Mux(exReg.immOp(0), exReg.brcfAddr, op(0).toUFix)
  io.exmem.mem.callRetAddr := Mux(exReg.call || exReg.brcf, UFix(0), op(1).toUFix)
  io.exmem.mem.callRetBase := Mux(exReg.call, callAddr,
								  Mux(exReg.brcf, brcfAddr,
									  op(0).toUFix))
  // branch
  io.exfe.doBranch := exReg.jmpOp.branch && doExecute(0)
  val target = Mux(exReg.immOp(0),
				   exReg.jmpOp.target,
				   op(0)(DATA_WIDTH-1, 2).toUFix - exReg.jmpOp.reloc)
  io.exfe.branchPc := target
  
  io.exmem.pc := exReg.pc

  //call/return for mcache
  io.exmcache.doCallRet := ((exReg.call || exReg.ret || exReg.brcf) && doExecute(0))
  io.exmcache.callRetBase := io.exmem.mem.callRetBase(31,2)
  io.exmcache.callRetAddr := io.exmem.mem.callRetAddr(31,2)

}
