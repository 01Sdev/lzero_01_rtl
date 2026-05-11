package pim

import chisel3._
import chisel3.util._
import PIMParams._

// 단일 포트 SyncReadMem 기반 통합 버퍼
// 합성 시 BRAM으로 매핑되도록 SyncReadMem 사용
class UnifiedBuffer extends Module {
  val io = IO(new Bundle {
    val we    = Input(Bool())                  // write enable
    val addr  = Input(UInt(ADDR_WIDTH.W))
    val wdata = Input(UInt(DATA_WIDTH.W))
    val rdata = Output(UInt(DATA_WIDTH.W))
  })

  val mem = SyncReadMem(BUF_DEPTH, UInt(DATA_WIDTH.W))

  // write
  when(io.we) {
    mem.write(io.addr, io.wdata)
  }

  // read (1-cycle latency)
  io.rdata := mem.read(io.addr, !io.we)
}