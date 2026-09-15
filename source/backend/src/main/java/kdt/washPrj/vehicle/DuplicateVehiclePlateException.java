package kdt.washPrj.vehicle;

public class DuplicateVehiclePlateException extends RuntimeException {
    public DuplicateVehiclePlateException() {
        super("이미 등록된 차량 번호입니다.");
    }
}
