package pim

import chisel3._
import chisel3.util._
import PIMParams._

class PIMTop extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val done  = Output(Bool())

    // 외부에서 버퍼 초기화/검증용
    val ext_we    = Input(Bool())
    val ext_addr  = Input(UInt(ADDR_WIDTH.W))
    val ext_wdata = Input(UInt(DATA_WIDTH.W))
    val ext_rdata = Output(UInt(DATA_WIDTH.W))
  })

  val buffer = Module(new UnifiedBuffer)
  val orch   = Module(new Orchestrator)
  val mac    = Module(new MacArray)

  // Buffer 인터페이스: orchestrator 동작 중엔 orch가, idle일 땐 외부가 접근
  // (간단화를 위해 done 상태 또는 idle 상태에서만 외부 접근)
  val orch_owns = !io.done || io.start

  buffer.io.we    := Mux(orch_owns, orch.io.buf_we,    io.ext_we)
  buffer.io.addr  := Mux(orch_owns, orch.io.buf_addr,  io.ext_addr)
  buffer.io.wdata := Mux(orch_owns, orch.io.buf_wdata, io.ext_wdata)
  io.ext_rdata := buffer.io.rdata
  orch.io.buf_rdata := buffer.io.rdata

  // MAC array 연결
  mac.io.in_a  := orch.io.mac_in_a
  mac.io.in_b  := orch.io.mac_in_b
  mac.io.en    := orch.io.mac_en
  mac.io.clear := orch.io.mac_clear
  orch.io.mac_acc := mac.io.out_acc

  // 외부 제어
  orch.io.start := io.start
  io.done := orch.io.done
}