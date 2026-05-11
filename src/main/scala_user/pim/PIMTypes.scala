package pim

import chisel3._
import chisel3.util._

// 전역 파라미터를 한 곳에 모음
object PIMParams {
  val MATRIX_SIZE = 4       // 4x4 matrix
  val DATA_WIDTH  = 8       // input/weight: 8-bit
  val ACC_WIDTH   = 32      // accumulator: 32-bit
  val BUF_DEPTH   = 1024    // unified buffer 총 entry 수
  val ADDR_WIDTH  = log2Ceil(BUF_DEPTH)  // = 10

  // Unified Buffer 영역 분할 (base address)
  val A_BASE = 0
  val B_BASE = 256
  val C_BASE = 512
}