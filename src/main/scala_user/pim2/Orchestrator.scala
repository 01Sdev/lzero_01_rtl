package pim2

import chisel3._
import chisel3.util._
import PIMParams._

class Orchestrator extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val done  = Output(Bool())

    // Unified Buffer 인터페이스 (A read, C write 전용)
    val buf_we    = Output(Bool())
    val buf_addr  = Output(UInt(ADDR_WIDTH.W))
    val buf_wdata = Output(UInt(DATA_WIDTH.W))
    val buf_rdata = Input(UInt(DATA_WIDTH.W))

    // Weight Buffer 인터페이스
    val wbuf_row_sel = Output(UInt(log2Ceil(MATRIX_SIZE).W))
    val wbuf_row_out = Input(Vec(MATRIX_SIZE, UInt(DATA_WIDTH.W)))

    // MAC array 제어
    val mac_in_a     = Output(UInt(DATA_WIDTH.W))
    val mac_weight   = Output(Vec(MATRIX_SIZE, UInt(DATA_WIDTH.W)))
    val mac_load_w   = Output(Bool())
    val mac_en       = Output(Bool())
    val mac_clear    = Output(Bool())
    val mac_acc      = Input(Vec(MATRIX_SIZE, UInt(ACC_WIDTH.W)))
  })

  // === FSM 상태 ===
  // Weight Stationary에서는 사실 4x4 weight를 한꺼번에 모든 lane에 로드 못 함
  // (4 lane은 한 column에 해당, weight matrix는 4 row × 4 col)
  // → outer loop: output column 단위로 처리
  //   각 output column j에 대해:
  //     1) weight column j를 4-lane에 load (각 lane = B[k=0..3, j])
  //        그런데 weight buffer는 row 단위 출력이므로 lane별로 다른 row를 골라야 함
  //
  // 단순화 전략: 각 lane이 "행렬 곱셈의 한 row of output"을 담당하도록 재배치
  //   - lane(k)에 weight = B[k, j] (j 고정)
  //   - 매 cycle: A[i, ?]를 외부에서 흘려보냄
  //
  // 더 직관적인 다른 매핑:
  //   - 4 lane 전체가 C[i, j] 4개를 동시 계산 (j 변경)
  //   - lane(j)의 weight = B[k, j], k 변경하며 누적
  //
  // 채택: 4 lane이 output의 한 row 4개 element를 동시 계산
  //   for i in 0..3:
  //     clear accumulators
  //     for k in 0..3:
  //       load weight row k of B into 4 lanes (= B[k, 0..3])
  //       wait 1 cycle (weight reg update)
  //       read A[i, k], broadcast to all lanes, compute (en=1)
  //     store accumulators to C[i, 0..3]

  val sIdle      = 0.U(4.W)
  val sClearAcc  = 1.U(4.W)
  val sLoadW1    = 2.U(4.W)  // weight buffer row k 출력 selet
  val sLoadW2    = 3.U(4.W)  // load_w pulse, weight reg update
  val sLoadA1    = 4.U(4.W)  // A[i,k] read 시작
  val sLoadA2    = 5.U(4.W)  // A capture + compute enable
  val sNextK     = 6.U(4.W)  // k 증가
  val sStore     = 7.U(4.W)  // C[i, 0..3] write (4 cycle)
  val sNextI     = 8.U(4.W)
  val sDone      = 9.U(4.W)

  val state = RegInit(sIdle)

  // 인덱스
  val i = RegInit(0.U(log2Ceil(MATRIX_SIZE + 1).W))
  val k = RegInit(0.U(log2Ceil(MATRIX_SIZE + 1).W))
  val storeJ = RegInit(0.U(log2Ceil(MATRIX_SIZE + 1).W))

  val aReg = RegInit(0.U(DATA_WIDTH.W))

  // 기본값
  io.buf_we     := false.B
  io.buf_addr   := 0.U
  io.buf_wdata  := 0.U
  io.wbuf_row_sel := k(log2Ceil(MATRIX_SIZE) - 1, 0)
  io.mac_in_a   := aReg
  io.mac_weight := io.wbuf_row_out  // 항상 현재 row 출력
  io.mac_load_w := false.B
  io.mac_en     := false.B
  io.mac_clear  := false.B
  io.done       := false.B

  switch(state) {
    is(sIdle) {
      when(io.start) {
        i := 0.U
        k := 0.U
        storeJ := 0.U
        state := sClearAcc
      }
    }

    is(sClearAcc) {
      io.mac_clear := true.B
      k := 0.U
      state := sLoadW1
    }

    is(sLoadW1) {
      // weight buffer row k를 read (이미 row_sel = k로 항상 출력 중)
      io.wbuf_row_sel := k(log2Ceil(MATRIX_SIZE) - 1, 0)
      state := sLoadW2
    }
    is(sLoadW2) {
      // weight를 MAC lane reg에 latch
      io.wbuf_row_sel := k(log2Ceil(MATRIX_SIZE) - 1, 0)
      io.mac_load_w := true.B
      state := sLoadA1
    }

    is(sLoadA1) {
      // A[i, k] read 시작
      io.buf_addr := A_BASE.U + i * MATRIX_SIZE.U + k
      io.wbuf_row_sel := k(log2Ceil(MATRIX_SIZE) - 1, 0)
      state := sLoadA2
    }
    is(sLoadA2) {
      io.wbuf_row_sel := k(log2Ceil(MATRIX_SIZE) - 1, 0)
      aReg := io.buf_rdata  // a를 캡처
      state := sNextK
    }

    is(sNextK) {
      // 이제 weight는 박혀있고, a는 aReg에 있음. compute!
      io.mac_in_a := aReg
      io.mac_en := true.B
      when(k === (MATRIX_SIZE - 1).U) {
        // 한 row의 누적 끝
        storeJ := 0.U
        state := sStore
      }.otherwise {
        k := k + 1.U
        state := sLoadW1
      }
    }

    is(sStore) {
      // C[i, storeJ] = mac_acc[storeJ]의 하위 8bit
      io.buf_we    := true.B
      io.buf_addr  := C_BASE.U + i * MATRIX_SIZE.U + storeJ
      io.buf_wdata := io.mac_acc(storeJ)(DATA_WIDTH - 1, 0)
      when(storeJ === (MATRIX_SIZE - 1).U) {
        state := sNextI
      }.otherwise {
        storeJ := storeJ + 1.U
      }
    }

    is(sNextI) {
      when(i === (MATRIX_SIZE - 1).U) {
        state := sDone
      }.otherwise {
        i := i + 1.U
        state := sClearAcc
      }
    }

    is(sDone) {
      io.done := true.B
      when(!io.start) {
        state := sIdle
      }
    }
  }
}