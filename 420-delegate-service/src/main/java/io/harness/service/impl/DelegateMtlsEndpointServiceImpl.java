/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateMtlsEndpoint;
import io.harness.delegate.beans.DelegateMtlsEndpoint.DelegateMtlsEndpointKeys;
import io.harness.delegate.beans.DelegateMtlsEndpointDetails;
import io.harness.delegate.beans.DelegateMtlsEndpointRequest;
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
public class DelegateMtlsEndpointServiceImpl extends DelegateMtlsEndpointServiceReadOnlyImpl {
  private static final UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_ALL_SCHEMES);

  private final String delegateMtlsSubdomain;

  /**
   * Creates a new instance.
   * @param persistence the object used for data peristence.
   * @param delegateMtlsSubdomain the mTLS subdomain to use for mTLS endpoints.
   */
  @Inject
  public DelegateMtlsEndpointServiceImpl(
      HPersistence persistence, @Named("delegateMtlsSubdomain") String delegateMtlsSubdomain) {
    super(persistence);

    if (isBlank(delegateMtlsSubdomain)) {
      throw new IllegalArgumentException("Non-blank 'delegateMtlsSubdomain' is required to run this service.");
    }

    this.delegateMtlsSubdomain = delegateMtlsSubdomain;
  }

  @Override
  public DelegateMtlsEndpointDetails createEndpointForAccount(
      String accountId, DelegateMtlsEndpointRequest endpointRequest) {
    log.info("Create delegate mTLS endpoint for account '{}' with domain prefix '{}' and mode '{}'.", accountId,
        endpointRequest.getDomainPrefix(), endpointRequest.getMode());

    this.validateEndpointRequest(endpointRequest);

    String uuid = generateUuid();
    String fqdn = this.buildFqdn(endpointRequest.getDomainPrefix());
    DelegateMtlsEndpoint endpoint = DelegateMtlsEndpoint.builder()
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
          String.format("Delegate mTLS endpoint with domain prefix '%s' or accountId '%s' already exists.",
              endpointRequest.getDomainPrefix(), accountId));
    }

    return this.buildEndpointDetails(endpoint);
  }

  @Override
  public DelegateMtlsEndpointDetails updateEndpointForAccount(
      String accountId, DelegateMtlsEndpointRequest endpointRequest) {
    log.info("Update delegate mTLS endpoint for account '{}' with domain prefix '{}' and mode '{}'.", accountId,
        endpointRequest.getDomainPrefix(), endpointRequest.getMode());

    this.validateEndpointRequest(endpointRequest);

    Query<DelegateMtlsEndpoint> query =
        persistence.createQuery(DelegateMtlsEndpoint.class).filter(DelegateMtlsEndpointKeys.accountId, accountId);

    String fqdn = this.buildFqdn(endpointRequest.getDomainPrefix());
    UpdateOperations<DelegateMtlsEndpoint> updateOperations =
        persistence.createUpdateOperations(DelegateMtlsEndpoint.class)
            .set(DelegateMtlsEndpointKeys.fqdn, fqdn)
            .set(DelegateMtlsEndpointKeys.caCertificates, endpointRequest.getCaCertificates())
            .set(DelegateMtlsEndpointKeys.mode, endpointRequest.getMode());

    // only update (no insert) and return the resulting endpoint.
    DelegateMtlsEndpoint updatedEndpoint =
        persistence.findAndModify(query, updateOperations, HPersistence.returnNewOptions);

    if (updatedEndpoint == null) {
      throw new EntityNotFoundException(String.format(ERROR_ENDPOINT_FOR_ACCOUNT_NOT_FOUND_FORMAT, accountId));
    }

    return this.buildEndpointDetails(updatedEndpoint);
  }

  @Override
  public DelegateMtlsEndpointDetails patchEndpointForAccount(
      String accountId, DelegateMtlsEndpointRequest patchRequest) {
    log.info("Patch delegate mTLS endpoint for account '{}'.", accountId);

    Query<DelegateMtlsEndpoint> query =
        persistence.createQuery(DelegateMtlsEndpoint.class).filter(DelegateMtlsEndpointKeys.accountId, accountId);

    // Build update operation based on the patch request
    boolean emptyPatch = true;
    UpdateOperations<DelegateMtlsEndpoint> updateOperations =
        persistence.createUpdateOperations(DelegateMtlsEndpoint.class);

    // patch domain prefix
    if (patchRequest.getDomainPrefix() != null) {
      log.info("Patch request contains domainPrefix '{}'.", patchRequest.getDomainPrefix());

      this.validateDomainPrefix(patchRequest.getDomainPrefix());

      String fqdn = this.buildFqdn(patchRequest.getDomainPrefix());
      updateOperations = updateOperations.set(DelegateMtlsEndpointKeys.fqdn, fqdn);
      emptyPatch = false;
    }

    // patch CA certificates
    if (patchRequest.getCaCertificates() != null) {
      log.info("Patch request contains CA certificate.");
      this.validateCaCertificates(patchRequest.getCaCertificates());

      updateOperations =
          updateOperations.set(DelegateMtlsEndpointKeys.caCertificates, patchRequest.getCaCertificates());
      emptyPatch = false;
    }

    // patch mode
    if (patchRequest.getMode() != null) {
      log.info("Patch request contains mode '{}'.", patchRequest.getMode());

      updateOperations = updateOperations.set(DelegateMtlsEndpointKeys.mode, patchRequest.getMode());
      emptyPatch = false;
    }

    // throw in case the patch request was empty
    if (emptyPatch) {
      throw new InvalidRequestException("The patch request is empty.");
    }

    // only update (no insert) and return the full resulting endpoint.
    DelegateMtlsEndpoint updatedEndpoint =
        persistence.findAndModify(query, updateOperations, HPersistence.returnNewOptions);

    if (updatedEndpoint == null) {
      throw new EntityNotFoundException(String.format(ERROR_ENDPOINT_FOR_ACCOUNT_NOT_FOUND_FORMAT, accountId));
    }

    return this.buildEndpointDetails(updatedEndpoint);
  }

  @Override
  public boolean deleteEndpointForAccount(String accountId) {
    log.info("Delete delegate mTLS endpoint for account '{}'.", accountId);

    Query<DelegateMtlsEndpoint> query =
        persistence.createQuery(DelegateMtlsEndpoint.class).filter(DelegateMtlsEndpointKeys.accountId, accountId);

    return persistence.delete(query);
  }

  @Override
  public boolean isDomainPrefixAvailable(String domainPrefix) {
    this.validateDomainPrefix(domainPrefix);

    String fqdn = this.buildFqdn(domainPrefix);
    DelegateMtlsEndpoint endpoint =
        persistence.createQuery(DelegateMtlsEndpoint.class).field(DelegateMtlsEndpointKeys.fqdn).equal(fqdn).get();

    return endpoint == null;
  }

  /**
   * Validates a complete endpoint request to ensure its correctness.
   *
   * @param endpointRequest The request to validate.
   * @throws InvalidRequestException If the request is invalid.
   */
  private void validateEndpointRequest(DelegateMtlsEndpointRequest endpointRequest) {
    this.validateCaCertificates(endpointRequest.getCaCertificates());

    this.validateDomainPrefix(endpointRequest.getDomainPrefix());

    if (endpointRequest.getMode() == null) {
      throw new InvalidRequestException("No delegate mTLS mode was provided.");
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
      throw new InvalidRequestException("No CA certificate was provided for the delegate mTLS endpoint.");
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
      throw new InvalidRequestException("No domain prefix was provided for the delegate mTLS endpoint.");
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
   * Creates the fully qualified domain name for a delegate mTLS endpoint with the given domain prefix.
   *
   * @param domainPrefix The domain prefix.
   * @return The FQDN.
   */
  private String buildFqdn(String domainPrefix) {
    return String.format("%s.%s", domainPrefix, this.delegateMtlsSubdomain);
  }
}
