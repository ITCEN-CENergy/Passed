package com.gooroomees.neulbomgil_backend.global.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import com.gooroomees.neulbomgil_backend.domain.auth.entity.UserAuth;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtProvider {
    @Value("${application.security.jwt.secret}")
    private String secretKey;

    @Value("${application.security.jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${application.security.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    public Long extractUserId(String token) {
        return Long.parseLong(extractClaim(token, Claims::getSubject));
    }

    public String extractUsername(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("email", String.class);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, accessTokenExpiration);
    }

    // Refresh 토큰 생성
    public String generateRefreshToken(UserAuth user) {
        return Jwts
                .builder()
                .subject(String.valueOf(user.getUserId()))
                .claim("type", "refresh")
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSignInKey())
                .compact();
    }

    public String generateAccessToken(UserAuth user) {
        return Jwts
                .builder()
                .subject(String.valueOf(user.getUserId()))
                .claim("email", user.getUsername())
                .claim("type", "access")
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSignInKey())
                .compact();
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
        return Jwts
                .builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey())
                .compact();
    }

    // Jwt 토큰 유효성 검증
    public boolean isTokenValid(String token) {
        // 토큰 검증
        try {
            Jwts.parser()
                    .verifyWith(getSignInKey())
                    .build()
                    .parseSignedClaims(token);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public boolean isRefreshToken(String token) {
        String type = extractType(token);
        return type.equals("refresh");
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private String extractType(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("type", String.class);
    }

    private Claims extractAllClaims(String token) {
        return Jwts
                .parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 서명에 사용할 키 생성
    private SecretKey getSignInKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}