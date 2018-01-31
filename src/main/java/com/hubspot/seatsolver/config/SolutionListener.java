package com.hubspot.seatsolver.config;

import com.hubspot.seatsolver.model.PopulationResult;

public interface SolutionListener {
  void checkpointSolution(PopulationResult populationResult, long generation);
  void completeSolution(PopulationResult populationResult);
}
