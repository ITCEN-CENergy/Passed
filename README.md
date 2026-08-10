# Passed

## AI 서비스 실행 방법

`passed-ai`는 FastAPI 기반 AI 서비스이며 Python 패키지와 가상환경은
[`uv`](https://docs.astral.sh/uv/)로 관리합니다.

### 1. uv 설치

Windows PowerShell:

```powershell
powershell -ExecutionPolicy ByPass -c "irm https://astral.sh/uv/install.ps1 | iex"
```

macOS/Linux:

```bash
curl -LsSf https://astral.sh/uv/install.sh | sh
```

설치 후 새 터미널을 열고 다음 명령으로 확인합니다.

```bash
uv --version
```

### 2. 프로젝트 준비

저장소 루트에서 AI 서비스 디렉터리로 이동한 뒤 의존성을 설치합니다.

```bash
cd passed-ai
uv sync
```

`uv sync`는 `uv.lock`에 맞춰 `.venv` 가상환경을 자동으로 만들고 필요한
패키지를 설치합니다. 가상환경을 직접 활성화하지 않아도 됩니다.

> 이 프로젝트는 `pyproject.toml` 기준 Python 3.14 이상을 사용합니다.

### 3. 개발 서버 실행

FastAPI 진입 파일은 `app/main.py`이며 다음 명령으로 실행합니다.

```bash
uv run fastapi dev app/main.py
```

서버가 실행되면 다음 주소를 사용할 수 있습니다.

- API: <http://127.0.0.1:8000>
- Swagger 문서: <http://127.0.0.1:8000/docs>
- ReDoc 문서: <http://127.0.0.1:8000/redoc>

FastAPI 진입점은 `passed-ai/app/main.py`로 단일화되어 있습니다.

### 자주 사용하는 uv 명령

```bash
uv add 패키지명       # 패키지 추가
uv remove 패키지명    # 패키지 제거
uv sync               # uv.lock 기준으로 환경 동기화
uv run python app/main.py # 가상환경에서 FastAPI 진입 파일 실행
```
