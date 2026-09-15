package kdt.washPrj.customer;

import kdt.washPrj.customer.dto.ChangePasswordRequestDTO;
import kdt.washPrj.customer.dto.CustomerMyPageDTO;
import kdt.washPrj.customer.dto.WithdrawRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerMapper customerMapper;

    public CustomerMyPageDTO getMyPage(String loginId) {
        CustomerMyPageDTO myPage = customerMapper.findMyPageByLoginId(loginId);
        if (myPage == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer information was not found."
            );
        }
        return myPage;
    }

    @Transactional
    public CustomerMyPageDTO updateMyPage(
            String loginId,
            CustomerMyPageDTO request
    ) {
        CustomerMyPageDTO updated = customerMapper.updateMyPageAndGet(loginId, request);
        if (updated == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer information was not updated."
            );
        }
        return updated;
    }

    @Transactional
    public void changePassword(
            String loginId,
            ChangePasswordRequestDTO request
    ) {
        if (request.getCurrentPassword() == null
                || request.getCurrentPassword().isBlank()) {
            throw new IllegalArgumentException("Current password is required.");
        }
        if (request.getNewPassword() == null
                || request.getNewPassword().isBlank()) {
            throw new IllegalArgumentException("New password is required.");
        }
        if (request.getNewPassword().length() < 4) {
            throw new IllegalArgumentException(
                    "New password must contain at least four characters."
            );
        }
        int result = customerMapper.changePasswordChecked(
                loginId,
                request.getCurrentPassword(),
                request.getNewPassword()
        );
        if (result == -1) {
            throw new IllegalArgumentException("Current password does not match.");
        }
        if (result == 0) {
            throw new IllegalStateException("Password was not changed.");
        }
    }

    @Transactional
    public void withdraw(
            String loginId,
            WithdrawRequestDTO request
    ) {
        if (request == null
                || request.getCurrentPassword() == null
                || request.getCurrentPassword().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Current password is required."
            );
        }
        int result = customerMapper.withdrawChecked(
                loginId,
                request.getCurrentPassword()
        );
        if (result == -3) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Current password does not match."
            );
        }
        if (result == -2) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Prepaid balance must be zero before withdrawal."
            );
        }
        if (result == -1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer is already withdrawn or cannot be withdrawn."
            );
        }
        if (result == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer information was not found."
            );
        }
    }
}
