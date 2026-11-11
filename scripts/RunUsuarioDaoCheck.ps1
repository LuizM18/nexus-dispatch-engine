# Atalho para a suíte Maven. Pode ser chamado de qualquer pasta.
# Stop transforma erros do PowerShell em falhas interrompendo o script.
# Programas externos usam LASTEXITCODE, conferido separadamente apos sua execucao.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
# Executa a partir da raiz para resolver recursos relativos, inclusive .env
# quando o programa usa ConexaoBD. finally restaura a pasta anterior do terminal.
Push-Location $projectRoot
try {
    # Ativa Failsafe e seleciona a classe de integracao por it.test. verify
    # confere o resultado da execucao; o teste usa as variaveis NEXUS_TEST_DB_*.
    & mvn -Pintegracao '-Dit.test=UsuarioDaoIT' verify
    if ($LASTEXITCODE -ne 0) { throw 'Falha no teste JDBC de UsuarioDao.' }
} finally {
    Pop-Location
}
