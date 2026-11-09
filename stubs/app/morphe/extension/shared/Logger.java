package app.morphe.extension.shared;

public class Logger {
    public interface LogMessage {
        String buildMessageString();
    }
    public static void printDebug(LogMessage msg) {}
    public static void printException(LogMessage msg, Throwable t) {}
}
