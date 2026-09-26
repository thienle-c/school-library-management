package thuvien.client.view.common;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/**
 * Executes a network/background operation off the Swing EDT,
 * and passes the result back to the EDT via consumers.
 */
public class AsyncWorker<T> extends SwingWorker<T, Void> {
    private final Callable<T> task;
    private final Consumer<T> onSuccess;
    private final Consumer<Exception> onError;

    public AsyncWorker(Callable<T> task, Consumer<T> onSuccess, Consumer<Exception> onError) {
        this.task = task;
        this.onSuccess = onSuccess;
        this.onError = onError;
    }

    public static <T> void run(Callable<T> task, Consumer<T> onSuccess, Consumer<Exception> onError) {
        new AsyncWorker<>(task, onSuccess, onError).execute();
    }

    @Override
    protected T doInBackground() throws Exception {
        return task.call();
    }

    @Override
    protected void done() {
        try {
            T result = get();
            if (onSuccess != null) {
                onSuccess.accept(result);
            }
        } catch (java.util.concurrent.ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (onError != null) {
                onError.accept(cause instanceof Exception ? (Exception) cause : ee);
            }
        } catch (Exception e) {
            if (onError != null) {
                onError.accept(e);
            }
        }
    }
}
