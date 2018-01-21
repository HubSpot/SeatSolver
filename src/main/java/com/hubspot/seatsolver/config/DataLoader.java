package com.hubspot.seatsolver.config;

import java.util.List;

import com.hubspot.seatsolver.model.SeatCore;
import com.hubspot.seatsolver.model.TeamCore;

public interface DataLoader {

  List<? extends SeatCore> getSeats();
  List<? extends TeamCore> getTeams();
}
