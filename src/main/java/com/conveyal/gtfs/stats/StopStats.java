package com.conveyal.gtfs.stats;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Route;
import com.conveyal.gtfs.model.StopTime;
import com.conveyal.gtfs.model.Trip;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Created by landon on 9/2/16.
 */
public class StopStats {

    private GTFSFeed feed = null;
    private FeedStats stats = null;
    private RouteStats routeStats = null;

    public StopStats (GTFSFeed f) {
        feed = f;
        stats = new FeedStats(feed);
        routeStats = new RouteStats(feed);
    }

    public long getTripCountForDate (String stop_id, LocalDate date) {
        return getTripsForDate(stop_id, date).size();
    }

    /** Get list of trips for specified date of service */
    public List<Trip> getTripsForDate (String stop_id, LocalDate date) {
        List<String> tripIds = stats.getTripsForDate(date).stream()
                .map(trip -> trip.trip_id)
                .collect(Collectors.toList());

        return feed.getStopTimesForStop(stop_id).stream()
                .map(t -> feed.trips.get(t.b.a)) // map to trip ids
                .filter(t -> tripIds.contains(t.trip_id)) // filter by trip_id list for date
                .collect(Collectors.toList());
    }

    public int getAverageHeadwayForStop (String stop_id, LocalDate date, LocalTime from, LocalTime to) {
        List<Trip> tripsForStop = feed.getStopTimesForStop(stop_id).stream()
                .map(t -> feed.trips.get(t.b.a))
                .filter(trip -> feed.services.get(trip.service_id).activeOn(date))
                .collect(Collectors.toList());

        return getStopHeadwayForTrips(stop_id, tripsForStop, from, to);
    }

    /** Get the route headway for a given service date at a stop over a time window, in seconds */
    public Map<String, Integer> getRouteHeadwaysForStop (String stop_id, LocalDate date, LocalTime from, LocalTime to) {
        Map<String, Integer> routeHeadwayMap = new HashMap<>();
        List<Route> routes = feed.patterns.values().stream()
                .filter(p -> p.orderedStops.contains(stop_id))
                .map(p -> feed.routes.get(p.route_id))
                .collect(Collectors.toList());

        for (Route route : routes) {
            routeHeadwayMap.put(route.route_id, getHeadwayForStopByRoute(stop_id, route.route_id, date, from, to));
        }
        return routeHeadwayMap;
    }

    /** Get the average headway for a set of trips at a stop over a time window, in seconds */
    public int getStopHeadwayForTrips (String stop_id, List<Trip> trips, LocalTime from, LocalTime to) {
        TIntList timesAtStop = new TIntArrayList();

        for (Trip trip : trips) {
            StopTime st;
            try {
                // use interpolated times in case our common stop is not a time point
                st = StreamSupport.stream(feed.getInterpolatedStopTimesForTrip(trip.trip_id).spliterator(), false)
                        .filter(candidate -> candidate.stop_id.equals(stop_id))
                        .findFirst()
                        .orElse(null);
            } catch (GTFSFeed.FirstAndLastStopsDoNotHaveTimes e) {
                return -1;
            }

            // these trips are actually running on the next day, skip them
            if (st.departure_time > 86399) continue;

            LocalTime timeAtStop = LocalTime.ofSecondOfDay(st.departure_time);

            if (timeAtStop.isAfter(to) || timeAtStop.isBefore(from)) {
                continue;
            }

            timesAtStop.add(st.departure_time);
        }
        timesAtStop.sort();

        // convert to deltas
        TIntList deltas = new TIntArrayList();

        for (int i = 0; i < timesAtStop.size() - 1; i++) {
            int delta = timesAtStop.get(i + 1) - timesAtStop.get(i);

            if (delta > 60) deltas.add(delta);
        }

        if (deltas.isEmpty()) return -1;

        return deltas.sum() / deltas.size();
    }

    public int getHeadwayForStopByRoute (String stop_id, String route_id, LocalDate date, LocalTime from, LocalTime to) {

        List<Trip> tripsForStop = feed.getStopTimesForStop(stop_id).stream()
                .filter(t -> feed.trips.get(t.a).route_id.equals(route_id))
                .map(t -> feed.trips.get(t.b.a))
                .filter(trip -> feed.services.get(trip.service_id).activeOn(date))
                .collect(Collectors.toList());

        return getStopHeadwayForTrips(stop_id, tripsForStop, from, to);
    }
}
