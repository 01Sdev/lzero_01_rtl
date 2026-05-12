package pim2

import chisel3._
import chisel3.util._
import PIMParams._

// Weight 전용 버퍼.
// 외부에서 row-major로 write되고, MAC array로 한 row(4개)를 동시에 출력.
class WeightBuffer extends Module {
  val io = IO(new Bundle {
    // 외부 쓰기 인터페이스 (PIMTop에서 weight load 시 사용)
    val we    = Input(Bool())
    val waddr = Input(UInt(WEIGHT_ADDR_WIDTH.W))
    val wdata = Input(UInt(DATA_WIDTH.W))

    // MAC array로 row 단위 출력
    val row_sel = Input(UInt(log2Ceil(MATRIX_SIZE).W))
    val row_out = Output(Vec(MATRIX_SIZE, UInt(DATA_WIDTH.W)))
  })

  // 16개 8-bit register file (4x4 weight matrix)
  val mem = RegInit(VecInit(Seq.fill(WEIGHT_BUF_DEPTH)(0.U(DATA_WIDTH.W))))

  // write
  when(io.we) {
    mem(io.waddr) := io.wdata
  }

  // read: row_sel * MATRIX_SIZE 부터 4개를 한 번에 출력
  for (j <- 0 until MATRIX_SIZE) {
    io.row_out(j) := mem(io.row_sel * MATRIX_SIZE.U + j.U)
  }
}