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
  private static final String MINING_PATTERENS_FILE = "suspiciousMiningPatterns.txt";
  private static final String VALID_DOMAINS_FILE = "validDomains.txt";
  private static final String WHITE_LISTED_DOMAINS_FILE = "whiteListedDomains.txt";

  private final LoadingCache<String, Set<String>> maliciousMiningPatternsCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(CACHE_UPDATE_PERIOD, TimeUnit.MINUTES)
          .build(new CacheLoader<String, Set<String>>() {
            @Override
            public Set<String> load(String blank) {
              return initializeMiningPatterns();
            }
          });

  private final LoadingCache<String, Set<String>> validDomains =
      CacheBuilder.newBuilder()
          .expireAfterWrite(CACHE_UPDATE_PERIOD, TimeUnit.MINUTES)
          .build(new CacheLoader<String, Set<String>>() {
            @Override
            public Set<String> load(String blank) {
              return initializeValidDomains();
            }
          });

  private final LoadingCache<String, Set<String>> whiteListedDomains =
      CacheBuilder.newBuilder()
          .expireAfterWrite(CACHE_UPDATE_PERIOD, TimeUnit.MINUTES)
          .build(new CacheLoader<String, Set<String>>() {
            @Override
            public Set<String> load(String blank) {
              return initializeWhiteListedDomains();
            }
          });

  public Set<String> getMaliciousMiningPatterns() {
    try {
      return maliciousMiningPatternsCache.get("");
    } catch (Exception e) {
      return new HashSet<>();
    }
  }

  public Set<String> getValidDomains() {
    try {
      return validDomains.get("");
    } catch (Exception e) {
      return new HashSet<>();
    }
  }

  public Set<String> getWhiteListed() {
    try {
      return whiteListedDomains.get("");
    } catch (Exception e) {
      return new HashSet<>();
    }
  }

  private Set<String> initializeMiningPatterns() {
    Set<String> maliciousMiningPatterns = new HashSet<>();

    try {
      byte[] fileContent = downloadFromGCS(MINING_PATTERENS_FILE);

      String[] patterns = new String(fileContent, StandardCharsets.UTF_8).split("\n");

      if (patterns == null || patterns.length != 0) {
        maliciousMiningPatterns.clear();
      }

      for (String pattern : patterns) {
        if (isNotEmpty(pattern)) {
          maliciousMiningPatterns.add(pattern.trim());
        }
      }

    } catch (Exception e) {
      log.error("Error initialize mining patterns for CI hosted");
      return maliciousMiningPatterns;
    }

    log.info("Successfully initialize mining patterns. Set size {}", maliciousMiningPatterns.size());
    return maliciousMiningPatterns;
  }

  private Set<String> initializeValidDomains() {
    Set<String> validDomains = new HashSet<>();

    try {
      byte[] fileContent = downloadFromGCS(VALID_DOMAINS_FILE);

      String[] patterns = new String(fileContent, StandardCharsets.UTF_8).split("\n");

      if (patterns == null || patterns.length != 0) {
        validDomains.clear();
      }

      for (String pattern : patterns) {
        if (isNotEmpty(pattern)) {
          validDomains.add(pattern.trim());
        }
      }

    } catch (Exception e) {
      log.error("Error initialize valid domains set for CI hosted");
      return validDomains;
    }

    log.info("Successfully initialize valid domains. Set size {}", validDomains.size());
    return validDomains;
  }

  private Set<String> initializeWhiteListedDomains() {
    Set<String> whiteListed = new HashSet<>();

    try {
      byte[] fileContent = downloadFromGCS(WHITE_LISTED_DOMAINS_FILE);

      String[] patterns = new String(fileContent, StandardCharsets.UTF_8).split("\n");

      if (patterns == null || patterns.length != 0) {
        whiteListed.clear();
      }

      for (String pattern : patterns) {
        if (isNotEmpty(pattern)) {
          whiteListed.add(pattern.trim());
        }
      }

    } catch (Exception e) {
      log.error("Error initialize white listed domains set for CI hosted");
      return whiteListed;
    }

    log.info("Successfully initialize white listed domains. Set size {}", whiteListed.size());
    return whiteListed;
  }

  private byte[] downloadFromGCS(String fileName) {
    byte[] fileContent = null;
    MiningPatternConfig miningPatternConfig = ciExecutionServiceConfig.getMiningPatternConfig();

    if (Objects.isNull(miningPatternConfig)) {
      log.warn("Couldn't get GCS credentials from configuration");
      return fileContent;
    }

    String projectId = miningPatternConfig.getProjectId();
    String bucketName = miningPatternConfig.getBucketName();
    String gcsCredsBase64 = miningPatternConfig.getGcsCreds();
    String objectName = fileName;

    if (isBlank(projectId) || isBlank(bucketName) || isBlank(gcsCredsBase64)) {
      log.warn("Couldn't get GCS credentials from configuration");
      return fileContent;
    }

    try {
      byte[] gcsCredsDecoded = Base64.getDecoder().decode(gcsCredsBase64);

      Credentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(gcsCredsDecoded));
      Storage storage =
          StorageOptions.newBuilder().setCredentials(credentials).setProjectId(projectId).build().getService();

      byte[] content = storage.readAllBytes(bucketName, objectName);
      return content;

    } catch (Exception e) {
      log.error("Exception occurred while fetching file {}", fileName, e);
      return null;
    }
  }
}
