package fintech2.easypay.payment.service;

import fintech2.easypay.member.entity.Member;

import java.util.Optional;

/**
 * 결제 모듈에서 회원 관련 기능을 위한 추상화 인터페이스
 * 실제 MemberRepository 의존성을 제거하고 테스트 가능하도록 분리
 */
public interface MemberService {
    
    /**
     * 휴대폰 번호로 회원 조회
     */
    Optional<Member> findByPhoneNumber(String phoneNumber);
    
    /**
     * 회원 ID로 회원 조회
     */
    Optional<Member> findById(Long memberId);
}