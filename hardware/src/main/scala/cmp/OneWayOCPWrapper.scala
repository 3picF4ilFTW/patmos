/*
 * Copyright: 2018, Technical University of Denmark, DTU Compute
 * Author: Martin Schoeberl (martin@jopdesign.com)
 * License: Simplified BSD License
 * 
 * OCP wrapper for the one-way shared memory
 */
package cmp

import Chisel._
import Node._

import patmos._
import patmos.Constants._
import ocp._
import io.CoreDeviceIO
import oneway._

class OneWayOCPWrapper(nrCores: Int) extends Module {

  val dim = math.sqrt(nrCores).toInt
  if (dim * dim != nrCores) throw new Error("Number of cores must be quadratic")
  // just start with four words
  val size = 4 * nrCores
  val onewaymem = Module(new oneway.OneWayMem(dim, size))

  println("OneWayMem")

  val io = Vec(nrCores, new OcpCoreSlavePort(ADDR_WIDTH, DATA_WIDTH))

  // Connection between OneWay memories and OCPcore ports
  for (i <- 0 until nrCores) {

    val resp = Mux(io(i).M.Cmd === OcpCmd.RD || io(i).M.Cmd === OcpCmd.WR,
      OcpResp.DVA, OcpResp.NULL)

    // addresses are in words
    onewaymem.io.memPorts(i).rdAddr := io(i).M.Addr >> 2
    onewaymem.io.memPorts(i).wrAddr := io(i).M.Addr >> 2
    onewaymem.io.memPorts(i).wrData := io(i).M.Data
    onewaymem.io.memPorts(i).wrEna := io(i).M.Cmd === OcpCmd.WR
    io(i).S.Data := Reg(next = onewaymem.io.memPorts(i).rdData)
    io(i).S.Resp := Reg(init = OcpResp.NULL, next = resp)
  }
}
