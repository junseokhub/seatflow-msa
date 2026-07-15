package com.seatflow.reservation.util;


// 실패했을 때 에러 메시지가 "그냥 AssertionError"라 원인 파악이 느림
// 폴링 간격, 타임아웃 같은 세부 조정을 할 때마다 이 헬퍼 코드를 계속 고쳐야 함
// 나중에 비슷한 헬퍼를 또 만들게 되면 중복이 생김
public class Wait {

    public void waitUntil(java.util.function.BooleanSupplier condition, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        throw new AssertionError("Timeout waiting for condition (" + timeoutSeconds + "s)");
    }
}
