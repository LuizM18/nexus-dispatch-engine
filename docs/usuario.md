# Usuario e perfis

Implementado em 08/10/2026. Usuario e uma superclasse abstrata com id, nome,
email, telefone opcional e senhaHash. O construtor protegido e chamado por
Consumidor, Lojista, Administrador e Entregador via super(...).

- ID inicia null; atribuicao pelo banco ainda pendente.
- Nome obrigatorio, com pelo menos uma letra Unicode; espacos externos removidos.
- Email obrigatorio, com verificacao basica de formato; espacos externos removidos.
- Telefone null ou em branco vira null; demais valores recebem strip().
- Hash obrigatorio e preservado literalmente. A entidade nao gera hashes, nao
  verifica seu algoritmo e nao recebe senha original como parte do contrato.
- Setters apenas para nome e telefone. Nome invalido preserva o valor anterior.
- Endereco nao pertence a Usuario. O endereco de coleta e CNPJ pertencerao a Loja.

## Limites e trabalho futuro

Unicidade global do email deve ser garantida no banco e tratada no servico.
Uma conta por perfil; perfis diferentes exigirao emails diferentes nesta etapa.
Verificacao por email foi adiada. Login, autorizacao, biblioteca de hash de senha,
troca de credenciais, persistencia, limites de tamanho e formato de telefone ainda
nao estao implementados. O getter de hash nao deve ser serializado em respostas,
exibido em telas ou registrado em logs; usar objetos de resposta sem esse campo.

## Verificacao

UsuarioCheck executa 180 verificacoes nos quatro perfis: construtores, dados
obrigatorios, normalizacao, preservacao apos erro e manutencao literal do hash.
Usa hashes ficticios, sem credenciais reais. EntregadorCheck foi adaptado para
o novo construtor e manteve suas 1.313 verificacoes. EnderecoCheck passou em 104
verificacoes; os dois testes de Bairro tambem passaram. Compilacao com Java 25
e -Xlint:all sem erros ou avisos. Os testes ainda sao locais, nao integrados ao Maven.
