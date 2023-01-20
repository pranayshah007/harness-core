/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.ADFS_ERROR;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.AdfsAuthException;
import io.harness.exception.KeyManagerBuilderException;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Responsible for generating and verifying Jwt signed request for ADFS using x509 certificates and Pkcs8 format private
 * key
 *
 * this JWT Token will be used further to get access token from ADFS.
 */
@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class ADFSAuthHelper {
  private static final String KEY_ALGORITHM_RSA = "RSA";
  private static final int NO_OF_DAYS = 2;

  public static String generateJwtSignedRequestWithCertificate(
      String certificateContent, String keyContent, String clientId, String adfsUrl) {
    try {
      log.debug("ADFS: Getting certs....");
      Map<String, Object> context = getSslContextFromX509CertAndPkcs8Key(certificateContent, keyContent);

      log.debug("ADFS: Creating JWT Header...");
      Map<String, Object> jwtHeader = createJWTHeader((Certificate) context.get("certificate"));
      log.info("ADFS: jwt header created");

      log.debug("ADFS: Creating JWT Claims...");
      Map<String, String> jwtClaims = createJwtClaims(clientId, adfsUrl);
      log.info("ADFS: jwt claims created");

      log.debug("ADFS: Creating Signed JWT Request...");
      String jwtSignedRequest = JWTTokenServiceUtils.generateJWTToken(
          jwtClaims, jwtHeader, TimeUnit.DAYS.toMillis(NO_OF_DAYS), (RSAPrivateKey) context.get("privateKey"));
      log.info("ADFS: jwt_signed_request");

      log.debug("ADFS: Verifying the Signed JWT Request...");
      JWTTokenServiceUtils.verifyJWTToken(jwtSignedRequest, (RSAPublicKey) context.get("publicKey"), clientId);
      log.info("ADFS: decoded_jwt_signed_request");
      return jwtSignedRequest;
    } catch (AdfsAuthException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AdfsAuthException(
          String.format("Error while generating jwt token: %s", ExceptionUtils.getMessage(ex)), ADFS_ERROR, USER, ex);
    }
  }

  private static Map<String, Object> getSslContextFromX509CertAndPkcs8Key(
      String certificateContent, String privateKeyContent) {
    try {
      Map<String, Object> context = new HashMap<>();
      PKCS8EncodedKeySpec pkcs8keySpec;
      try {
        pkcs8keySpec = X509KeyManagerBuilder.loadPkcs8EncodedPrivateKeySpecFromPem(privateKeyContent);
      } catch (KeyManagerBuilderException ex) {
        throw new AdfsAuthException(
            String.format("Failed to load Pkcs8 encoded private key spec: %s", ExceptionUtils.getMessage(ex)),
            ADFS_ERROR, USER, ex);
      }
      KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM_RSA);
      PrivateKey privateKey;
      try {
        // generate private key
        privateKey = keyFactory.generatePrivate(pkcs8keySpec);
      } catch (InvalidKeySpecException ex) {
        throw new AdfsAuthException(
            String.format("Failed to generate private key due to invalid spec: %s", ExceptionUtils.getMessage(ex)),
            ADFS_ERROR, USER, ex);
      }
      // getting public key spec from private key
      RSAPrivateCrtKey rsaPrivateCrtKey = (RSAPrivateCrtKey) privateKey;
      RSAPublicKeySpec publicKeySpec =
          new RSAPublicKeySpec(rsaPrivateCrtKey.getModulus(), rsaPrivateCrtKey.getPublicExponent());

      // generating public key : keyFactory.generatePublic(publicKeySpec)
      try {
        context.put("certificate", X509KeyManagerBuilder.loadCertificatesFromPemString(certificateContent)[0]);
      } catch (KeyManagerBuilderException ex) {
        throw new AdfsAuthException(
            String.format("Failed to load x509 certificate provided: %s", ExceptionUtils.getMessage(ex)), ADFS_ERROR,
            USER, ex);
      }
      context.put("privateKey", privateKey);
      try {
        context.put("publicKey", keyFactory.generatePublic(publicKeySpec));
      } catch (InvalidKeySpecException ex) {
        throw new AdfsAuthException(
            String.format("Failed to generate public key due to invalid spec: %s", ExceptionUtils.getMessage(ex)),
            ADFS_ERROR, USER, ex);
      }
      return context;
    } catch (AdfsAuthException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AdfsAuthException(ExceptionUtils.getMessage(ex), ADFS_ERROR, USER, ex);
    }
  }

  private static Map<String, Object> createJWTHeader(Certificate certificate) {
    try {
      Map<String, Object> headers = new HashMap<>();
      String kid = DigestUtils.sha1Hex(certificate.getEncoded()).toUpperCase();
      headers.put("alg", "RS256");
      headers.put("typ", "JWT");
      headers.put("kid", kid);
      return headers;
    } catch (CertificateEncodingException ex) {
      throw new AdfsAuthException(
          String.format("Failed to encode the loaded x509 certificate: %s", ExceptionUtils.getMessage(ex)), ADFS_ERROR,
          USER, ex);
    }
  }

  private static Map<String, String> createJwtClaims(String clientId, String adfsUrl) {
    Map<String, String> claims = new HashMap<>();
    claims.put("iss", clientId);
    claims.put("sub", clientId);
    claims.put("aud", adfsUrl);
    claims.put("jti", UUIDGenerator.generateUuid());
    // add exp and iat claims later
    return claims;
  }
}
