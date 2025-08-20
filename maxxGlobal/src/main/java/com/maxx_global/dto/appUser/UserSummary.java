package com.maxx_global.dto.appUser;

public record UserSummary(
        Long id,
        String firstName,
        String lastName,
        String email,
        String fullName // EKLENEN ALAN - firstName + lastName
) {
    public UserSummary(Long id, String firstName, String lastName, String email) {
        this(id, firstName, lastName, email, firstName + " " + lastName);
    }
}