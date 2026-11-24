# Локальный запуск всех проверок без mise. Повторяет задачи из mise.toml:
# contract -> generate -> lint -> typecheck -> test -> e2e.
# Нужны установленные: Node.js (LTS), JDK 21 (JAVA_HOME или java в PATH).
# Запуск из корня репозитория: powershell -ExecutionPolicy Bypass -File scripts\check.ps1

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')

# Подхватываем PATH из реестра: после установки Node/JDK открытый терминал может его не знать
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')

# Локальные обходы для корпоративного/антивирусного TLS-перехвата.
# В CI и на чистых машинах они не нужны, поэтому ставим только если переменные не заданы.
if (-not $env:NODE_OPTIONS) { $env:NODE_OPTIONS = '--use-system-ca' }
if (-not $env:MAVEN_OPTS) { $env:MAVEN_OPTS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT' }

$steps = @(
    @{ Name = 'contract';  Dir = 'contract';  Run = { npm ci --silent; npx tsp compile . } },
    @{ Name = 'generate';  Dir = 'frontend';  Run = { npm ci --silent; npm run generate:api } },
    @{ Name = 'spotless';  Dir = 'backend';   Run = { ./mvnw -B -q spotless:check } },
    @{ Name = 'oxlint';    Dir = 'frontend';  Run = { npm run lint } },
    @{ Name = 'typecheck'; Dir = 'frontend';  Run = { npm run build } },
    @{ Name = 'test-frontend'; Dir = 'frontend'; Run = { npm test } },
    @{ Name = 'test';      Dir = 'backend';   Run = { ./mvnw -B -q test } },
    # Playwright сам поднимает бэкенд (из каталога backend запускается mvnw.cmd) и фронтенд
    @{ Name = 'e2e';       Dir = 'e2e';       Run = { $env:E2E_MVN = '.\mvnw.cmd'; npm ci --silent; npx playwright install chromium; npx playwright test } }
)

foreach ($step in $steps) {
    Write-Host "== $($step.Name)" -ForegroundColor Cyan
    Push-Location (Join-Path $root $step.Dir)
    try {
        & $step.Run
        if ($LASTEXITCODE -ne 0) {
            throw "Шаг '$($step.Name)' завершился с кодом $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

Write-Host 'Все проверки прошли.' -ForegroundColor Green
