package core_api.chat;

public class ChatUnavailableException extends RuntimeException {
    public ChatUnavailableException() { super("General chat is temporarily unavailable. Please try again later."); }
}
