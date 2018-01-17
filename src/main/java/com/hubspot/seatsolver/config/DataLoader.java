package com.hubspot.seatsolver.config;

import java.util.List;

import com.hubspot.seatsolver.model.SeatIF;
import com.hubspot.seatsolver.model.TeamIF;

public interface DataLoader {

  List<? extends SeatIF> getSeats();
  List<? extends TeamIF> getTeams();
}
