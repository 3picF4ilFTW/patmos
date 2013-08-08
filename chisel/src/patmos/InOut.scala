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
 * IO component of Patmos.
 * 
 * Authors: Martin Schoeberl (martin@jopdesign.com)
 *          Wolfgang Puffitsch (wpuffitsch@gmail.com)
 * 
 */

package patmos

import Chisel._
import Node._

import Constants._

import ocp._

import io.Timer
import io.UART
import io.Leds

class InOut() extends Component {
  val io = new InOutIO()

  // Compute selects
  val selIO = io.memInOut.M.Addr(ADDR_WIDTH-1, ADDR_WIDTH-4) === Bits("b1111")
  val selSpm = !selIO & io.memInOut.M.Addr(ISPM_ONE_BIT) === Bits(0x0)
  val selTimer = selIO & io.memInOut.M.Addr(11, 8) === Bits(0x0)
  val selUart = selIO & io.memInOut.M.Addr(11, 8) === Bits(0x1)
  val selLed = selIO & io.memInOut.M.Addr(11, 8) === Bits(0x2)
  val selExc = selIO & io.memInOut.M.Addr(11, 8) === Bits(0x3)

  // Register selects
  val selSpmReg = Reg(resetVal = Bits("b0"))
  val selTimerReg = Reg(resetVal = Bits("b0"))
  val selUartReg = Reg(resetVal = Bits("b0"))
  val selLedReg = Reg(resetVal = Bits("b0"))
  val selExcReg = Reg(resetVal = Bits("b0"))
  when(io.memInOut.M.Cmd != OcpCmd.IDLE) {
	selSpmReg := selSpm
	selTimerReg := selTimer
	selUartReg := selUart
	selLedReg := selLed
	selExcReg := selExc
  }

  // Register for error response
  val errResp = Reg(resetVal = OcpResp.NULL)
  errResp := Mux(io.memInOut.M.Cmd != OcpCmd.IDLE &&
                 selIO && !(selTimer || selUart || selLed || selExc),
                 OcpResp.ERR, OcpResp.NULL)

  // The SPM
  val spm = new Spm(1 << DSPM_BITS)
  spm.io.M := io.memInOut.M
  spm.io.M.Cmd := Mux(selSpm, io.memInOut.M.Cmd, OcpCmd.IDLE)
  val spmS = spm.io.S

  // The Timer
  val timer = new Timer(CLOCK_FREQ)
  timer.io.ocp.M := io.memInOut.M
  timer.io.ocp.M.Cmd := Mux(selTimer, io.memInOut.M.Cmd, OcpCmd.IDLE)
  val timerS = timer.io.ocp.S

  // The UART
  val uart = new UART(CLOCK_FREQ, UART_BAUD)
  uart.io.ocp.M := io.memInOut.M
  uart.io.ocp.M.Cmd := Mux(selUart, io.memInOut.M.Cmd, OcpCmd.IDLE)
  val uartS = uart.io.ocp.S
  io.uartPins <> uart.io.pins

  // The LEDs
  val leds = new Leds(LED_COUNT)
  leds.io.ocp.M := io.memInOut.M
  leds.io.ocp.M.Cmd := Mux(selLed, io.memInOut.M.Cmd, OcpCmd.IDLE)
  val ledsS = leds.io.ocp.S
  io.ledPins <> leds.io.pins

  // The exception unit is outside this unit
  io.excInOut.M := io.memInOut.M
  io.excInOut.M.Cmd := Mux(selExc, io.memInOut.M.Cmd, OcpCmd.IDLE)
  val excS = io.excInOut.S

  // Return data to pipeline
  io.memInOut.S.Data := spmS.Data
  when(selTimerReg) { io.memInOut.S.Data := timerS.Data }
  when(selUartReg)  { io.memInOut.S.Data := uartS.Data }
  when(selLedReg)   { io.memInOut.S.Data := ledsS.Data }
  when(selExcReg)   { io.memInOut.S.Data := excS.Data }

  io.memInOut.S.Resp := (spmS.Resp |
                         timerS.Resp |
                         uartS.Resp |
                         ledsS.Resp |
                         excS.Resp |
                         errResp)
}
