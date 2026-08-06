package com.gooroomees.neulbomgil_backend.domain.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user")
public class UserAuth implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    private Role role;

    private Status status;

    public void activate() {
        this.status = Status.ACTIVE;
    }

    public void updateName(String name) {
        this.name = name;
        this.modifiedAt = LocalDateTime.now();
    }

    public void updatePassword(String password) {
        this.password = password;
    }

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime modifiedAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status.equals(Status.ACTIVE);
    }

    public void changeStatus(Status status) {
        this.status = status;
    }

    public void changePassword(String newPassword) {
        this.password = newPassword;
    }

    public void deleteUser() {
        this.status = Status.REMOVED;
    }
}