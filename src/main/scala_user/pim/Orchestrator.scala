package pim

import chisel3._
import chisel3.util._
import PIMParams._

class Orchestrator extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val done  = Output(Bool())

    // Unified Buffer 인터페이스 (single port)
    val buf_we    = Output(Bool())
    val buf_addr  = Output(UInt(ADDR_WIDTH.W))
    val buf_wdata = Output(UInt(DATA_WIDTH.W))
    val buf_rdata = Input(UInt(DATA_WIDTH.W))

    // MAC array 제어
    val mac_in_a  = Output(UInt(DATA_WIDTH.W))
    val mac_in_b  = Output(Vec(MATRIX_SIZE, UInt(DATA_WIDTH.W)))
    val mac_en    = Output(Bool())
    val mac_clear = Output(Bool())
    val mac_acc   = Input(Vec(MATRIX_SIZE, UInt(ACC_WIDTH.W)))
  })

  // FSM 상태
  val sIdle    = 0.U(3.W)
  val sLoadB   = 1.U(3.W)   // B의 한 row를 fetch해서 레지스터에 저장
  val sLoadA   = 2.U(3.W)   // A의 원소 fetch
  val sCompute = 3.U(3.W)   // MAC enable
  val sNextK   = 4.U(3.W)   // k++ or next row
  val sStore   = 5.U(3.W)   // 결과를 C area에 write
  val sDone    = 6.U(3.W)

  val state = RegInit(sIdle)

  // 인덱스 레지스터: C[i,j] = sum_k A[i,k] * B[k,j]
  val i = RegInit(0.U(log2Ceil(MATRIX_SIZE).W))  // 현재 row of A/C
  val k = RegInit(0.U(log2Ceil(MATRIX_SIZE).W))  // inner index

  // B 한 row를 저장할 레지스터 (MAC array의 in_b로 공급)
  val bRow = RegInit(VecInit(Seq.fill(MATRIX_SIZE)(0.U(DATA_WIDTH.W))))
  val bColIdx = RegInit(0.U(log2Ceil(MATRIX_SIZE).W))

  // C 저장용 인덱스
  val storeJ = RegInit(0.U(log2Ceil(MATRIX_SIZE).W))

  // 기본값
  io.buf_we    := false.B
  io.buf_addr  := 0.U
  io.buf_wdata := 0.U
  io.mac_in_a  := 0.U
  io.mac_in_b  := bRow
  io.mac_en    := false.B
  io.mac_clear := false.B
  io.done      := false.B

  // 어드레스 계산 helper
  def aAddr(row: UInt, col: UInt): UInt = (A_BASE.U + row * MATRIX_SIZE.U + col)
  def bAddr(row: UInt, col: UInt): UInt = (B_BASE.U + row * MATRIX_SIZE.U + col)
  def cAddr(row: UInt, col: UInt): UInt = (C_BASE.U + row * MATRIX_SIZE.U + col)

  switch(state) {
    is(sIdle) {
      when(io.start) {
        i := 0.U
        k := 0.U
        bColIdx := 0.U
        io.mac_clear := true.B   // accumulator 초기화
        state := sLoadB
      }
    }


    is(sLoadB) {
      // B[k, bColIdx] 읽기 시작
      io.buf_addr := bAddr(k, bColIdx)
      // SyncReadMem이라 다음 cycle에 rdata가 유효해짐
      // → rdata 캡처는 다음 state에서
      state := sLoadB  // 일단 stay
      // 사실은 latency 관리를 위해 별도 sub-state가 깔끔한데,
      // 단순화 위해 카운터로 처리
      // 아래 별도 로직으로 대체
    }

    // ... (아래에 다시 깔끔하게 정리)
  }
}