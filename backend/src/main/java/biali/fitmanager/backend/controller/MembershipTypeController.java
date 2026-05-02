package biali.fitmanager.backend.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import biali.fitmanager.backend.model.MembershipType;
import biali.fitmanager.backend.repository.MembershipTypeRepository;

@RestController
@RequestMapping("/api/membership-types")
public class MembershipTypeController {

     private final MembershipTypeRepository repository;

    public MembershipTypeController(MembershipTypeRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<MembershipType> getAll() {
        return repository.findAll();
    }
}
