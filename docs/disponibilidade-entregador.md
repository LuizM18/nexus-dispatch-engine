# Disponibilidade do entregador

Implementada em 15/10/2026. Cada Entregador comeca indisponivel (false).
isDisponivel consulta; ficarDisponivel atribui true; ficarIndisponivel atribui
false. Operacoes repetidas mantem o estado, sem alternar involuntariamente.
Alterar disponibilidade nao modifica dados pessoais, veiculo ou CNH.

O boolean representa a disponibilidade declarada. Nao comprova permissao,
ausencia de entregas em andamento ou elegibilidade territorial. Esses controles
pertencerao aos servicos. Ficar indisponivel nao cancela ordens existentes.
Nao ha persistencia nem coordenacao entre requisicoes concorrentes nesta etapa.

EntregadorCheck cobre estado inicial, ativacao, desativacao, repeticao,
independencia entre objetos e preservacao do estado nas atualizacoes de veiculo
validas e recusadas. Mantem os testes anteriores de CNH, peso e lotacao.
