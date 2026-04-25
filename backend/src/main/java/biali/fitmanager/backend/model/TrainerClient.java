package biali.fitmanager.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "trainer_clients")
public class TrainerClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "trainer_id", nullable = false)
    private Integer trainerId;

    @Column(name = "client_id", nullable = false)
    private Integer clientId;

    public Integer getId() {
        return id;
    }

    public Integer getTrainerId() {
        return trainerId;
    }

    public Integer getClientId() {
        return clientId;
    }

    public void setTrainerId(Integer trainerId) {
        this.trainerId = trainerId;
    }

    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }
}
