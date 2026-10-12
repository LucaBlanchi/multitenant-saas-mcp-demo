package com.acme.tenancy.config;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.*;
import java.util.Arrays;
@Configuration
public class AppConfig implements WebMvcConfigurer {
 private final String allowedOrigins;
 public AppConfig(@Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5174,http://127.0.0.1:5173,http://127.0.0.1:5174}") String allowedOrigins) { this.allowedOrigins=allowedOrigins; }
 @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
 @Override public void addCorsMappings(CorsRegistry registry) {
  CorsRegistration cors = registry.addMapping("/**").allowedMethods("GET","POST","PUT","DELETE","OPTIONS").allowedHeaders("Authorization","Content-Type","X-Tenant-Id","Accept","MCP-Protocol-Version","Mcp-Session-Id","Last-Event-ID").exposedHeaders("Mcp-Session-Id","MCP-Protocol-Version","WWW-Authenticate","Link");
  String[] origins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(origin -> !origin.isEmpty()).toArray(String[]::new);
  if (origins.length > 0) cors.allowedOrigins(origins);
 }
}
