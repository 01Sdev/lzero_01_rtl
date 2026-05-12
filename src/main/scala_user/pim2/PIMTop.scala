package pim2

import chisel3._
import chisel3.util._
import PIMParams._

class PIMTop extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val done  = Output(Bool())

    // 외부에서 Unified Buffer 접근 (A 입력 및 C 결과 read용)
    val ext_we    = Input(Bool())
    val ext_addr  = Input(UInt(ADDR_WIDTH.W))
    val ext_wdata = Input(UInt(DATA_WIDTH.W))
    val ext_rdata = Output(UInt(DATA_WIDTH.W))

    // 외부에서 Weight Buffer 쓰기 (weight load 전용)
    val wbuf_we    = Input(Bool())
    val wbuf_waddr = Input(UInt(WEIGHT_ADDR_WIDTH.W))
    val wbuf_wdata = Input(UInt(DATA_WIDTH.W))
  })

  val buffer  = Module(new UnifiedBuffer)
  val wbuffer = Module(new WeightBuffer)
  val orch    = Module(new Orchestrator)
  val mac     = Module(new MacArray)

  // === Unified Buffer 라우팅 ===
  val orch_owns = !io.done || io.start
  buffer.io.we    := Mux(orch_owns, orch.io.buf_we,    io.ext_we)
  buffer.io.addr  := Mux(orch_owns, orch.io.buf_addr,  io.ext_addr)
  buffer.io.wdata := Mux(orch_owns, orch.io.buf_wdata, io.ext_wdata)
  io.ext_rdata := buffer.io.rdata
  orch.io.buf_rdata := buffer.io.rdata

  // === Weight Buffer 라우팅 ===
  // write는 항상 외부에서만 (Orchestrator는 read만)
  wbuffer.io.we    := io.wbuf_we
  wbuffer.io.waddr := io.wbuf_waddr
  wbuffer.io.wdata := io.wbuf_wdata
  wbuffer.io.row_sel := orch.io.wbuf_row_sel
  orch.io.wbuf_row_out := wbuffer.io.row_out

  // === MAC array 연결 ===
  mac.io.in_a      := orch.io.mac_in_a
  mac.io.weight_in := orch.io.mac_weight
  mac.io.load_w    := orch.io.mac_load_w
  mac.io.en        := orch.io.mac_en
  mac.io.clear     := orch.io.mac_clear
  orch.io.mac_acc := mac.io.out_acc

  // === 제어 ===
  orch.io.start := io.start
  io.done := orch.io.done
}