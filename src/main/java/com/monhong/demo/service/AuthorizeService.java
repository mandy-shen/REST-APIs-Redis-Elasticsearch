package com.monhong.demo.service;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestHeader;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

@Service
public class AuthorizeService {
	
	private static String finalKey = "0123456789abcdef";
	
	public String getToken() throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
			InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
		
		String initVector = "RandomInitVector";

		// create token(Sample token)
		JSONObject object = new JSONObject();
		object.put("organization", "Info7255.com");
		object.put("user", "Mandy");

		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.MINUTE, 10); // after 10 min, expired
		Date date = calendar.getTime();
		SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		object.put("ttl", df.format(date));

		// Partial token created
		String token = object.toString();

		IvParameterSpec iv = new IvParameterSpec(initVector.getBytes(StandardCharsets.UTF_8));
		SecretKeySpec skeySpec = new SecretKeySpec(finalKey.getBytes(StandardCharsets.UTF_8), "AES");
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
		cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);

		// encrypting token
		byte[] encrypted = cipher.doFinal(token.getBytes());

		// encoded token (Base64 encoding)
		return Base64.encodeBase64String(encrypted);
	}

	public String authorizeToken(@RequestHeader HttpHeaders headers) {
		String token = headers.getFirst("Authorization");
		if (token == null || token.isEmpty()) {
			return "No token Found";
		}

		String token1 = "";
		if (!token.contains("Bearer ")) {
			return "Improper Format of Token";
		}

		token1 = token.substring(7);
		System.out.println("token value is " + token1);

		boolean authorized = authorize(token1);
		System.out.println("Authorized value is " + authorized);

		if (!authorized) {
			return "Token is Expired or Invalid Token";
		}
		return "Valid Token";
	}

	private boolean authorize(String token) {
		try {
			System.out.println("token coming in authorize" + token);
			String initVector = "RandomInitVector";
			IvParameterSpec iv = new IvParameterSpec(initVector.getBytes(StandardCharsets.UTF_8));
			SecretKeySpec skeySpec = new SecretKeySpec(finalKey.getBytes(StandardCharsets.UTF_8), "AES");

			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
			cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);
			byte[] original = cipher.doFinal(Base64.decodeBase64(token));
			String entityDecoded = new String(original);

			System.out.println("***Entity Decoded is " + entityDecoded);

			JSONObject jsonobj = new JSONObject(entityDecoded);
			Object arrayOfTests = jsonobj.get("ttl");
			Calendar calendar = Calendar.getInstance();
			Date date = calendar.getTime();
			String getDate = arrayOfTests.toString();
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

			Date end = formatter.parse(getDate);
			Date start = formatter.parse(formatter.format(date));

			System.out.println(start.toString());
			System.out.println(end.toString());

			if (!start.before(end)) {
				System.out.println("The Token Validity has expired");
				return false;
			}

		} catch (Exception e) {
			System.out.println("inside exception---" + e);
			return false;
		}

		return true;
	}
}
