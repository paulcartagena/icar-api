package com.paulcartagena.icarapi.member.repository;

import com.paulcartagena.icarapi.member.entity.Member;
import com.paulcartagena.icarapi.member.enums.MemberStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

// Reusable filters for GET /members: each one is combined with .and() only when its param is present
public final class MemberSpecifications {

    private MemberSpecifications() {
    }

    public static Specification<Member> hasStatus(MemberStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Member> inFamily(UUID familyId) {
        return (root, query, cb) -> cb.equal(root.get("family").get("id"), familyId);
    }

    // A member joins a ministry at most once (composite PK), so the join never duplicates rows
    public static Specification<Member> inMinistry(UUID ministryId) {
        return (root, query, cb) -> cb.equal(root.join("ministries").get("ministry").get("id"), ministryId);
    }

    // Case-insensitive match on "firstName lastName", so "ana pe" finds "Ana Pérez"
    public static Specification<Member> nameContains(String search) {
        return (root, query, cb) -> cb.like(
                cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))),
                "%" + search.toLowerCase() + "%"
        );
    }
}
