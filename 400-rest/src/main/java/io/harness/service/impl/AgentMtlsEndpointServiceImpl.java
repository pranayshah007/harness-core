/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.agent.beans.AgentMtlsEndpoint;
import io.harness.agent.beans.AgentMtlsEndpoint.AgentMtlsEndpointKeys;
import io.harness.agent.beans.AgentMtlsEndpointDetails;
import io.harness.agent.beans.AgentMtlsEndpointRequest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.DuplicateKeyException;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Singleton
@ValidateOnExecution
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class AgentMtlsEndpointServiceImpl extends AgentMtlsEndpointServiceReadOnlyImpl {
  private static final UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_ALL_SCHEMES);

  private final String agentMtlsSubdomain;

  /**
   * Creates a new instance.
   * @param persistence the object used for data peristence.
   * @param agentMtlsSubdomain the mTLS subdomain to use for mTLS endpoints.
   */
  @Inject
  public AgentMtlsEndpointServiceImpl(
      HPersistence persistence, @Named("agentMtlsSubdomain") String agentMtlsSubdomain) {
    super(persistence);

    if (isBlank(agentMtlsSubdomain)) {
      throw new IllegalArgumentException("Non-blank 'agentMtlsSubdomain' is required to run this service.");
    }

    this.agentMtlsSubdomain = agentMtlsSubdomain;
  }

  @Override
  public AgentMtlsEndpointDetails createEndpointForAccount(String accountId, AgentMtlsEndpointRequest endpointRequest) {
    log.info("Create agent mTLS endpoint for account '{}' with domain prefix '{}' and mode '{}'.", accountId,
        endpointRequest.getDomainPrefix(), endpointRequest.getMode());

    this.validateEndpointRequest(endpointRequest);

    String uuid = generateUuid();
    String fqdn = this.buildFqdn(endpointRequest.getDomainPrefix());
    AgentMtlsEndpoint endpoint = AgentMtlsEndpoint.builder()
                                     .uuid(uuid)
                                     .accountId(accountId)
                                     .fqdn(fqdn)
                                     .mode(endpointRequest.getMode())
                                     .caCertificates(endpointRequest.getCaCertificates())
                                     .build();

    try {
      persistence.save(endpoint);
    } catch (DuplicateKeyException e) {
      // We assume it's not the uuid that collides (very unlikely)
      throw new InvalidRequestException(
          String.format("Agent mTLS endpoint with domain prefix '%s' or accountId '%s' already exists.",
              endpointRequest.getDomainPrefix(), accountId));
    }

    return this.buildEndpointDetails(endpoint);
  }

  @Override
  public AgentMtlsEndpointDetails updateEndpointForAccount(String accountId, AgentMtlsEndpointRequest endpointRequest) {
    log.info("Update agent mTLS endpoint for account '{}' with domain prefix '{}' and mode '{}'.", accountId,
        endpointRequest.getDomainPrefix(), endpointRequest.getMode());

    this.validateEndpointRequest(endpointRequest);

    Query<AgentMtlsEndpoint> query =
        persistence.createQuery(AgentMtlsEndpoint.class).filter(AgentMtlsEndpointKeys.accountId, accountId);

    String fqdn = this.buildFqdn(endpointRequest.getDomainPrefix());
    UpdateOperations<AgentMtlsEndpoint> updateOperations =
        persistence.createUpdateOperations(AgentMtlsEndpoint.class)
            .set(AgentMtlsEndpointKeys.fqdn, fqdn)
            .set(AgentMtlsEndpointKeys.caCertificates, endpointRequest.getCaCertificates())
            .set(AgentMtlsEndpointKeys.mode, endpointRequest.getMode());

    // only update (no insert) and return the resulting endpoint.
    AgentMtlsEndpoint updatedEndpoint =
        persistence.findAndModify(query, updateOperations, HPersistence.returnNewOptions);

    if (updatedEndpoint == null) {
      throw new EntityNotFoundException(String.format(ERROR_ENDPOINT_FOR_ACCOUNT_NOT_FOUND_FORMAT, accountId));
    }

    return this.buildEndpointDetails(updatedEndpoint);
  }

  @Override
  public AgentMtlsEndpointDetails patchEndpointForAccount(String accountId, AgentMtlsEndpointRequest patchRequest) {
    log.info("Patch agent mTLS endpoint for account '{}'.", accountId);

    Query<AgentMtlsEndpoint> query =
        persistence.createQuery(AgentMtlsEndpoint.class).filter(AgentMtlsEndpointKeys.accountId, accountId);

    // Build update operation based on the patch request
    boolean emptyPatch = true;
    UpdateOperations<AgentMtlsEndpoint> updateOperations = persistence.createUpdateOperations(AgentMtlsEndpoint.class);

    // patch domain prefix
    if (patchRequest.getDomainPrefix() != null) {
      log.info("Patch request contains domainPrefix '{}'.", patchRequest.getDomainPrefix());

      this.validateDomainPrefix(patchRequest.getDomainPrefix());

      String fqdn = this.buildFqdn(patchRequest.getDomainPrefix());
      updateOperations = updateOperations.set(AgentMtlsEndpointKeys.fqdn, fqdn);
      emptyPatch = false;
    }

    // patch CA certificates
    if (patchRequest.getCaCertificates() != null) {
      log.info("Patch request contains CA certificate.");
      this.validateCaCertificates(patchRequest.getCaCertificates());

      updateOperations = updateOperations.set(AgentMtlsEndpointKeys.caCertificates, patchRequest.getCaCertificates());
      emptyPatch = false;
    }

    // patch mode
    if (patchRequest.getMode() != null) {
      log.info("Patch request contains mode '{}'.", patchRequest.getMode());

      updateOperations = updateOperations.set(AgentMtlsEndpointKeys.mode, patchRequest.getMode());
      emptyPatch = false;
    }

    // throw in case the patch request was empty
    if (emptyPatch) {
      throw new InvalidRequestException("The patch request is empty.");
    }

    // only update (no insert) and return the full resulting endpoint.
    AgentMtlsEndpoint updatedEndpoint =
        persistence.findAndModify(query, updateOperations, HPersistence.returnNewOptions);

    if (updatedEndpoint == null) {
      throw new EntityNotFoundException(String.format(ERROR_ENDPOINT_FOR_ACCOUNT_NOT_FOUND_FORMAT, accountId));
    }

    return this.buildEndpointDetails(updatedEndpoint);
  }

  @Override
  public boolean deleteEndpointForAccount(String accountId) {
    log.info("Delete agent mTLS endpoint for account '{}'.", accountId);

    Query<AgentMtlsEndpoint> query =
        persistence.createQuery(AgentMtlsEndpoint.class).filter(AgentMtlsEndpointKeys.accountId, accountId);

    return persistence.delete(query);
  }

  @Override
  public boolean isDomainPrefixAvailable(String domainPrefix) {
    this.validateDomainPrefix(domainPrefix);

    String fqdn = this.buildFqdn(domainPrefix);
    AgentMtlsEndpoint endpoint =
        persistence.createQuery(AgentMtlsEndpoint.class).field(AgentMtlsEndpointKeys.fqdn).equal(fqdn).get();

    return endpoint == null;
  }

  /**
   * Validates a complete endpoint request to ensure its correctness.
   *
   * @param endpointRequest The request to validate.
   * @throws InvalidRequestException If the request is invalid.
   */
  private void validateEndpointRequest(AgentMtlsEndpointRequest endpointRequest) {
    this.validateCaCertificates(endpointRequest.getCaCertificates());

    this.validateDomainPrefix(endpointRequest.getDomainPrefix());

    if (endpointRequest.getMode() == null) {
      throw new InvalidRequestException("No agent mTLS mode was provided.");
    }
  }

  /**
   * Validates a list of CA certificates provided in PEM format.
   *
   * @param caCertificates The certificates to verify.
   * @throws InvalidRequestException If any of the certificates is invalid.
   */
  private void validateCaCertificates(String caCertificates) {
    // TODO: Add more CERT validations.
    if (StringUtils.isBlank(caCertificates)) {
      throw new InvalidRequestException("No CA certificate was provided for the agent mTLS endpoint.");
    }
  }

  /**
   * Validates a provided domain prefix (doesn't verify if it's already taken).
   *
   * @param domainPrefix The domain prefix to verify.
   * @throws InvalidRequestException If the domain prefix is invalid.
   */
  private void validateDomainPrefix(String domainPrefix) {
    // domain prefix is mandatory.
    if (StringUtils.isBlank(domainPrefix)) {
      throw new InvalidRequestException("No domain prefix was provided for the agent mTLS endpoint.");
    }

    // no multi-subdomain prefix is allowed - otherwise our wildcard server certificate won't work.
    if (domainPrefix.contains(".")) {
      throw new InvalidRequestException(
          String.format("Provided domain prefix '%s' has more than one level.", domainPrefix));
    }

    // ensure the resulting fqdn is valid.
    String fqdn = this.buildFqdn(domainPrefix);
    if (!urlValidator.isValid("http://" + fqdn)) {
      throw new InvalidRequestException(
          String.format("Provided domain prefix '%s' is invalid (full fqdn: '%s').", domainPrefix, fqdn));
    }
  }

  /**
   * Creates the fully qualified domain name for an agent mTLS endpoint with the given domain prefix.
   *
   * @param domainPrefix The domain prefix.
   * @return The FQDN.
   */
  private String buildFqdn(String domainPrefix) {
    return String.format("%s.%s", domainPrefix, this.agentMtlsSubdomain);
  }
}
