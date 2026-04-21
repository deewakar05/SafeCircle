package com.safecircle.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "locations")
public class Location {
    @Id
    private String id;

    private String userId;
    private String groupId;
    private String userName;

    private double lat;
    private double lng;

    // ONLINE | OFFLINE | NO_GPS
    private String status;

    private long timestamp;  // epoch millis
}
