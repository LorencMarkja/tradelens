package io.github.lorencmarkja.tradelens.scenario;

import io.github.lorencmarkja.tradelens.economics.OfferSnapshot;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "offer")
public class Offer {

    @Id
    private Long id;
    private String lotCode;
    private String buyerName;

    private BigDecimal salePricePerMt;
    @Enumerated(EnumType.STRING)
    private ValueSource salePriceSource;

    private BigDecimal logisticsCostPerMt;
    @Enumerated(EnumType.STRING)
    private ValueSource logisticsSource;

    private Integer financingDays;
    @Enumerated(EnumType.STRING)
    private ValueSource financingDaysSource;

    private LocalDate validUntil;
    private LocalDate logisticsQuoteExpiry;

    @Version
    private Long version;

    protected Offer() {
    }

    public Offer(Long id, String lotCode, String buyerName) {
        this.id = id;
        this.lotCode = lotCode;
        this.buyerName = buyerName;
    }

    public OfferSnapshot snapshot() {
        return new OfferSnapshot(id, buyerName, salePricePerMt, logisticsCostPerMt, financingDays, validUntil,
                logisticsQuoteExpiry);
    }

    public void resetTo(BigDecimal salePrice, BigDecimal logistics, Integer days, LocalDate validUntil) {
        this.salePricePerMt = salePrice;
        this.salePriceSource = ValueSource.DEMO_DATA;
        this.logisticsCostPerMt = logistics;
        this.logisticsSource = ValueSource.DEMO_DATA;
        this.financingDays = days;
        this.financingDaysSource = ValueSource.DEMO_DATA;
        this.validUntil = validUntil;
        this.logisticsQuoteExpiry = null;
    }

    public void updateSalePrice(BigDecimal value, ValueSource source) {
        this.salePricePerMt = value;
        this.salePriceSource = source;
    }

    public void updateLogistics(BigDecimal value, ValueSource source) {
        this.logisticsCostPerMt = value;
        this.logisticsSource = source;
    }

    public void updateFinancingDays(Integer value, ValueSource source) {
        this.financingDays = value;
        this.financingDaysSource = source;
    }

    public void updateValidUntil(LocalDate value) {
        this.validUntil = value;
    }

    public void updateLogisticsQuoteExpiry(LocalDate value) {
        this.logisticsQuoteExpiry = value;
    }

    public Long getId() {
        return id;
    }

    public String getLotCode() {
        return lotCode;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public BigDecimal getSalePricePerMt() {
        return salePricePerMt;
    }

    public ValueSource getSalePriceSource() {
        return salePriceSource;
    }

    public BigDecimal getLogisticsCostPerMt() {
        return logisticsCostPerMt;
    }

    public ValueSource getLogisticsSource() {
        return logisticsSource;
    }

    public Integer getFinancingDays() {
        return financingDays;
    }

    public ValueSource getFinancingDaysSource() {
        return financingDaysSource;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public LocalDate getLogisticsQuoteExpiry() {
        return logisticsQuoteExpiry;
    }

    public Long getVersion() {
        return version;
    }
}
