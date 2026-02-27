package com.metrix.api.security;

import com.metrix.api.model.User;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Puente entre Spring Security y el modelo User de METRIX.
 * <p>
 * Spring Security necesita un {@link UserDetailsService} para resolver
 * credenciales. Esta implementación busca por {@code numeroUsuario}
 * (el #Usuario de la especificación) en MongoDB.
 * <p>
 * Los roles se mapean con prefijo "ROLE_" para compatibilidad con
 * {@code @PreAuthorize("hasRole('ADMIN')")}.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String numeroUsuario) throws UsernameNotFoundException {
        User user = userRepository.findByNumeroUsuario(numeroUsuario)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado: " + numeroUsuario
                ));

        if (!user.isActivo()) {
            throw new UsernameNotFoundException("Usuario desactivado: " + numeroUsuario);
        }

        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toSet());

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getNumeroUsuario())
                .password(user.getPassword())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!user.isActivo())
                .build();
    }
}
