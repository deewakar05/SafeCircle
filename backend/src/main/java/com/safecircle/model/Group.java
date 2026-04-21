package com.safecircle.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "groups")
public class Group {
    @Id
    private String id;

    private String name;

    private String adminId;

    @Builder.Default
    private List<String> memberIds = new ArrayList<>();

    @Indexed(unique = true)
    private String inviteCode;  // 6-char alphanumeric

    private double distanceThreshold;  // metres (default 300)
}
