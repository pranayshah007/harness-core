/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.validation;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.MiningPatternConfig;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class CIMiningPatternJob {
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;

  private static final int CACHE_UPDATE_PERIOD = 10;

  private final LoadingCache<String, Set<String>> maliciousMiningPatternsCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(CACHE_UPDATE_PERIOD, TimeUnit.MINUTES)
          .build(new CacheLoader<String, Set<String>>() {
            @Override
            public Set<String> load(String blank) {
              return downloadFromGCS();
            }
          });

  public Set<String> getMaliciousMiningPatterns() {
    try {
      return maliciousMiningPatternsCache.get("");
    } catch (Exception e) {
      return new HashSet<>();
    }
  }

  private Set<String> downloadFromGCS() {
    Set<String> maliciousMiningPatterns = new HashSet<>();
    MiningPatternConfig miningPatternConfig = ciExecutionServiceConfig.getMiningPatternConfig();

    if (Objects.isNull(miningPatternConfig)) {
      log.warn("Couldn't get GCS credentials from configuration");
      return maliciousMiningPatterns;
    }

    String projectId = miningPatternConfig.getProjectId();
    String bucketName = miningPatternConfig.getBucketName();
    String gcsCredsBase64 = miningPatternConfig.getGcsCreds();
    String objectName = "suspiciousMiningPatterns.txt";

    if (isBlank(projectId) || isBlank(bucketName) || isBlank(gcsCredsBase64)) {
      log.warn("Couldn't get GCS credentials from configuration");
      return maliciousMiningPatterns;
    }

    try {
      byte[] gcsCredsDecoded = Base64.getDecoder().decode(gcsCredsBase64);

      Credentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(gcsCredsDecoded));
      Storage storage =
          StorageOptions.newBuilder().setCredentials(credentials).setProjectId(projectId).build().getService();

      byte[] content = storage.readAllBytes(bucketName, objectName);
      String[] patterns = new String(content, StandardCharsets.UTF_8).split("\n");

      if (patterns.length != 0) {
        maliciousMiningPatterns.clear();
      }

      for (String pattern : patterns) {
        if (isNotEmpty(pattern)) {
          maliciousMiningPatterns.add(pattern.trim());
        }
      }
    } catch (Exception e) {
      log.error("Exception occurred while fetching mining patterns: ", e);
    }
    return maliciousMiningPatterns;
  }
}
