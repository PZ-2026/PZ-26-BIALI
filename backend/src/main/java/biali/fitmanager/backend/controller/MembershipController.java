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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.dto.MembershipUpsertRequest;
import biali.fitmanager.backend.model.Membership;
import biali.fitmanager.backend.repository.AppUserRepository;
import biali.fitmanager.backend.repository.MembershipRepository;

@RestController
@RequestMapping("/api/memberships")
public class MembershipController {

    private final MembershipRepository membershipRepository;
    private final AppUserRepository appUserRepository;

    public MembershipController(MembershipRepository membershipRepository, AppUserRepository appUserRepository) {
        this.membershipRepository = membershipRepository;
        this.appUserRepository = appUserRepository;
    }

    @GetMapping
    public List<Membership> getMemberships(@RequestParam(required = false) Integer userId) {
        if (userId != null) {
            return membershipRepository.findAllByUserId(userId);
        }
        return membershipRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMembership(@PathVariable Integer id) {
        return membershipRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("Membership not found")));
    }

    @PostMapping
    public ResponseEntity<?> createMembership(@RequestBody MembershipUpsertRequest request) {
        String validationError = validateRequest(request);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse(validationError));
        }

        Membership membership = new Membership();
        fillMembership(membership, request);
        Membership saved = membershipRepository.save(membership);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMembership(@PathVariable Integer id, @RequestBody MembershipUpsertRequest request) {
        return membershipRepository.findById(id)
                .<ResponseEntity<?>>map(existing -> {
                    String validationError = validateRequest(request);
                    if (validationError != null) {
                        return ResponseEntity.badRequest().body(new AuthErrorResponse(validationError));
                    }

                    fillMembership(existing, request);
                    return ResponseEntity.ok(membershipRepository.save(existing));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("Membership not found")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMembership(@PathVariable Integer id) {
        if (!membershipRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("Membership not found"));
        }
        membershipRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private String validateRequest(MembershipUpsertRequest request) {
        if (request == null
                || request.getUserId() == null
                || request.getMembershipType() == null || request.getMembershipType().isBlank()
                || request.getStartDate() == null
                || request.getEndDate() == null
                || request.getStatus() == null || request.getStatus().isBlank()) {
            return "Invalid membership payload";
        }

        if (!appUserRepository.existsById(request.getUserId())) {
            return "User does not exist";
        }

        if (request.getEndDate().isBefore(request.getStartDate())) {
            return "End date cannot be before start date";
        }

        return null;
    }

    private void fillMembership(Membership membership, MembershipUpsertRequest request) {
        membership.setUserId(request.getUserId());
        membership.setMembershipType(request.getMembershipType().trim());
        membership.setStartDate(request.getStartDate());
        membership.setEndDate(request.getEndDate());
        membership.setStatus(request.getStatus().trim().toUpperCase());
    }
}
