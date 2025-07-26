package fintech2.easypay.payment.service.impl;

import fintech2.easypay.member.entity.Member;
import fintech2.easypay.member.repository.MemberRepository;
import fintech2.easypay.payment.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 결제 모듈용 회원 서비스 구현체
 * 실제 MemberRepository를 사용하여 회원 관련 기능 제공
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberServiceImpl implements MemberService {
    
    private final MemberRepository memberRepository;
    
    @Override
    public Optional<Member> findByPhoneNumber(String phoneNumber) {
        return memberRepository.findByPhoneNumber(phoneNumber);
    }
    
    @Override
    public Optional<Member> findById(Long memberId) {
        return memberRepository.findById(memberId);
    }
}