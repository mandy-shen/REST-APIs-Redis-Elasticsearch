package com.monhong.demo.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.http.HttpHeaders;

import java.security.*;
import java.time.Instant;
import java.util.Date;

/**
 * JwtOAuth: Digital signatures
 * https://connect2id.com/products/nimbus-jose-jwt/algorithm-selection-guide
 *
 * use cases:
 * 1. ID tokens (OpenID Connect)
 * 2. Self-contained access tokens (OAuth 2.0)
 * 3. Passing security assertions and tokens between domains
 * 4. Data which integrity and authenticity must be verifiable by others
 *
 * ps. ID tokens != access tokens
  */
public class JwtOAuth {
	private static final String subject = "Mandy";
	private static final String issuer = "mandy.com";
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
			publicKey = (PublicKey) kp.getPublic();
			privateKey = (PrivateKey) kp.getPrivate();
		}

		// new token
		return Jwts.builder().setSubject(subject)
				.setIssuer(issuer)
				.setExpiration(Date.from(Instant.now().plusSeconds(60 * 10))) // 10min
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
			e.printStackTrace();
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
