# Compila e executa o menu JDBC usando dependencias Maven ja instaladas.
param([string]$TaxaCompraPct, [switch]$CriarAdminInicial)

if ($CriarAdminInicial -and $PSBoundParameters.ContainsKey('TaxaCompraPct')) {
    throw 'Use -CriarAdminInicial em uma execucao separada.'
}

# Stop transforma erros do PowerShell em falhas interrompendo o script.
# Programas externos usam LASTEXITCODE, conferido separadamente apos sua execucao.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $projectRoot 'target/console'
$jars = @(
    (Join-Path $env:USERPROFILE '.m2/repository/io/github/cdimascio/dotenv-java/3.2.0/dotenv-java-3.2.0.jar'),
    (Join-Path $env:USERPROFILE '.m2/repository/com/mysql/mysql-connector-j/9.5.0/mysql-connector-j-9.5.0.jar')
)
foreach ($jar in $jars) {
    if (-not (Test-Path -LiteralPath $jar)) { throw "Dependencia ausente: $jar. Execute mvn compile primeiro." }
}
# O classpath informa onde Java encontra as bibliotecas. Ele nao baixa JARs;
# os caminhos precisam existir no repositorio Maven local.
$classpath = $jars -join ';'
$sources = @(Get-ChildItem (Join-Path $projectRoot 'src/main/java') -Recurse -Filter '*.java' | ForEach-Object FullName)
New-Item -ItemType Directory -Force $outputDirectory | Out-Null
# Compila os fontes antes de executar. UTF-8 preserva textos; -Xlint:all
# habilita avisos. Os .class ficam em target, separados dos fontes de estudo.
& javac -encoding UTF-8 -Xlint:all -Werror -cp $classpath -d $outputDirectory $sources
if ($LASTEXITCODE -ne 0) { throw 'Compilacao falhou.' }
# Executa a partir da raiz para resolver recursos relativos, inclusive .env
# quando o programa usa ConexaoBD. finally restaura a pasta anterior do terminal.
Push-Location $projectRoot
try {
    # Monta argumentos como elementos do array, sem concatenar um comando shell.
    # A opcao administrativa e exclusiva; o percentual habilita o checkout simulado.
    $consoleArgs = @()
    if ($CriarAdminInicial) { $consoleArgs += '--criar-admin-inicial' }
    elseif ($PSBoundParameters.ContainsKey('TaxaCompraPct')) { $consoleArgs += $TaxaCompraPct }
    & java -cp "$outputDirectory;$classpath" com.nexusdispatch.console.ConsoleNexus @consoleArgs
    if ($LASTEXITCODE -ne 0) { throw 'Console terminou com erro.' }
} finally { Pop-Location }
