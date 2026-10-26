# CMC Helper

갤럭시 휴대폰의 삼성 CMC 역할을 수신 기기(`sd`) 또는 발신 기기(`pd`)로 바꾸는 Helper입니다. 휴대폰 전체의 화면 배치나 기기 종류는 바꾸지 않습니다.

## 권한과 실행 방식

- 일반 앱에는 전화·SMS·연락처·네트워크·접근성·루트 권한을 요청하지 않습니다.
- 역할을 쓸 때 `WRITE_SECURE_SETTINGS`가 필요합니다. `tools/device.py`는 작업 때만 ADB로 부여하고 완료·실패 후 회수합니다. 원래 부여돼 있던 권한은 유지합니다.
- 삼성 내부 플래그 준비·해제는 PC의 ADB로 시작한 계측에서만 실행합니다. `MANAGE_GAME_ACTIVITY`를 한 번의 고정 대상 화면 실행 동안 위임받고 즉시 반환합니다. 앱의 Linux UID는 일반 앱 UID 그대로입니다.
- 일상적으로 앱 버튼에서 역할을 바꾸려면 아래 선택 명령으로 `WRITE_SECURE_SETTINGS` 하나를 계속 부여할 수 있습니다. Shizuku는 필요하지 않습니다.

## 설치와 수신 설정

휴대폰 잠금을 해제하고 USB 디버깅을 허용한 PC에서 이 폴더를 기준으로 실행합니다. 여러 기기가 연결돼 있으면 각 명령에 `--serial 기기번호`를 추가합니다.

```sh
python3 tools/device.py install
python3 tools/device.py prepare
python3 tools/device.py receiver
```

`prepare`는 삼성 CMC 정보 화면 → 개발자 인증 → 테스트 플래그 설정을 자동으로 진행합니다. 화면을 조작하지 말고 완료를 기다리세요. 초기화·재부팅 버튼은 누르지 않습니다.

Helper의 **네이티브 CMC 열기**에서 설정을 계속합니다. 발신 휴대폰과 같은 삼성 계정으로 로그인하고, 처음에는 두 기기를 같은 Wi-Fi에 연결합니다. 발신 휴대폰의 CMC도 켜져 있어야 합니다. 실제 전화와 SMS 도착 여부를 각각 시험하세요.

이미 CMC가 사용 중일 때는 같은 역할 확인만 허용합니다. 다른 역할로 바꾸려면 삼성 CMC에서 전화·메시지를 모두 끕니다.

## 역할 왕복 전환

```sh
# 삼성 CMC에서 전화·메시지를 모두 끈 뒤 실행
python3 tools/device.py sender
python3 tools/device.py receiver

# 이 Helper가 최초 변경 전에 저장한 역할로 복원
python3 tools/device.py restore
```

## 역할 예외(test_flag) 끄기와 다시 켜기

```sh
# 삼성 CMC에서 전화·메시지를 모두 끈 상태
python3 tools/device.py sender
python3 tools/device.py disable-exception

# 다시 수신 역할을 허용
python3 tools/device.py prepare
python3 tools/device.py receiver
```

`disable-exception`은 내부 `test_flag`만 해제하는 경로입니다. 삼성 개발자 화면의 표시용 스위치는 켜져 있을 수 있습니다. 내부 플래그와 표시 스위치는 서로 다른 값입니다. 기본 스위치를 직접 끄면 삼성 CMC가 초기화와 재부팅을 요청하므로 이 Helper는 그 동작을 사용하지 않습니다.

예외가 꺼진 휴대폰에서 `sd`를 쓰면 삼성 CMC가 다시 `pd`로 돌립니다. Helper는 이를 실패로 표시하고 기존 역할·복원 기록을 보존합니다. 예외가 켜져 있으면 `sd`와 `pd`가 모두 허용됩니다.

## 앱 버튼을 계속 사용하려는 경우

```sh
adb shell pm grant com.haha5039.cmc android.permission.WRITE_SECURE_SETTINGS

# 필요가 없어졌을 때 회수
adb shell pm revoke com.haha5039.cmc android.permission.WRITE_SECURE_SETTINGS
```

권한을 회수해도 이미 적용한 역할은 지워지지 않습니다. 상태 확인과 삼성 CMC 열기는 계속 사용할 수 있습니다. 플래그 설정·해제에는 별도로 ADB 계측 실행이 필요합니다.

## 작동 원리

삼성 `CommonUtils.isValidDeviceType()`는 저장된 `cmc_device_type`이 실제 기기 기본 역할과 같거나 내부 `TemporaryData.test_flag`가 참이면 허용합니다. 실제 휴대폰 기본 역할은 계속 `pd`입니다. 예외가 꺼져 있으면 `DeviceTypeObserver`와 초기화·내부 API 검사가 불일치를 감지해 기본 역할로 복원합니다.

켜기는 삼성 정보 화면의 인증된 경로로 `test_flag=true`를 기록합니다. 끄기는 인증되지 않은 개발자 화면의 **켜기 콜백**이 `test_flag=false`를 기록하는 현재 버전 동작을 사용합니다. 일회성 인증 표식이 남아 있는 경우까지 고려해 화면을 두 번 새로 엽니다. 기본 OFF/Reset 동작은 호출하지 않습니다.

설치된 OneUIX와 upstream의 CMC 관련 훅은 `SimUtils.isChinaSIMActive()` 결과를 바꾸는 지역 제한 해제입니다. 이번 검증에서는 OneUIX CMC 옵션을 끈 상태에서 플래그 OFF 시 `sd`가 거부되고 ON 시 유지되는 것을 비교했습니다. OneUIX 소스나 삼성 APK 코드는 이 앱에 포함하지 않습니다.

## 빌드

```sh
python3 tools/build.py
```

JDK 17, Android SDK platform/build-tools 36을 사용합니다. `JAVA_HOME`, `ANDROID_HOME`, `CMC_SIGNING_HELPER`로 로컬 도구 경로를 지정할 수 있습니다. 서명은 기존 local signing helper helper의 비공개 설정을 외부에서 사용합니다. 설정이 없거나 인증서가 다르면 실패하며 새 키를 생성하지 않습니다.

필수 인증서 SHA-256:

```text
fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c
```

참고: [OneUIX](https://github.com/SoClear/OneUIX), [CMC 관련 upstream 코드](https://github.com/SoClear/OneUIX/blob/6ccbb71fe81464eaeb396de89160cd948b500d58/hook/src/main/java/io/github/soclear/oneuix/hook/MdecService.kt).
