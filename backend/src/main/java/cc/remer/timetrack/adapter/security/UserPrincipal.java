package cc.remer.timetrack.adapter.security;

import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * UserDetails implementation for Spring Security.
 */
@AllArgsConstructor
@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final Role role;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean active;

    /**
     * Create UserPrincipal from User entity.
     *
     * @param user the user entity
     * @return UserPrincipal
     */
    public static UserPrincipal create(User user) {
        Collection<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );

        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole(),
                authorities,
                user.getActive()
        );
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
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
        return active;
    }
}
