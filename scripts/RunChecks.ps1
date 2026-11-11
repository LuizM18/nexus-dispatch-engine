# Atalho para a suíte Maven. Pode ser chamado de qualquer pasta.
# Stop transforma erros do PowerShell em falhas interrompendo o script.
# Programas externos usam LASTEXITCODE, conferido separadamente apos sua execucao.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
# Executa a partir da raiz para resolver recursos relativos, inclusive .env
# quando o programa usa ConexaoBD. finally restaura a pasta anterior do terminal.
Push-Location $projectRoot
try {
    # A fase test executa os testes de dominio via Surefire. Os testes *IT de
    # integracao ficam no perfil integracao e exigem a fase verify do Failsafe.
    & mvn test
    if ($LASTEXITCODE -ne 0) { throw 'Falha nos testes de domínio.' }
} finally {
    Pop-Location
}
