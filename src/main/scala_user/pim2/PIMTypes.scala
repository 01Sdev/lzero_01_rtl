package pim2

import chisel3._
import chisel3.util._

object PIMParams {
  val MATRIX_SIZE = 4
  val DATA_WIDTH  = 8
  val ACC_WIDTH   = 32
  val BUF_DEPTH   = 1024
  val ADDR_WIDTH  = log2Ceil(BUF_DEPTH)

  // Unified Buffer 영역 (Input A, Output C용으로 축소)
  val A_BASE = 0
  val C_BASE = 512

  // Weight Buffer 별도 모듈로 분리: 16 entries (4x4)
  val WEIGHT_BUF_DEPTH = MATRIX_SIZE * MATRIX_SIZE  // 16
  val WEIGHT_ADDR_WIDTH = log2Ceil(WEIGHT_BUF_DEPTH)
}