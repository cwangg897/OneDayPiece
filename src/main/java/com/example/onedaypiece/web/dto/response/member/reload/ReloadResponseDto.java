package com.example.onedaypiece.web.dto.response.member.reload;


import com.example.onedaypiece.web.domain.member.Member;
import com.example.onedaypiece.web.dto.response.member.MemberResponseDto;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReloadResponseDto {

    private MemberResponseDto userInfo;


    public ReloadResponseDto(Member member, int challengeCount){
        this.userInfo = new MemberResponseDto(member, challengeCount);
    }
}
