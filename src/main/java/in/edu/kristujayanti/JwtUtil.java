package in.edu.kristujayanti;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Date;

public class JwtUtil {
    secretclass srt=new secretclass();

    private final String SECRET_KEY = srt.JWTSECRET;
    public String generateAccessToken(String email,long expmin, String role) {
        long nowMillis = System.currentTimeMillis();
        long expMillis = nowMillis + (expmin * 60 * 1000);

        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date(nowMillis))
                .setExpiration(new Date(expMillis))
                .claim("role",role)
                .signWith(SignatureAlgorithm.HS512, SECRET_KEY)
                .compact();
    }

    public String generateRefreshToken(String email,long days, String role) {
        long nowMillis = System.currentTimeMillis();
        long expMillis = nowMillis + (days * 24L * 60L * 60L * 1000L);  // 1 hour

        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date(nowMillis))
                .setExpiration(new Date(expMillis))
                .claim("role",role)
                .signWith(SignatureAlgorithm.HS512, SECRET_KEY)
                .compact();
    }

    public String extractEmail(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(SECRET_KEY)
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getSubject(); // email
        } catch (Exception e) {
            return null;
        }
    }

    public String extractRole(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(SECRET_KEY)
                    .parseClaimsJws(token)
                    .getBody();

            return claims.get("role",String.class);
        }catch (Exception e) {
            return null;
        }
    }
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
