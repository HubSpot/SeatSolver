package com.hubspot.seatsolver.config;

import java.util.List;

import com.hubspot.seatsolver.model.Seat;
import com.hubspot.seatsolver.model.Team;

public interface DataLoader {

  List<Seat> getSeats();
  List<Team> getTeams();
}
