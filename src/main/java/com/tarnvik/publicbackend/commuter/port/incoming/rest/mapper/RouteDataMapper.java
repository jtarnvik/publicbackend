package com.tarnvik.publicbackend.commuter.port.incoming.rest.mapper;

import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveStop;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveTrip;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveVehicle;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.RouteData;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.LiveStopResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.LiveTripResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.LiveVehicleResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.RouteDataResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Flattens the live route data onto the wire. The domain objects behind {@link RouteData} carry far more
 * than the view needs — a vehicle's trip alone reaches the whole route and every stop's parent station — so
 * this mapper picks out a deliberately small subset. Expect it to grow as the schematic is built.
 */
@Mapper(componentModel = "spring")
public interface RouteDataMapper {
  RouteDataResponse toResponse(RouteData routeData);

  /**
   * The variants on {@link LiveTrip} are left out on purpose — see {@link LiveTripResponse}. They are
   * unmapped source properties, which MapStruct is happy to ignore; only unmapped targets are reported.
   */
  @Mapping(target = "stops", source = "liveStops")
  LiveTripResponse toResponse(LiveTrip liveTrip);

  @Mapping(target = "vehicleId", source = "position.vehicleId")
  @Mapping(target = "lat", source = "position.lat")
  @Mapping(target = "lng", source = "position.lng")
  @Mapping(target = "bearing", source = "position.bearing")
  @Mapping(target = "timestamp", source = "position.timestamp")
  @Mapping(target = "tripId", source = "trip.tripId")
  @Mapping(target = "directionId", source = "trip.directionId")
  @Mapping(target = "segIdx", source = "location.segIdx")
  @Mapping(target = "segmentFraction", source = "location.t")
  @Mapping(target = "distanceMetres", source = "location.dist")
  LiveVehicleResponse toResponse(LiveVehicle liveVehicle);

  LiveStopResponse toResponse(LiveStop liveStop);
}
