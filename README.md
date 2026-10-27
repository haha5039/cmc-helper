# CMC Helper

갤럭시 휴대폰의 삼성 CMC를 수신 기기(`sd`) 또는 발신 기기(`pd`)로 준비하는 앱입니다. **1.1.0부터 Shizuku 연결 후 앱 버튼으로 준비·역할 변경·복원·역할 예외 해제를 실행**할 수 있습니다. 시스템 전체의 태블릿 모드나 화면 배치는 바꾸지 않습니다.

[APK 프리릴리스](https://github.com/haha5039/cmc-helper/releases/tag/v1.1.0)

## 휴대전화만으로 사용하기

1. APK를 설치합니다. 기존 1.0과 패키지·서명 인증서가 같아 업데이트할 수 있습니다.
2. 휴대전화의 개발자 옵션에서 무선 디버깅을 켭니다. Shizuku 앱 안내에 따라 페어링하고 시작합니다.
3. CMC Helper에서 **Shizuku 연결**을 누르고 권한을 허용합니다. 실제 엔진 UID가 표시됩니다.
4. 잠금을 해제하고 **수신 기능 준비**를 누릅니다. 삼성 CMC 화면이 전환되는 동안 화면을 조작하지 않습니다.
5. 삼성 CMC의 전체·통화·메시지 사용을 끈 뒤 **이 기기를 수신 기기로**를 누릅니다. 이미 같은 역할이면 쓰지 않습니다.
6. **삼성 CMC 설정 열기**에서 같은 삼성 계정, 발신 휴대폰, QR 기기 연결을 확인합니다. 처음에는 두 기기를 같은 Wi-Fi에 연결합니다.
7. 실제 전화와 별도 SMS를 보내 수신 여부를 각각 확인합니다. 사용 설정이 켜져 있다는 표시만으로 연결·수신 성공을 판정하지 않습니다.

Shizuku는 Android 11 이상에서 무선 디버깅으로 PC 없이 시작할 수 있습니다. 재부팅 후에는 다시 시작해야 합니다. [공식 시작 안내](https://shizuku.rikka.app/guide/setup/)

## 지원 범위와 권한

무선 디버깅으로 시작한 Shizuku는 shell UID 2000, 루트로 시작한 서버는 UID 0입니다. 앱이 실제 모드를 구분합니다. 루트 없이 쓰려면 Shizuku를 무선 디버깅으로 시작하세요.

## 역할 변경과 예외 ON/OFF

- **수신 기능 준비:** 삼성 정보 화면의 인증된 경로로 내부 `test_flag`를 켭니다.
- **수신/발신 기기:** 원래 값을 기록하고 쓰기 후 3초간 확인합니다. 실패 시 복원하며 외부에서 바뀐 값은 덮어쓰지 않습니다.
- **원래 역할 복원:** 최초 쓰기 전에 보관한 값으로 복원합니다. 원래 키가 없던 상태도 구분합니다.
- **역할 예외 해제:** CMC 사용이 모두 꺼져 있고 `pd`일 때만 허용합니다. 삼성 기본 OFF·Reset 동작은 사용하지 않습니다.

예외가 꺼지면 휴대폰의 `sd`를 삼성 CMC가 기본 `pd`로 되돌립니다. 켜져 있으면 두 역할을 허용합니다. 표시용 개발자 스위치와 내부 `test_flag`는 별개입니다.

## Shizuku 구현

UserService의 IPC는 고정 역할 쓰기와 고정 준비 작업만 제공합니다. 입력 문자열을 셸 코드로 실행하지 않습니다. 시간·출력 길이를 제한하고 요청 ID가 맞는 완료 결과만 받습니다. 버튼 작업은 직렬화하며 Activity 재생성 시 작업을 재전송하지 않습니다.

준비 버튼은 `am instrument --no-restart`로 실행 중인 앱에 자체 계측을 붙입니다. 숨겨진 시스템 API를 사용하는 화면 실행은 Shizuku 엔진에서 처리하고, 일반 앱의 일회성 UiAutomation이 삼성 화면을 조작합니다. 요청 ID와 Helper 자체 작업 ID를 확인합니다. 무재시작 경로가 실패해도 앱을 종료하는 계측 방식으로 자동 대체하지 않습니다.

무재시작 동작은 [Android 16 AOSP ActivityThread](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/app/ActivityThread.java)의 `handleInstrumentWithoutRestart`와 종료 처리를 참고했습니다. Samsung 펌웨어의 새 Shizuku 경로는 아직 시험하지 않았습니다.

## 개발과 검사

```sh
python3 tools/check-host.py
python3 tools/build.py
```

JDK 17과 Android SDK platform/build-tools 36을 사용합니다. `JAVA_HOME`, `ANDROID_HOME`, `CMC_SIGNING_HELPER`로 경로를 지정할 수 있습니다. 의존성 해시는 `app/dependencies.json`으로 확인하며 라이선스는 APK assets와 소스에 포함합니다.

서명은 기존 local signing helper helper의 비공개 설정을 외부에서 사용합니다. 없거나 인증서가 다르면 실패하며 대체 키를 만들지 않습니다.

```text
인증서 SHA-256
fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c
```

## 기존 ADB 방식 (선택)

`python3 tools/device.py install|prepare|receiver|sender|restore|disable-exception`을 사용할 수 있습니다. 실제 실행에서는 작업 이름 하나를 지정합니다. 여러 기기는 `--serial`을 추가합니다. 역할 스크립트는 쓰기 동안만 WRITE_SECURE_SETTINGS를 부여하고 회수하며, 원래 있던 사용자 승인은 유지합니다. Shizuku 앱 버튼에는 이 PC 명령이 필요하지 않습니다.

## 기록

참고: [Shizuku API](https://github.com/RikkaApps/Shizuku-API), [OneUIX CMC 훅](https://github.com/SoClear/OneUIX/blob/6ccbb71fe81464eaeb396de89160cd948b500d58/hook/src/main/java/io/github/soclear/oneuix/hook/MdecService.kt).
