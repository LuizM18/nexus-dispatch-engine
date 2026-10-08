# Implementação e revisão de Entregador

## Alteração

Implementado o código aprovado no chat: CategoriaCnh inclui AC; Entregador recebe veículo,
CNH, PBT em kg e capacidade de passageiros sem o motorista. Construtor e atualização
compartilham validação privada, antes de qualquer atribuição. Não há setters individuais.
Dados pessoais e disponibilidade continuam pendentes.

## Verificação executada

- Todos os arquivos Java de src e as verificações locais compilaram com Java 25,
  UTF-8 e -Xlint:all, sem erros ou avisos.
- work/EntregadorCheck.java: 1.313 verificações passaram, cobrindo todas as categorias
  e null, construtor e atualização, limites de 3.500/3.501 kg e 8/9 passageiros,
  valores ausentes/negativos, campos exclusivos de van, transições válidas e
  preservação dos quatro atributos após alterações recusadas.
- EnderecoCheck: 104 verificações passaram. BairroSetterCheck e BairroLetterCheck passaram.
- Maven não está disponível no PATH; os testes foram executados diretamente com javac/java.
  Os testes locais ainda não são executados automaticamente por Maven.

## Revisão de segurança e limites

Revisão estática dos arquivos Java existentes e do pom.xml, mais os testes locais.
Não foi uma auditoria de uma aplicação publicada: controllers, serviços e DAOs são
estruturas vazias; não há servidor, banco, autenticação ou fluxo web executável.
Não foram encontrados nesses arquivos comandos SQL, execução de processos ou
credenciais embutidas. Isso não certifica ausência de vulnerabilidades no sistema futuro.

1. **Características não verificadas:** VAN pressupõe carga, combustão e ausência de
   reboque. CARRO pressupõe enquadramento na categoria B. A entidade não tem campos
   para comprovar essas condições. Antes de cadastro real, usar dados documentais
   conferidos ou catálogo de veículos validado; o comentário não impõe essas restrições.
2. **Dados declarados:** peso e capacidade não são confrontados com documentação.
   Um PBT positivo irreal (inclusive Integer.MAX_VALUE) passa com C/AC se a lotação
   estiver no intervalo. Não se inventou limite máximo de van: falta uma fonte de
   dados/modelagem para verificar plausibilidade. CNH também não é autenticada,
   nem verificada quanto a vencimento ou situação.
3. **Autorização ausente:** a futura operação de alteração deve confirmar quem pode
   editar cada cadastro. Atributos privados não substituem autenticação/autorização.
4. **Entradas futuras:** controllers devem converter tipos, tratar erros sem expor
   detalhes internos, limitar tamanho das requisições e rejeitar campos desconhecidos.
   Valores inválidos de CNH não devem ser convertidos silenciosamente para null.
5. **Demais entidades:** Bairro e Endereco aceitam textos sem limites máximos e não
   fazem escape HTML. Isso não comprova XSS sem uma tela que os renderize, mas a futura
   interface precisa escapar a saída, e persistência deve usar SQL parametrizado.
6. **Concorrência:** preservar dados após erro não significa transação nem segurança
   entre threads. Não compartilhar uma instância mutável entre requisições concorrentes;
   definir controle transacional/versionamento ao implementar persistência.
7. **Entrega em andamento:** o bloqueio de troca de veículo depende de consulta pelo
   serviço e ainda não está implementado.

## Próximo passo

Definir e implementar os dados comuns de Usuario, depois disponibilidade e cadastro
de Entregador. Implementar conferência de características do veículo antes de integrar
o cadastro à interface. Configurar testes automatizados no Maven e, depois, serviços,
persistência e controle de acesso conforme o escopo acordado.

Esta alteração foi aplicada ao projeto local para estudo e desenvolvimento; não houve
publicação em servidor. As regras implementadas são o recorte aprovado no projeto,
não uma verificação integral de conformidade legal ou de documentação.
