package com.hubspot.seatsolver.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.hubspot.seatsolver.genetic.EmptySeatChromosome;
import com.hubspot.seatsolver.genetic.SeatGene;
import com.hubspot.seatsolver.genetic.TeamChromosome;
import com.hubspot.seatsolver.model.Point;
import com.hubspot.seatsolver.model.Seat;
import com.hubspot.seatsolver.model.Team;

import info.leadinglight.jdot.Graph;
import info.leadinglight.jdot.Node;
import info.leadinglight.jdot.enums.Color.SVG;
import info.leadinglight.jdot.enums.GraphType;
import info.leadinglight.jdot.enums.Shape;
import io.jenetics.Genotype;

public class GenotypeVisualizer {
  private static final HashFunction HASH = Hashing.goodFastHash(8);

  private static final SVG[] COLORS = EnumSet.of(SVG.blue, SVG.cornflowerblue, SVG.lightcoral, SVG.burlywood, SVG.chocolate, SVG.deeppink, SVG.blueviolet, SVG.green, SVG.greenyellow, SVG.darkgreen, SVG.indigo, SVG.dodgerblue, SVG.goldenrod, SVG.crimson, SVG.orangered, SVG.lightseagreen, SVG.lightsalmon, SVG.plum, SVG.gold, SVG.red, SVG.blue, SVG.yellow, SVG.limegreen).toArray(new SVG[]{});
  private static final int N_COLORS = COLORS.length - 1;

  private final Genotype<SeatGene> genotype;

  public GenotypeVisualizer(Genotype<SeatGene> genotype) {
    this.genotype = genotype;
  }

  public void outputGraphViz(String filename) throws IOException {
    Graph g = new Graph().setType(GraphType.graph);

    List<Node> nodes = genotype.stream()
        .filter(c -> !(c instanceof EmptySeatChromosome))
        .map(c -> ((TeamChromosome) c))
        .flatMap(this::chromosomeToNodes)
        .collect(Collectors.toList());

    List<Node> emptyNodes = genotype.stream()
        .filter(c -> c instanceof EmptySeatChromosome)
        .map(c -> ((EmptySeatChromosome) c))
        .flatMap(EmptySeatChromosome::stream)
        .map(SeatGene::getSeat)
        .map(seat -> {
          return seatToNode(seat, SVG.black);
        })
        .collect(Collectors.toList());

    g.addNodes(nodes.toArray(new Node[]{}));
    g.addNodes(emptyNodes.toArray(new Node[]{}));

    try (FileWriter fileWriter = new FileWriter(filename)) {
      fileWriter.write(g.toDot());
    }
  }

  private Stream<Node> chromosomeToNodes(TeamChromosome chromosome) {
    List<Node> results = Lists.newArrayList(chromosomeToNode(chromosome));

    results.addAll(chromosome.stream()
        .map(gene -> geneToNode(chromosome.getTeam().id(), gene))
        .collect(Collectors.toList())
    );

    return results.stream();
  }

  private Node chromosomeToNode(TeamChromosome chromosome) {
    Team team = chromosome.getTeam();
    SVG color = colorForTeam(chromosome.getTeam().id());

    Point center = chromosome.centroid();
    String pos = String.format("%d,%d", ((int) center.x()), ((int) center.y()));

    return new Node(chromosome.getTeam().id())
        .setFontColor(color)
        .setGroup(team.id())
        .setShape(Shape.none)
        .setColor(SVG.white)
        .setFontSize(8)
        .setPos(pos);
  }

  private Node geneToNode(String team, SeatGene gene) {
    SVG color = colorForTeam(team);
    Seat seat = gene.getSeat();

    return seatToNode(seat, color);
  }

  private Node seatToNode(Seat seat, SVG color) {
    String pos = String.format("%d,%d", ((int) seat.x()), ((int) seat.y()));

    Node node = new Node(seat.id())
        .setLabel("")
        .setWidth(.05)
        .setHeight(.1)
        .setColor(color)
        .setShape(Shape.rectangle)
        .setPos(pos);

    return node;
  }

  private SVG colorForTeam(String team) {
    int colorIdx = Math.abs(HASH.hashString(team, StandardCharsets.UTF_8).asInt()) % N_COLORS;
    return COLORS[colorIdx];
  }
}