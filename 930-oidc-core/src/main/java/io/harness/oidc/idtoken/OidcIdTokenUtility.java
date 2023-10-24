/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.idtoken;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.inject.Singleton;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
@Singleton
public class OidcIdTokenUtility {
  /**
   * This function generates the OIDC ID token using the given header, payload and private key.
   * ID token is generated as a JWT. Thus, it contains JWT header, the JWT claims will be the
   * given payload and the JWT is signed using the given RSA private key.
   *
   * @param oidcIdTokenHeaderStructure JWT header for the ID token
   * @param oidcIdTokenPayloadStructure JWT claims for the ID token
   * @param rsaPrivateKeyPEM used to sign the JWT
   * @return generate the JWT
   */
  public static String generateOidcIdToken(OidcIdTokenHeaderStructure oidcIdTokenHeaderStructure,
      OidcIdTokenPayloadStructure oidcIdTokenPayloadStructure, String rsaPrivateKeyPEM) {
    try {
      // Create an ObjectMapper with desired settings
      ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
      objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

      // Get the Header object into a Map
      Map<String, Object> headerMap = objectMapper.convertValue(oidcIdTokenHeaderStructure, Map.class);

      // Get the Payload object into a Map
      Map<String, Object> payloadMap = objectMapper.convertValue(oidcIdTokenPayloadStructure, Map.class);

      // Load the RSA private key from the String
      PrivateKey privateKey = loadPrivateKeyFromString(rsaPrivateKeyPEM);

      // Build the JWT using the private key
      String jwt = Jwts.builder()
                       .setHeader(headerMap)
                       .setClaims(payloadMap)
                       .signWith(SignatureAlgorithm.RS256, privateKey)
                       .compact();

      return jwt;
    } catch (Exception ex) {
      log.error("Encountered exception while generating OIDC ID Token for Aud {} : {} ",
          oidcIdTokenPayloadStructure.getAud(), ex);
    }

    return "";
  }

  /**
   * Utility method to capture the contents in the placeholder {}.
   * @param inputString string containing {} to be parsed
   * @return list of values contained within {}
   */
  public static List<String> capturePlaceholderContents(String inputString) {
    List<String> capturedValues = new ArrayList<>();

    // Define a regular expression pattern to match placeholders within curly braces
    String regex = "\\{([^}]+)\\}";

    // Create a Pattern object
    Pattern pattern = Pattern.compile(regex);

    // Create a Matcher to find and capture placeholders
    Matcher matcher = pattern.matcher(inputString);

    // Iterate through the matches
    while (matcher.find()) {
      String capturedValue = matcher.group(1); // Get the content within curly braces
      capturedValues.add(capturedValue);
    }

    return capturedValues;
  }

  /**
   * Function to load private key from PEM.
   * @param privateKeyPEM private key in PEM format
   * @return private key
   */
  private static PrivateKey loadPrivateKeyFromString(String privateKeyPEM) {
    try {
      String privateKey = privateKeyPEM.replaceAll("-----BEGIN [^-]+-----", "")
                              .replaceAll("-----END [^-]+-----", "")
                              .replaceAll("\\s", "");
      byte[] keyBytes = Base64.getDecoder().decode(privateKey);
      PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      return keyFactory.generatePrivate(keySpec);
    } catch (Exception ex) {
      log.error("Error while loading RSA private key from PEM");
    }

    return null;
  }
}
