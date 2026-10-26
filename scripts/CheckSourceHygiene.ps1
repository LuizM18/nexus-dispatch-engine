# Verificacao local complementar. Nao substitui o linter/revisor da PR.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$errorsFound = @()
Get-ChildItem (Join-Path $projectRoot 'src') -Recurse -Filter '*.java' | ForEach-Object {
    # Detector conservador de placeholders do formato class Nome { }.
    # Records e subclasses com construtor nao sao classes vazias.
    $code = [regex]::Replace((Get-Content $_.FullName -Raw), '(?s)/\*.*?\*/|(?m)//[^\r\n]*', '')
    if ($code -match '(?s)\bclass\s+\w+\s*\{\s*\}\s*$') {
        $errorsFound += "Classe vazia: $($_.FullName)"
    }
}
$binaryExtensions = @('.pdf', '.docx', '.xlsx', '.pptx', '.class', '.jar', '.png', '.jpg', '.zip')
if (Test-Path -LiteralPath (Join-Path $projectRoot '.git')) {
    $tracked = @(& git -C $projectRoot ls-files)
    if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel conferir arquivos versionados.' }
    foreach ($file in $tracked) {
        if ([IO.Path]::GetExtension($file).ToLowerInvariant() -in $binaryExtensions) {
            $errorsFound += "Artefato binario versionado: $file"
        }
    }
} else {
    # Sem .git, verifica os diretorios de entrega; caches locais nao sao candidatos.
    foreach ($folder in @('src', 'tests', 'scripts', 'docs', 'database', 'outputs', 'work')) {
        $path = Join-Path $projectRoot $folder
        if (Test-Path -LiteralPath $path) {
            Get-ChildItem -LiteralPath $path -Recurse -File | Where-Object {
                $_.Extension.ToLowerInvariant() -in $binaryExtensions
            } | ForEach-Object { $errorsFound += "Artefato na arvore de entrega: $($_.FullName)" }
        }
    }
    Write-Output 'Pasta sem .git: verificacao de arquivos rastreados deve ser repetida na branch.'
}
if ($errorsFound.Count -gt 0) { throw ($errorsFound -join [Environment]::NewLine) }
Write-Output 'Higiene local passou: nenhum placeholder vazio ou binario nos caminhos verificados.'
