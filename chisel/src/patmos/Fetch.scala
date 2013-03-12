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
 * Fetch stage of Patmos.
 * 
 * Author: Martin Schoeberl (martin@jopdesign.com)
 * 
 */

package patmos

import Chisel._
import Node._

class Fetch(fileName: String) extends Component {
  val io = new FetchIO()

  val pc = Reg(resetVal = UFix(0, Constants.PC_SIZE))
  val addr_even = Reg(resetVal = UFix(0, Constants.PC_SIZE-1))
  val addr_odd = Reg(resetVal = UFix(0, Constants.PC_SIZE-1))

  val rom = Utility.readBin(fileName)
  // Split the ROM into two blocks for dual fetch
//  val len = rom.length / 2
//  val rom_a = Vec(len) { Bits(width = 32) }
//  val rom_b = Vec(len) { Bits(width = 32) }
//  for (i <- 0 until len) {
//    rom_a(i) = rom(i * 2)
//    rom_b(i) = rom(i * 2 + 1)
//    val a:Bits = rom_a(i)
//    val b:Bits = rom_b(i)
//    println(i+" "+a.toUFix.litValue()+" "+b.toUFix.litValue())
//  }
//
//  // addr_even and odd count in words. Shall this be optimized?
//  val data_even: Bits = rom_a(addr_even(Constants.PC_SIZE-1, 1))
//  val data_odd: Bits = rom_b(addr_odd(Constants.PC_SIZE-1, 1))
  // relay on the optimization to recognize that those addresses are always even and odd
  // TODO: maybe make it explicite
  val data_even = rom(addr_even)
  val data_odd = rom(addr_odd)

  val instr_a = Mux(pc(0) === Bits(0), data_even, data_odd)
  val instr_b = Mux(pc(0) === Bits(0), data_odd, data_even)

  // This becomes an issue when no bit 31 is set in the ROM!
  // Too much optimization happens here. We set the unused words with bit 31 set.
  // Probably an instruction SPM will help to avoid this optimization.
  val b_valid = instr_a(31) === Bits(1)
  val pc_next = pc + Mux(b_valid, UFix(2), Bits(1))

  // TODO clean up
  val xyz = Cat(pc_next(Constants.PC_SIZE - 1, 1), Bits(0))
  val abc = Cat(pc_next(Constants.PC_SIZE - 1, 1) + UFix(1), Bits(0))
  val even_next = Mux(pc_next(0) === Bits(1), abc, xyz)

  when(io.ena) {
    addr_even := even_next.toUFix
    addr_odd := Cat(pc_next(Constants.PC_SIZE - 1, 1), Bits(1)).toUFix
    pc := pc_next
    when(io.exfe.doBranch) {
      pc := io.exfe.branchPc
    }
  }

  io.fedec.pc := pc
  io.fedec.instr_a := instr_a
  io.fedec.instr_b := instr_b
  io.fedec.b_valid := b_valid // not used at the moment
}