# 찰랑: 물컵 챌린지

대시보드 위 가상의 물 한 컵으로 "뒷자리 사람이 편한 운전"을 보여 주고, 물을 쏟게 만든 원인을 차량 신호로 짚어 음성으로 알려 주는 Pleos Connect 차량용 승차감 코치 앱.

- 주행 중: 컵 하나와 쏟은 양만 표시. 급감속·급가속·급조향으로 물이 넘치면 원인을 한마디로 안내
- 정차(P단) 후: 주행 거리·시간, 쏟은 횟수와 원인별 집계, 한 줄 조언을 화면과 음성으로 정리
- 원인 구분: 급제동 / 가속 페달 급히 떼기(회생제동) / 급가속 / 급핸들(방향지시등 없이 돌린 경우 따로 표시)
- 케어 주행: 뒷좌석 탑승이 감지될 때만 음성 코칭
- 민감도 3단계(아이·반려동물 / 어른 / 혼자), 주행 기록 저장과 지난 주행 비교
- 예고 안내: 길 안내 중 다음 회전 지점 앞에서 미리 감속 안내
- 말로 물어보기: 정차 중 "오늘 어땠어?", "왜 쏟았어?"에 최근 기록으로 답변

## 사용하는 SDK

| SDK | 용도 |
|---|---|
| Pleos Vehicle SDK 2.3.2 | 브레이크 페달, 핸들 각도, 기어, 방향지시등, 뒷좌석 탑승 상태 |
| Pleos TextToSpeech SDK 3.3.1 | 온디바이스 음성 안내 |
| Pleos SpeechToText SDK 3.3.1 | 정차 중 음성 질문 (온디바이스) |
| Pleos NaviHelper SDK 3.0.5 | 다음 회전 지점 조회 → 예고 안내 |
| Android Automotive Car API | 차량 속도(`PERF_VEHICLE_SPEED`) |

차량 제어 권한은 쓰지 않고 조회 권한만 선언한다.

## 실행 방법

1. Android Studio SDK Manager의 SDK Update Sites에 Pleos Connect System Image 저장소를 추가하고 API 34 이미지를 설치한다. ([설정 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator))
2. Automotive 기기 프로필 `Pleos Connect v2.1.0`으로 AVD를 만들어 실행한다. (Cold boot, RAM 4GB)
3. 빌드·설치

```bash
./gradlew installDebug
```

4. 앱 서랍에서 `찰랑`을 실행하고 차량 속도 권한을 허용한다.

## 시연 방법 (Virtual Console)

```bash
adb forward tcp:8088 tcp:8088
```

- 직접 조작: 브라우저에서 `http://127.0.0.1:8088/` 을 열어 기어·가속·브레이크·핸들을 조작
- 자동 재생: 같은 주행을 반복해서 보여 줄 때

```bash
python3 tools/drive_demo.py rough
```

```bash
python3 tools/drive_demo.py smooth
```

`rough`는 급가속·회생제동·급조향·급제동이 섞인 주행, `smooth`는 한 방울도 쏟지 않는 주행이다.

## 테스트 환경

- Pleos Connect Emulator v2.1.0 (Android API 34, x86_64), 2560×1440
- JDK 17, Gradle 8.14.3, Android Gradle Plugin 8.8.2, Kotlin 2.0.0
- 단위 테스트: `./gradlew testDebugUnitTest` (물 출렁임·원인 분류, 예고 시점, 음성 질문 응답 17건)
- 에뮬레이터에서 확인하지 못한 것: 실제 음성 입출력(오디오 끈 상태로 실행), 경로 안내 중 예고 안내

## APK

- release 빌드: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
- 제출 시 `apk/팀명_앱이름_release.apk` 로 복사해 올린다.

## 시연 자료

- 영상: `docs/demo/watercup-demo.mp4` (거친 주행 시나리오 50초)
- 화면: `docs/screenshots/`
- 다시 만들기: `tools/capture_demo.sh` (에뮬레이터에 설치 → 시나리오 재생 → 화면·영상 저장)

## 구조

```
app/src/main/java/app/watercup/
  engine/WaterCupEngine.kt   속도·핸들·브레이크 → 물 출렁임, 넘침 판정, 원인 분류
  signal/VehicleSignals.kt   Pleos Vehicle SDK 신호 구독
  signal/CarSpeedSource.kt   차량 속도
  signal/Voice.kt            Pleos TextToSpeech
  signal/VoiceAsk.kt         Pleos SpeechToText (음성 질문)
  signal/NaviGuide.kt        Pleos NaviHelper (다음 회전 지점)
  engine/TurnPreview.kt      예고 시점 판단
  Answers.kt                 음성 질문 응답
  ui/WaterCupApp.kt          대기·주행·도착 리뷰 화면
  MainViewModel.kt           주행 상태 관리
  TripStore.kt               주행 기록·민감도 저장
tools/drive_demo.py          Virtual Console 주행 시나리오 재생
tools/capture_demo.sh        시연 화면·영상 다시 만들기
tools/fill_form.py           기획서 초안을 접수양식 DOCX에 채우기
```
