package com.vein.user;

/**
 * Public projection of a {@link User}. Shared by auth and user controllers.
 */
public record UserDto(Long id, String email, String role, String status, String tier) {

    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getRole(),
                u.getStatus() == null ? null : u.getStatus().name(), u.getTier());
    }
}
