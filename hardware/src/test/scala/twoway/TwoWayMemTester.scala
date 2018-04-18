/*
 * Copyright: 2017, Technical University of Denmark, DTU Compute
 * Author: Martin Schoeberl (martin@jopdesign.com)
 * License: Simplified BSD License
 */

package twoway

import Chisel._

/**
 * Test a 2x2 Network.
 */
class TestLocalReadWrite(dut: TwoWayMem) extends Tester(dut) {  
  for (j <- 0 until 4) {
      //Write to all memReq channels, asking for memory in node 3
      poke(dut.io.nodearray(j).test.out.rw, 0)  
      poke(dut.io.nodearray(j).test.out.data, 0)
      poke(dut.io.nodearray(j).test.out.address, 0)
      poke(dut.io.nodearray(j).test.out.valid, false)
  }  

  step(1)

  //Write to all memReq channels, asking for memory in node 3
  poke(dut.io.nodearray(0).test.out.rw, 1)  
  poke(dut.io.nodearray(0).test.out.data, 0x42)
  poke(dut.io.nodearray(0).test.out.address, 0x42)
  poke(dut.io.nodearray(0).test.out.valid, true)

  step(1)


  //Wait for valid returns
  while(peek(dut.io.nodearray(0).test.in.valid) == 0){
    step(1)
  }

  poke(dut.io.nodearray(0).test.out.valid, false)
  step(1)


  //Read same value that we wrote, hopefully
  poke(dut.io.nodearray(0).test.out.rw, 0)  
  poke(dut.io.nodearray(0).test.out.data, 0x00)
  poke(dut.io.nodearray(0).test.out.address, 0x42)
  poke(dut.io.nodearray(0).test.out.valid, true)

  step(1)

  while(peek(dut.io.nodearray(0).test.in.valid) == 0){
    step(1)
  }
  poke(dut.io.nodearray(0).test.out.valid, false)

  step(3)
}

class TestExternalWrite(dut: TwoWayMem) extends Tester(dut) {

    //Set all inputs to 0
    for (j <- 0 until 4) {
      poke(dut.io.nodearray(j).test.out.rw, 0)  
      poke(dut.io.nodearray(j).test.out.data, 0)
      poke(dut.io.nodearray(j).test.out.address, 0)
      poke(dut.io.nodearray(j).test.out.valid, false)
  }  

  step(1)

  poke(dut.io.nodearray(0).test.out.rw, 1)  
  poke(dut.io.nodearray(0).test.out.data, 0x42)
  poke(dut.io.nodearray(0).test.out.address, 0x142) //Write to node 1
  poke(dut.io.nodearray(0).test.out.valid, true)

  step(1)


  //Wait for valid returns
  while(peek(dut.io.nodearray(0).test.in.valid) == 0){
    step(1)
  }

  poke(dut.io.nodearray(0).test.out.valid, false)
  step(1)
}

class TestExternalReadback(dut: TwoWayMem) extends Tester(dut) {

}


object TwoWayMemTester {
  def main(args: Array[String]): Unit = {
    chiselMainTest(Array("--genHarness", "--test", "--backend", "c",
      "--compile", "--vcd", "--targetDir", "generated"),
      () => Module(new TwoWayMem(2, 1024))) {
        c => new TestExternalWrite(c)
      }
  }
}