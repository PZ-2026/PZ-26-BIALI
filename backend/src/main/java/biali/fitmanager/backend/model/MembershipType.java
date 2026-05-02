package biali.fitmanager.backend.model;

import java.math.BigDecimal;
import jakarta.persistence.*;

@Entity
@Table(name = "membership_types")
public class MembershipType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String name;
    private BigDecimal price;
    
    @Column(name = "duration_days")
    private int durationDays;

    private String description;

    // GETTERY / SETTERY

    public Long getId() { return id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public int getDurationDays() { return durationDays; }
    public void setDurationDays(int durationDays) { this.durationDays = durationDays; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}