/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.impl;

import io.harness.agent.beans.AgentMtlsEndpoint;
import io.harness.agent.beans.AgentMtlsEndpoint.AgentMtlsEndpointKeys;
import io.harness.agent.beans.AgentMtlsEndpointDetails;
import io.harness.agent.beans.AgentMtlsEndpointRequest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.UnavailableFeatureException;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.AgentMtlsEndpointService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

/**
 * Exposes only get operations.
 *
 * Note:
 *    In environments that aren't fully configured with mTLS yet, creating endpoints with
 *    an unconfigured or default `agentMtlsSubdomain` can lead to potential issues.
 *    Additionally, when generating kubernetes yaml files (or verifying mTLS on datapath)
 *    it is necessary to verify if an mTLS endpoint exists for the account.
 *    Furthermore, we don't want a future misconfiguration break existing mTLS setups.
 *    This class was added to solve these issues by allowing read but no write operations.
 */
@Singleton
@ValidateOnExecution
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class AgentMtlsEndpointServiceReadOnlyImpl implements AgentMtlsEndpointService {
  protected static final String ERROR_ENDPOINT_FOR_ACCOUNT_NOT_FOUND_FORMAT =
      "Agent mTLS endpoint for account '%s' was not found.";
  protected static final String ERROR_NOT_AVAILABLE = "The requested feature is not available.";

  protected final HPersistence persistence;

  /**
   * Creates a new instance.
   * @param persistence the object used for data peristence.
   */
  @Inject
  public AgentMtlsEndpointServiceReadOnlyImpl(HPersistence persistence) {
    this.persistence = persistence;
  }

  @Override
  public AgentMtlsEndpointDetails createEndpointForAccount(String accountId, AgentMtlsEndpointRequest endpointRequest) {
    throw new UnavailableFeatureException(ERROR_NOT_AVAILABLE);
  }

  @Override
  public AgentMtlsEndpointDetails updateEndpointForAccount(String accountId, AgentMtlsEndpointRequest endpointRequest) {
    throw new UnavailableFeatureException(ERROR_NOT_AVAILABLE);
  }

  @Override
  public AgentMtlsEndpointDetails patchEndpointForAccount(String accountId, AgentMtlsEndpointRequest patchRequest) {
    throw new UnavailableFeatureException(ERROR_NOT_AVAILABLE);
  }

  @Override
  public AgentMtlsEndpointDetails getEndpointForAccount(String accountId) {
    AgentMtlsEndpoint endpoint =
        persistence.createQuery(AgentMtlsEndpoint.class).field(AgentMtlsEndpointKeys.accountId).equal(accountId).get();

    if (endpoint == null) {
      throw new EntityNotFoundException(String.format(ERROR_ENDPOINT_FOR_ACCOUNT_NOT_FOUND_FORMAT, accountId));
    }

    return this.buildEndpointDetails(endpoint);
  }

  @Override
  public AgentMtlsEndpointDetails getEndpointForAccountOrNull(String accountId) {
    AgentMtlsEndpoint endpoint =
        persistence.createQuery(AgentMtlsEndpoint.class).field(AgentMtlsEndpointKeys.accountId).equal(accountId).get();

    return endpoint == null ? null : this.buildEndpointDetails(endpoint);
  }

  @Override
  public boolean deleteEndpointForAccount(String accountId) {
    throw new UnavailableFeatureException(ERROR_NOT_AVAILABLE);
  }

  @Override
  public boolean isDomainPrefixAvailable(String domainPrefix) {
    throw new UnavailableFeatureException(ERROR_NOT_AVAILABLE);
  }

  protected AgentMtlsEndpointDetails buildEndpointDetails(AgentMtlsEndpoint endpoint) {
    return AgentMtlsEndpointDetails.builder()
        .uuid(endpoint.getUuid())
        .accountId(endpoint.getAccountId())
        .fqdn(endpoint.getFqdn())
        .caCertificates(endpoint.getCaCertificates())
        .mode(endpoint.getMode())
        .build();
  }
}
