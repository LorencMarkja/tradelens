package io.github.lorencmarkja.tradelens.scenario;

import io.github.lorencmarkja.tradelens.economics.LotTerms;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "lot")
public class Lot {

    @Id
    private String code;
    private String product;
    @Column(name = "quantity_mt")
    private BigDecimal quantityMt;
    private String location;
    private String currency;
    private BigDecimal purchaseCostPerMt;
    private BigDecimal financedCapitalPerMt;
    private BigDecimal annualRate;
    private int dayCountBasis;

    protected Lot() {
    }

    public Lot(String code, String product, BigDecimal quantityMt, String location, String currency,
               BigDecimal purchaseCostPerMt, BigDecimal financedCapitalPerMt, BigDecimal annualRate, int dayCountBasis) {
        this.code = code;
        this.product = product;
        this.quantityMt = quantityMt;
        this.location = location;
        this.currency = currency;
        this.purchaseCostPerMt = purchaseCostPerMt;
        this.financedCapitalPerMt = financedCapitalPerMt;
        this.annualRate = annualRate;
        this.dayCountBasis = dayCountBasis;
    }

    public LotTerms terms() {
        return new LotTerms(quantityMt, purchaseCostPerMt, financedCapitalPerMt, annualRate, dayCountBasis);
    }

    public String getCode() {
        return code;
    }

    public String getProduct() {
        return product;
    }

    public BigDecimal getQuantityMt() {
        return quantityMt;
    }

    public String getLocation() {
        return location;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getPurchaseCostPerMt() {
        return purchaseCostPerMt;
    }

    public BigDecimal getFinancedCapitalPerMt() {
        return financedCapitalPerMt;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    public int getDayCountBasis() {
        return dayCountBasis;
    }
}
