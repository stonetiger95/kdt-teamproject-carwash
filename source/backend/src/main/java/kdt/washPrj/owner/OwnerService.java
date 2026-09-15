package kdt.washPrj.owner;

import kdt.washPrj.owner.dto.ChangePasswordRequestDTO;
import kdt.washPrj.owner.dto.OwnerMyPageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OwnerService {

    private final OwnerMapper ownerMapper;


    public OwnerMyPageDTO getMyPage(
            String loginId
    ) {

        OwnerMyPageDTO myPage =
                ownerMapper.findMyPageByLoginId(
                        loginId
                );


        if (myPage == null) {

            throw new RuntimeException(
                    "업주 정보를 찾을 수 없습니다."
            );
        }


        return myPage;
    }


    public OwnerMyPageDTO updateMyPage(
            String loginId,
            OwnerMyPageDTO request
    ) {

        OwnerMyPageDTO updated =
                ownerMapper.updateMyPageAndGet(
                        loginId,
                        request
                );


        if (updated == null) {

            throw new RuntimeException(
                    "업주 정보를 수정할 수 없습니다."
            );
        }


        return updated;
    }

    @Transactional
    public void changePassword(
            String loginId,
            ChangePasswordRequestDTO request
    ) {

        if (
                request.getCurrentPassword() == null
                        || request.getCurrentPassword().isBlank()
        ) {

            throw new IllegalArgumentException(
                    "현재 비밀번호를 입력해주세요."
            );
        }


        if (
                request.getNewPassword() == null
                        || request.getNewPassword().isBlank()
        ) {

            throw new IllegalArgumentException(
                    "새 비밀번호를 입력해주세요."
            );
        }


        if (request.getNewPassword().length() < 4) {

            throw new IllegalArgumentException(
                    "새 비밀번호는 4자 이상이어야 합니다."
            );
        }


        int result =
                ownerMapper.changePasswordChecked(
                        loginId,
                        request.getCurrentPassword(),
                        request.getNewPassword()
                );


        if (result == -1) {

            throw new IllegalArgumentException(
                    "현재 비밀번호가 일치하지 않습니다."
            );
        }


        if (result == 0) {

            throw new IllegalArgumentException(
                    "비밀번호를 변경할 수 없습니다."
            );
        }
    }
}
