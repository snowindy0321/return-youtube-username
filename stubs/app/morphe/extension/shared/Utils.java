package app.morphe.extension.shared;

import android.content.Context;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class Utils {
    public static boolean isContextSet() {
        return false;
    }

    public static Context getContext() {
        return null;
    }

    public static <T> Future<T> submitOnBackgroundThread(Callable<T> callable) {
        return null;
    }
}
