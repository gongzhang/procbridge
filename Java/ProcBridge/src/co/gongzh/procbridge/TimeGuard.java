package co.gongzh.procbridge;

import org.jetbrains.annotations.Nullable;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

/**
 * @author Gong Zhang
 */
class TimeGuard {

    private final long timeout;
    private final Runnable task;

    TimeGuard(long timeout, Runnable task) {
        this.timeout = timeout;
        this.task = task;
    }

    void execute() throws ProcBridgeException {
        execute(null);
    }

    void execute(@Nullable ExecutorService executorService) throws ProcBridgeException {
        final Semaphore semaphore = new Semaphore(0);
        final boolean[] overtime = { false };
        final Exception[] exception = { null };

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                overtime[0] = true;
                semaphore.release();
            }
        }, timeout);

        Runnable runnable = () -> {
            try {
                task.run();
            } catch (Exception ex) {
                exception[0] = ex;
            } finally {
                semaphore.release();
            }
        };
        if (executorService != null) {
            executorService.execute(runnable);
        } else {
            new Thread(runnable).start();
        }

        try {
            semaphore.acquire();
            if (overtime[0]) {
                throw ProcBridgeException.timeout();
            }
            if (exception[0] != null) {
                Throwable ex = exception[0].getCause();
                if (ex == null) {
                    ex = exception[0];
                }
                if (ex instanceof ProcBridgeException) {
                    throw (ProcBridgeException) ex;
                } else {
                    throw new ProcBridgeException(ex);
                }
            }
        } catch (InterruptedException e) {
            throw new ProcBridgeException(e);
        } finally {
            timer.cancel();
        }
    }

}
