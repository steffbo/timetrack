import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;

class Reference {
 public static void main(String[] args) {
  String secret="your-secret-key-change-this-in-production-min-256-bits";
  var key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  if(args.length>0){
   var claims=Jwts.parser().verifyWith(key).clock(()->new Date(1894708800000L)).build()
    .parseSignedClaims(args[0]).getPayload();
   System.out.println(claims.getSubject()+" "+claims.get("email"));
  } else {
   for(int length:new int[]{32,48,64}){
    var k=Keys.hmacShaKeyFor("x".repeat(length).getBytes(StandardCharsets.UTF_8));
    System.out.println(length+" "+Jwts.builder().subject("42").claim("email","java@test.local")
     .claim("authorities","ROLE_ADMIN").issuedAt(new Date(1894708800000L))
     .expiration(new Date(1894795200000L)).claim("impersonatedBy",7L).signWith(k).compact());
   }
  }
 }
}
