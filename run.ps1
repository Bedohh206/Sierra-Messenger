#requires -version 5.1
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
chcp 65001 | Out-Null

#requires -version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "----------------------------------------"
Write-Host "        Sierra Pulse Smart Installer"
Write-Host "----------------------------------------"
Write-Host ""

# ======= CONFIG (edit if needed) =======
$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Package    = "com.btmessenger.app"
$Activity   = "com.btmessenger.app/.MainActivity"
$Gradlew    = Join-Path $ProjectDir "gradlew.bat"   # Windows
$ApkPath    = Join-Path $ProjectDir "app\build\outputs\apk\debug\app-debug.apk"

# ======= Helpers =======
function Exec {
        param([string]$Cmd)
        Write-Host ">> $Cmd"
        cmd.exe /c $Cmd
        if ($LASTEXITCODE -ne 0) { throw "Command failed ($LASTEXITCODE): $Cmd" }
}

function Get-DeviceSerial {
        # Returns first device with state "device"
        # Call external adb via cmd.exe to avoid invoking the local `Adb` function
        $lines = & cmd.exe /c 'adb devices' 2>&1
        $dev = $lines | Select-String -Pattern '^\S+\s+device$' | Select-Object -First 1
        if (-not $dev) { return $null }
        return ($dev.Line -split "\s+")[0]
}

function Adb {
        param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Args)
        if (-not $script:Serial) { throw "ADB serial not set." }

        # Resolve the external adb executable path to avoid calling this function recursively
        $adbCmd = (Get-Command adb -CommandType Application -ErrorAction SilentlyContinue).Source
        if (-not $adbCmd) { $adbCmd = 'adb' }

        & $adbCmd -s $script:Serial @Args
}

function Ensure-AdbDevice {
        Exec "adb kill-server"
        Exec "adb start-server"
        Start-Sleep -Milliseconds 400

        $script:Serial = Get-DeviceSerial
        if (-not $script:Serial) {
                Write-Host ""
                Write-Host "❌ No ADB device found. Check: USB cable, phone 'USB debugging' ON, allow RSA prompt."
                Write-Host ""
                Exec "adb devices"
                throw "No devices"
        }

        Write-Host "✅ Device: $script:Serial"
}

function Try-WirelessAdb {
        # Optional: attempts wireless if device supports it. If it fails, we continue with USB.
        Write-Host ""
        Write-Host "Trying wireless ADB (optional)..."

        try {
                # Get device IP from adb shell
                $ipRoute = (Adb shell "ip route 2>/dev/null") -join "`n"
                $m = [regex]::Match($ipRoute, "src\s+(\d+\.\d+\.\d+\.\d+)")
                if (-not $m.Success) {
                        Write-Host "⚠️ Could not detect device IP; staying on USB."
                        return
                }

                $ip = $m.Groups[1].Value
                Write-Host "Device IP: $ip"

                # Switch to tcpip mode and connect
                Adb tcpip 5555 | Out-Null
                Start-Sleep -Milliseconds 600

                & adb connect "$ip`:5555" | Out-Null
                Start-Sleep -Milliseconds 600

                # Confirm at least one device is connected now
                $connected = (& adb devices) -join "`n"
                if ($connected -match "$ip`:5555\s+device") {
                        Write-Host "✅ Wireless ADB connected: $ip:5555"
                        # Prefer wireless serial for subsequent commands
                        $script:Serial = "$ip`:5555"
                } else {
                        Write-Host "⚠️ Wireless connect failed; staying on USB."
                }
        }
        catch {
                Write-Host "⚠️ Wireless ADB attempt failed; staying on USB."
        }
}

function Uninstall-AppBestEffort {
        Write-Host ""
        Write-Host "Uninstalling $Package (best effort)..."

        # 1) adb uninstall
        $out = (& adb -s $script:Serial uninstall $Package 2>&1) -join "`n"
        Write-Host $out
        if ($out -match "Success") { return }

        # 2) pm uninstall
        $out2 = (Adb shell "pm uninstall $Package 2>&1") -join "`n"
        Write-Host $out2
        if ($out2 -match "Success") { return }

        # 3) pm uninstall --user 0
        $out3 = (Adb shell "pm uninstall --user 0 $Package 2>&1") -join "`n"
        Write-Host $out3
        if ($out3 -match "Success") { return }

        # If still failing: disable + clear. We'll overwrite on install (-r).
        Write-Host "⚠️ Uninstall failed; will try disable/clear + reinstall."
        Adb shell "pm disable-user --user 0 $Package" | Out-Null
        Adb shell "pm clear $Package" | Out-Null
}

function Build-And-Install {
        Write-Host ""
        Write-Host "Building debug APK..."
        Push-Location $ProjectDir
        try {
                Exec "`"$Gradlew`" clean :app:assembleDebug"
        } finally {
                Pop-Location
        }

        if (-not (Test-Path $ApkPath)) {
                throw "APK not found at: $ApkPath"
        }

        Write-Host ""
        Write-Host "Installing debug APK..."
        # Use -r to replace existing, -d to allow versionCode downgrade if needed
        $installOut = (& adb -s $script:Serial install -r -d $ApkPath 2>&1) -join "`n"
        Write-Host $installOut
        if ($installOut -notmatch "Success") {
                throw "Install failed."
        }
}
function Launch-And-Log {
    Write-Host ""
    Write-Host "Launching: $Activity"
    Adb shell "am start -n $Activity" | Out-Null
    Start-Sleep -Milliseconds 800

    # If app crashes instantly, PID will be empty
    $appPid = ((Adb shell "pidof -s $Package 2>/dev/null") -join "").Trim()

    if ([string]::IsNullOrWhiteSpace($appPid)) {
        Write-Host "❌ App not running (no PID). Showing crash lines:"

        $filtered = (Adb logcat -d -v time) | Select-String -Pattern "AndroidRuntime|FATAL EXCEPTION|$Package" | Select-Object -Last 80
        if ($filtered) {
            $filtered
        } else {
            Write-Host "(no filtered crash lines found) Showing recent logcat lines:"
            (Adb logcat -d -v time) | Select-Object -Last 200
        }
        return
    }

    Write-Host "✅ PID=$appPid"
    Write-Host ""
    Write-Host "Recent app log lines:"

    $filteredPid = (Adb logcat -d --pid $appPid -v time) | Select-String -Pattern "MainActivity|AppRoot|AndroidRuntime|FATAL EXCEPTION|UI_" | Select-Object -Last 80
    if ($filteredPid) {
        $filteredPid
    } else {
        Write-Host "(no filtered PID-specific lines) Showing recent PID logcat lines:"
        (Adb logcat -d --pid $appPid -v time) | Select-Object -Last 200
    }
}

# ======= MAIN =======
Ensure-AdbDevice
Try-WirelessAdb

Uninstall-AppBestEffort
Build-And-Install
Launch-And-Log

Write-Host ""
Write-Host "✅ Done."
    if ($out2 -match "Success") { return }

    # 3) pm uninstall --user 0
    $out3 = (Adb shell "pm uninstall --user 0 $Package 2>&1") -join "`n"
    Write-Host $out3
    if ($out3 -match "Success") { return }

    # If still failing: disable + clear. We'll overwrite on install (-r).
    Write-Host "⚠️ Uninstall failed; will try disable/clear + reinstall."
    Adb shell "pm disable-user --user 0 $Package" | Out-Null
    Adb shell "pm clear $Package" | Out-Null

function Build-And-Install {
    Write-Host ""
    Write-Host "Building debug APK..."
    Push-Location $ProjectDir
    try {
        Exec "`"$Gradlew`" clean :app:assembleDebug"
    } finally {
        Pop-Location
    }

    if (-not (Test-Path $ApkPath)) {
        throw "APK not found at: $ApkPath"
    }

    Write-Host ""
    Write-Host "Installing debug APK..."
    # Use -r to replace existing, -d to allow versionCode downgrade if needed
    $installOut = (& adb -s $script:Serial install -r -d $ApkPath 2>&1) -join "`n"
    Write-Host $installOut
    if ($installOut -notmatch "Success") {
        throw "Install failed."
    }
}

function Launch-And-Log {
    Write-Host ""
    Write-Host "Launching: $Activity"
    Adb shell "am start -n $Activity" | Out-Null
    Start-Sleep -Milliseconds 800

        # If app crashes instantly, PID will be empty
        $appPid = ((Adb shell "pidof -s $Package 2>/dev/null") -join "").Trim()

        if ([string]::IsNullOrWhiteSpace($appPid)) {
                Write-Host "❌ App not running (no PID). Showing crash lines:"
                Adb logcat -d -v time | Select-String -Pattern "AndroidRuntime|FATAL EXCEPTION|$Package" | Select-Object -Last 80
                return
        }

        Write-Host "✅ PID=$appPid"
        Write-Host ""
        Write-Host "Recent app log lines:"
        Adb logcat -d --pid $appPid -v time | Select-String -Pattern "MainActivity|AppRoot|AndroidRuntime|FATAL EXCEPTION|UI_" | Select-Object -Last 80
}

# ======= MAIN =======
Ensure-AdbDevice
Try-WirelessAdb

Uninstall-AppBestEffort
Build-And-Install
Launch-And-Log

Write-Host ""
Write-Host "✅ Done."