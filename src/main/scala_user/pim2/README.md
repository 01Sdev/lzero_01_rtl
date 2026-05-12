\# PIM Core (Task 2): Weight Loading \& Weight Stationary



L-ZERO RTL Track Week 5 - Task 2



\## 설계 개요



1번 과제(Output Stationary)에서 발전시켜, \*\*Weight Stationary dataflow\*\*와 \*\*별도 Weight Buffer 모듈\*\*을 도입한 4×4 매트릭스 곱셈 가속기.



\## 1번 과제와의 차이점



| 항목 | Task 1 (Output Stationary) | Task 2 (Weight Stationary) |

|------|----------------------------|----------------------------|

| Weight 저장 위치 | Unified Buffer 내 B 영역 | 별도 WeightBuffer 모듈 |

| Dataflow | Weight를 시간축으로 흘림 | Weight가 MAC reg에 박힘 |

| MAC 내부 reg | accReg만 | weightReg + accReg |

| Weight 재사용 | 매 행렬 곱마다 다시 로드 | 한 번 로드 후 재사용 가능 |



\## 아키텍처┌──────────────────┐     ┌─────────────────┐

│  Unified Buffer  │     │  Weight Buffer  │

│   (A, C 전용)    │     │  (16 entries)   │

└────┬─────────────┘     └────────┬────────┘

│ rdata/wdata                │ row\_out\[4]

│                            │

┌────▼────────────────────────────▼────────┐

│            Orchestrator (FSM)            │

│   - A 읽기      - Weight row select      │

│   - C 저장      - MAC load\_w 제어        │

└────┬──────────┬──────────────────────────┘

│ in\_a     │ weight\[4]    │ load\_w/en/clear

│          │              │

┌────▼──────────▼──────────────▼───────────┐

│         MAC Array (Weight Stationary)    │

│   각 lane 내부 weightReg에 weight 박힘   │

│   compute 시 weightReg와 in\_a 곱셈       │

└──────────────────────────────────────────┘



\## Weight Loading 방식



\### 핵심 결정: Weight를 MAC array 내부에 stationary시킴



\*\*왜 Weight Stationary?\*\*

\- \*\*Weight 재사용\*\*: NN inference에서 같은 weight를 여러 input batch에 재사용 → reload overhead 절감

\- \*\*메모리 대역폭 절감\*\*: compute 중에는 weight를 매 cycle fetch 안 함, input만 흐름

\- \*\*시스톨릭 어레이의 표준 패턴\*\*: TPU 등 상용 가속기와 동일 철학



\### 로딩 절차



1\. \*\*외부 → WeightBuffer 쓰기\*\*: PIMTop의 `wbuf\_we`, `wbuf\_waddr`, `wbuf\_wdata` 포트로 16개 weight를 미리 적재 (4×4 matrix)

2\. \*\*WeightBuffer → MAC array 로딩\*\*: Orchestrator가 row 단위로 weight를 골라 MAC array의 4 lane에 동시 broadcast, `load\_w` 신호로 MAC 내부 weightReg에 latch

3\. \*\*Compute\*\*: weight는 lane 안에 머문 채로 input A를 흘려서 누적



\### 효율성 포인트



\- \*\*Row-wise broadcast\*\*: 한 cycle에 weight 4개 동시 로드 (4× 대역폭)

\- \*\*별도 read port\*\*: WeightBuffer가 Orchestrator와 직결되어 Unified Buffer와 read 경쟁 없음

\- \*\*register file 구현\*\*: 작은 weight(16개)는 BRAM 대신 register로 → 1-cycle access



\## 메모리 맵



\### Unified Buffer (1번과 동일하지만 B 영역 제거)

| 영역 | 주소 범위 | 용도 |

|------|-----------|------|

| A area | 0 \~ 255 | Input matrix A |

| C area | 512 \~ 767 | Output matrix C |



\### Weight Buffer (신규)

| 인덱스 | 내용 |

|--------|------|

| 0 \~ 15 | Weight matrix B (4×4, row-major) |



\## 구성 모듈



| 파일 | 변경 |

|------|------|

| `PIMTypes.scala` | B\_BASE 제거, WEIGHT\_BUF\_DEPTH 추가 |

| `UnifiedBuffer.scala` | 변경 없음 (1번과 동일) |

| `WeightBuffer.scala` | \*\*신규\*\* |

| `MacArray.scala` | weightReg 추가, load\_w 신호 |

| `Orchestrator.scala` | FSM 재설계 (weight load phase 도입) |

| `PIMTop.scala` | WeightBuffer 인스턴스 추가, wbuf 포트 노출 |

| `PIMTopMain.scala` | package pim2 |



\## 빌드



```bash



docker exec -it lzero\_01\_rtl-lzero-rtl-1 sbt "runMain pim2.PIMTopMain"

```



결과: `generated/PIMTop.sv` (2번 과제 버전으로 덮어씀)



1번 과제 결과를 보존하려면 빌드 전 `generated/PIMTop.sv`를 `PIMTop\_task1.sv`로 백업 후 빌드.



\## 검증 상태



\- \[x] Chisel → SystemVerilog elaborate 통과

\- \[ ] 시뮬레이션 기반 동작 검증 (향후 작업)

