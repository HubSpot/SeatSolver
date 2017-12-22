package com.hubspot.seatsolver.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.hubspot.seatsolver.genetic.SeatGene;
import com.hubspot.seatsolver.genetic.TeamChromosome;
import com.hubspot.seatsolver.model.Seat;

import info.leadinglight.jdot.Graph;
import info.leadinglight.jdot.Node;
import info.leadinglight.jdot.enums.Color.SVG;
import info.leadinglight.jdot.enums.GraphType;
import info.leadinglight.jdot.enums.Shape;
import io.jenetics.Genotype;

public class GenotypeVisualizer {
  private static final HashFunction HASH = Hashing.goodFastHash(8);
  private static final int N_COLORS = SVG.values().length - 1;

  private final Genotype<SeatGene> genotype;

  public GenotypeVisualizer(Genotype<SeatGene> genotype) {
    this.genotype = genotype;
  }

  public void outputGraphViz(String filename) throws IOException {
    Graph g = new Graph().setType(GraphType.graph);

    List<Node> nodes = genotype.stream()
        .map(c -> ((TeamChromosome) c))
        .flatMap(teamChromosome -> {
          return teamChromosome.stream().map(gene -> geneToNode(teamChromosome.getTeam().id(), gene));
        })
        .collect(Collectors.toList());

    g.addNodes(nodes.toArray(new Node[]{}));

    try (FileWriter fileWriter = new FileWriter(filename)) {
      fileWriter.write(g.toDot());
    }
  }

  private Node geneToNode(String team, SeatGene gene) {

    int colorIdx = Math.abs(HASH.hashString(team, StandardCharsets.UTF_8).asInt()) % N_COLORS;
    SVG color = SVG.values()[colorIdx];

    Seat seat = gene.getSeat();

    String pos = String.format("%d,%d", ((int) seat.x()), ((int) seat.y()));

    Node node = new Node(seat.id())
        .setLabel("")
        .setColor(color)
        .setShape(Shape.rectangle)
        .setPos(pos);

    return node;
  }
}
