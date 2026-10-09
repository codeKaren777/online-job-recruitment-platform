# Import literal KEY=VALUE entries, never evaluate .env as executable code.
$ErrorActionPreference = 'Stop'
$envPath = Join-Path $PSScriptRoot '..\.env'
foreach ($line in Get-Content -LiteralPath $envPath) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
    if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') {
        throw 'Invalid .env entry: expected literal KEY=VALUE (no quotes).'
    }
    [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
}
foreach ($name in @('DB_URL','POSTGRES_USER','POSTGRES_PASSWORD','JWT_SECRET_BASE64')) {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value) -or $value.StartsWith('REPLACE_')) {
        throw "Configure $name in .env before starting."
    }
}
