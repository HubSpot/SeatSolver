package com.hubspot.seatsolver.genetic;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.AtomicDouble;
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.Point;
import com.hubspot.seatsolver.model.PointBase;
import com.hubspot.seatsolver.model.SeatCore;
import com.hubspot.seatsolver.model.TeamCore;
import com.hubspot.seatsolver.utils.PointUtils;

import io.jenetics.Chromosome;
import io.jenetics.EnumGene;
import io.jenetics.util.ISeq;
import io.jenetics.util.MSeq;
import io.jenetics.util.RandomRegistry;

public class TeamChromosome extends AbstractSeatChromosome {
  private static final Logger LOG = LoggerFactory.getLogger(TeamChromosome.class);

  private final SeatGrid seatGrid;
  private final ISeq<SeatCore> allSeats;
  private final Map<SeatCore, Integer> seatIndex;
  private final TeamCore team;

  private AtomicReference<Point> centroid = new AtomicReference<>(null);
  private AtomicDouble meanWeightedSeatDist = new AtomicDouble(-1);
  private AtomicDouble teamDistanceCost = new AtomicDouble(-1);

  public TeamChromosome(ISeq<? extends EnumGene<SeatCore>> genes,
                        SeatGrid seatGrid,
                        ISeq<SeatCore> allSeats,
                        Map<SeatCore, Integer> seatIndex,
                        TeamCore team) {
    super(genes);
    this.seatGrid = seatGrid;
    this.seatIndex = seatIndex;
    this.allSeats = allSeats;
    this.team = team;
  }

  public TeamChromosome(SeatGrid grid,
                        ISeq<SeatCore> allSeats,
                        Map<SeatCore, Integer> seatIndex,
                        BitSet usedSeatIndexes,
                        TeamCore team) {
    super(generateSeq(allSeats, usedSeatIndexes));
    this.seatGrid = grid;
    this.allSeats = allSeats;
    this.seatIndex = seatIndex;
    this.team = team;
  }

  private static ISeq<EnumGene<SeatCore>> generateSeq(ISeq<SeatCore> allSeats,
                                                      BitSet selectedSeats) {
    MSeq<EnumGene<SeatCore>> result = MSeq.ofLength(selectedSeats.cardinality());

    int idx = 0;
    for (int i = selectedSeats.nextSetBit(0); i >= 0; i = selectedSeats.nextSetBit(i + 1)) {
      if (i == Integer.MAX_VALUE) {
        break;
      }
      int current = idx++;
      result.set(current, EnumGene.<SeatCore>of(i, allSeats));
    }
    return result.toISeq();
  }

  public TeamCore getTeam() {
    return team;
  }

  public SeatGrid getSeatGrid() {
    return seatGrid;
  }


  public double calculateTeamDistanceCost() {
    Double dist = teamDistanceCost.get();
    if (dist >= 0) {
      return dist;
    }
    ISeq<EnumGene<SeatCore>> seatsSequence = toSeq();
    int seatLen = seatsSequence.size();
    double maxDistance = 0.;
    for (int i = 0; i < seatLen; ++i) {
      for (int j = i + 1; j < seatLen; ++j) {
        SeatCore seatA = seatsSequence.get(i).getAllele();
        SeatCore seatB = seatsSequence.get(j).getAllele();
        maxDistance = Math.max(maxDistance,
            PointUtils.distance(seatA, seatB));
      }
    }
    teamDistanceCost.set(maxDistance);
    return maxDistance;
  }

  public double meanWeightedSeatDistance() {
    Double dist = meanWeightedSeatDist.get();
    if (dist < 0) {
      dist = calculateMeanWeightedSeatDistance();
      meanWeightedSeatDist.set(dist);
    }

    return dist;
  }

  private double calculateMeanWeightedSeatDistance() {
    double totalDist = 0.;
    int pairs = 0;
    ISeq<EnumGene<SeatCore>> seatsSequence = toSeq();

    int seatLen = seatsSequence.size();
    for (int i = 0; i < seatLen; ++i) {
      for (int j = i + 1; j < seatLen; ++j) {
        SeatCore seatA = seatsSequence.get(i).getAllele();
        SeatCore seatB = seatsSequence.get(j).getAllele();
        totalDist += PointUtils.distance(seatA, seatB);
        pairs++;
      }
    }
    if (pairs == 0) {
      return 0;
    }

    return totalDist / pairs;
  }

  public Point centroid() {
    Point c = centroid.get();
    if (c == null) {
      c = centroid(toSeq().stream().map(EnumGene::getAllele).iterator());
      centroid.set(c);
    }
    return c;
  }

  @Override
  public AbstractSeatChromosome newSeatChromosome(ISeq<EnumGene<SeatCore>> genes) {
    return new TeamChromosome(genes, seatGrid, allSeats, seatIndex, team);
  }

  public TeamChromosome newTeamChromosome(ISeq<SeatCore> availability) {
    return new TeamChromosome(seatGrid, allSeats, seatIndex, selectSeatBlock(availability), team);
  }

  @Override
  public Chromosome<EnumGene<SeatCore>> newInstance(ISeq<EnumGene<SeatCore>> genes) {
    return newSeatChromosome(genes);
  }

  @Override
  public Chromosome<EnumGene<SeatCore>> newInstance() {
    BitSet selected = selectSeatBlock(
        seatGrid,
        allSeats,
        seatIndex,
        createAvailabilityBitSet(allSeats),
        length());
    return new TeamChromosome(seatGrid, allSeats, seatIndex, selected, team);
  }

  public static BitSet createAvailabilityBitSet(ISeq<SeatCore> allSeats) {
    BitSet availabilityBitSet = new BitSet(allSeats.size());
    availabilityBitSet.set(0, allSeats.size());
    return availabilityBitSet;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("team", team)
        .add("_genes", _genes)
        .add("_valid", _valid)
        .toString();
  }


  private static final int MAX_SEAT_ATTEMPTS = 100;
  private static final int MAX_BLOCK_ATTEMPTS = 100;
  private static final int MAX_FILL_ATTEMPTS = 100;

  private BitSet selectSeatBlock(ISeq<SeatCore> availableSeats) {
    Set<SeatCore> seatsSet = new HashSet<>(availableSeats.size());
    availableSeats.stream().forEach(seatsSet::add);
    BitSet availableSeatsBitSet = new BitSet(allSeats.size());
    for (int i = 0; i < allSeats.size(); ++i) {
      if (seatsSet.contains(allSeats.get(i))) {
        availableSeatsBitSet.set(i);
      }
    }
    return selectSeatBlock(
        seatGrid,
        allSeats,
        seatIndex,
        availableSeatsBitSet,
        length()
    );
  }

  public static BitSet selectSeatBlock(SeatGrid grid,
                                       ISeq<SeatCore> seats,
                                       Map<SeatCore, Integer> seatIndex,
                                       BitSet availableSeats,
                                       int size) {
    BitSet selected = selectBlock(grid, seats, seatIndex, availableSeats, size);
    BitSet usedSeats = (BitSet) availableSeats.clone();
    if (selected.cardinality() < size) {
      LOG.debug("Could not find enough adjacent seats for team of size {}", size);

      // fill with random seats now, this will not be valid
      int fillAttempts = 0;
      while (selected.size() < size && fillAttempts < MAX_FILL_ATTEMPTS) {
        fillAttempts++;

        int availableSeatIdx = getAvailableIndex(availableSeats);
        if (usedSeats.get(availableSeatIdx)) {
          continue;
        }
        availableSeats.clear(availableSeatIdx);
      }

      if (selected.size() < size) {
        LOG.debug("Failed to create full seat block for {} seats", size);
      }
    }

    return selected;
  }

  private static int getAvailableIndex(BitSet availableSeats) {
    int availableSeatOffset = RandomRegistry.getRandom().nextInt(availableSeats.cardinality());
    return getValueOfIndex(availableSeats, availableSeatOffset);
  }

  @VisibleForTesting
  static int getValueOfIndex(BitSet availableSeats, int idx) {
    int lastIndex = -1;
    int currentCount = 0;
    for (int i = availableSeats.nextSetBit(0); i >= 0; i = availableSeats.nextSetBit(i + 1)) {
      if (i == Integer.MAX_VALUE) {
        break;
      }
      if (currentCount++ > idx) {
        break;
      }
      lastIndex = i;
    }
    return lastIndex;
  }

  private static BitSet selectBlock(SeatGrid grid,
                                    ISeq<SeatCore> seats,
                                    Map<SeatCore, Integer> seatIndex,
                                    BitSet availableSeats,
                                    int size) {
    BitSet lastSelected = new BitSet(seats.size());

    for (int y = 0; y < MAX_BLOCK_ATTEMPTS; y++) {
      // pick a random starting point
      int randomSeatIndex = getAvailableIndex(availableSeats);

      BitSet selected = new BitSet(seats.size());
      selected.set(randomSeatIndex);

      for (int x = 0; x < MAX_SEAT_ATTEMPTS; x++) {
        if (selected.cardinality() == size) {
          break;
        }

        OptionalInt adjacentIdx = selectAdjacent(seats, seatIndex, selected, availableSeats, grid);
        if (!adjacentIdx.isPresent()) {
          break;
        }

        selected.set(adjacentIdx.getAsInt());
      }

      if (selected.cardinality() == size) {
        return selected;
      }

      lastSelected = selected;
    }

    return lastSelected;
  }

  private static OptionalInt selectAdjacent(ISeq<SeatCore> allSeats,
                                            Map<SeatCore, Integer> seatIndex,
                                            BitSet selected,
                                            BitSet availableSeats,
                                            SeatGrid grid) {
    Set<SeatCore> allAdjacent = new HashSet<>();
    List<SeatCore> existing = new ArrayList<>(selected.cardinality());

    for (int i = selected.nextSetBit(0); i >= 0; i = selected.nextSetBit(i + 1)) {
      if (i == Integer.MAX_VALUE) {
        break;
      }
      existing.add(allSeats.get(i));
      allAdjacent.addAll(grid.getAdjacent(allSeats.get(i)));
    }

    BitSet availableForSelection = (BitSet) availableSeats.clone();
    availableForSelection.andNot(selected);

    BitSet adjacent = new BitSet(allSeats.size());

    for (SeatCore seatCore : allAdjacent) {
      adjacent.set(seatIndex.get(seatCore));
    }

    adjacent.and(availableForSelection);


    Point center = centroid(existing.iterator());
    double minDistance = Double.MAX_VALUE;
    int nearestSeat = -1;

    for (int i = adjacent.nextSetBit(0); i >= 0; i = adjacent.nextSetBit(i + 1)) {
      if (i == Integer.MAX_VALUE) {
        break;
      }

      double myDistance = PointUtils.distance(allSeats.get(i), center);
      if (myDistance < minDistance) {
        nearestSeat = i;
        minDistance = myDistance;
      }
    }

    return nearestSeat < 0 ?
        OptionalInt.empty() : OptionalInt.of(nearestSeat);
  }

  @Override
  public String getIdentifier() {
    return team.id();
  }

  private static Point centroid(Iterator<? extends PointBase> points) {
    double sumX = 0;
    double sumY = 0;

    int count = 0;

    while (points.hasNext()) {
      PointBase point = points.next();
        sumX += point.x();
        sumY += point.y();
        ++count;
    }

    double x = sumX / count;
    double y = sumY / count;

    return Point.builder().x(x).y(y).build();
  }

}
