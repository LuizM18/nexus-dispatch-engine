# Consumidor com endereco obrigatorio

Implementado em 15/10/2026. Consumidor herda os dados de Usuario e exige um
Endereco no construtor. Possui getEndereco e setEndereco; null e recusado em
ambos os pontos de entrada, preservando o valor anterior na alteracao.

Endereco e uma referencia mutavel, nao uma copia. Alterar seus campos afeta
todos que compartilham o objeto. Substituir a referencia no consumidor nao
troca as referencias anteriormente obtidas por outros objetos.

Compras deverao preservar copias independentes dos dados do endereco, incluindo
dados de bairro/cidade necessarios ao historico. Guardar somente a referencia
ou uma copia que compartilha Bairro mutavel nao basta. Esse historico ainda
nao esta implementado. O cadastro possui um endereco por consumidor e permite
que pessoas diferentes morem no mesmo local.

Valida apenas presenca de Endereco. Sua classe valida os campos. Consultar bairro
cadastrado/atendido, autorizar alteracoes e persistir dependem de servico/banco.
Construtor exige nome, email, telefone, senhaHash e endereco; chamadas antigas
com quatro argumentos precisam ser adaptadas. UsuarioCheck foi atualizado.

ConsumidorCheck verifica construtor, getter, troca valida, rejeicao de null,
preservacao apos falha, validacao herdada e comportamento de referencias.
Testes locais sem banco; nao certificam seguranca nem historico de compras.

Resultado de 15/10/2026: 32 verificações de Consumidor e todos os testes locais anteriores passaram; compilação Java 25 com -Xlint:all sem erros ou avisos.
