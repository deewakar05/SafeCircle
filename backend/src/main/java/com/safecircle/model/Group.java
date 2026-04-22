package com.safecircle.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "groups")
public class Group {

    public static class Checkpoint {
        private double lat;
        private double lng;
        private String name;

        public Checkpoint() {}

        public Checkpoint(double lat, double lng, String name) {
            this.lat = lat;
            this.lng = lng;
            this.name = name;
        }

        public double getLat() { return lat; }
        public void setLat(double lat) { this.lat = lat; }

        public double getLng() { return lng; }
        public void setLng(double lng) { this.lng = lng; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @Id
    private String id;

    private String name;
    private String adminId;
    private List<String> memberIds = new ArrayList<>();

    @Indexed(unique = true)
    private String inviteCode;

    private double distanceThreshold;

    private List<Checkpoint> route = new ArrayList<>();

    public Group() {}

    public Group(String id, String name, String adminId, List<String> memberIds,
                 String inviteCode, double distanceThreshold, List<Checkpoint> route) {
        this.id = id; this.name = name; this.adminId = adminId;
        this.memberIds = memberIds != null ? memberIds : new ArrayList<>();
        this.inviteCode = inviteCode; this.distanceThreshold = distanceThreshold;
        this.route = route != null ? route : new ArrayList<>();
    }

    public String getId()             { return id; }
    public void   setId(String id)    { this.id = id; }

    public String getName()               { return name; }
    public void   setName(String name)    { this.name = name; }

    public String getAdminId()                  { return adminId; }
    public void   setAdminId(String adminId)    { this.adminId = adminId; }

    public List<String> getMemberIds()                      { return memberIds; }
    public void         setMemberIds(List<String> memberIds){ this.memberIds = memberIds; }

    public String getInviteCode()                   { return inviteCode; }
    public void   setInviteCode(String inviteCode)  { this.inviteCode = inviteCode; }

    public double getDistanceThreshold()                        { return distanceThreshold; }
    public void   setDistanceThreshold(double distanceThreshold){ this.distanceThreshold = distanceThreshold; }

    public List<Checkpoint> getRoute() { return route; }
    public void setRoute(List<Checkpoint> route) { this.route = route; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id, name, adminId, inviteCode;
        private List<String> memberIds = new ArrayList<>();
        private double distanceThreshold = 300;
        private List<Checkpoint> route = new ArrayList<>();

        public Builder id(String v)                       { id = v;                return this; }
        public Builder name(String v)                     { name = v;              return this; }
        public Builder adminId(String v)                  { adminId = v;           return this; }
        public Builder memberIds(List<String> v)          { memberIds = v;         return this; }
        public Builder inviteCode(String v)               { inviteCode = v;        return this; }
        public Builder distanceThreshold(double v)        { distanceThreshold = v; return this; }
        public Builder route(List<Checkpoint> v)          { route = v;             return this; }
        public Group build() {
            return new Group(id, name, adminId, memberIds, inviteCode, distanceThreshold, route);
        }
    }
}
