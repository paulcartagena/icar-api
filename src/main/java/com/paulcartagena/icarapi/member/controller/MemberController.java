package com.paulcartagena.icarapi.member.controller;

import com.paulcartagena.icarapi.member.dto.MemberRequest;
import com.paulcartagena.icarapi.member.dto.MemberResponse;
import com.paulcartagena.icarapi.member.enums.MemberStatus;
import com.paulcartagena.icarapi.member.service.MemberService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/members")
@Tag(name = "Members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    // search matches "firstName lastName", case-insensitive
    @PreAuthorize("hasAuthority('MEMBERS_READ')")
    @GetMapping
    public List<MemberResponse> getAllMembers(@RequestParam(required = false) MemberStatus status,
                                              @RequestParam(required = false) UUID familyId,
                                              @RequestParam(required = false) UUID ministryId,
                                              @RequestParam(required = false) String search) {
        return memberService.getAllMembers(status, familyId, ministryId, search);
    }

    @PreAuthorize("hasAuthority('MEMBERS_READ')")
    @GetMapping("/{id}")
    public MemberResponse getMemberById(@PathVariable UUID id) {
        return memberService.getMemberById(id);
    }

    @PreAuthorize("hasAuthority('MEMBERS_WRITE')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse createMember(@Valid @RequestBody MemberRequest memberRequest) {
        return memberService.createMember(memberRequest);
    }

    @PreAuthorize("hasAuthority('MEMBERS_WRITE')")
    @PutMapping("/{id}")
    public MemberResponse updateMember(@PathVariable UUID id, @Valid @RequestBody MemberRequest memberRequest) {
        return memberService.updateMember(id, memberRequest);
    }
}
