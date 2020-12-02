package cop

import Chisel._

import patmos.Constants._
import util._
import ocp._

object Dummy extends CoprocessorObject {

  def init(params: Map[String, String]) = {}

  def create(params: Map[String, String]): Dummy = Module(new Dummy())
}


class Dummy() extends CoprocessorMemory() {
  //coprocessor definitions
  def FUNC_ADD = "b00000".U(5.W)
  def FUNC_VECTOR_ADD = "b00002".U(5.W)

  //some coprocessor registers
  val resultReg = Reg(init = UInt(0, 32))
  val busy = Reg(init = Bool(false))

  io.copOut.ena_out := Bool(false)


  when(io.copIn.trigger) {
    // read or custom command
    when(io.copIn.read){
      when(busy){
        io.copOut.ena_out := Bool(true)
      }
      when(io.copIn.funcId === FUNC_ADD) {
        resultReg := io.copIn.opData(0) + io.copIn.opData(1)
      }
      io.copOut.result := resultReg
    //write command
    }.otherwise{
      when(io.copIn.funcId === FUNC_VECTOR_ADD) {
        busy := Bool(true)
        //read from memory
        //memPort.MCmd := memPort.RD
        busy := Bool(false)
      }
    }
  }
  when(io.copIn.ena_in) {

  }
}