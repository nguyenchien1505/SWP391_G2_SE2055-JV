package com.example.SWP391_G2_SE2055_JV.config;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long    id;
    private final String  username;   // stores email
    private final String  password;
    private final Role    role;
    private final Long    hotelId;    // null for ADMIN_PLATFORM
    private final boolean enabled;

    public CustomUserDetails(Long id, String username, String password,
                              Role role, Long hotelId, boolean enabled) {
        this.id       = id;
        this.username = username;
        this.password = password;
        this.role     = role;
        this.hotelId  = hotelId;
        this.enabled  = enabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String  getPassword()             { return password; }
    @Override public String  getUsername()             { return username; }
    @Override public boolean isEnabled()               { return enabled; }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}