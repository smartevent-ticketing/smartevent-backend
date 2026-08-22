package com.smartevent.modules.reservation.service;

import com.smartevent.common.enums.SeatStatus;
import com.smartevent.modules.event.repository.EventSeatRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatReservationConcurrencyTest {

    @Mock
    private EventSeatRepository eventSeatRepository;

    @Test
    @DisplayName("50 Luồng đồng thời tranh chấp giữ cùng 1 ghế -> Duy nhất 1 luồng thành công, 49 luồng bị chặn")
    void concurrentSeatReservation_ExactlyOneSuccess() throws InterruptedException {
        int numberOfThreads = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        UUID targetSeatId = UUID.randomUUID();
        AtomicBoolean isSeatHeld = new AtomicBoolean(false);
        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger failedCounter = new AtomicInteger(0);

        // Giả lập cơ chế Atomic Update của Database PostgreSQL:
        // Chỉ luồng nào đến đầu tiên khi ghế còn AVAILABLE mới chuyển được thành HELD (trả về 1), các luồng sau trả về 0.
        when(eventSeatRepository.updateSeatStatusAtomic(eq(targetSeatId), eq(SeatStatus.AVAILABLE), eq(SeatStatus.HELD)))
                .thenAnswer(invocation -> {
                    // CompareAndSet mô phỏng khóa hàng (Row-level Lock) nguyên tử của PostgreSQL
                    boolean acquired = isSeatHeld.compareAndSet(false, true);
                    return acquired ? 1 : 0;
                });

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Toàn bộ 50 luồng cùng xuất phát tại 1 mili-giây
                    int affectedRows = eventSeatRepository.updateSeatStatusAtomic(targetSeatId, SeatStatus.AVAILABLE, SeatStatus.HELD);
                    if (affectedRows == 1) {
                        successCounter.incrementAndGet();
                    } else {
                        failedCounter.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Bắn súng hiệu cho 50 luồng đồng loạt chạy đua
        startLatch.countDown();
        finishLatch.await();
        executorService.shutdown();

        // Kiểm chứng tính bất biến chống Race Condition
        assertEquals(1, successCounter.get(), "Chỉ duy nhất 1 khách hàng được phép giữ ghế thành công!");
        assertEquals(49, failedCounter.get(), "49 khách hàng đến sau phải bị từ chối ngay lập tức!");
    }
}
