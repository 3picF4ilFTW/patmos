/*
 * Copyright: 2017, Technical University of Denmark, DTU Compute
 * Author: Martin Schoeberl (martin@jopdesign.com)
 * License: Simplified BSD License
 * 
 * A router for the S4NOC.
 * 
 */

package s4noc

import Chisel._


/**
 * Channel directions
 */
object Const {
  val NORTH = 0
  val EAST = 1
  val SOUTH = 2
  val WEST = 3
  val LOCAL = 4
  val NR_OF_PORTS = 5
}

class SingleChannel[T <: Bits](dt: T) extends Bundle {
  val data = dt // dt.clone // UInt(width = 32)
  val valid = Bool()
}

class Channel[T <: Bits](dt: T) extends Bundle {
  val out = new SingleChannel[T](dt).asOutput
  val in = new SingleChannel[T](dt).asInput
}

class RouterPorts[T <: Bits](dt: T) extends Bundle {
  val ports = Vec(Const.NR_OF_PORTS, new Channel(dt))
}

class Router[T <: Bits](schedule: Array[Array[Int]], dt: T) extends Module {
  val io = new RouterPorts(dt)

  val regCounter = RegInit(UInt(0, log2Up(schedule.length)))
  val end = regCounter === UInt(schedule.length - 1)
  regCounter := Mux(end, UInt(0), regCounter + UInt(1))
  

  // Convert schedule table to a Chisel type table
  val sched = Vec(schedule.length, Vec(Const.NR_OF_PORTS, UInt(width = 3)))
  for (i <- 0 until schedule.length) {
    for (j <- 0 until Const.NR_OF_PORTS) {
      sched(i)(j) := UInt(schedule(i)(j), 3)
    }
  }

  // TDM schedule starts one cycles later for read data delay
  val regDelay = RegNext(regCounter, init=UInt(0))
  val currentSched = sched(regDelay)

  // We assume that on reset the valid signal is false.
  // Better have it reset. 
  for (j <- 0 until Const.NR_OF_PORTS) {
    io.ports(j).out := RegNext(io.ports(currentSched(j)).in)
  }
}

object Router extends App {

  chiselMain(Array("--backend", "v", "--targetDir", "generated"),
    () => Module(new Router(Schedule.genRandomSchedule(7), UInt(width = 32))))
}
