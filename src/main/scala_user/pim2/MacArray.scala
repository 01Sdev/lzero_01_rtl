package pim2

import chisel3._
import chisel3.util._
import PIMParams._

// Weight Stationary MAC lane: weight를 내부 reg에 저장 후 그 자리에서 곱셈
class MacLane extends Module {
  val io = IO(new Bundle {
    val in_a       = Input(UInt(DATA_WIDTH.W))
    val weight_in  = Input(UInt(DATA_WIDTH.W))  // 새로 추가: weight 로드용
    val load_w     = Input(Bool())              // weight 로드 enable
    val en         = Input(Bool())              // compute enable
    val clear      = Input(Bool())
    val out_acc    = Output(UInt(ACC_WIDTH.W))
  })

  val weightReg = RegInit(0.U(DATA_WIDTH.W))  // weight stays here (stationary)
  val accReg    = RegInit(0.U(ACC_WIDTH.W))

  // weight load
  when(io.load_w) {
    weightReg := io.weight_in
  }

  // accumulator update
  val product = (io.in_a * weightReg).asUInt
  when(io.clear) {
    accReg := 0.U
  }.elsewhen(io.en) {
    accReg := accReg + product
  }

  io.out_acc := accReg
}

// 4-lane MAC array. weight는 각 lane에 박혀있고, input a가 broadcast됨.
class MacArray extends Module {
  val io = IO(new Bundle {
    val in_a       = Input(UInt(DATA_WIDTH.W))                              // broadcast
    val weight_in  = Input(Vec(MATRIX_SIZE, UInt(DATA_WIDTH.W)))            // 4 lane 동시 로드
    val load_w     = Input(Bool())
    val en         = Input(Bool())
    val clear      = Input(Bool())
    val out_acc    = Output(Vec(MATRIX_SIZE, UInt(ACC_WIDTH.W)))
  })

  val lanes = Seq.fill(MATRIX_SIZE)(Module(new MacLane))
  for (i <- 0 until MATRIX_SIZE) {
    lanes(i).io.in_a      := io.in_a
    lanes(i).io.weight_in := io.weight_in(i)
    lanes(i).io.load_w    := io.load_w
    lanes(i).io.en        := io.en
    lanes(i).io.clear     := io.clear
    io.out_acc(i)         := lanes(i).io.out_acc
  }
}