# Loja: regras atuais e proximas validacoes

A classe implementada pelo usuario foi preservada. A entidade valida nome
preenchido, CNPJ numerico de exatamente 14 digitos ASCII (0 a 9), endereco e
lojista nao nulos. Remove espacos externos do nome e CNPJ. Comeca fechada;
abrir/fechar mudam somente esse estado. Nome e endereco podem ser alterados.
Nao ha setters para CNPJ, responsavel ou ID.

## Pendencias na propria entidade ou em um validador dedicado

- Calculo dos digitos verificadores de CNPJ, adiado por decisao do grupo.
- Rejeicao de sequencias repetidas: ainda nao implementada; zeros passam no formato.
- Suporte alfanumerico: fora do escopo atual, que aceita apenas numeros.
- Limite de tamanho do nome, a alinhar com o banco.
- Regras adicionais para nomes (a regra atual exige apenas texto nao vazio).
- Estrategia para reconstruir objetos com ID atribuido pelo banco.
- Endereco e Lojista sao referencias mutaveis; final impede substituir o
  responsavel, mas nao torna os dados desse objeto imutaveis.

## Pendencias fora de Loja.java

Nao existe conexao, tabela nem script SQL neste repositorio para integrar a
unicidade agora. Criar duas instancias com o mesmo CNPJ ainda e possivel.
Uma colecao estatica nao substitui a restricao do banco.

Ao integrar a persistencia:
1. Armazenar CNPJ normalizado, sem mascara, como texto e obrigatorio.
2. Identificar e resolver registros duplicados preexistentes antes da migracao.
3. Adicionar uma restricao UNIQUE ao CNPJ completo na tabela real.
4. Implementar LojaDao.existePorCnpj com consulta parametrizada.
5. LojaService consulta antes de salvar para fornecer uma mensagem amigavel.
6. A insercao tambem precisa tratar especificamente violacao da unicidade de
   CNPJ, pois duas solicitacoes podem passar pela consulta simultaneamente.
   Outros erros de banco nao devem ser apresentados como CNPJ duplicado.
7. Testar duas insercoes concorrentes: apenas uma deve persistir. Fechar uma
   loja nao libera seu CNPJ para outro cadastro.

Tambem dependem de servico/banco: um lojista por loja e uma loja por lojista;
responsavel cadastrado e autorizado; bairro existente e atendido; permissoes
para editar/abrir/fechar; tratamento de mudanca de endereco com pedidos ativos.

CompraService devera conferir loja aberta, quantidade positiva e saldo suficiente por oferta, de forma transacional. Fechar nao cancela pedidos.
Consulta de existencia ou regularidade do CNPJ exige conferencia externa.

## Testes

work/LojaCheck.java cobre dados validos, zeros iniciais, espacos externos,
14 digitos, letras, mascara, espacos internos, digitos Unicode, referencias
obrigatorias, abertura/fechamento e preservacao de dados apos erros.
Inclui demonstracoes explicitas das limitacoes de unicidade e digitos.
Nao e teste de banco ou de autorizacao.
