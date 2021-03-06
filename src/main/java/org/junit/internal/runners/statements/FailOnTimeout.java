package org.junit.internal.runners.statements;

import org.junit.runners.model.Statement;

import java.util.concurrent.TimeUnit;

public class FailOnTimeout extends Statement {
    private final Statement fOriginalStatement;
    private final TimeUnit fTimeUnit;
    private final long fTimeout;

    public FailOnTimeout(Statement originalStatement, long millis) {
        this(originalStatement, millis, TimeUnit.MILLISECONDS);
    }

    public FailOnTimeout(Statement originalStatement, long timeout, TimeUnit unit) {
        fOriginalStatement = originalStatement;
        fTimeout = timeout;
        fTimeUnit = unit;
    }

    @Override
    public void evaluate() throws Throwable {
        StatementThread thread = evaluateStatement();
        if (!thread.fFinished) {
            throwExceptionForUnfinishedThread(thread);
        }
    }

    private StatementThread evaluateStatement() throws InterruptedException {
        StatementThread thread = new StatementThread(fOriginalStatement);
        thread.start();
        fTimeUnit.timedJoin(thread, fTimeout);
        if (!thread.fFinished) {
            thread.recordStackTrace();
        }
        thread.interrupt();
        return thread;
    }

    private void throwExceptionForUnfinishedThread(StatementThread thread)
            throws Throwable {
        if (thread.fExceptionThrownByOriginalStatement != null) {
            throw thread.fExceptionThrownByOriginalStatement;
        } else {
            throwTimeoutException(thread);
        }
    }

    private void throwTimeoutException(StatementThread thread) throws Exception {
        Exception exception = new Exception(String.format(
                "test timed out after %d %s", fTimeout, fTimeUnit.name().toLowerCase()));
        exception.setStackTrace(thread.getRecordedStackTrace());
        throw exception;
    }

    private static class StatementThread extends Thread {
        private final Statement fStatement;

        private boolean fFinished = false;

        private Throwable fExceptionThrownByOriginalStatement = null;

        private StackTraceElement[] fRecordedStackTrace = null;

        public StatementThread(Statement statement) {
            fStatement = statement;
        }

        public void recordStackTrace() {
            fRecordedStackTrace = getStackTrace();
        }

        public StackTraceElement[] getRecordedStackTrace() {
            return fRecordedStackTrace;
        }

        @Override
        public void run() {
            try {
                fStatement.evaluate();
                fFinished = true;
            } catch (InterruptedException e) {
                // don't log the InterruptedException
            } catch (Throwable e) {
                fExceptionThrownByOriginalStatement = e;
            }
        }
    }
}