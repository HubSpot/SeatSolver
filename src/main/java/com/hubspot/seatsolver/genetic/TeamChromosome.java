package com.hubspot.seatsolver.genetic;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AtomicDouble;
import com.hubspot.seatsolver.grid.SeatGrid;
import com.hubspot.seatsolver.model.Point;
import com.hubspot.seatsolver.model.PointBase;
import com.hubspot.seatsolver.model.SeatCore;
import com.hubspot.seatsolver.model.TeamCore;
import com.hubspot.seatsolver.utils.Pair;
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
  private final BitSet usedSeatIndexes;

  private AtomicReference<Pair<SeatCore, Integer>> furthestSeat = new AtomicReference<>();
  private AtomicReference<Point> centroid = new AtomicReference<>(null);
  private AtomicDouble meanWeightedSeatDist = new AtomicDouble(-1);
  private AtomicDouble teamDistanceCost = new AtomicDouble(-1);
  private AtomicDouble pinnedDistanceCost = new AtomicDouble(-1);
  private AtomicDouble squarenessScore = new AtomicDouble(-1);

  public TeamChromosome(ISeq<? extends EnumGene<SeatCore>> genes,
                        BitSet usedSeatIndexes,
                        SeatGrid seatGrid,
                        ISeq<SeatCore> allSeats,
                        Map<SeatCore, Integer> seatIndex,
                        TeamCore team) {
    super(genes);
    this.usedSeatIndexes = usedSeatIndexes;
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
    this(
        generateSeq(allSeats, usedSeatIndexes),
        usedSeatIndexes,
        grid,
        allSeats,
        seatIndex,
        team
    );
  }

  public SeatCore getSeat(int i) {
    return getGene(i).getAllele();
  }

  public boolean hasAnyAdjacent(SeatCore seat) {
    Set<SeatCore> adjacent = seatGrid.getAdjacent(seat);
    for (EnumGene<SeatCore> gene : this) {
      if (adjacent.contains(gene.getAllele())) {
        return true;
      }
    }
    return false;
  }

  public boolean hasTheRightNumberOfSeats() {
    return length() == team.numMembers();
  }

  public double squarenessScore() {
    if (length() <= 1) {
      return 1;
    }
    if (this.squarenessScore.get() >= 0) {
      return this.squarenessScore.get();
    }

    double nPairs = 0;
    double nAdjacent = 0;

    Set<SeatCore> seats = stream().map(EnumGene::getAllele).collect(Collectors.toSet());
    Set<SeatCore> remaining = new HashSet<>(seats);
    for (SeatCore seat : seats) {
      remaining.remove(seat);

      nPairs += seats.size();
      nAdjacent += Sets.intersection(seatGrid.getAdjacent(seat), remaining).size();
    }

    double result = nPairs / nAdjacent;
    this.squarenessScore.set(result);
    return result;
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

  public double calculatePinnedDistanceCost() {
    Double dist = pinnedDistanceCost.get();
    if (dist >= 0) {
      return dist;
    }
    if (team.wantsSeatProximity().isPresent()) {
      double maxDistance = 0;
      for (EnumGene<SeatCore> seatedSeat : this) {
        maxDistance = Math.max(maxDistance,
            PointUtils.distance(seatedSeat.getAllele(), team.wantsSeatProximity().get()));
      }
      double cost = Math.pow(maxDistance, 1.5) * 10;
      pinnedDistanceCost.set(cost);
      return cost;
    } else {
      pinnedDistanceCost.set(0);
      return 0;
    }
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
    BitSet usedSeatIndexes = new BitSet(this.usedSeatIndexes.size());
    for (EnumGene<SeatCore> gene : genes) {
      usedSeatIndexes.set(seatIndex.get(gene.getAllele()));
    }
    return new TeamChromosome(genes, usedSeatIndexes, seatGrid, allSeats, seatIndex, team);
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
  private static final int MAX_FILL_ATTEMPTS = 250;

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

  public boolean hasSeatIndex(int seatIndex) {
    return usedSeatIndexes.get(seatIndex);
  }

  public Pair<SeatCore, Integer> getFurthestSeat() {
    if (length() == 1) {
      SeatCore seat = getSeat(0);
      return Pair.of(seat, seatIndex.get(seat));
    }
    if (furthestSeat.get() != null) {
      return furthestSeat.get();
    }
    double maxDistance = 0;
    SeatCore worstSeat = null;
    for (int i = 0; i < length(); ++i) {
      double currentDistance = 0;
      SeatCore needleSeat = getSeat(i);
      for (int j = 0; j < length(); ++j) {
        if (i != j) {
          currentDistance += PointUtils.distance(needleSeat, getSeat(j));
        }
      }
      if (currentDistance > maxDistance) {
        maxDistance = currentDistance;
        worstSeat = needleSeat;
      }
    }
    Pair<SeatCore, Integer> result = Pair.of(
        worstSeat, seatIndex.get(worstSeat)
    );
    furthestSeat.set(result);
    return result;
  }

  public Optional<Pair<SeatCore, Integer>> findAdjacentSeat() {
    BitSet allAvailable = new BitSet(usedSeatIndexes.size());
    allAvailable.set(0, allAvailable.size());
    OptionalInt maybeIdx = selectAdjacent(
        allSeats,
        seatIndex,
        usedSeatIndexes,
        allAvailable,
        seatGrid
    );
    if (maybeIdx.isPresent()) {
      return Optional.of(Pair.of(allSeats.get(maybeIdx.getAsInt()), maybeIdx.getAsInt()));
    } else {
      return Optional.empty();
    }
  }

  public static BitSet selectSeatBlock(SeatGrid grid,
                                       ISeq<SeatCore> seats,
                                       Map<SeatCore, Integer> seatIndex,
                                       BitSet availableSeats,
                                       int size) {
    BitSet selected = selectBlockWithRetries(grid, seats, seatIndex, availableSeats, size);
    BitSet usedSeats = (BitSet) availableSeats.clone();
    if (selected.cardinality() < size) {
      LOG.debug("Could not find enough adjacent seats for team of size {}", size);

      // fill with random seats now, this will not be valid
      int fillAttempts = 0;
      while (selected.cardinality() < size && fillAttempts < MAX_FILL_ATTEMPTS) {
        fillAttempts++;

        int availableSeatIdx = getAvailableIndex(availableSeats);
        if (usedSeats.get(availableSeatIdx)) {
          continue;
        }
        availableSeats.clear(availableSeatIdx);
      }

      if (selected.cardinality() < size) {
        LOG.debug("Failed to create full seat block for {} seats", size);
      }
    }

    return selected;
  }

  private static int getAvailableIndex(BitSet availableSeats) {
    if (((double) availableSeats.cardinality()) / availableSeats.size() > 0.01) {
      while (true) {
        int offset = RandomRegistry.getRandom().nextInt(availableSeats.size());
        if (availableSeats.get(offset)) {
          return offset;
        }
      }
    } else {
      int availableSeatOffset = RandomRegistry.getRandom().nextInt(availableSeats.cardinality());
      return getValueOfIndex(availableSeats, availableSeatOffset);
    }
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

  private static BitSet selectBlockWithRetries(SeatGrid grid,
                                               ISeq<SeatCore> seats,
                                               Map<SeatCore, Integer> seatIndex,
                                               BitSet availableSeats,
                                               int size) {
    BitSet lastSelected = new BitSet(seats.size());

    for (int y = 0; y < MAX_BLOCK_ATTEMPTS; y++) {
      // pick a random starting point with a low connection count
      
      int randomSeatIndex = getAvailableIndex(availableSeats);

      BitSet selected = selectBlock(randomSeatIndex, grid, seats, seatIndex, availableSeats, size);

      if (selected.cardinality() == size) {
        return selected;
      }

      lastSelected = selected;
    }

    return lastSelected;
  }

  public static BitSet selectBlock(int startIdx,
                                   SeatGrid grid,
                                   ISeq<SeatCore> seats,
                                   Map<SeatCore, Integer> seatIndex,
                                   BitSet availableSeats,
                                   int size) {
    BitSet selected = new BitSet(seats.size());
    selected.set(startIdx);

    for (int x = 0; x < MAX_SEAT_ATTEMPTS; x++) {
      if (selected.cardinality() == size) {
        return selected;
      }

      OptionalInt adjacentIdx = selectAdjacent(seats, seatIndex, selected, availableSeats, grid);
      if (!adjacentIdx.isPresent()) {
        break;
      }

      selected.set(adjacentIdx.getAsInt());
    }

    return selected;
  }

  public static OptionalInt selectAdjacent(ISeq<SeatCore> allSeats,
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


    //Point center = centroid(existing.iterator());
    double minDistance = Double.MAX_VALUE;
    int nearestSeat = -1;

    for (int i = adjacent.nextSetBit(0); i >= 0; i = adjacent.nextSetBit(i + 1)) {
      if (i == Integer.MAX_VALUE) {
        break;
      }
      double myDistance = 0;
      for (SeatCore existingSeat : existing) {
        myDistance = Math.max(PointUtils.distance(allSeats.get(i), existingSeat), myDistance);
      }
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
