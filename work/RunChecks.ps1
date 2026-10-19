# Executar a partir de qualquer pasta. Nao modifica fontes nem cria commits.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $projectRoot 'target/validation-integration'
$sources = @(Get-ChildItem (Join-Path $projectRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
$checks = @(Get-ChildItem $PSScriptRoot -Recurse -Filter '*Check.java' | ForEach-Object FullName)
New-Item -ItemType Directory -Force $outputDirectory | Out-Null
& javac -encoding UTF-8 -Xlint:all -d $outputDirectory $sources $checks
if ($LASTEXITCODE -ne 0) { throw 'Falha na compilacao.' }
$tests = @('CompraEntregaIntegrationCheck', 'PedidoCheck', 'ItemPedidoCheck',
    'OfertaProdutoCheck', 'ProdutoCheck', 'LojaCheck', 'ConsumidorCheck',
    'UsuarioCheck', 'EntregadorCheck', 'EnderecoCheck', 'BairroSetterCheck', 'BairroLetterCheck')
foreach ($test in $tests) {
    & java -cp $outputDirectory $test
    if ($LASTEXITCODE -ne 0) { throw "Falha em $test." }
}
