/*
 * Patmos top level component and test driver.
 *
 * Authors: Martin Schoeberl (martin@jopdesign.com)
 *          Wolfgang Puffitsch (wpuffitsch@gmail.com)
 *
 */

package patmos

import Chisel._
import java.io.File

import Constants._
import util._
import io._
import datacache._
import ocp.{OcpCoreSlavePort, _}
import argo._

import scala.collection.immutable.Stream.Empty

/**
 * Module for one Patmos core.
 */
class PatmosCore(binFile: String, nr: Int, cnt: Int) extends Module {

  val io = IO(Config.getPatmosCoreIO(nr))

  val icache =
    if (ICACHE_SIZE <= 0)
      Module(new NullICache())
    else if (ICACHE_TYPE == ICACHE_TYPE_METHOD && ICACHE_REPL == CACHE_REPL_FIFO)
      Module(new MCache())
    else if (ICACHE_TYPE == ICACHE_TYPE_LINE && ICACHE_ASSOC == 1)
      Module(new ICache())
    else {
      throw new Error("Unsupported instruction cache configuration:" +
        " type \"" + ICACHE_TYPE + "\"" +
        " (must be \"" + ICACHE_TYPE_METHOD + "\" or \"" + ICACHE_TYPE_LINE + "\")" +
        " associativity " + ICACHE_ASSOC +
        " with replacement policy \"" + ICACHE_REPL + "\"")
      Module(new NullICache()) // return at least a dummy cache
    }

  val fetch = Module(new Fetch(binFile))
  val decode = Module(new Decode())
  val execute = Module(new Execute())
  val memory = Module(new Memory())
  val writeback = Module(new WriteBack())
  val exc = Module(new Exceptions())
  
  val dcache = Module(new DataCache())

  //connect icache
  icache.io.feicache <> fetch.io.feicache
  icache.io.icachefe <> fetch.io.icachefe
  icache.io.exicache <> execute.io.exicache
  icache.io.illMem <> memory.io.icacheIllMem

  decode.io.fedec <> fetch.io.fedec
  execute.io.decex <> decode.io.decex
  memory.io.exmem <> execute.io.exmem
  writeback.io.memwb <> memory.io.memwb
  // RF write connection
  decode.io.rfWrite <> writeback.io.rfWrite
  // This is forwarding of registered result
  // Take care that it is the plain register
  execute.io.exResult <> memory.io.exResult
  execute.io.memResult <> writeback.io.memResult

  // Connect stack cache
  execute.io.exsc <> dcache.io.scIO.exsc
  dcache.io.scIO.scex <> execute.io.scex
  dcache.io.scIO.illMem <> memory.io.scacheIllMem

  // We branch in EX
  fetch.io.exfe <> execute.io.exfe
  // We call in MEM
  fetch.io.memfe <> memory.io.memfe
  // We store the return base in EX (in cycle corresponding to MEM)
  fetch.io.feex <> execute.io.feex

  memory.io.localInOut <> io.inout.memInOut

  // Connect exception unit
  exc.io.ocp <> io.inout.excInOut
  exc.io.intrs <> io.inout.intrs
  exc.io.excdec <> decode.io.exc
  exc.io.memexc <> memory.io.exc

  // Connect data cache
  dcache.io.master <> memory.io.globalInOut

  // Merge OCP ports from data caches and method cache
  val burstBus = Module(new OcpBurstBus(ADDR_WIDTH, DATA_WIDTH, BURST_LENGTH))
  val selICache = Wire(Bool())
  val burstJoin = if (ICACHE_TYPE == ICACHE_TYPE_METHOD) {
    // requests from D-cache and method cache never collide
    new OcpBurstJoin(icache.io.ocp_port, dcache.io.slave,
      burstBus.io.slave, selICache)
  } else {
    // join requests such that D-cache requests are buffered
    new OcpBurstPriorityJoin(icache.io.ocp_port, dcache.io.slave,
      burstBus.io.slave, selICache)
  }

  val mmu = Module(if (HAS_MMU) new MemoryManagement() else new NoMemoryManagement())
  mmu.io.exec <> selICache
  mmu.io.ctrl <> io.inout.mmuInOut
  mmu.io.virt <> burstBus.io.master

  // Enable signals for memory stage, method cache and stack cache
  memory.io.ena_in := icache.io.ena_out && !dcache.io.scIO.stall
  icache.io.ena_in := memory.io.ena_out && !dcache.io.scIO.stall
  dcache.io.scIO.ena_in := memory.io.ena_out && icache.io.ena_out

  // Enable signal
  val enable = memory.io.ena_out & icache.io.ena_out & !dcache.io.scIO.stall
  fetch.io.ena := enable
  decode.io.ena := enable
  execute.io.ena := enable
  writeback.io.ena := enable
  exc.io.ena := enable
  val enableReg = Reg(next = enable)

  // Flush signal
  val flush = memory.io.flush
  val brflush = execute.io.brflush
  decode.io.flush := flush || brflush
  execute.io.flush := flush

  // Software resets
  icache.io.invalidate := exc.io.invalICache
  dcache.io.invalDCache := exc.io.invalDCache

  // Make privileged mode visible internally and externally
  io.inout.superMode := exc.io.superMode
  mmu.io.superMode := exc.io.superMode
  io.superMode := exc.io.superMode

  // Internal "I/O" data
  io.inout.internalIO.perf.ic := icache.io.perf
  io.inout.internalIO.perf.dc := dcache.io.dcPerf
  io.inout.internalIO.perf.sc := dcache.io.scPerf
  io.inout.internalIO.perf.wc := dcache.io.wcPerf
  io.inout.internalIO.perf.mem.read := (io.memPort.M.Cmd === OcpCmd.RD &&
    io.memPort.S.CmdAccept === UInt(1))
    io.inout.internalIO.perf.mem.write := (io.memPort.M.Cmd === OcpCmd.WR &&
    io.memPort.S.CmdAccept === UInt(1))

  // The inputs and outputs
  io.memPort <> mmu.io.phys
  //Config.connectAllIOPins(io, iocomp.io)

  // Keep signal alive for debugging
  debug(enableReg)
}

/**
 * The main (top-level) component of Patmos.
 */
class Patmos(configFile: String, binFile: String, datFile: String) extends Module {
  Config.loadConfig(configFile)
  Config.minPcWidth = util.log2Up((new File(binFile)).length.toInt / 4)
  Config.datFile = datFile

  val io = Config.getPatmosIO()

  val nrCores = Config.getConfig.coreCount

  println("Config core count: " + nrCores)

  // Instantiate cores
  val cores = (0 until nrCores).map(i => Module(new PatmosCore(binFile, i, nrCores)))

  // Forward ports to/from core
  println("Config cmp: ")
  val MAX_IO_DEVICES = 16
  val cmpdevs = Config.getConfig.cmpDevices.map(e => {
    println(e)
    val (off, _dev) = e match {
      // Address 0 reserved for Argo
      case "Argo" =>  (0, Module(new argo.Argo(nrCores, wrapped=false, emulateBB=false)))
      case "Hardlock" => (1, Module(new cmp.HardlockOCPWrapper(() => new cmp.Hardlock(nrCores, 1))))
      case "SharedSPM" => (2, Module(new cmp.SharedSPM(nrCores, (nrCores-1)*2*1024)))
      case "OneWay" => (3, Module(new cmp.OneWayOCPWrapper(nrCores)))
      case "TdmArbiter" => (4, Module(new cmp.TdmArbiter(nrCores)))
      case "OwnSPM" => (5, Module(new cmp.OwnSPM(nrCores, (nrCores-1)*2, 1024)))
      case "SPMPool" => (6, Module(new cmp.SPMPool(nrCores, (nrCores-1)*2, 1024)))
      case "S4noc" => (7, Module(new cmp.S4nocOCPWrapper(nrCores, 4, 4)))
      case "CASPM" => (8, Module(new cmp.CASPM(nrCores, nrCores * 8)))
      case "AsyncLock" => (9, Module(new cmp.AsyncLock(nrCores, nrCores * 2)))
      case "UartCmp" => (10, Module(new cmp.UartCmp(nrCores,CLOCK_FREQ,115200,16)))
      case "TwoWay" => (11, Module(new cmp.TwoWayOCPWrapper(nrCores, 1024)))
      case "TransactionalMemory" => (12, Module(new cmp.TransactionalMemory(nrCores, 512)))
      case "LedsCmp" => (13, Module(new cmp.LedsCmp(nrCores, 1)))
      case _ => throw new Error("Unknown device " + e)
    }

    Config.connectIOPins(_dev.getClass.getSimpleName, io, _dev.io, "cmp.")
    
    new {
      val offset = off
      val dev = _dev
    }
  })

  for (i <- (0 until nrCores)) {

    val IO_DEVICE_ADDR_WIDTH = 16

    // Default values for interrupt pins
    cores(i).io.inout.intrs := UInt(0)
    
    val connectDevice = (devio: CoreDeviceIO, off: Int) => 
      {
        devio.superMode <> cores(i).io.inout.superMode
        devio.internalPort <> cores(i).io.inout.internalIO
      }

    // Creation of IO devices
    val conf = Config.getConfig

    val cpuinfo = Module(new CpuInfoCmp(Config.datFile, i, nrCores))
    connectDevice(cpuinfo.io, CPUINFO_OFFSET)

    val singledevios = 
      (Config.getConfig.Devs
      .map(e => 
      {
        new {
          val clazz = 
            try { Class.forName("io."+e.name+"$Pins") }
            catch { case e: Exception => null};
          val conf = e
        }
      })
      .filter(e => i == 0 || e.clazz == null || !e.clazz.getMethods.nonEmpty)
      .map(e => 
      {
          val dev = Config.createDevice(e.conf).asInstanceOf[CoreDevice]
          connectDevice(dev.io, e.conf.offset)
          Config.connectIOPins(e.conf.name, io, dev.io)
          Config.connectIntrPins(e.conf, cores(i).io.inout, dev.io)
          new {
            val off = e.conf.offset
            val io = dev.io.ocp
            val name = e.conf.name
          }
      }) ++ List(new {
        val off = CPUINFO_OFFSET;
        val io = cpuinfo.io.ocp;
        val name = cpuinfo.moduleName
      }, new {
        val off = EXC_IO_OFFSET;
        val io = cores(i).io.inout.excInOut;
        val name = "Exception unit"
      }, if(HAS_MMU) new {
        val off = MMU_IO_OFFSET;
        val io = cores(i).io.inout.mmuInOut;
        val name = "MMU"
      } else null).filter(e => e != null))
      .map(e => {
        new {
          val addr = (0xF0 << 8) + e.off;
          val addrwidth = IO_DEVICE_ADDR_WIDTH;
          val io = e.io
          val name = e.name
        }
      })
    
    val cmpdevios = cmpdevs
      .map(e => new {
        val addr = (0xE8 << 8) + e.offset;
        val addrwidth = IO_DEVICE_ADDR_WIDTH;
        val io = e.dev.io match {
          case cmpio: cmp.CmpIO => cmpio.cores(i)
          case _ => e.dev.io.asInstanceOf[Vec[OcpCoreSlavePort]](i)
        }
        val name = e.dev.moduleName
      })

    // The SPM
    val spm = Module(new Spm(DSPM_SIZE))

    // Dummy ISPM (create fake response)
    val ispmio = new OcpCoreSlavePort(ADDR_WIDTH, DATA_WIDTH)
    ispmio.S.Data := 0.U
    ispmio.S.Resp := RegNext(Mux(ispmio.M.Cmd === OcpCmd.IDLE, OcpResp.NULL, OcpResp.DVA))

    val devios = (singledevios ++ cmpdevios) ++ List( 
      new {
          val addr = 0x0000;
          val addrwidth = IO_DEVICE_ADDR_WIDTH;
          val io = spm.io
          val name = "SPM"
        },
      new {
          val addr = 0x0001;
          val addrwidth = IO_DEVICE_ADDR_WIDTH;
          val io = ispmio
          val name = "ISPM"
        }
    )
    
    for(dupldev <- devios
                    .groupBy(e => e.addr)
                    .collect { case (addr,e) if e.lengthCompare(1) > 0 => e}
                    .flatten) {
      throw new Error("Can't assign multiple devices to the same address. " +
        "Device " + dupldev.name + " conflicting on address " +
        dupldev.addr + ". ")
    }

    cores(i).io.inout.memInOut.S.Data := UInt(0)
    val validdev = Bool()
    validdev := false.B
    for(dev <- devios) {
      val addr = cores(i).io.inout.memInOut.M.Addr(ADDR_WIDTH-1, ADDR_WIDTH-dev.addrwidth)
      println(addr.getWidth())
      println(dev.addr)
      println(dev.addrwidth)
      dev.io.M := cores(i).io.inout.memInOut.M
      dev.io.M.Cmd := OcpCmd.IDLE
      when(addr === dev.addr.U) {
        dev.io.M.Cmd := cores(i).io.inout.memInOut.M.Cmd
        validdev := true.B
      }
      val selReg = RegInit(false.B)
      when(cores(i).io.inout.memInOut.M.Cmd =/= OcpCmd.IDLE) {
        selReg := addr === dev.addr.U
      }
      when(selReg) {
        cores(i).io.inout.memInOut.S.Data := dev.io.S.Data
      }

      // TODO: maybe a better way is for all interfaces to have the bits 'superMode' and 'flags'
      // e.g., all IO devices should be possible to have interrupts
      if(dev.io.isInstanceOf[OcpArgoSlavePort]){
        val argoslaveport = dev.io.asInstanceOf[OcpArgoSlavePort]
        argoslaveport.superMode := UInt(0)
        argoslaveport.superMode(i) := cores(i).io.superMode

        // Hard-wire the sideband flags from the NI to interrupt pins
        cores(i).io.inout.intrs(NI_MSG_INTR) := argoslaveport.flags(2*i)
        cores(i).io.inout.intrs(NI_EXT_INTR) := argoslaveport.flags(2*i+1)
      }
    }

    // Register for error response
    val errRespReg = Reg(init = OcpResp.NULL)
    when(cores(i).io.inout.memInOut.M.Cmd =/= OcpCmd.IDLE && !validdev) {
      errRespReg := OcpResp.ERR
    }

    // Merge responses
    cores(i).io.inout.memInOut.S.Resp := errRespReg | devios.map(e => e.io.S.Resp).fold(OcpResp.NULL)(_|_)
  }

  // Connect memory controller
  val ramConf = Config.getConfig.ExtMem.ram
  val ramCtrl = Config.createDevice(ramConf).asInstanceOf[BurstDevice]

  Config.connectIOPins(ramConf.name, io, ramCtrl.io)

  // TODO: fix memory arbiter to have configurable memory timing.
  // E.g., it does not work with on-chip main memory.
  if (cores.length == 1) {
    ramCtrl.io.ocp <> cores(0).io.memPort
  } else {
    val memarbiter = Module(new ocp.TdmArbiterWrapper(nrCores, ADDR_WIDTH, DATA_WIDTH, BURST_LENGTH))
    for (i <- (0 until cores.length)) {
      memarbiter.io.master(i) <> cores(i).io.memPort
    }
    ramCtrl.io.ocp <> memarbiter.io.slave
  }

  // Print out the configuration
  Utility.printConfig(configFile)
}

// this testing and main file should go into it's own folder
//commented out Chisel3 tester has changed see https://github.com/schoeberl/chisel-examples/blob/master/TowardsChisel3.md 
class PatmosTest(pat: Patmos) extends Tester(pat) {

  println("Patmos start")

  for (i <- 0 until 100) {
    step(1) // false as third argument disables printout
    // The PC printout is a little off on a branch
    val pc = peek(pat.cores(0).memory.io.memwb.pc) - 2
    print(pc + " - ")
    for (j <- 0 until 32)
      print(peek(pat.cores(0).decode.rf.rf(UInt(j))) + " ")
    println()
  }
}

object PatmosMain {
  def main(args: Array[String]): Unit = {

    val chiselArgs = args.slice(3, args.length)
    val configFile = args(0)
    val binFile = args(1)
    val datFile = args(2)

    chiselMainTest(chiselArgs, () => Module(new Patmos(configFile, binFile, datFile))) { f => new PatmosTest(f) }
  }
}
