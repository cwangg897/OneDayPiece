package com.example.onedaypiece.web.domain.challenge;

import com.example.onedaypiece.web.domain.member.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QPageRequest;

import java.util.List;
import java.util.Optional;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    @Query("select c from Challenge c " +
            "Where c.challengeStatus = true and c.challengeProgress < 3 " +
            "and c.member.memberStatus = 1 and c.member = :member")
    List<Challenge> findAllByMember(Member member);

    @Query("select c from Challenge c " +
            "Where c.challengeStatus = true and c.challengeProgress = 1 and c.categoryName = :categoryName " +
            "ORDER BY c.modifiedAt DESC")
    List<Challenge> findAllByCategoryNameOrderByModifiedAtDescListed(CategoryName categoryName, Pageable pageable);

    @Query("select c from Challenge c " +
            "WHERE c.challengeStatus = true and c.challengeProgress = 1 and c.challengeTitle like %?1%" +
            "ORDER BY c.modifiedAt DESC")
    List<Challenge> findAllByWordsAndChallengeStatusTrueOrderByModifiedAtDesc(String words, Pageable pageable);

    @Query("select c from Challenge c where c.challengeStatus = true and c.challengeId =:challengeId")
    Optional<Challenge> findChallengeStatusTrue(Long challengeId);

    List<Challenge> findAllByChallengeStatusTrueAndChallengeProgressLessThan(Long challengeProgress);


    @Modifying(clearAutomatically = true)
    @Query("update ChallengeRecord c " +
            "set c.challengeRecordStatus = false " +
            "where c.member.memberId in :kickMember " +
            "and c.challenge.challengeId in :kickChallenge")
    int kickMemberOnChallenge(List<Long> kickMember, List<Long> kickChallenge);


}
