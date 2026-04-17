package com.solv.wefin.domain.trading.dart.entity;

import com.solv.wefin.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "dart_corp_code")
public class DartCorpCode extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dart_corp_code_id")
    private Long id;

    @Column(name = "stock_code", nullable = false, unique = true, length = 20)
    private String stockCode;

    @Column(name = "corp_code", nullable = false, length = 20)
    private String corpCode;

    @Column(name = "corp_name", nullable = false, length = 200)
    private String corpName;

    @Column(name = "modify_date", length = 8)
    private String modifyDate;

    public DartCorpCode(String stockCode, String corpCode, String corpName, String modifyDate) {
        this.stockCode = stockCode;
        this.corpCode = corpCode;
        this.corpName = corpName;
        this.modifyDate = modifyDate;
    }

    public void update(String corpCode, String corpName, String modifyDate) {
        this.corpCode = corpCode;
        this.corpName = corpName;
        this.modifyDate = modifyDate;
    }
}
