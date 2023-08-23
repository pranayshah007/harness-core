/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.helpers.vault;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.helpers.ext.vault.VaultAppRoleLoginResult;

import software.wings.beans.BaseVaultConfig;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.google.common.annotations.VisibleForTesting;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;

@UtilityClass
@OwnedBy(PL)
@Slf4j
public class HashicorpVaultTokenCacheHelper {
  private final long TOKEN_CACHE_SIZE = 500L;
  private final double TOKEN_CACHE_EXPIRY_THRESHOLD = 0.99;
  public static final long MAX_CACHE_EXPIRY_DURATION_IN_SECONDS = 3600L;

  @Value
  @Builder
  private static class VaultTokenCacheValue {
    char[] token;
    long expiresIn;
  }

  @Value
  @EqualsAndHashCode
  @Builder
  private static class VaultTokenCacheKey {
    String accountIdentifier;
    String orgIdentifier;
    String projectIdentifier;
    String identifier;
  }

  /**
   * Uses cache with maximum size, and per entry expiration period
   * which is calculated from expiry timestamp obtained with a threshold defined in the class
   *
   * Expiry duration for an entry will update on either create or update of the entity
   *
   * Expiry Docs:
   * https://www.javadoc.io/doc/com.github.ben-manes.caffeine/caffeine/2.8.4/com/github/benmanes/caffeine/cache/Expiry.html
   * https://github.com/ben-manes/caffeine/wiki/Eviction#time-based
   * */
  private final Cache<VaultTokenCacheKey, VaultTokenCacheValue> hashicorpVaultAppRoleTokenCache =
      Caffeine.newBuilder()
          .maximumSize(TOKEN_CACHE_SIZE)
          .expireAfter(new Expiry<VaultTokenCacheKey, VaultTokenCacheValue>() {
            @Override
            public long expireAfterCreate(
                @NonNull VaultTokenCacheKey key, @NonNull VaultTokenCacheValue value, long currentTime) {
              return getExpiryWithLeewayInNanos(value.getExpiresIn());
            }

            @Override
            public long expireAfterUpdate(@NonNull VaultTokenCacheKey key, @NonNull VaultTokenCacheValue value,
                long currentTime, @NonNegative long currentDuration) {
              return getExpiryWithLeewayInNanos(value.getExpiresIn());
            }

            @Override
            public long expireAfterRead(@NonNull VaultTokenCacheKey key, @NonNull VaultTokenCacheValue value,
                long currentTime, @NonNegative long currentDuration) {
              return currentDuration;
            }
          })
          .build();

  @VisibleForTesting
  long getExpiryWithLeewayInNanos(long expiresIn) {
    long expiryInSeconds = MAX_CACHE_EXPIRY_DURATION_IN_SECONDS;
    if (expiresIn != 0) {
      expiryInSeconds = (long) ((double) expiresIn * TOKEN_CACHE_EXPIRY_THRESHOLD);
    }
    return TimeUnit.SECONDS.toNanos(expiryInSeconds);
  }

  public void putInAppRoleTokenCache(BaseVaultConfig vaultConfig, VaultAppRoleLoginResult vaultAppRoleLoginResult) {
    if (Objects.nonNull(vaultConfig) && Objects.nonNull(vaultAppRoleLoginResult)
        && isNotEmpty(vaultAppRoleLoginResult.getClientToken())) {
      VaultTokenCacheValue vaultTokenCacheValue = VaultTokenCacheValue.builder()
                                                      .token(vaultAppRoleLoginResult.getClientToken().toCharArray())
                                                      .expiresIn(vaultAppRoleLoginResult.getLeaseDuration())
                                                      .build();
      hashicorpVaultAppRoleTokenCache.put(getVaultAppRoleTokenCacheKey(vaultConfig), vaultTokenCacheValue);
    }
  }

  public String getAppRoleToken(BaseVaultConfig vaultConfig) {
    if (Objects.nonNull(vaultConfig)) {
      VaultTokenCacheValue vaultTokenCacheValue =
          hashicorpVaultAppRoleTokenCache.getIfPresent(getVaultAppRoleTokenCacheKey(vaultConfig));
      return Objects.isNull(vaultTokenCacheValue) ? null : String.valueOf(vaultTokenCacheValue.getToken());
    }
    return null;
  }

  public void invalidateAppRoleToken(BaseVaultConfig vaultConfig) {
    if (Objects.nonNull(vaultConfig)) {
      hashicorpVaultAppRoleTokenCache.invalidate(getVaultAppRoleTokenCacheKey(vaultConfig));
    }
  }

  private VaultTokenCacheKey getVaultAppRoleTokenCacheKey(BaseVaultConfig vaultConfig) {
    return VaultTokenCacheKey.builder()
        .accountIdentifier(vaultConfig.getAccountIdentifier())
        .projectIdentifier(vaultConfig.getProjectIdentifier())
        .orgIdentifier(vaultConfig.getOrgIdentifier())
        .identifier(vaultConfig.getIdentifier())
        .build();
  }
}
