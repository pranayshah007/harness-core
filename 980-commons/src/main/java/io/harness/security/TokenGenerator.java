/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.EncryptDecryptException;
import io.harness.exception.WingsException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEEncrypter;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

@OwnedBy(PL)
@Slf4j
@Singleton
public class TokenGenerator {
  private static final TemporalAmount EXP_DURATION = Duration.ofMinutes(30);
  private long expiryTimeOfLastJWT;
  private String lastEncryptedJWT;

  ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

  private final String accountId;
  private final JWEEncrypter encrypter;

  @Inject
  public TokenGenerator(String accountId, String accountSecret) {
    this.accountId = accountId;
    this.encrypter = makeEncrypter(accountSecret);
  }

  public String getToken(String scheme, String host, int port, String issuer) {
    return getToken(scheme + "://" + host + ":" + port, issuer);
  }

  public String getToken(String audience, String issuer) {
    // since the expiry time of JWT is EXP_DURATION, so we can send the same JWT till it expires (but keeping a buffer
    // of 5 mins for safe side).
    try {
      readWriteLock.readLock().lock();
      if (lastEncryptedJWT != null && expiryTimeOfLastJWT > System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)) {
        return lastEncryptedJWT;
      }
    } finally {
      readWriteLock.readLock().unlock();
    }

    try {
      readWriteLock.writeLock().lock();
      Instant now = Instant.now();
      JWTClaimsSet jwtClaims = new JWTClaimsSet.Builder()
                                   .issuer(issuer)
                                   .subject(accountId)
                                   .audience(audience)
                                   .expirationTime(Date.from(now.plus(EXP_DURATION)))
                                   .notBeforeTime(Date.from(now))
                                   .issueTime(Date.from(now))
                                   .jwtID(UUID.randomUUID().toString())
                                   .build();

      JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A128GCM).build();
      EncryptedJWT jwt = new EncryptedJWT(header, jwtClaims);

      try {
        jwt.encrypt(encrypter);
      } catch (JOSEException e) {
        log.error("", e);
      }

      expiryTimeOfLastJWT = jwtClaims.getExpirationTime().getTime();
      lastEncryptedJWT = jwt.serialize();
      return lastEncryptedJWT;
    } finally {
      readWriteLock.writeLock().unlock();
    }
  }

  private JWEEncrypter makeEncrypter(String accountSecret) {
    try {
      byte[] encodedKey = Hex.decodeHex(accountSecret.toCharArray());
      return new DirectEncrypter(new SecretKeySpec(encodedKey, 0, encodedKey.length, "AES"));
    } catch (DecoderException | KeyLengthException e) {
      throw new EncryptDecryptException("Failed to initialize token generator", e, WingsException.USER_SRE);
    }
  }
}
