package pim2

import chisel3._
import circt.stage.ChiselStage

object PIMTopMain extends App {
  println("🚀 Generating SystemVerilog for PIMTop (Task 2: Weight Stationary)...")
  ChiselStage.emitSystemVerilogFile(
    new PIMTop,
    Array("--target-dir", "generated")
  )
}