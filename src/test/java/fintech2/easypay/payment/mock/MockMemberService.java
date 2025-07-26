package fintech2.easypay.payment.mock;

import fintech2.easypay.member.entity.Member;
import fintech2.easypay.payment.service.MemberService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 결제 테스트용 회원 서비스 Mock 구현체
 * 실제 데이터베이스 없이 메모리에서 회원 정보를 관리
 */
public class MockMemberService implements MemberService {
    
    private final Map<String, Member> membersByPhoneNumber = new HashMap<>();
    private final Map<Long, Member> membersById = new HashMap<>();
    
    /**
     * 테스트용 회원 데이터 추가
     */
    public void addTestMember(Member member) {
        membersByPhoneNumber.put(member.getPhoneNumber(), member);
        membersById.put(member.getId(), member);
    }
    
    /**
     * 테스트용 데이터 초기화
     */
    public void clearTestData() {
        membersByPhoneNumber.clear();
        membersById.clear();
    }
    
    @Override
    public Optional<Member> findByPhoneNumber(String phoneNumber) {
        return Optional.ofNullable(membersByPhoneNumber.get(phoneNumber));
    }
    
    @Override
    public Optional<Member> findById(Long memberId) {
        return Optional.ofNullable(membersById.get(memberId));
    }
}