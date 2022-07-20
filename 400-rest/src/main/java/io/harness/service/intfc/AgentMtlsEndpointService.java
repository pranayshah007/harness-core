/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.intfc;

import io.harness.agent.beans.AgentMtlsEndpointDetails;
import io.harness.agent.beans.AgentMtlsEndpointRequest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;

import javax.annotation.Nullable;

/**
 * An abstraction of a service that allows managing agent mTLS endpoints.
 */
@OwnedBy(HarnessTeam.DEL)
public interface AgentMtlsEndpointService {
  /**
   * Creates the mTLS endpoint for an account.
   *
   * @param accountId The account id.
   * @param endpointRequest The requested configuration of the endpoint.
   * @return The details of the created endpoint.
   *
   * @throws InvalidRequestException If there already exists an endpoint for the account, or the request is invalid.
   */
  AgentMtlsEndpointDetails createEndpointForAccount(String accountId, AgentMtlsEndpointRequest endpointRequest);

  /**
   * Updates the existing mTLS endpoint for an account.
   *
   * @param accountId The account id.
   * @param endpointRequest The requested updated configuration of the endpoint.
   * @return The details of the updated endpoint.
   *
   * @throws InvalidRequestException If the request is invalid.
   * @throws EntityNotFoundException If there is no existing endpoint for the account.
   */
  AgentMtlsEndpointDetails updateEndpointForAccount(String accountId, AgentMtlsEndpointRequest endpointRequest);

  /**
   * Updates the existing mTLS endpoint for an account with only the properties that are specified in the
   * request.
   *
   * @param accountId The account id.
   * @param endpointRequest The requested configuration update for the endpoint (Keep field null if it should be
   *     ignored).
   * @return The details of the updated endpoint.
   *
   * @throws InvalidRequestException If the request is invalid.
   * @throws EntityNotFoundException If there is no existing endpoint for the account.
   */
  AgentMtlsEndpointDetails patchEndpointForAccount(String accountId, AgentMtlsEndpointRequest endpointRequest);

  /**
   * Returns the mTLS endpoint for the account.
   *
   * @param accountId The account id.
   * @return The details of the requested mTLS endpoint.
   *
   * @throws EntityNotFoundException If there is no existing endpoint for the account.
   */
  AgentMtlsEndpointDetails getEndpointForAccount(String accountId);

  /**
   * Returns the mTLS endpoint for the account if it exists.
   *
   * @param accountId The account id.
   * @return The details of the requested mTLS endpoint or null if it doesn't exist.
   */
  @Nullable AgentMtlsEndpointDetails getEndpointForAccountOrNull(String accountId);

  /**
   * Removes the mTLS endpoint for the account.
   *
   * @param accountId The account id.
   * @return True if and only if the endpoint for the account existed and got removed successfully.
   */
  boolean deleteEndpointForAccount(String accountId);

  /**
   * Checks whether the provided domain prefix is available.
   *
   * @param domainPrefix The domain prefix to check.
   * @return True if and only if there is no existing mTLS endpoint that uses the provided domain prefix.
   *
   * @throws InvalidRequestException If the domain prefix is invalid.
   */
  boolean isDomainPrefixAvailable(String domainPrefix);
}
