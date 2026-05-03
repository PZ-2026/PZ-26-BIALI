package biali.fitmanager.backend.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import biali.fitmanager.backend.model.MembershipType;
import biali.fitmanager.backend.repository.MembershipTypeRepository;

@RestController
@RequestMapping("/api")
public class MembershipTypeController {

     private final MembershipTypeRepository repository;

    public MembershipTypeController(MembershipTypeRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/membership-types")
    public List<MembershipType> getAll() {
        return repository.findAll();
    }

    @PostMapping("/admin/membership-types")
    public ResponseEntity<MembershipType> create(@RequestBody MembershipType membershipType) {
        try {
            MembershipType saved = repository.save(membershipType);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/admin/membership-types/{id}")
    public ResponseEntity<MembershipType> update(@PathVariable Long id, @RequestBody MembershipType membershipType) {
        return repository.findById(id)
            .map(existing -> {
                existing.setName(membershipType.getName());
                existing.setPrice(membershipType.getPrice());
                existing.setDurationDays(membershipType.getDurationDays());
                existing.setDescription(membershipType.getDescription());
                if (membershipType.getCode() != null) {
                    existing.setCode(membershipType.getCode());
                }
                MembershipType updated = repository.save(existing);
                return ResponseEntity.ok(updated);
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/admin/membership-types/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
