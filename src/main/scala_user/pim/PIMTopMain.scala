package pim

import chisel3._
import circt.stage.ChiselStage

object PIMTopMain extends App {
  println("🚀 Generating SystemVerilog for PIMTop...")
  ChiselStage.emitSystemVerilogFile(
    new PIMTop,
    Array("--target-dir", "generated")
  )
}