package com.dji.GSDemo.GoogleMap;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

public class Mission {
    private LatLng start;
    private List<LatLng> waypoints;

    public Mission(LatLng start, List<LatLng> waypoints) {
        this.start = start;
        this.waypoints = waypoints;
    }

    public LatLng getStart() {
        return start;
    }

    public void setStart(LatLng start) {
        this.start = start;
    }

    public List<LatLng> getWaypoints() {
        return waypoints;
    }

    public void setWaypoints(List<LatLng> waypoints) {
        this.waypoints = waypoints;
    }
}
