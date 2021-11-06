package com.monhong.demo.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.http.HttpHeaders;

import java.security.*;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * JwtOAuth: Digital signatures
 * https://connect2id.com/products/nimbus-jose-jwt/algorithm-selection-guide
 * https://wstutorial.com/misc/jwt-java-public-key-rsa.html
 * ps. ID tokens != access tokens
 *
 * use cases:
 * 1. ID tokens (OpenID Connect)
 * 2. Self-contained access tokens (OAuth 2.0)
 * 3. Passing security assertions and tokens between domains
 * 4. Data which integrity and authenticity must be verifiable by others
 *
 * two ways to verify token signature:
 * 1. https://jwt.io/
 * 2. https://oauth2.googleapis.com/tokeninfo?id_token=your_generated_token
  */
public class JwtOAuth {

	private static final String subject = "user123"; // clientId
	private static final String issuer = "info7255"; // auth server provider
	private static final String audience = "mandy.com"; // resource provider
	private static final String jwtId = "jwtId123";
	private static PublicKey publicKey;
	private static PrivateKey privateKey;

	// server side(issuer): use privateKey to generate new token
	// Always keep your private keys secret!!!!
	// do not public your token!!!!, this is just for demo.
	public static String genJwt() throws NoSuchAlgorithmException {

		// generate public/private key pair
		// it should be generated for one time, just for demo.
		if (privateKey == null) {
			// The recommended RSA key size is 2048 bits
			KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("RSA");
			keyGenerator.initialize(2048);

			KeyPair kp = keyGenerator.genKeyPair();
			publicKey = kp.getPublic();
			privateKey = kp.getPrivate();

			// encode key pairs, so that to minimum the size and verify the signature on jwt.io website
			System.out.println("---------------------------------------");
			System.out.println("publicKey: " + Base64.getEncoder().encodeToString(publicKey.getEncoded()));
			System.out.println("privateKey: " + Base64.getEncoder().encodeToString(privateKey.getEncoded()));
			System.out.println("---------------------------------------");
		}

		// new token
		return Jwts.builder().setHeaderParam("type", "JWT")
				.setSubject(subject)
				.setIssuer(issuer)
				.claim("name", "Mandy")
				.setAudience(audience)
				.setId(jwtId)
				.setIssuedAt(Date.from(Instant.now()))
				.setExpiration(Date.from(Instant.now().plusSeconds(60 * 10))) // 10min -- too long, just for demo.
				// RS256 with privateKey
				.signWith(SignatureAlgorithm.RS256, privateKey).compact();
	}

	// client side: use publicKey to decode token
	// server side(issuer) gives the publicKey to client side, so that client side can decode token.
	private static boolean isValidJwt(String jwt) {
		if (publicKey == null)
			return false;

		if (jwt == null || jwt.isBlank())
			return false;

		try {
			Claims claims = Jwts.parser().setSigningKey(publicKey).parseClaimsJws(jwt).getBody();

			if (!subject.equals(claims.getSubject()))
				return false;

			if (!issuer.equals(claims.getIssuer()))
				return false;

			Date expiration = claims.getExpiration();
			Date now = Date.from(Instant.now());
			System.out.println("expiration: " + expiration);

			return now.before(expiration);
		} catch (Exception e) {
			System.out.println("error: " + e.getMessage());
		}
		return false;
	}

	public static String authorizeToken(HttpHeaders headers) {
		String authorization = headers.getFirst("Authorization");
		if (authorization == null || authorization.isBlank()) {
			return "No token Found";
		}

		if (!authorization.contains("Bearer ")) {
			return "Improper Format of Token";
		}

		String token = authorization.substring(7);
		System.out.println("token => " + token);

		if (!isValidJwt(token)) {
			return "Token is Expired or Invalid Token";
		}
		return "Valid Token";
	}

}
