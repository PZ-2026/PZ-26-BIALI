package biali.fitmanager.backend.dto;

public class PurchaseMembershipRequest {
    private Integer userId;
    private Long membershipTypeId;

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public Long getMembershipTypeId() { return membershipTypeId; }
    public void setMembershipTypeId(Long membershipTypeId) { this.membershipTypeId = membershipTypeId; }
}
