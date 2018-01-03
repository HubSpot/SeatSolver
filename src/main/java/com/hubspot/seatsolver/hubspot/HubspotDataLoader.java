package com.hubspot.seatsolver.hubspot;

import java.io.FileReader;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.hubspot.seatsolver.config.DataLoader;
import com.hubspot.seatsolver.model.Adjacency;
import com.hubspot.seatsolver.model.Seat;
import com.hubspot.seatsolver.model.Team;

public class HubspotDataLoader implements DataLoader {
  private static final Logger LOG = LoggerFactory.getLogger(HubspotDataLoader.class);

  private final String filename;
  private final ObjectMapper objectMapper = new ObjectMapper()
      .registerModules(new GuavaModule(), new Jdk8Module());

  private List<Seat> seats;
  private List<Team> teams;

  public HubspotDataLoader(String filename) {
    this.filename = filename;
  }

  public void load() throws Exception {
    FileReader reader = new FileReader("data/x.json");

    HubspotData data = objectMapper.readValue(reader, HubspotData.class);

    LOG.info("Loaded {} teams", data.teamData().size());

    List<Seat> second = data.floorData().get("2c2").stream()
        .map(HubspotSeat::toSeat)
        .collect(Collectors.toList());

    List<Seat> first = data.floorData().get("2c1").stream()
        .map(HubspotSeat::toSeat)
        .map(seat -> {
          return Seat.builder().from(seat)
              .x(seat.x() + 1000)
              .y(seat.y() + 1000)
              .build();
        })
        .collect(Collectors.toList());

    List<Seat> dav = data.floorData().get("HP").stream()
        .map(HubspotSeat::toSeat)
        .map(seat -> {
          return Seat.builder().from(seat)
              .x(seat.x() + 10000)
              .y(seat.y() + 10000)
              .build();
        })
        .collect(Collectors.toList());

    second.addAll(first);
    second.addAll(dav);
    seats = second;

    teams = data.teamData().entrySet().stream()
        .map(entry -> {
              List<HubspotAdjacency> adjacencies = data.adjacency().getOrDefault(entry.getKey(), Collections.emptyList());

              double totalAdjacencyWeight = adjacencies.stream().mapToDouble(HubspotAdjacency::value).sum();
              List<Adjacency> wantsAdjacent = adjacencies.stream()
                  .map(a -> {
                    return Adjacency.builder()
                        .id(a.target())
                        .weight(a.value())
                        .build();
                  })
                  .collect(Collectors.toList());

              return Team.builder()
                  .id(entry.getKey())
                  .numMembers(entry.getValue().size())
                  .addAllWantsAdjacent(wantsAdjacent)
                  .build();
            }
        )
        .collect(Collectors.toList());
  }

  public List<Seat> getSeats() {
    return seats;
  }

  public List<Team> getTeams() {
    return teams;
  }
}
