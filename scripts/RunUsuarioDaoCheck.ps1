# Teste JDBC opt-in. Usa somente uma tabela temporaria na conexao do teste.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $projectRoot 'target/validation-usuario-dao'
$jars = @(
    (Join-Path $env:USERPROFILE '.m2/repository/com/mysql/mysql-connector-j/9.5.0/mysql-connector-j-9.5.0.jar'),
    (Join-Path $env:USERPROFILE '.m2/repository/io/github/cdimascio/dotenv-java/3.2.0/dotenv-java-3.2.0.jar')
)
foreach ($jar in $jars) {
    if (!(Test-Path -LiteralPath $jar)) { throw "Dependencia ausente: $jar. Resolva as dependencias do pom.xml com Maven." }
}
$classpath = $jars -join [IO.Path]::PathSeparator
New-Item -ItemType Directory -Force $outputDirectory | Out-Null
$sources = @(Get-ChildItem (Join-Path $projectRoot 'src/main/java') -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources += Join-Path $projectRoot 'tests/manual/UsuarioDaoIntegrationCheck.java'
& javac -encoding UTF-8 -Xlint:all -cp $classpath -d $outputDirectory $sources
if ($LASTEXITCODE -ne 0) { throw 'Falha na compilacao.' }
Push-Location $projectRoot
try {
    & java -cp "$outputDirectory$([IO.Path]::PathSeparator)$classpath" UsuarioDaoIntegrationCheck
    if ($LASTEXITCODE -ne 0) { throw 'Falha no teste JDBC de UsuarioDao.' }
} finally {
    Pop-Location
}
