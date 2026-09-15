package kdt.washPrj.auth;

public class AccountWithdrawnException
        extends RuntimeException {

    public AccountWithdrawnException() {
        super("탈퇴된 계정입니다.");
    }
}