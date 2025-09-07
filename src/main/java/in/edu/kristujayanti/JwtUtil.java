package in.edu.kristujayanti;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Date;

public class JwtUtil {
    secretclass srt=new secretclass();

    private final String SECRET_KEY = srt.JWTSECRET;
    public String generateToken(String email,long exp) {
        long nowMillis = System.currentTimeMillis();
        long expMillis = nowMillis + exp; // 1 hour

        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date(nowMillis))
                .setExpiration(new Date(expMillis))
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
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
