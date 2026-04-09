package com.solv.wefin.domain.group.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long id;

    @Column(name = "group_name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_type", nullable = false, length = 20)
    private GroupType groupType;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    @Builder
    private Group(String name, GroupType groupType) {
        this.name = name;
        this.groupType = groupType != null ? groupType : GroupType.SHARED;
    }

    public static Group createHomeGroup(String name) {
        return Group.builder()
                .name(name)
                .groupType(GroupType.HOME)
                .build();
    }

    public static Group createSharedGroup(String name) {
        return Group.builder()
                .name(name)
                .groupType(GroupType.SHARED)
                .build();
    }

    public boolean isHomeGroup() {
        return this.groupType == GroupType.HOME;
    }

    public boolean isSharedGroup() {
        return this.groupType == GroupType.SHARED;
    }
}