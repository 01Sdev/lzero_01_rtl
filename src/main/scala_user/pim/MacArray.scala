package pim

import chisel3._
import chisel3.util._
import PIMParams._

// 단일 lane: 누적 폭 확장 버전 MAC
class MacLane extends Module {
  val io = IO(new Bundle {
    val in_a    = Input(UInt(DATA_WIDTH.W))
    val in_b    = Input(UInt(DATA_WIDTH.W))
    val en      = Input(Bool())              // enable 누적
    val clear   = Input(Bool())
    val out_acc = Output(UInt(ACC_WIDTH.W))
  })

  val accReg = RegInit(0.U(ACC_WIDTH.W))
  val product = (io.in_a * io.in_b).asUInt   // 16-bit result

  when(io.clear) {
    accReg := 0.U
  }.elsewhen(io.en) {
    accReg := accReg + product
  }

  io.out_acc := accReg
}

// 4-lane MAC array. 같은 a를 4개 lane에 broadcast,
// 각 lane은 다른 b를 받음 → output stationary
class MacArray extends Module {
  val io = IO(new Bundle {
    val in_a    = Input(UInt(DATA_WIDTH.W))                              // broadcast
    val in_b    = Input(Vec(MATRIX_SIZE, UInt(DATA_WIDTH.W)))            // per-lane
    val en      = Input(Bool())
    val clear   = Input(Bool())
    val out_acc = Output(Vec(MATRIX_SIZE, UInt(ACC_WIDTH.W)))
  })

  val lanes = Seq.fill(MATRIX_SIZE)(Module(new MacLane))
  for (i <- 0 until MATRIX_SIZE) {
    lanes(i).io.in_a  := io.in_a
    lanes(i).io.in_b  := io.in_b(i)
    lanes(i).io.en    := io.en
    lanes(i).io.clear := io.clear
    io.out_acc(i)     := lanes(i).io.out_acc
  }
}