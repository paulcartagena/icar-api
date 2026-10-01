package com.paulcartagena.icarapi.member.repository;

import com.paulcartagena.icarapi.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, UUID> {
}
