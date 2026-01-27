<#
Automates download/install of Android command-line tools and the Android 36.1 system image.
Run from PowerShell as an admin or a user with write access to the SDK folder.
Usage: powershell -ExecutionPolicy Bypass -File .\scripts\install-android-cmdline-tools.ps1
#>
param(
    [string]$SdkRoot = $Env:ANDROID_SDK_ROOT
)
if (-not $SdkRoot) {
    Write-Host "ANDROID_SDK_ROOT not set. Using default C:\Users\alpha\AppData\Local\Android\Sdk"
    $SdkRoot = 'C:\Users\alpha\AppData\Local\Android\Sdk'
}
$SdkRoot = (Resolve-Path $SdkRoot).ProviderPath
Write-Host "Using SDK root: $SdkRoot"

$cmdlineDest = Join-Path $SdkRoot 'cmdline-tools\latest'
if (-not (Test-Path $cmdlineDest)) { New-Item -ItemType Directory -Path $cmdlineDest -Force | Out-Null }

$tempZip = Join-Path $env:TEMP 'commandlinetools.zip'
$urls = @(
    'https://dl.google.com/android/repository/commandlinetools-win-9477386_latest.zip',
    'https://dl.google.com/android/repository/commandlinetools-win-8512546_latest.zip',
    'https://dl.google.com/android/repository/commandlinetools-win-latest.zip'
)
$downloaded = $false
foreach ($url in $urls) {
    try {
        Write-Host "Trying to download command-line tools from $url"
        Invoke-WebRequest -Uri $url -OutFile $tempZip -UseBasicParsing -ErrorAction Stop
        $downloaded = $true
        break
    } catch {
        Write-Host ("Failed to download from {0}: {1}" -f $url, $_.Exception.Message)
    }
}
if (-not $downloaded) {
    Write-Host "Could not download command-line tools. Please download manually from https://developer.android.com/studio#command-tools"; exit 1
}

# Extract
try {
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue (Join-Path $cmdlineDest '*')
    Expand-Archive -Path $tempZip -DestinationPath $cmdlineDest -Force
} catch {
    Write-Host ("Failed to extract archive: {0}" -f $_.Exception.Message); exit 1
}

# If a nested 'cmdline-tools' folder was created, move contents up
if (Test-Path (Join-Path $cmdlineDest 'cmdline-tools')) {
    Get-ChildItem -Path (Join-Path $cmdlineDest 'cmdline-tools') | Move-Item -Destination $cmdlineDest -Force
    Remove-Item -Recurse -Force (Join-Path $cmdlineDest 'cmdline-tools')
}

$sdkmanager = Join-Path $cmdlineDest 'bin\sdkmanager.bat'
if (-not (Test-Path $sdkmanager)) {
    Write-Host "sdkmanager.bat not found at $sdkmanager"; exit 1
}

Write-Host "Installing emulator and system image (this may take a while)..."
& $sdkmanager 'emulator' 'platform-tools' 'system-images;android-36.1;google_apis_playstore;x86_64' --sdk_root="$SdkRoot"

Write-Host "Accepting licenses..."
& $sdkmanager --licenses --sdk_root="$SdkRoot"

Write-Host "Done. System image installation attempted. Verify in SDK folder: $SdkRoot\system-images\android-36.1"
Write-Host "You can start the emulator with:"
Write-Host "    & \"$SdkRoot\emulator\emulator.exe\" -avd Medium_Phone_API_36.1 -gpu swiftshader_indirect"
Write-Host "Or recreate the AVD via Android Studio's AVD Manager if needed."
