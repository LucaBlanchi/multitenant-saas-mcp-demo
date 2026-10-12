package com.acme.tenancy.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
@Profile("!production")
public class JwtFilter extends OncePerRequestFilter {
 private final JwtService jwt; public JwtFilter(JwtService jwt) { this.jwt=jwt; }
 @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, java.io.IOException {
  String header=request.getHeader("Authorization");
  if(header!=null && header.startsWith("Bearer ")) try { CurrentUser u=jwt.parse(header.substring(7)); var auth=new UsernamePasswordAuthenticationToken(u,null,java.util.List.of(new SimpleGrantedAuthority(u.superadmin()?"ROLE_SUPERADMIN":"ROLE_USER"))); SecurityContextHolder.getContext().setAuthentication(auth); } catch(Exception ignored) { SecurityContextHolder.clearContext(); }
  chain.doFilter(request,response);
 }
}
