package com.safecircle.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "groups")
public class Group {
    @Id
    private String id;

    private String name;
    private String adminId;
    private List<String> memberIds = new ArrayList<>();

    @Indexed(unique = true)
    private String inviteCode;

    private double distanceThreshold;

    public Group() {}

    public Group(String id, String name, String adminId, List<String> memberIds,
                 String inviteCode, double distanceThreshold) {
        this.id = id; this.name = name; this.adminId = adminId;
        this.memberIds = memberIds != null ? memberIds : new ArrayList<>();
        this.inviteCode = inviteCode; this.distanceThreshold = distanceThreshold;
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

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id, name, adminId, inviteCode;
        private List<String> memberIds = new ArrayList<>();
        private double distanceThreshold = 300;

        public Builder id(String v)                       { id = v;                return this; }
        public Builder name(String v)                     { name = v;              return this; }
        public Builder adminId(String v)                  { adminId = v;           return this; }
        public Builder memberIds(List<String> v)          { memberIds = v;         return this; }
        public Builder inviteCode(String v)               { inviteCode = v;        return this; }
        public Builder distanceThreshold(double v)        { distanceThreshold = v; return this; }
        public Group build() {
            return new Group(id, name, adminId, memberIds, inviteCode, distanceThreshold);
        }
    }
}
