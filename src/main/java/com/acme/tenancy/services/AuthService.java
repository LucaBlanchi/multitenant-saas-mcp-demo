package com.acme.tenancy.services;
import com.acme.tenancy.datatypes.ApiDtos.*;
import com.acme.tenancy.exceptions.ApiException;
import com.acme.tenancy.repositories.UserRepository;
import com.acme.tenancy.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
@Service
public class AuthService {
 private final UserRepository users; private final PasswordEncoder encoder; private final JwtService jwt;
 public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) { this.users=users; this.encoder=encoder; this.jwt=jwt; }
 public LoginResponse login(LoginRequest req) { var u=users.findByEmail(req.email().trim().toLowerCase()).orElseThrow(()->ApiException.unauthorized("Invalid email or password")); if(!encoder.matches(req.password(),u.getPasswordHash())) throw ApiException.unauthorized("Invalid email or password"); return new LoginResponse(jwt.create(u.getId(),u.isSuperadmin()),view(u)); }
 public UserView view(com.acme.tenancy.entities.UserAccount u) { return new UserView(u.getId(),u.getEmail(),u.getDisplayName(),u.isSuperadmin()); }
}
