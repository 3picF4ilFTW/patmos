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
  def FUNC_ADD_STALL = "b00001".U(5.W)
  //def FUNC_VECTOR_ADD = "b00002".U(5.W)

  //some coprocessor registers
  val idle :: scalar_add :: Nil = Enum(2)
  val state = Reg(init = idle)

  io.copOut.result := UInt(0)
  io.copOut.ena_out := Bool(false)

  // start operation
  when(io.copIn.trigger & io.copIn.ena_in) {
    // read or custom command
    when(io.copIn.read) {
      when(state === idle) {
        // 0-latency add
        when(io.copIn.funcId === FUNC_ADD) {
          io.copOut.result := io.copIn.opData(0) + io.copIn.opData(1)
          io.copOut.ena_out := Bool(true)
        }
		// 1-latency add
        when(io.copIn.funcId === FUNC_ADD_STALL) {
          state := scalar_add
        }
      }
    }
  	//write command: TODO
	/*.otherwise{
      when(io.copIn.funcId === FUNC_VECTOR_ADD) {
        busy := Bool(true)
        //read from memory
        //memPort.MCmd := memPort.RD
        busy := Bool(false)
      }
    }*/
  }
  
  // output logic for 1-latency add
  when(io.copIn.ena_in & state === scalar_add) {
    io.copOut.result := io.copIn.opData(0) + io.copIn.opData(1)
    io.copOut.ena_out := Bool(true)
    state := idle
  }
  
}