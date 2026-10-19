package core_api.auth;

import core_api.user.UserResponse;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    @Override
    public String toString() {
        return "LoginResponse[redacted]";
    }
}
