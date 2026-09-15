package kdt.washPrj.auth;

public class AccountSuspendedException
        extends RuntimeException {

    public AccountSuspendedException() {
        super("정지된 계정입니다. 관리자에게 문의해주세요.");
    }
}