# GitHub Pages KEY 계산기

`site/`는 별도 빌드나 npm 의존성이 없는 정적 HTML/CSS/JavaScript 계산기입니다. KEY는 입력칸의 현재 값으로만 처리하고 서버 전송·URL 반영·로컬 저장소/계산 이력·분석 도구를 사용하지 않습니다. CSP도 connect-src/form-action을 none으로 제한합니다. 페이지 파일을 불러오는 일반 호스팅 요청과 사용자가 직접 누르는 GitHub 링크는 별도입니다.

5자리 KEY의 대문자·공백 정리, UTC 기준 계산, 분 경계 자동 갱신, 복사 직전 재계산, KEY 변경 시 이전 결과 무효화, 입력 지우기와 KEY 안내를 제공합니다. 화면이 숨겨지면 타이머를 멈추고 돌아오면 현재 코드로 갱신합니다. 복사를 지원하지 않거나 거부하는 브라우저에서는 코드를 선택해 수동 복사를 안내합니다.

하단 가이드는 CMC 9.1.00.15의 인증된 개발자 화면 경로를 안내합니다. KEY 인증 → 테스트 사용 ON → Device Type 선택 → Apply and Reset → 재설정·기기 연결 순서입니다. 드롭다운 선택과 실제 역할 적용을 구분하고, 초기화·재부팅 및 연결 재설정 절차를 설명합니다.

## 검사와 로컬 실행

```sh
node --test tests/web-*.test.mjs
python3 -m http.server 8767 --bind 127.0.0.1 --directory site
```

http://localhost:8767/ 에서 사용합니다. ES module이므로 index.html 파일을 file://로 직접 열기보다 HTTP 서버를 사용합니다. 테스트는 앱의 CmcOtp.java로 생성한 136개 참조값과 비교하고, 해당 Java 소스 해시가 바뀌면 참조값 갱신을 요구합니다. 참조값 생성은 `python3 tools/generate-web-vectors.py`입니다.

## Pages 배포

`.github/workflows/pages.yml`은 main의 웹 파일이 바뀌거나 수동 실행할 때 계산 검사를 먼저 수행합니다. 저장소에서 Pages가 활성화되어 있으면 **site 폴더만** 배포합니다. 앱 소스·APK·기기 기록·서명 자료는 Pages 산출물에 포함하지 않습니다. Pages 미설정(HTTP 404)은 명시적 notice 후 배포만 건너뛰며, 다른 API 오류는 실패 처리합니다.

Pages 사용이 가능한 저장소/요금제에서 Settings → Pages → Source를 GitHub Actions로 설정하고 workflow를 실행합니다. 기본 프로젝트 주소는 https://haha5039.github.io/cmc-helper/ 입니다. 배포 상태는 저장소의 GitHub Actions에서 확인할 수 있습니다.
