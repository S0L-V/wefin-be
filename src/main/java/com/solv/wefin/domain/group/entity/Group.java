package com.solv.wefin.domain.group.entity;

import com.solv.wefin.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Group extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long id;

    @Column(name = "group_name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_type", nullable = false, length = 20)
    private GroupType groupType;

    private Group(String name, GroupType groupType) {
        this.name = name;
        this.groupType = groupType;
    }

    public static Group createHomeGroup(String name) {
        return new Group(name, GroupType.HOME);
    }

    public static Group createSharedGroup(String name) {
        return new Group(name, GroupType.SHARED);
    }

    public boolean isHomeGroup() {
        return this.groupType == GroupType.HOME;
    }

    public boolean isSharedGroup() {
        return this.groupType == GroupType.SHARED;
    }
}