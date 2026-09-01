$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$targetPath = [IO.Path]::GetFullPath((Join-Path $projectPath 'app/libs/glyph-matrix-sdk-2.0.aar'))
$expectedHash = '329393019DB5F0F987C6245855D13FA273D06756C68829CA0F6AE686BA336DA1'
if (Test-Path -LiteralPath $targetPath) {
    if ((Get-FileHash -LiteralPath $targetPath -Algorithm SHA256).Hash -eq $expectedHash) {
        Write-Output 'Official Glyph SDK already present; SHA-256 verified.'
        exit 0
    }
    throw 'A different SDK already exists. Review it manually; this script will not overwrite it.'
}
New-Item -ItemType Directory -Path (Split-Path -Parent $targetPath) -Force | Out-Null
$downloadPath = $targetPath + '.' + [Guid]::NewGuid().ToString('N') + '.download'
try {
    # Fixed upstream revision plus content checksum, not a silently updating binary.
    Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/Nothing-Developer-Programme/Glyph-Developer-Kit/8ee807a9312a640b0d43051450924e3446bc1d78/sdk/glyph-matrix-sdk-2.0.aar' -OutFile $downloadPath
    if ((Get-FileHash -LiteralPath $downloadPath -Algorithm SHA256).Hash -ne $expectedHash) { throw 'SDK checksum mismatch; nothing installed.' }
    Move-Item -LiteralPath $downloadPath -Destination $targetPath
    Write-Output 'Official Glyph SDK downloaded and verified. Its vendor terms apply; see NOTICE.md.'
} finally {
    if (Test-Path -LiteralPath $downloadPath) { Remove-Item -LiteralPath $downloadPath }
}
