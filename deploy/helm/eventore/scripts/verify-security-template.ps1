# Acceptance tests for FEAT-1.2 / FEAT-1.3 Helm security wiring.
$ErrorActionPreference = 'Stop'
$ChartDir = Join-Path $PSScriptRoot '..'
$Release = 'eventore-test'
$Failed = 0

function Pass($Label) { Write-Host "PASS: $Label" }
function Fail($Label) { Write-Host "FAIL: $Label"; $script:Failed = 1 }

if (-not (Get-Command helm -ErrorAction SilentlyContinue)) {
    Write-Host 'BLOCKED: helm CLI not installed'
    exit 2
}

$Values = Join-Path $ChartDir 'values.yaml'
foreach ($key in @('apiToken', 'apiTokenExistingSecret', 'apiTokenSecretKey', 'allowedOrigins')) {
    if (Select-String -Path $Values -Pattern "${key}:" -Quiet) {
        Pass "FEAT-1.2 AC-1: values.yaml has eventore.security.$key"
    } else {
        Fail "FEAT-1.2 AC-1: values.yaml missing eventore.security.$key"
    }
}

foreach ($overlay in @('values-admin.yaml', 'values-readonly.yaml')) {
    $path = Join-Path $ChartDir $overlay
    if (Select-String -Path $path -Pattern 'apiTokenExistingSecret' -Quiet) {
        Pass "FEAT-1.2 AC-4: $overlay documents security overlay"
    } else {
        Fail "FEAT-1.2 AC-4: $overlay missing security comments"
    }
}

$DefaultOut = helm template $Release $ChartDir 2>$null | Out-String
if ($DefaultOut -notmatch 'EVENTORE_SECURITY_API_TOKEN') { Pass 'FEAT-1.2 AC-6 compat: no EVENTORE_SECURITY_API_TOKEN when token unset' } else { Fail 'FEAT-1.2 AC-6 compat: unexpected EVENTORE_SECURITY_API_TOKEN' }
if ($DefaultOut -match '"allowed-origins"') { Pass 'FEAT-1.2 AC-6 compat: allowed-origins in SPRING_APPLICATION_JSON' } else { Fail 'FEAT-1.2 AC-6 compat: missing allowed-origins' }

$SecuredOut = helm template $Release $ChartDir -f (Join-Path $ChartDir 'values-admin.yaml') --set eventore.security.apiToken=test-token 2>$null | Out-String
if ($SecuredOut -match 'EVENTORE_SECURITY_API_TOKEN') { Pass 'FEAT-1.2 AC-7: EVENTORE_SECURITY_API_TOKEN in backend deployment' } else { Fail 'FEAT-1.2 AC-7: missing EVENTORE_SECURITY_API_TOKEN' }
if ($SecuredOut -match '"allowed-origins"') { Pass 'FEAT-1.2 AC-2: allowed-origins in SPRING_APPLICATION_JSON security block' } else { Fail 'FEAT-1.2 AC-2: missing allowed-origins in JSON' }
if ($SecuredOut -match 'kind: Secret' -and $SecuredOut -match 'test-token') { Pass 'FEAT-1.2 AC-3: api-auth Secret created with token' } else { Fail 'FEAT-1.2 AC-3: Secret/token wiring' }
if ($SecuredOut -notmatch '"api-token": "test-token"') { Pass 'FEAT-1.2 AC-3: api-token not in ConfigMap SPRING_APPLICATION_JSON' } else { Fail 'FEAT-1.2 AC-3: token leaked into ConfigMap JSON' }

$NotesTemplate = Get-Content (Join-Path $ChartDir 'templates/NOTES.txt') -Raw
if ($NotesTemplate -match 'Settings' -and $NotesTemplate -match '401' -and $NotesTemplate -match 'eventore.apiAuthEnabled') {
    Pass 'FEAT-1.2 AC-5: NOTES template documents Settings and 401 verification'
} else {
    Fail 'FEAT-1.2 AC-5: NOTES template content'
}

if ($SecuredOut -match '"api-token":\s*"test-token"') {
    Fail 'FEAT-1.2 AC-3: api-token must not be inlined in ConfigMap SPRING_APPLICATION_JSON'
} elseif ($SecuredOut -match 'EVENTORE_SECURITY_API_TOKEN') {
    Pass 'FEAT-1.2 AC-2: api-token wired via EVENTORE_SECURITY_API_TOKEN env + allowed-origins in JSON'
} else {
    Fail 'FEAT-1.2 AC-2: missing EVENTORE_SECURITY_API_TOKEN env when token configured'
}

$FrontendOut = helm template $Release $ChartDir --set frontend.env.apiToken=front-token 2>$null | Out-String
if ($FrontendOut -match 'apiToken: "front-token"') { Pass 'FEAT-1.3 AC-8: frontend-config.js includes apiToken when set' } else { Fail 'FEAT-1.3 AC-8: frontend apiToken injection' }
$FrontendDefault = helm template $Release $ChartDir 2>$null | Out-String
if ($FrontendDefault -notmatch 'apiToken:') { Pass 'FEAT-1.3 AC-8: frontend-config.js omits apiToken when unset' } else { Fail 'FEAT-1.3 AC-8: unexpected apiToken in default render' }

$FrontendSecretOut = helm template $Release $ChartDir --set frontend.env.apiTokenExistingSecret=eventore-frontend-token 2>$null | Out-String
if ($FrontendSecretOut -match 'inject-frontend-api-token' -and $FrontendSecretOut -match 'eventore-frontend-token' -and $FrontendSecretOut -match 'apiToken') {
    Pass 'FEAT-1.3 AC-8: frontend apiTokenExistingSecret renders via initContainer Secret reference'
} else {
    Fail 'FEAT-1.3 AC-8: frontend.env.apiTokenExistingSecret not wired in chart templates'
}

if ($Failed -ne 0) {
    Write-Host 'Helm security template verification: FAILED'
    exit 1
}
Write-Host 'Helm security template verification: ALL PASS'
exit 0
