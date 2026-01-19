package com.atina.invoice.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {

                // ------------------------------------
                // Extraer información del token
                // ------------------------------------

                String username = tokenProvider.getUsernameFromToken(jwt);
                String tenantCode = tokenProvider.getRoleFromToken(jwt);
                String role = tokenProvider.getRoleFromToken(jwt);

                log.info("Authenticating user: {} with role: {} from tenant: {}",
                        username, role, tenantCode);

                // ------------------------------------
                // Cargar detalles del usuario
                // ------------------------------------

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // ------------------------------------
                // Crear authorities con prefijo "ROLE_"
                // Spring Security requiere el prefijo "ROLE_"
                // para hasRole() y hasAnyRole()
                // ------------------------------------

                List<GrantedAuthority> authorities = Arrays.asList(
                        new SimpleGrantedAuthority("ROLE_" + role)
                );

                // --------------------------------------------------
                // Crear token de autenticación con las authorities
                // --------------------------------------------------
                
                UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
                
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                
                log.info("Set authentication for user: {}", username);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        
        return null;
    }
}