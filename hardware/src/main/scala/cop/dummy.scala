package cop

import Chisel._

import patmos.Constants._
import util._
import ocp._

object Dummy extends CoprocessorObject {

  def init(config: Config#CoprocessorConfig) = {}

  def create(config: Config#CoprocessorConfig): Dummy = Module(new Dummy())
}

class Dummy() extends Coprocessor() {
  

}