/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExplanationException;

import com.google.api.client.auth.oauth2.DataStoreCredentialRefreshListener;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.util.store.DataStore;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.Builder;

@OwnedBy(HarnessTeam.CDP)
public class GcpAccessTokenSupplier implements Supplier<String> {
  private final String serviceAccountJsonKey;
  private final DataStore<StoredCredential> cache;
  private final Clock clock;
  private final GoogleCredential googleCredential;

  @Builder
  public GcpAccessTokenSupplier(String serviceAccountJsonKey, Function<String, GoogleCredential> jsonKeyToCredential,
      DataStore<StoredCredential> cache, Clock clock) {
    this.serviceAccountJsonKey = serviceAccountJsonKey;
    this.googleCredential = copyAndAddRefreshListener(jsonKeyToCredential.apply(serviceAccountJsonKey), clock, cache);
    this.cache = cache;
    this.clock = clock;
  }

  @Override
  public String get() {
    try {
      return tryToGetToken();
    } catch (IOException e) {
      throw new ExplanationException("Could not get token from cache. Should not happen with in-memory caches.", e);
    }
  }

  /**
   * Null if Using default application credentials
   */
  public Optional<String> getServiceAccountJsonKey() {
    return Optional.ofNullable(serviceAccountJsonKey);
  }

  private String tryToGetToken() throws IOException {
    StoredCredential storedCredential = cache.get(googleCredential.getServiceAccountId());
    if (isNullOrExpired(storedCredential)) {
      googleCredential.refreshToken();
      return googleCredential.getAccessToken();
    } else {
      return storedCredential.getAccessToken();
    }
  }

  private boolean isNullOrExpired(StoredCredential storedCredential) {
    if (storedCredential == null) {
      return true;
    }
    Instant expirationTime = Instant.ofEpochMilli(storedCredential.getExpirationTimeMilliseconds());
    return clock.instant().isAfter(expirationTime.minus(Duration.ofMinutes(1L)));
  }

  private GoogleCredential copyAndAddRefreshListener(
      GoogleCredential googleCredential, Clock clock, DataStore<StoredCredential> cache) {
    // This could be null if we are using workload identity. In this case skip the cache, since we cannot reliable get
    // account identifier to use as cache key
    if (googleCredential.getServiceAccountId() == null) {
      return googleCredential;
    }

    return new GoogleCredential.Builder()
        .setJsonFactory(googleCredential.getJsonFactory())
        .setTransport(googleCredential.getTransport())
        .setServiceAccountId(googleCredential.getServiceAccountId())
        .setServiceAccountScopes(googleCredential.getServiceAccountScopes())
        .setServiceAccountPrivateKey(googleCredential.getServiceAccountPrivateKey())
        .setServiceAccountPrivateKeyId(googleCredential.getServiceAccountPrivateKeyId())
        .setTokenServerEncodedUrl(googleCredential.getTokenServerEncodedUrl())
        .setServiceAccountProjectId(googleCredential.getServiceAccountProjectId())
        .addRefreshListener(new DataStoreCredentialRefreshListener(googleCredential.getServiceAccountId(), cache))
        .setClock(clock::millis)
        .build();
  }
}
