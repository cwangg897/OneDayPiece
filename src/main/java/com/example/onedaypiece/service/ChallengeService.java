package com.example.onedaypiece.service;

import com.example.onedaypiece.exception.ApiRequestException;
import com.example.onedaypiece.web.domain.challenge.CategoryName;
import com.example.onedaypiece.web.domain.challenge.Challenge;
import com.example.onedaypiece.web.domain.challenge.ChallengeRepository;
import com.example.onedaypiece.web.domain.challengeRecord.ChallengeRecord;
import com.example.onedaypiece.web.domain.challengeRecord.ChallengeRecordRepository;
import com.example.onedaypiece.web.domain.history.UserHistoryRepository;
import com.example.onedaypiece.web.domain.member.Member;
import com.example.onedaypiece.web.domain.member.MemberRepository;
import com.example.onedaypiece.web.dto.request.challenge.ChallengeRequestDto;
import com.example.onedaypiece.web.dto.request.challenge.PutChallengeRequestDto;
import com.example.onedaypiece.web.dto.response.challenge.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.example.onedaypiece.web.domain.challenge.CategoryName.*;

@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeRecordRepository challengeRecordRepository;
    private final MemberRepository memberRepository;
    private final UserHistoryRepository userHistoryRepository;

    private final int categorySize = 3;

    private final LocalDateTime currentLocalDateTime = LocalDateTime.now();

    public ChallengeResponseDto getChallengeDetail(Long challengeId) {
        Challenge challenge = ChallengeChecker(challengeId);
        List<Long> memberList = challengeRecordRepository.findAllByChallengeId(challengeId)
                .stream()
                .map(c -> c.getMember().getMemberId())
                .collect(Collectors.toList());
        return new ChallengeResponseDto(challenge, memberList);
    }

    @Transactional
    public void deleteChallenge(Long challengeId, String username) {
        Challenge challenge = ChallengeChecker(challengeId);
        deleteChallengeException(username, challenge);
        challengeRecordRepository.deleteAllByChallenge(challenge);
    }

    @Transactional
    public Long createChallenge(ChallengeRequestDto requestDto, String email) {
        Member member = memberChecker(email);
        createChallengeException(requestDto, member);
        Challenge challenge = new Challenge(requestDto, member);
        challengeRecordRepository.save(new ChallengeRecord(challenge, member));
        return challengeRepository.save(challenge).getChallengeId();
    }

    @Transactional
    public void putChallenge(PutChallengeRequestDto requestDto, String email) {
        Member member = memberChecker(email);
        Challenge challenge = ChallengeChecker(requestDto.getChallengeId());
        putChallengeException(member, challenge);
        challenge.putChallenge(requestDto);
    }

    // 메인 페이지
    public ChallengeMainResponseDto getMainPage(String email) {
        ChallengeMainResponseDto responseDto = new ChallengeMainResponseDto();
        List<ChallengeRecord> records = challengeRecordRepository.findAllByStatusTrueOrderByModifiedAtDesc();

        userSliderUpdate(responseDto, email, records);
        popularUpdate(responseDto, email);

        categoryCollector(EXERCISE, records).forEach(responseDto::addExercise);
        categoryCollector(LIVINGHABITS, records).forEach(responseDto::addLivingHabits);
        categoryCollector(NODRINKNOSMOKE, records).forEach(responseDto::addNoDrinkNoSmoke);

        responseDto.setHistoryCount(userHistoryRepository.countAllByStatusFalseAndMemberEmail(email));
        return responseDto;
    }

    private void userSliderUpdate(ChallengeMainResponseDto responseDto, String email, List<ChallengeRecord> records) {
        List<Challenge> userChallengeList = records
                .stream()
                .filter(r -> r.getMember().getEmail().equals(email))
                .map(ChallengeRecord::getChallenge)
                .collect(Collectors.toList());
        List<ChallengeSourceResponseDto> sliderSourceList = new ArrayList<>();

        for (Challenge challenge : userChallengeList) {
            ChallengeSourceResponseDto dto = new ChallengeSourceResponseDto(challenge, records);
            sliderSourceList.add(dto);
        }
        responseDto.addSlider(sliderSourceList);
    }

    private void popularUpdate(ChallengeMainResponseDto responseDto, String email) {
        final int popularSize = 4;
        List<ChallengeRecord> records = challengeRecordRepository.findPopularOrderByDesc(email, PageRequest.of(0, popularSize));
        responseDto.addPopular(records);
    }

    private List<ChallengeSourceResponseDto> categoryCollector(CategoryName category, List<ChallengeRecord> records) {
        Set<Long> recordIdList = new HashSet<>();
        List<ChallengeRecord> recordList = new ArrayList<>();

        records.stream().filter(categoryListDeduplicator(category, recordIdList)).forEach(r -> {
            recordIdList.add(r.getChallenge().getChallengeId());
            recordList.add(r);
        });

        List<ChallengeSourceResponseDto> categorySourceList = new ArrayList<>();

        for (Challenge c : recordList.stream().map(ChallengeRecord::getChallenge).collect(Collectors.toList())) {
            ChallengeSourceResponseDto dto = new ChallengeSourceResponseDto(c, recordList);
            categorySourceList.add(dto);
        }

        return categorySourceList;
    }

    private Predicate<ChallengeRecord> categoryListDeduplicator(CategoryName category, Set<Long> recordIdList) {
        return r -> !recordIdList.contains(r.getChallenge().getChallengeId()) &&
                r.getChallenge().getCategoryName().equals(category) &&
                recordIdList.size() < categorySize;
    }

    private Challenge ChallengeChecker(Long challengeId) {
        return challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ApiRequestException("존재하지 않는 챌린지입니다"));
    }

    private void putChallengeException(Member member, Challenge challenge) {
        if (!challenge.getMember().equals(member)) {
            throw new ApiRequestException("다른 유저가 만든 챌린지입니다.");
        }
        if (!challenge.getChallengeProgress().equals(1L)) {
            throw new ApiRequestException("이미 시작되거나 종료된 챌린지입니다.");
        }
        if (!challenge.isChallengeStatus()) {
            throw new ApiRequestException("삭제된 챌린지입니다.");
        }
    }

    private Member memberChecker(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new ApiRequestException("존재하지 않는 유저입니다."));
    }

    private void deleteChallengeException(String username, Challenge challenge) {
        if (!challenge.getMember().getEmail().equals(username)) {
            throw new IllegalArgumentException("작성자가 아닙니다.");
        }
        if (currentLocalDateTime.isBefore(challenge.getChallengeStartDate())) {
            challenge.setChallengeStatus(false);
            challenge.setChallengeProgress(3L);
        } else {
            throw new ApiRequestException("이미 시작된 챌린지는 삭제할 수 없습니다.");
        }
    }

    private void createChallengeException(ChallengeRequestDto requestDto, Member member) {
        challengeRepository.findAllByMember(member)
                .stream()
                .filter(value -> value.getCategoryName() == requestDto.getCategoryName())
                .forEach(value -> {
                    throw new ApiRequestException("이미 해당 카테고리에 챌린지를 생성한 유저입니다.");
                });
        if (requestDto.getChallengePassword().length() < 4) {
            if (!requestDto.getChallengePassword().equals("")) {
                throw new ApiRequestException("비밀번호는 4자리 이상으로 설정해야합니다.");
            }
        }
    }
}
