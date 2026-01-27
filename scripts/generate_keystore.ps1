# Generates a release keystore interactively using keytool
# Usage: run from repository root in PowerShell

$keystoreDir = "keystore"
$keystorePath = Join-Path $keystoreDir "my-release-key.jks"

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    Write-Error "keytool not found on PATH. Install a JDK (OpenJDK or Oracle JDK) and ensure 'keytool' is available in PATH."
    exit 1
}

if (-not (Test-Path $keystoreDir)) {
    New-Item -ItemType Directory -Path $keystoreDir | Out-Null
}

Write-Host "About to generate a keystore at: $keystorePath"
Write-Host "You'll be prompted to enter a keystore password and certificate details."

# Interactive generation; user will be prompted for passwords and DNAME fields
& keytool -genkeypair -v -keystore $keystorePath -alias release -keyalg RSA -keysize 4096 -validity 9125

if ($LASTEXITCODE -eq 0) {
    Write-Host "Keystore generated at: $keystorePath"
    Write-Host "Next: copy keystore.properties.template to keystore.properties and update values to point to this keystore and passwords."
} else {
    Write-Error "keytool failed with exit code $LASTEXITCODE"
}
