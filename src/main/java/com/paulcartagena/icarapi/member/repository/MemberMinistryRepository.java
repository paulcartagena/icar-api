package com.paulcartagena.icarapi.member.repository;

import com.paulcartagena.icarapi.member.entity.MemberMinistry;
import com.paulcartagena.icarapi.member.entity.MemberMinistryId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberMinistryRepository extends JpaRepository<MemberMinistry, MemberMinistryId> {
}
