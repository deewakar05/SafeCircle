package com.safecircle.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "locations")
@CompoundIndexes({
    @CompoundIndex(name = "group_idx",      def = "{'groupId': 1}"),
    @CompoundIndex(name = "user_group_idx", def = "{'userId': 1, 'groupId': 1}", unique = true),
})
public class Location {
    @Id
    private String id;

    private String userId;
    private String groupId;
    private String userName;
    private double lat;
    private double lng;
    private String status;
    private long   timestamp;
    private Double accuracy;  // GPS horizontal accuracy in metres (nullable)

    public Location() {}

    public Location(String id, String userId, String groupId, String userName,
                    double lat, double lng, String status, long timestamp) {
        this.id = id; this.userId = userId; this.groupId = groupId;
        this.userName = userName; this.lat = lat; this.lng = lng;
        this.status = status; this.timestamp = timestamp;
    }

    public String getId()           { return id; }
    public void   setId(String id)  { this.id = id; }

    public String getUserId()               { return userId; }
    public void   setUserId(String userId)  { this.userId = userId; }

    public String getGroupId()                { return groupId; }
    public void   setGroupId(String groupId)  { this.groupId = groupId; }

    public String getUserName()                 { return userName; }
    public void   setUserName(String userName)  { this.userName = userName; }

    public double getLat()          { return lat; }
    public void   setLat(double lat){ this.lat = lat; }

    public double getLng()          { return lng; }
    public void   setLng(double lng){ this.lng = lng; }

    public String getStatus()               { return status; }
    public void   setStatus(String status)  { this.status = status; }

    public long getTimestamp()                { return timestamp; }
    public void setTimestamp(long timestamp)  { this.timestamp = timestamp; }

    public Double getAccuracy()                 { return accuracy; }
    public void   setAccuracy(Double accuracy)  { this.accuracy = accuracy; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id, userId, groupId, userName, status;
        private double lat, lng;
        private long   timestamp;
        private Double accuracy;

        public Builder id(String v)         { id = v;        return this; }
        public Builder userId(String v)     { userId = v;    return this; }
        public Builder groupId(String v)    { groupId = v;   return this; }
        public Builder userName(String v)   { userName = v;  return this; }
        public Builder lat(double v)        { lat = v;       return this; }
        public Builder lng(double v)        { lng = v;       return this; }
        public Builder status(String v)     { status = v;    return this; }
        public Builder timestamp(long v)    { timestamp = v; return this; }
        public Builder accuracy(Double v)   { accuracy = v;  return this; }
        public Location build() {
            Location l = new Location(id, userId, groupId, userName, lat, lng, status, timestamp);
            l.setAccuracy(accuracy);
            return l;
        }
    }
}
