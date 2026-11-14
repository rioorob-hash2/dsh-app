# Сборка DshApp в этой песочнице (Windows).
# Использование:  powershell -ExecutionPolicy Bypass -File tools/build.ps1 apk|test|all
param([string]$task = "apk")

$ErrorActionPreference = "Stop"
$proj = "C:\Users\rioor\Documents\DeepSeek Harness\DshApp"
$ab   = "C:\Users\rioor\Documents\DeepSeek Harness\.android-build"

$env:JAVA_HOME         = "$ab\downloads\jdk17\jdk-17.0.21+9"
$env:ANDROID_HOME      = "$ab\android-sdk"
$env:ANDROID_USER_HOME = "$ab\android-user-home"
$env:GRADLE_USER_HOME  = "$ab\gradle-home"
$env:TEMP = "$proj\.tmp"; $env:TMP = "$proj\.tmp"
Remove-Item Env:\ANDROID_SDK_HOME -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$proj\.tmp" | Out-Null

$gradle = "$ab\downloads\gradle\gradle-8.13\bin\gradle.bat"
$targets = switch ($task) {
    "test" { @(":app:assembleDebug") }
    "all"  { @(":app:assembleDebug", ":app:assembleProd") }
    default { @(":app:assembleProd") }
}

& $gradle -p $proj @targets --console=plain
exit $LASTEXITCODE
