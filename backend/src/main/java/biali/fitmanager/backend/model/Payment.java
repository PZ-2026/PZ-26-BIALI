package biali.fitmanager.backend.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "membership_id")
    private Integer membershipId;

    @Column(nullable = false)
    private java.math.BigDecimal amount;

    @Column(name = "payment_date", insertable = false, updatable = false)
    private LocalDateTime paymentDate;

    @Column(nullable = false)
    private String status;

    public Integer getId() { return id; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public Integer getMembershipId() { return membershipId; }
    public void setMembershipId(Integer membershipId) { this.membershipId = membershipId; }
    public java.math.BigDecimal getAmount() { return amount; }
    public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }
    public LocalDateTime getPaymentDate() { return paymentDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
