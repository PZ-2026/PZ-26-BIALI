package biali.fitmanager.backend.dto;

import java.math.BigDecimal;

public class TopUpRequest {
    private BigDecimal amount;

    public TopUpRequest() {}

    public TopUpRequest(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
