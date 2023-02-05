/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.agent.utils;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.agent.beans.AgentMtlsEndpointDetails;
import io.harness.agent.beans.AgentMtlsMode;
import io.harness.annotations.dev.OwnedBy;
import io.harness.service.intfc.AgentMtlsEndpointService;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(DEL)
@Slf4j
public class AgentMtlsVerifier {
  // Use optional as Caffeine cache doesn't cache null value.
  private final LoadingCache<String, Optional<AgentMtlsCacheEntry>> mtlsCache =
      Caffeine.newBuilder()
          .maximumSize(10000)
          .expireAfterWrite(3, TimeUnit.MINUTES)
          .build(this::loadMtlsCacheEntryForAccount);

  private final AgentMtlsEndpointService agentMtlsEndpointService;

  @Inject
  public AgentMtlsVerifier(AgentMtlsEndpointService agentMtlsEndpointService) {
    this.agentMtlsEndpointService = agentMtlsEndpointService;
  }

  @Data
  @Builder
  private static class AgentMtlsCacheEntry {
    private final String fqdn;
    private final AgentMtlsMode mode;
  }

  @VisibleForTesting
  protected void invalidateCacheFor(String accountId) {
    this.mtlsCache.invalidate(accountId);
  }

  public boolean isValidRequest(String accountId, String agentMtlsAuthorityHeader) {
    if (accountId == null) {
      throw new IllegalArgumentException("accountId can't be null");
    }

    Optional<AgentMtlsCacheEntry> entry = this.mtlsCache.get(accountId);
    boolean isNoMtls = isEmpty(agentMtlsAuthorityHeader);

    // no mTLS configured -> no mTLS accepted (as mTLS connection would always be via another account's endpoint)
    if (entry == null || !entry.isPresent()) {
      return isNoMtls;
    }

    // in LOOSE mode we accept non-mtls connections
    if (entry.get().mode == AgentMtlsMode.LOOSE && isNoMtls) {
      return true;
    }

    // (STRICT) or (LOOSE with agentMtlsAuthorityHeader) - either way it has to match the configured fqdn.
    return Objects.equals(entry.get().fqdn, agentMtlsAuthorityHeader);
  }

  private Optional<AgentMtlsCacheEntry> loadMtlsCacheEntryForAccount(String accountId) {
    AgentMtlsEndpointDetails endpoint = this.agentMtlsEndpointService.getEndpointForAccountOrNull(accountId);
    if (endpoint == null) {
      return Optional.empty();
    }

    return Optional.of(AgentMtlsCacheEntry.builder().fqdn(endpoint.getFqdn()).mode(endpoint.getMode()).build());
  }
}
