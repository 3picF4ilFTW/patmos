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
 * Author: Martin Schoeberl (martin@jopdesign.com)
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

class Execute() extends Component {
  val io = new ExecuteIO()

  val exReg = Reg(new DecEx())
  when(io.ena) {
    exReg := io.decex
  }
  // no access to io.decex after this point!!!

  def alu(func: Bits, op1: UFix, op2: UFix): Bits = {
    val result = UFix(width = 32)
    val sum = op1 + op2
    result := sum // some default 0 default biggest, fastest. sum default slower smallest
    val shamt = op2(4, 0).toUFix
    // This kind of decoding of the ALU op in the EX stage is not efficient,
    // but we keep it for now to get something going soon.
    switch(func) {
      is(Bits("b0000")) { result := sum }
      is(Bits("b0001")) { result := op1 - op2 }
      is(Bits("b0010")) { result := (op1 ^ op2).toUFix }
      is(Bits("b0011")) { result := (op1 << shamt).toUFix }
      is(Bits("b0100")) { result := (op1 >> shamt).toUFix }
      is(Bits("b0101")) { result := (op1.toFix >> shamt).toUFix }
      is(Bits("b0110")) { result := (op1 | op2).toUFix }
      is(Bits("b0111")) { result := (op1 & op2).toUFix }
      is(Bits("b1011")) { result := (~(op1 | op2)).toUFix }
      // TODO: shadd shift shall be in it's own operand MUX
      is(Bits("b1100")) { result := (op1 << UFix(1)) + op2 }
      is(Bits("b1101")) { result := (op1 << UFix(2)) + op2 }
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
      (Bits("b000"), (op1 === op2)),
      (Bits("b001"), (op1 != op2)),
      (Bits("b010"), (op1s < op2s)),
      (Bits("b011"), (op1s <= op2s)),
      (Bits("b100"), (op1 < op2)),
      (Bits("b101"), (op1 <= op2)),
      (Bits("b110"), ((op1 & (Bits(1) << op2)) != UFix(0)))))
  }

  def pred(func: Bits, op1: Bool, op2: Bool): Bool = {
    MuxLookup(func, Bool(false), Array(
      (Bits("b00"), op1 | op2),
      (Bits("b01"), op1 & op2),
      (Bits("b10"), op1 ^ op2),
      (Bits("b11"), ~(op1 | op2))))
  }

  val predReg = Vec(8) { Reg(resetVal = Bool(false)) }

  // data forwarding
  val fwEx0 = exReg.rsAddr(0) === io.exResult.addr && io.exResult.valid
  val fwMem0 = exReg.rsAddr(0) === io.memResult.addr && io.memResult.valid
  val ra = Mux(fwEx0, io.exResult.data, Mux(fwMem0, io.memResult.data, exReg.rsData(0)))
  val fwEx1 = exReg.rsAddr(1) === io.exResult.addr && io.exResult.valid
  val fwMem1 = exReg.rsAddr(1) === io.memResult.addr && io.memResult.valid
  val rb = Mux(fwEx1, io.exResult.data, Mux(fwMem1, io.memResult.data, exReg.rsData(1)))

  val op2 = Mux(exReg.immOp, exReg.immVal, rb)
  val op1 = ra
  val aluResult = alu(exReg.func, op1, op2)
  val compResult = comp(exReg.func(2, 0), op1, op2)

  val ps1 = predReg(exReg.ps1Addr(2,0)) ^ exReg.ps1Addr(3)
  val ps2 = predReg(exReg.ps2Addr(2,0)) ^ exReg.ps2Addr(3)
  val predResult = pred(exReg.pfunc, ps1, ps2)

  val doExecute = predReg(exReg.pred(2, 0)) ^ exReg.pred(3)

  when((exReg.cmpOp || exReg.predOp) && doExecute && io.ena) {
    predReg(exReg.pd) := Mux(exReg.cmpOp, compResult, predResult)
  }
  predReg(0) := Bool(true)

  // result
  io.exmem.rd.addr := exReg.rdAddr(0)
  io.exmem.rd.data := aluResult
  io.exmem.rd.valid := exReg.wrReg && doExecute
  // load/store
  io.exmem.mem.load := exReg.load && doExecute
  io.exmem.mem.store := exReg.store && doExecute
  io.exmem.mem.hword := exReg.hword
  io.exmem.mem.byte := exReg.byte
  io.exmem.mem.zext := exReg.zext
  io.exmem.mem.addr := op1 + exReg.immVal
  io.exmem.mem.data := op2
  //branch
  io.exfe.doBranch := exReg.branch && doExecute
  io.exfe.branchPc := exReg.branchPc
  
  io.exmem.pc := exReg.pc
  io.exmem.predDebug := predReg

}