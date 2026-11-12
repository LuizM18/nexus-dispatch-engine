# Organizacao - 20/10/2026

Reorganizacao da pasta local Nexus Dispatch, sem alteracao de regras de negocio.

| Origem | Destino |
|---|---|
| work/*Check.java e tests/manual/*Check.java | tests/manual/ |
| work/RunChecks.ps1 | scripts/RunChecks.ps1 |
| work/build_requirements.py | scripts/build_requirements.py |
| outputs/ | archive/documentos/ |
| Demais materiais de work/ | archive/apoio-local/ |
| README anterior | docs/historico/README-antes-organizacao-2026-10-20.md |

O executor de checks agora compila somente model e enums, que sao o escopo das verificacoes manuais. A compilacao da aplicacao inteira, incluindo JDBC e dotenv, continua sendo responsabilidade do Maven/VS Code.

O gerador de requisitos e historico (conteudo de 30/09/2026): nao representa automaticamente as regras atuais. Sua saida agora vai para target/documentos, sem sobrescrever o PDF preservado.

Nenhum arquivo foi apagado. Credenciais permaneceram no .env original. Os caches, imagens de revisao, logs e auxiliares antigos foram preservados em archive/apoio-local e excluidos do versionamento futuro.

As verificacoes Java continuam com metodo main; nao foram convertidas em JUnit. A organizacao nao implementa DAOs nem cria o banco.
