package com.recovertogether.backend.security;

import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.recovertogether.backend.service.JwtService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;


@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository=userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        System.out.println("JWT FILTER RUNNING");
        String path = request.getServletPath();
        if (path.equals("/auth/login") || path.equals("/users/register")) {
            filterChain.doFilter(request, response);
            return;
        }


        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String token = authHeader.substring(7);
        System.out.println("TOKEN RECEIVED: " + token);
        System.out.println("VALID? " + jwtService.validateToken(token));

        if (jwtService.validateToken(token)) {
            String email = jwtService.extractEmail(token);

            User user= userRepository.findByEmail(email).orElse(null);

            if(user!=null)
            {
                UsernamePasswordAuthenticationToken auth=
                        new UsernamePasswordAuthenticationToken(user,null, java.util.Collections.emptyList());
                System.out.println(auth.isAuthenticated());
                SecurityContextHolder.getContext().setAuthentication(auth);
                System.out.println("AUTH STORED");
                System.out.println(SecurityContextHolder.getContext().getAuthentication());
            }

        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }


        filterChain.doFilter(request, response);

    }
}