package com.example.onedaypiece.service;

import com.example.onedaypiece.exception.ApiRequestException;
import com.example.onedaypiece.security.TokenProvider;
import com.example.onedaypiece.web.domain.challenge.Challenge;
import com.example.onedaypiece.web.domain.challengeRecord.ChallengeRecord;
import com.example.onedaypiece.web.domain.challengeRecord.ChallengeRecordRepository;
import com.example.onedaypiece.web.domain.member.Member;
import com.example.onedaypiece.web.domain.member.MemberRepository;
import com.example.onedaypiece.web.domain.point.Point;
import com.example.onedaypiece.web.domain.point.PointRepository;
import com.example.onedaypiece.web.domain.pointHistory.PointHistoryRepository;
import com.example.onedaypiece.web.domain.token.RefreshToken;
import com.example.onedaypiece.web.domain.token.RefreshTokenRepository;
import com.example.onedaypiece.web.dto.request.login.LoginRequestDto;
import com.example.onedaypiece.web.dto.request.mypage.ProfileUpdateRequestDto;
import com.example.onedaypiece.web.dto.request.mypage.PwUpdateRequestDto;
import com.example.onedaypiece.web.dto.request.signup.SignupRequestDto;
import com.example.onedaypiece.web.dto.request.token.TokenRequestDto;
import com.example.onedaypiece.web.dto.response.member.MemberResponseDto;
import com.example.onedaypiece.web.dto.response.member.MemberTokenResponseDto;
import com.example.onedaypiece.web.dto.response.member.reload.ReloadResponseDto;
import com.example.onedaypiece.web.dto.response.mypage.end.EndResponseDto;
import com.example.onedaypiece.web.dto.response.mypage.end.MyPageEndResponseDto;
import com.example.onedaypiece.web.dto.response.mypage.histroy.MemberHistoryResponseDto;
import com.example.onedaypiece.web.dto.response.mypage.histroy.MemberHistoryDto;
import com.example.onedaypiece.web.dto.response.mypage.histroy.PointHistoryDto;
import com.example.onedaypiece.web.dto.response.mypage.proceed.MypageProceedResponseDto;
import com.example.onedaypiece.web.dto.response.mypage.proceed.ProceedResponseDto;
import com.example.onedaypiece.web.dto.response.mypage.scheduled.MyPageScheduledResponseDto;
import com.example.onedaypiece.web.dto.response.mypage.scheduled.ScheduledResponseDto;
import com.example.onedaypiece.web.dto.response.token.TokenDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class MemberService {

    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PointRepository pointRepository;
    private final ChallengeRecordRepository challengeRecordRepository;
    private final PointHistoryRepository pointHistoryRepository;

    // 회원가입
    @Transactional
    public void registMember(SignupRequestDto requestDto){
        String email = requestDto.getEmail();
        String nickname = requestDto.getNickname();

        if (memberRepository.existsByEmail(email)) {
            throw new ApiRequestException("이미 가입되어 있는 유저입니다");
        }

        // 회원 email(ID)중복확인
        Optional<Member> found = memberRepository.findByEmail(email);
        if (found.isPresent()) {
            throw new ApiRequestException("중복된 사용자 email(ID)가 존재합니다.");
        }

        // 닉네임 중복확인
        existNickname(nickname);

        // 패스워드 인코딩
        String password= passwordEncoder.encode(requestDto.getPassword());
        requestDto.setPassword(password);

        Point point = new Point();
//        point = pointRepository.save(point);
        memberRepository.save(new Member(requestDto, pointRepository.save(point)));

    }

    // 로그인
    @Transactional
    public MemberTokenResponseDto loginMember(LoginRequestDto requestDto){
        // 1. Login ID/PW 를 기반으로 AuthenticationToken 생성
        UsernamePasswordAuthenticationToken authenticationToken = requestDto.toAuthentication();

        // 2. 실제로 검증 (사용자 비밀번호 체크) 이 이루어지는 부분
        //    authenticate 메서드가 실행이 될 때 CustomUserDetailsService 에서 만들었던 loadUserByUsername 메서드가 실행됨
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        // 3. 인증 정보를 기반으로 JWT 토큰 생성
        TokenDto tokenDto = tokenProvider.generateTokenDto(authentication);

        // 4. RefreshToken 저장
        RefreshToken refreshToken = RefreshToken.builder()
                .key(authentication.getName())
                .value(tokenDto.getRefreshToken())
                .build();

        refreshTokenRepository.save(refreshToken);

        Member member = getMemberByEmail(requestDto.getEmail());

        // 자기가 참여한 챌린지에서 현재 진행중인리스트
        List<ChallengeRecord> targetList = challengeRecordRepository.findAllByMemberAndProgressAndExpected(member,2L, 1L);

        return new MemberTokenResponseDto(tokenDto, member, targetList.size());
    }

    // 새로고침
    @Transactional(readOnly = true)
    public ReloadResponseDto reload(String email){
        Member member = getMemberByEmail(email);

        // 자기가 참여한 챌린지에서 현재 진행중인리스트
        List<ChallengeRecord> targetList = challengeRecordRepository.findAllByMemberAndProgressAndExpected(member,2L, 1L);

        return new ReloadResponseDto(member, targetList.size());
    }


    // 토큰 재발급
    @Transactional
    public MemberTokenResponseDto reissue(TokenRequestDto tokenRequestDto) {
        // 1. Refresh Token 검증
        if (!tokenProvider.validateToken(tokenRequestDto.getRefreshToken())) {
            throw new ApiRequestException("Refresh Token 이 유효하지 않습니다.");
        }

        // 2. Access Token 에서 Member ID 가져오기
        Authentication authentication = tokenProvider.getAuthentication(tokenRequestDto.getAccessToken());

        Member member = getMemberByEmail(authentication.getName());

        // 3. 저장소에서 Member ID 를 기반으로 Refresh Token 값 가져옴
        RefreshToken refreshToken = refreshTokenRepository.findByKey(authentication.getName())
                .orElseThrow(() -> new ApiRequestException("로그아웃 된 사용자입니다."));

        // 4. Refresh Token 일치하는지 검사
        if (!refreshToken.getValue().equals(tokenRequestDto.getRefreshToken())) {
            throw new ApiRequestException("리프레시 토큰의 유저 정보가 일치하지 않습니다.");
        }

        // 5. 새로운 토큰 생성
        TokenDto tokenDto = tokenProvider.generateTokenDto(authentication);

        // 6. 저장소 정보 업데이트
        RefreshToken newRefreshToken = refreshToken.updateValue(tokenDto.getRefreshToken());
        refreshTokenRepository.save(newRefreshToken);

        // 자기가 참여한 챌린지에서 현재 진행중인리스트
        List<ChallengeRecord> targetList = challengeRecordRepository.findAllByMemberAndProgressAndExpected(member,2L, 1L);

        // 토큰 발급
        return new MemberTokenResponseDto(tokenDto, member, targetList.size());
    }

    // 마이 페이지 비밀번호 수정
    @Transactional
    public void updatePassword(PwUpdateRequestDto requestDto, String email){
        Member member = getMemberByEmail(email);

        if(!passwordEncoder.matches(requestDto.getCurrentPassword(), member.getPassword())){
            throw new ApiRequestException("현재 비밀번호가 일치하지 않습니다.");
        }

        String newPassword = passwordEncoder.encode(requestDto.getNewPassword());
        requestDto.setNewPassword(newPassword);

        member.updatePassword(requestDto);
    }

    // 마이 페이지 (이미지 + 닉네임) 수정
    @Transactional
    public String updateProfile(ProfileUpdateRequestDto requestDto, String email){

        Member member = getMemberByEmail(email);

        // 닉네임 중복 처리 닉네임이 달라질경우만 중복확인체크 같은경우는 닉네임변경안하는경우
        if(!member.getNickname().equals(requestDto.getNickname())){
            existNickname(requestDto.getNickname());
        }

        return member.updateProfile(requestDto);
    }

    // 진행중인
    @Transactional(readOnly = true)
    public MypageProceedResponseDto getProceed(String email){
        Member member = getMemberByEmail(email);

        //본인이 참여한 챌린지 기록리스트  1: 진행 예정, 2: 진행 중, 3 : 진행 완료
        List<ChallengeRecord> targetList = challengeRecordRepository.findAllByMemberAndProgress(member,2L);

        // 본인이 참여한 챌린지 기록리스트 -> 챌린지 가져옴
        List<Challenge> proceeding = targetList.stream()
                .map(challengeRecord -> challengeRecord.getChallenge()).collect(Collectors.toList());

        // 본인이 참여한 챌린지 리스트 -> 가공
        List<ProceedResponseDto> proceedingResult = proceeding.stream()
                .map(challenge -> new ProceedResponseDto(challenge, challengeRecordRepository.findAllByChallenge(challenge)))
                .collect(Collectors.toList());

        return new MypageProceedResponseDto(member, member.getPoint().getAcquiredPoint(), proceedingResult);
    }

    // 예정인
    @Transactional(readOnly = true)
    public MyPageScheduledResponseDto getSchduled(String email){
        Member member = getMemberByEmail(email);

        //본인이 참여한 챌린지 리스트  1: 진행 예정, 2: 진행 중, 3 : 진행 완료
        List<ChallengeRecord> targetList = challengeRecordRepository.findAllByMemberAndProgress(member,1L);
//        List<Challenge> scheduled = challengeRepository.findAllByChallengeProgressAndMember(1L, member);
        List<Challenge> scheduled = targetList.stream()
                .map(challengeRecord -> challengeRecord.getChallenge()).collect(Collectors.toList());

        List<ScheduledResponseDto> scheduledList = scheduled.stream()
                .map(challenge -> new ScheduledResponseDto(challenge, challengeRecordRepository.findAllByChallenge(challenge)))
                .collect(Collectors.toList());

        return new MyPageScheduledResponseDto(member,  scheduledList);
    }

    // 종료된 챌린지
    @Transactional(readOnly = true)
    public MyPageEndResponseDto getEnd(String email){
        Member member = getMemberByEmail(email);

        //본인이 참여한 챌린지 리스트  1: 진행 예정, 2: 진행 중, 3 : 진행 완료
        List<ChallengeRecord> targetList = challengeRecordRepository.findAllByMemberAndProgress(member,3L);

        List<Challenge> end = targetList.stream()
                .map(challengeRecord -> challengeRecord.getChallenge()).collect(Collectors.toList());

        List<EndResponseDto> endList = end.stream()
                .map(challenge -> new EndResponseDto(challenge, challengeRecordRepository.findAllByChallenge(challenge)))
                .collect(Collectors.toList());

        return new MyPageEndResponseDto(member, endList);
    }

    // 마이 페이지 히스토리
    @Transactional(readOnly = true)
    public MemberHistoryResponseDto getHistory(String email){
        MemberResponseDto memberResponseDto;

        // 1. 자기가 얻은 포인트 가져오기
        List<MemberHistoryDto> memberHistoryList = pointHistoryRepository.findHistory(email);

        if(memberHistoryList.size() == 0){
            Member member = getMemberByEmail(email);
            memberResponseDto = new MemberResponseDto(member);
        } else{
            memberResponseDto = new MemberResponseDto(memberHistoryList.get(0));
        }

        // 2. 포인트에 관한것만 빼기 원하는정보만 빼기 히스토리에관한것만 따로뺴고
        List<PointHistoryDto> pointHistoryList = memberHistoryList.stream()
                .map(memberHistory -> new PointHistoryDto(memberHistory))
                .collect(Collectors.toList());

        return new MemberHistoryResponseDto(memberResponseDto , pointHistoryList);
    }


    // 닉네임 중복확인
    private void existNickname(String nickname){
        if(memberRepository.existsByNickname(nickname)){
            throw  new ApiRequestException("이미 존재하는 닉네임입니다.");
        }
    }

    // 이메일로 멤버 찾기
    private Member getMemberByEmail(String email){
        return memberRepository.findByEmail(email).orElseThrow(
                () -> new ApiRequestException("멤버를 찾을수없는 이메일입니다.")
        );
    }


}

