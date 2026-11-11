package com.nexusdispatch.service;

import com.nexusdispatch.enums.TipoVeiculo;
import java.math.BigDecimal;
import java.util.*;

/** Grafo dirigido imutavel; distancias e tempos sao dados cadastrados, nunca inventados. */
public final class MalhaEntrega {
    /** Rua dirigida; a volta exige outro trecho. */
    public record Trecho(String origem, String destino, BigDecimal km, BigDecimal minutos, Set<TipoVeiculo> veiculos) {
        public Trecho {
            texto(origem); texto(destino);
            if (km == null || minutos == null || km.signum() <= 0 || minutos.signum() <= 0)
                throw new IllegalArgumentException("Metricas devem ser positivas.");
            veiculos = Set.copyOf(veiculos);
            if (veiculos.isEmpty()) throw new IllegalArgumentException("Trecho sem veiculos.");
        }
    }
    /** Tempo e distancia do mesmo caminho. */
    public record Percurso(BigDecimal km, BigDecimal minutos) {
        public Percurso {
            if (km == null || minutos == null || km.signum() < 0 || minutos.signum() < 0)
                throw new IllegalArgumentException("Percurso invalido.");
        }
        // BigDecimal e imutavel: a soma cria um novo percurso, sem alterar os anteriores.
        public Percurso somar(Percurso outro) { return new Percurso(km.add(outro.km), minutos.add(outro.minutos)); }
    }
    private record Visita(String ponto, Percurso percurso) { }
    private final Map<String, List<Trecho>> saidas;
    /** Copia defensiva impede mudanca externa durante uma avaliacao. */
    public MalhaEntrega(List<Trecho> trechos) {
        // Lista de adjacencia: cada ponto guarda apenas os trechos que SAEM dele.
        // Um destino sem saidas tambem entra no mapa, pois continua sendo um ponto conhecido.
        Map<String, List<Trecho>> mapa = new HashMap<>();
        for (Trecho t : List.copyOf(trechos)) {
            mapa.computeIfAbsent(t.origem(), k -> new ArrayList<>()).add(t);
            mapa.computeIfAbsent(t.destino(), k -> new ArrayList<>());
        }
        Map<String, List<Trecho>> copia = new HashMap<>();
        mapa.forEach((p, ts) -> copia.put(p, List.copyOf(ts)));
        saidas = Map.copyOf(copia);
    }
    /** Dijkstra pelo menor tempo, desempate pela distancia, respeitando o veiculo. */
    public Percurso calcular(String origem, String destino, TipoVeiculo veiculo) {
        texto(origem); texto(destino); Objects.requireNonNull(veiculo);
        if (!saidas.containsKey(origem) || !saidas.containsKey(destino)) throw new IllegalStateException("Ponto ausente na malha.");
        // O custo e ordenado por tempo e, somente em empate, por quilometragem.
        // Nao somamos minutos com km: sao grandezas distintas. Os trechos positivos
        // permitem encerrar Dijkstra quando o destino sai da fila com o melhor custo.
        Comparator<Percurso> custo = Comparator.comparing(Percurso::minutos).thenComparing(Percurso::km);
        var fila = new PriorityQueue<Visita>(Comparator.comparing(Visita::percurso, custo).thenComparing(Visita::ponto));
        // A fila escolhe a proxima visita de menor custo; melhor guarda o menor
        // percurso ja descoberto para cada ponto. A fila pode conter visitas antigas
        // para o mesmo ponto, porque uma melhoria adiciona uma nova entrada.
        Map<String, Percurso> melhor = new HashMap<>();
        Percurso zero = new Percurso(BigDecimal.ZERO, BigDecimal.ZERO);
        melhor.put(origem, zero); fila.add(new Visita(origem, zero));
        while (!fila.isEmpty()) {
            Visita atual = fila.remove();
            // Ignora uma visita superada por um caminho descoberto depois dela.
            // Isso evita expandir novamente uma alternativa que ja sabemos ser pior.
            if (!atual.percurso().equals(melhor.get(atual.ponto()))) continue;
            if (atual.ponto().equals(destino)) return atual.percurso();
            for (Trecho t : saidas.get(atual.ponto())) {
                // Uma rua pode existir no grafo e ainda assim ser proibida para este veiculo.
                // Somamos tempo e distancia do MESMO caminho, preservando essa correspondencia.
                if (!t.veiculos().contains(veiculo)) continue;
                Percurso novo = atual.percurso().somar(new Percurso(t.km(), t.minutos()));
                Percurso anterior = melhor.get(t.destino());
                // Relaxar a aresta significa guardar a alternativa se ela chega pela primeira
                // vez ao destino do trecho ou melhora seu custo. Empate exato nao gera outra visita.
                if (anterior == null || custo.compare(novo, anterior) < 0) {
                    melhor.put(t.destino(), novo); fila.add(new Visita(t.destino(), novo));
                }
            }
        }
        // Fila esgotada significa que nenhum caminho permitido atingiu o destino.
        // Origem igual ao destino conhecido retorna o percurso zero antes deste ponto.
        throw new IllegalStateException("Sem caminho permitido para o veiculo.");
    }
    private static void texto(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("Ponto obrigatorio.");
    }
}
