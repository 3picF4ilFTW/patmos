package cop

import Chisel._

import patmos.Constants._
import util._
import ocp._

object Dummy extends CoprocessorObject {

  def init(params: Map[String, String]) = {}

  def create(params: Map[String, String]): Dummy = Module(new Dummy())
}

class Dummy() extends Coprocessor() {
  

}