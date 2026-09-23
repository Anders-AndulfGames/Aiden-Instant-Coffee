param([string]$AndroidPlayer = 'C:\Program Files\Unity\Hub\Editor\6000.5.10f1\Editor\Data\PlaybackEngines\AndroidPlayer')
$ErrorActionPreference = 'Stop'
$jdk = Join-Path $AndroidPlayer 'OpenJDK\bin'
$sdk = Join-Path $AndroidPlayer 'SDK'
$bt = Join-Path $sdk 'build-tools\36.0.0'
$platform = Join-Path $sdk 'platforms\android-34\android.jar'
$out = Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Force $out,"$out\gen","$out\classes","$out\dex" | Out-Null
function Run-Tool([string]$tool, [string[]]$arguments) {
    & $tool @arguments
    if ($LASTEXITCODE -ne 0) { throw "Build tool failed: $tool" }
}
Run-Tool "$bt\aapt2.exe" @('compile','--dir',"$PSScriptRoot\res",'-o',"$out\resources.zip")
Run-Tool "$bt\aapt2.exe" @('link','-o',"$out\unsigned.apk",'-I',$platform,'--manifest',"$PSScriptRoot\AndroidManifest.xml",'--java',"$out\gen",'-R',"$out\resources.zip",'--auto-add-overlay')
$sources = @(Get-ChildItem "$PSScriptRoot\src","$out\gen" -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
Run-Tool "$jdk\javac.exe" (@('-encoding','UTF-8','-source','8','-target','8','-classpath',$platform,'-d',"$out\classes") + $sources)
Run-Tool "$jdk\jar.exe" @('cf',"$out\classes.jar",'-C',"$out\classes",'.')
Run-Tool "$jdk\java.exe" @('-cp',"$bt\lib\d8.jar",'com.android.tools.r8.D8','--lib',$platform,'--min-api','26','--output',"$out\dex","$out\classes.jar")
Run-Tool "$jdk\jar.exe" @('uf',"$out\unsigned.apk",'-C',"$out\dex",'classes.dex')
Run-Tool "$bt\zipalign.exe" @('-f','4',"$out\unsigned.apk","$out\aligned.apk")
if (-not (Test-Path "$out\development.keystore")) {
    Run-Tool "$jdk\keytool.exe" @('-genkeypair','-keystore',"$out\development.keystore",'-storepass','android','-keypass','android','-alias','aiden-dev','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Aiden Personal Development')
}
Run-Tool "$jdk\java.exe" @('-jar',"$bt\lib\apksigner.jar",'sign','--ks',"$out\development.keystore",'--ks-pass','pass:android','--key-pass','pass:android','--out',"$out\Aiden-Brew.apk","$out\aligned.apk")
Run-Tool "$jdk\java.exe" @('-jar',"$bt\lib\apksigner.jar",'verify',"$out\Aiden-Brew.apk")
Write-Host "Built $out\Aiden-Brew.apk"
