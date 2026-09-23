param(
    [string]$AndroidPlayer = 'C:\Program Files\Unity\Hub\Editor\6000.5.10f1\Editor\Data\PlaybackEngines\AndroidPlayer',
    [Parameter(Mandatory=$true)][string]$Serial
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$out = Join-Path $root 'build\session-check'
$jdk = Join-Path $AndroidPlayer 'OpenJDK\bin'
$sdk = Join-Path $AndroidPlayer 'SDK'
$bt = Join-Path $sdk 'build-tools\36.0.0'
$platform = Join-Path $sdk 'platforms\android-34\android.jar'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
function Run-Tool([string]$tool, [string[]]$arguments) {
    & $tool @arguments
    if ($LASTEXITCODE -ne 0) { throw "Test tool failed: $tool" }
}
New-Item -ItemType Directory -Force $out,"$out\classes","$out\dex" | Out-Null
Run-Tool "$bt\aapt2.exe" @('link','-o',"$out\unsigned.apk",'-I',$platform,'--manifest',"$PSScriptRoot\android\AndroidManifest.xml")
Run-Tool "$jdk\javac.exe" @('-encoding','UTF-8','-source','8','-target','8','-classpath',"$platform;$root\build\classes",'-d',"$out\classes","$PSScriptRoot\android\SessionCheck.java")
Run-Tool "$jdk\jar.exe" @('cf',"$out\classes.jar",'-C',"$out\classes",'.')
Run-Tool "$jdk\java.exe" @('-cp',"$bt\lib\d8.jar",'com.android.tools.r8.D8','--lib',$platform,'--classpath',"$root\build\classes.jar",'--min-api','26','--output',"$out\dex","$out\classes.jar")
Run-Tool "$jdk\jar.exe" @('uf',"$out\unsigned.apk",'-C',"$out\dex",'classes.dex')
Run-Tool "$bt\zipalign.exe" @('-f','4',"$out\unsigned.apk","$out\aligned.apk")
Run-Tool "$jdk\java.exe" @('-jar',"$bt\lib\apksigner.jar",'sign','--ks',"$root\build\development.keystore",'--ks-pass','pass:android','--key-pass','pass:android','--out',"$out\checks.apk","$out\aligned.apk")
Run-Tool $adb @('-s',$Serial,'install','-r',"$root\build\Aiden-Brew.apk")
Run-Tool $adb @('-s',$Serial,'install','-r',"$out\checks.apk")
try {
    $result = & $adb -s $Serial shell am instrument -w com.andulf.aiden.tests/com.andulf.aiden.SessionCheck
    $result
    if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'PASS: 21 Android Keystore') { throw 'Saved-login tests failed' }
} finally {
    Run-Tool $adb @('-s',$Serial,'uninstall','com.andulf.aiden.tests')
}