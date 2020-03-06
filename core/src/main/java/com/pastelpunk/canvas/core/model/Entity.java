package com.pastelpunk.canvas.core.model;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
public class Entity {
    private String id;
    private Timestamp created;
    private Timestamp modified;
}
