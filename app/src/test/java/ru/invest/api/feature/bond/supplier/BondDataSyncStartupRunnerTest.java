package ru.invest.api.feature.bond.supplier;

import org.junit.jupiter.api.Test;
import ru.invest.api.bond.supplier.scheduler.BondDataSyncScheduler;
import ru.invest.api.bond.supplier.scheduler.BondDataSyncStartupRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

public class BondDataSyncStartupRunnerTest {
    private final BondDataSyncScheduler bondDataSyncScheduler = mock(BondDataSyncScheduler.class);

    @Test
    public void syncRunsInBackgroundTest() throws InterruptedException {
        final CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> release.await(5, TimeUnit.SECONDS)).when(bondDataSyncScheduler).sync();

        final long started = System.nanoTime();
        new BondDataSyncStartupRunner(bondDataSyncScheduler).onReady();
        final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        // onReady не ждёт окончания синхронизации, иначе приложение не стало бы ready
        assertTrue(elapsedMillis < 1000, "onReady blocked for " + elapsedMillis + " ms");
        verify(bondDataSyncScheduler, timeout(1000)).sync();
        release.countDown();
    }
}
