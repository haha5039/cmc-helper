# 호스트 검사

`python3 tools/check-host.py`는 JDK 17에서 생산 코드와 테스트용 어댑터를 임시 폴더에 컴파일합니다. org.json은 tests/dependencies.json의 해시로 확인하며 APK에 추가하지 않습니다.

순수 Java 검사 외에 실제 RoleStore, ShizukuSession, CmcUserService를 tests/host의 작은 Android/Shizuku 어댑터로 실행합니다. tests/fixtures/settings는 이 검사 프로세스의 PATH에만 추가되는 가짜 실행 파일이며 임시 상태·이벤트 기록만 다룹니다. 실제 ADB·기기·settings·Shizuku 서버에 접근하지 않습니다.

실제 저널 직렬화와 파일 쓰기, 롤백/외부 변경 보호, 명령 생성/프로세스 실행, 권한 응답·타임아웃 순서를 검사합니다. 가짜 시계와 Binder/설정/파일 어댑터는 Android 런타임의 타이밍·AtomicFile 장애 의미·네이티브 화면 자동화를 검증하지 않습니다. 테스트 소스와 어댑터는 앱 빌드 대상이 아닙니다.

## 격리 에뮬레이터 UI 검사

`tests/runtime`은 별도 계측 APK의 소스입니다. 앱의 resource-id를 사용해 화면 복원·KEY 입력·현재 코드·복사·오류/도움말·접근성 노드·실제 분 경계 갱신을 검사합니다. CMC 설정을 변경하지 않으며 실기기에서 실행하지 않습니다.

```sh
python3 tools/build-ui-tests.py --output build/cmc-ui-tests.apk
python3 tools/check-ui.py --serial emulator-5580 \
  --apk build/cmc-helper.apk \
  --test-apk build/cmc-ui-tests.apk \
  --evidence-dir build/ui-evidence
```

명시한 emulator-* 일련번호와 ro.kernel.qemu=1을 확인하고, 삼성 CMC나 Shizuku가 설치된 에뮬레이터는 거부합니다. 설치 전 앱과 테스트 APK의 지정 공통 인증서를 검사합니다. 테스트 APK·호스트 어댑터는 제품 APK에 포함하지 않습니다. UI 테스트는 격리 에뮬레이터에 두 APK를 설치하고 로그·합성 KEY 스크린샷을 수집합니다. 실제 TalkBack 음성, 삼성 화면, Shizuku 서버, 실제 통화/SMS 수신 검증은 별도입니다.


가이드 화면은 제품 HTML 절차를 오프라인으로 패키징해 표시합니다. UI 검사는 계산기에서 가이드를 열고 돌아온 뒤 입력·결과가 유지되는지 확인하며, 스크립트·네트워크·파일 접근이 꺼져 있는지 검사합니다. 초기화 대기 기록은 가짜 재부팅 횟수/역할을 사용한 합성 시나리오로 확인하고 원래 테스트 상태를 복구합니다.

실행 결과·로그·스크린샷은 버전 관리 밖에 보관합니다. 소스 저장소와 Release 패키지에 검증 결과 파일을 추가하지 않습니다.
