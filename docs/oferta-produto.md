# OfertaProduto

Implementada em 12/10/2026. Produto obrigatorio e fixo; loja obtida pelo produto, sem parametro separado; ID inicialmente
nulo. Preco BigDecimal obrigatorio e nao negativo: zero permitido conforme o PDF.
Nao ha arredondamento nem limite de casas nesta entrega; essa politica permanece
pendente de confirmacao. Produto.java foi implementado com propriedade por loja; veja docs/produto.md.

Estoque inicial nao negativo. Reposicoes recebem quantidades positivas; somas
acima do limite de int sao recusadas antes de qualquer alteracao. Cada entrada
guarda quantidade, saldo anterior, saldo posterior e data/hora local. Estoque
inicial positivo tambem gera entrada; zero nao gera movimentacao.

EntradaEstoque e um record interno imutavel. O getter retorna copia nao
modificavel da lista; nao existe setter de estoque. A quantidade disponivel
para venda e igual ao estoque total, sem reserva minima. A consulta nao efetua baixa.

## Limites

Historico somente em memoria: nao sobrevive a reinicio nem e trilha de auditoria
persistente. Nao ha identificacao do autor da entrada ou protecao entre threads.
O servico devera autorizar reposicoes, e o banco devera gravar saldo e historico
na mesma transacao, controlar concorrencia e tratar tentativas repetidas.
Reconstituir do banco precisara de fluxo proprio para nao registrar novamente
o saldo carregado como uma entrada inicial.

Baixa por compra, loja aberta, unicidade produto/loja e permissoes ainda nao estao
implementadas. ItemPedido armazena o preco historico da compra.
Nao se pretende um historico completo de saidas nesta entrega; foi solicitado
historico de entradas. Estoque inicial e reposicoes nao comprovam estoque real.

## Verificacao

work/OfertaProdutoCheck.java testa preco nulo/negativo/zero, preservacao apos
erros, referencias obrigatorias, estoque inicial, entradas sucessivas,
quantidades invalidas, overflow, historico imutavel, data/hora e disponibilidade de todo o estoque.
Executavel diretamente, seguindo os testes locais existentes, sem banco.
