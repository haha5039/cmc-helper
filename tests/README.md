`python3 tools/check-host.py`는 JDK 17에서 생산 코드와 테스트용 어댑터를 임시 폴더에 컴파일합니다. org.json은 tests/dependencies.json의 해시로 확인하며 APK에 추가하지 않습니다.

순수 Java 검사 외에 실제 RoleStore, ShizukuSession, CmcUserService를 tests/host의 작은 Android/Shizuku 어댑터로 실행합니다. tests/fixtures/settings는 이 검사 프로세스의 PATH에만 추가되는 가짜 실행 파일이며 임시 상태·이벤트 기록만 다룹니다. 실제 ADB·기기·settings·Shizuku 서버에 접근하지 않습니다.

실제 저널 직렬화와 파일 쓰기, 롤백/외부 변경 보호, 명령 생성/프로세스 실행, 권한 응답·타임아웃 순서를 검사합니다. 가짜 시계와 Binder/설정/파일 어댑터는 Android 런타임의 타이밍·AtomicFile 장애 의미·네이티브 화면 자동화를 검증하지 않습니다. 테스트 소스와 어댑터는 앱 빌드 대상이 아닙니다.
