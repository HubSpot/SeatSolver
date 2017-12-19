package com.hubspot.seatsolver;

import java.util.Iterator;

import io.jenetics.AnyGene;
import io.jenetics.Chromosome;
import io.jenetics.util.ISeq;

public class TeamAssignmentChromosome implements Chromosome<AnyGene<Seat>> {

  @Override
  public Chromosome<AnyGene<Seat>> newInstance(ISeq<AnyGene<Seat>> genes) {
    return null;
  }

  @Override
  public AnyGene<Seat> getGene(int index) {
    return null;
  }

  @Override
  public int length() {
    return 0;
  }

  @Override
  public ISeq<AnyGene<Seat>> toSeq() {
    return null;
  }

  @Override
  public Chromosome<AnyGene<Seat>> newInstance() {
    return null;
  }

  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public Iterator<AnyGene<Seat>> iterator() {
    return null;
  }
}
