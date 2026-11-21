param([Parameter(Mandatory)][string]$UnsignedApk,[Parameter(Mandatory)][string]$OutputApk,[switch]$CreateIdentity)
$ErrorActionPreference='Stop'
if (!(Test-Path -LiteralPath $UnsignedApk -PathType Leaf) -or (Test-Path -LiteralPath $OutputApk)){throw 'Input missing or output already exists'}
$dir=Join-Path $env:LOCALAPPDATA 'Silora/Signing'
$ks=Join-Path $dir 'silora-release.p12'
$wrapped=Join-Path $dir 'signing-password.dpapi'
$signer=Join-Path $env:ANDROID_HOME 'build-tools/35.0.0/apksigner.bat'
if (!(Test-Path -LiteralPath $signer)){throw 'Build tools 35.0.0 required'}
try {
    if($CreateIdentity){
        if ((Test-Path -LiteralPath $ks) -or (Test-Path -LiteralPath $wrapped)){throw 'Identity already exists; refusing to overwrite'}
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        $random=[byte[]]::new(48)
        $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
        try{$rng.GetBytes($random)}finally{$rng.Dispose()}
        $env:SILORA_SIGN_ONCE=[Convert]::ToBase64String($random)
        $secure=ConvertTo-SecureString -String $env:SILORA_SIGN_ONCE -AsPlainText -Force
        ConvertFrom-SecureString $secure | Set-Content -LiteralPath $wrapped
        & (Join-Path $env:JAVA_HOME 'bin/keytool.exe') -genkeypair -storetype PKCS12 -keystore $ks -alias silora-release -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 12000 -dname 'CN=Silora Release' -storepass:env SILORA_SIGN_ONCE -noprompt
        if($LASTEXITCODE -ne 0){throw 'Identity generation failed'}
    }else{
        $secure=Get-Content -LiteralPath $wrapped -Raw | ForEach-Object { ConvertTo-SecureString $_.Trim() }
        $env:SILORA_SIGN_ONCE=[Net.NetworkCredential]::new('', $secure).Password
    }
    & $signer sign --ks $ks --ks-key-alias silora-release --ks-pass env:SILORA_SIGN_ONCE --v4-signing-enabled false --out $OutputApk $UnsignedApk
    if($LASTEXITCODE -ne 0){throw 'APK signing failed'}
    & $signer verify --verbose --print-certs $OutputApk
    if($LASTEXITCODE -ne 0){throw 'APK verification failed'}
}finally{
    Remove-Item Env:SILORA_SIGN_ONCE -ErrorAction SilentlyContinue
    if($secure){$secure.Dispose()}
}
