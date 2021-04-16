package cop

import Chisel._

import patmos.Constants._
import util._
import ocp._

object Sha256 extends CoprocessorObject {

  def init(params: Map[String, String]) = {}

  def create(params: Map[String, String]): Sha256 = Module(new Sha256())
}


class Sha256() extends Coprocessor_MemoryAccess() {
  // constants for SHA256
  val ROUND_COUNT = 64
  val HASH_WORD_COUNT = 8
  val MSG_WORD_COUNT = 16
  
  // coprocessor function definitions
  val FUNC_RESET            = "b00000".U(5.W)   // reset hash state (COP_WRITE)
  val FUNC_POLL             = "b00001".U(5.W)   // check whether computation is in progress (COP_READ)
  val FUNC_SET_HASH         = "b00001".U(5.W)   // set the hash state (COP_WRITE src_addr)
  val FUNC_GET_HASH         = "b00010".U(5.W)   // get the hash state (COP_WRITE dest_addr) 
  val FUNC_SINGLE_BLOCK     = "b00011".U(5.W)   // hash a single block (COP_WRITE src_addr)
  val FUNC_MULTIPLE_BLOCKS  = "b00100".U(5.W)   // hash multiple blocks (COP_WRITE src_addr block_count)

  // general helper constants
  val BURSTS_PER_MSG = MSG_WORD_COUNT / BURST_LENGTH
  val MSG_WORD_COUNT_WIDTH = log2Ceil(MSG_WORD_COUNT)
  val BURSTS_PER_HASH = HASH_WORD_COUNT / BURST_LENGTH
  val HASH_WORD_COUNT_WIDTH = log2Ceil(HASH_WORD_COUNT)
  val WORD_COUNT_WIDTH = max(MSG_WORD_COUNT_WIDTH, HASH_WORD_COUNT_WIDTH)
  val BURST_OFFSET = log2Ceil(BURST_LEN)
  
  // state machine for memory reads/writes
  val mem_idle :: mem_read_req :: mem_read :: mem_write_req :: mem_write :: Nil = Enum(5)
  val mem_state = Reg(init = mem_idle)
  val block_addr = Reg(UInt(width = DATA_WIDTH))
  val block_count = Reg(UInt(width = DATA_WIDTH))
  val hash_addr = Reg(UInt(width = DATA_WIDTH))
  val word_count = Reg(UInt(width = WORD_COUNT_WIDTH))
  
  // start operation
  when(io.copIn.trigger & io.copIn.ena_in) {
    when(io.copIn.isCustom) {
      // no custom operations
    }.elsewhen(io.copIn.read) {
      switch(io.copIn.funcId) {
        is(FUNC_POLL) {
          io.copOut.ena_out := Bool(true)
          when(/* TODO */) {
            io.copOut.result := UInt(1, DATA_WIDTH.W)
          }.otherwise {
            io.copOut.result := UInt(0, DATA_WIDTH.W)
          }
        }
      }
    }.otherwise(io.copIn.write) {
      switch(io.copIn.funcId) {
        is(FUNC_RESET) {
          // TODO
        }
        is(FUNC_SET_HASH) {
          // TODO
        }
        is(FUNC_GET_HASH) {
          // TODO
        }
        is(FUNC_SINGLE_BLOCK) {
          // TODO
        }
        is(FUNC_MULTIPLE_BLOCKS) {
          // TODO
        }
      }
    }
  }
  
  // memory logic
  io.memPort.M.Cmd := OcpCmd.IDLE
  io.memPort.M.Addr := UInt(0)
  io.memPort.M.Data := UInt(0)
  io.memPort.M.DataValid := UInt(0)
  io.memPort.M.DataByteEn := "b1111".U
  switch(mem_state) {
    is(mem_read_req) {
      io.memPort.M.Cmd := OcpCmd.RD
      io.memPort.M.Addr := block_addr
      when(io.memPort.S.CmdAccept === UInt(1)) {
        mem_state = mem_read
      }
    }
    is(mem_read) {
      //TODO: read io.memPort.S.Data
      when(io.memPort.S.Resp === OcpResp.DVA) {
        when(word_count(BURST_OFFSET - 1, 0) < UInt(BURST_LEN - 1)) {
          word_count := word_count + UInt(1)
        }.otherwise {
          when(word_count(MSG_WORD_COUNT_WIDTH - 1, BURST_OFFSET) < UInt(BURSTS_PER_MSG - 1)) {
            mem_state := mem_read_req
          }.otherwise {
            word_count := 0
            mem_state := mem_idle
          }
        }
      }
    }
    is(mem_write_req) {
      io.memPort.M.Cmd := OcpCmd.WR
      io.memPort.M.Addr := hash_addr
      // TODO: write io.memPort.M.Data
      io.memPort.M.DataValid := UInt(1)
      when(io.memPort.S.CmdAccept === UInt(1) && io.memPort.S.DataAccept === UInt(1)) {
        word_count := word_count + UInt(1)
        mem_state := mem_write
      }
    }
    is(mem_write) {
      // TODO: write io.memPort.M.Data
      io.memPort.M.DataValid := UInt(1)
      when(io.memPort.S.DataAccept === UInt(1)) {
        when(word_count(BURST_OFFSET - 1, 0) < UInt(BURST_LENGTH - 1)) {
          word_count := word_count + UInt(1)
        }.otherwise {
          when(word_count(HASH_WORD_COUNT_WIDTH - 1, BURST_OFFSET) < UInt(BURSTS_PER_MSG - 1)) {
            mem_state := mem_write_req
          }.otherwise {
            word_count := 0
            mem_state := idle
          }
        }
      }
    }
  }
  
}
