# Sequencia de trabalho Java

03/11/2026. Ordem de dependencias, sem datas de entrega inventadas.
Referencia dos criterios: requisitos.md. UML aguarda acesso do usuario.

1. Concluir correspondencia das classes. Usuario/perfis/Lojista revisados na
   primeira etapa. Entregador/estado e visoes JDBC Loja/Produto/Oferta revisados
   na segunda etapa. Compra/Pedido/ItemPedido/OrdemEntrega revisados na terceira,
   com vinculos/totais conferidos e estados SQL preservados. Primeira rodada
   concluida; manter os limites dos modelos locais e integrar a leitura no menu.
2. Fechar matriz DAO: CRUD, comandos SQL, recursos, FKs e remocao segura.
3. Criar primeiro main/menu com adicionar/listar/buscar/remover no banco e
   demonstracao de heranca/polimorfismo. Evoluir junto aos services, sem esperar
   todos os services para iniciar a interface. Primeiro modulo concluido para
   produtos: ConsoleNexus/ProdutoService, com DELETE protegido e roteiro JDBC.
   Cadastros de cliente/lojista/entregador e listagem de IDs adicionados via
   CadastroService, com hash real e transacao unica usuario/complemento.
   Proximo: demais CRUDs e integracao de compra/entrega no roteiro.
4. Integrar fluxos Nexus necessarios a demonstracao: cadastros transacionais,
   compra e entrega. Distribuicao da taxa entre entregas ainda requer decisao;
   nao assumir repasse zero. Regras ficam nos services.
5. Conferir UML quando disponivel, comentarios, instrucoes e disponibilidade
   dos testes da entrega. PDF de estudo nao substitui comentario no fonte.
6. Validar com SQL do grupo no ambiente de demonstracao: CRUD, roteiro Nexus,
   persistencia apos reinicio, falhas relevantes e material Java para defesa.

Nao ampliar para web ou cobranca financeira real antes de fechar o minimo.
Concluir services nao encerra sozinho o projeto. Registrar evidencias de cada
etapa; compilacao isolada nao comprova atendimento funcional.
