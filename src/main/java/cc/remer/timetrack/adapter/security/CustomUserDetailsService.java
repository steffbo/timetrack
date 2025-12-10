package cc.remer.timetrack.adapter.security;

import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom UserDetailsService implementation for loading user details from the database.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Benutzer nicht gefunden mit E-Mail: " + email));

        return UserPrincipal.create(user);
    }

    /**
     * Load user by ID.
     *
     * @param id the user ID
     * @return UserDetails
     * @throws UsernameNotFoundException if user not found
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long id) throws UsernameNotFoundException {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("Benutzer nicht gefunden mit ID: " + id));

        return UserPrincipal.create(user);
    }
}
