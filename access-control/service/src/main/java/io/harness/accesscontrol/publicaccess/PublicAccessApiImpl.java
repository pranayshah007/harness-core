/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.publicaccess;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.remote.client.CGRestUtils.getResponse;

import io.harness.accesscontrol.resources.resourcetypes.ResourceType;
import io.harness.accesscontrol.resources.resourcetypes.ResourceTypeService;
import io.harness.account.AccountClient;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.spec.server.accesscontrol.v1.PublicAccessApi;
import io.harness.spec.server.accesscontrol.v1.model.PublicAccessRequest;
import io.harness.spec.server.accesscontrol.v1.model.Scope;

import com.google.inject.Inject;
import java.util.Optional;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PublicAccessApiImpl implements PublicAccessApi {
  private final ResourceTypeService resourceTypeService;
  private final PublicAccessService publicAccessService;
  private final AccountClient accountClient;

  @Inject
  public PublicAccessApiImpl(
      ResourceTypeService resourceTypeService, PublicAccessService publicAccessService, AccountClient accountClient) {
    this.resourceTypeService = resourceTypeService;
    this.publicAccessService = publicAccessService;
    this.accountClient = accountClient;
  }

  @Override
  public Response disablePublicAccess(String harnessAccount, String account, String org, String project,
      String resourceType, String resourceIdentifier) {
    validateAccount(account, harnessAccount);
    ResourceType resourceTypeDBO = null;
    if (isNotEmpty(resourceType)) {
      resourceTypeDBO = validateAndGetResourceType(resourceType);
    }
    publicAccessService.disablePublicAccess(account, org, project, resourceTypeDBO, resourceIdentifier);
    return Response.status(Response.Status.OK).entity(true).build();
  }

  @Override
  public Response enablePublicAccess(@Valid PublicAccessRequest body, String harnessAccount) {
    Scope resourceScope = body.getResourceScope();
    validateAccount(resourceScope.getAccount(), harnessAccount);
    validateIfPublicAccessIsEnabled(harnessAccount);
    ResourceType resourceType = validateAndGetResourceType(body.getResourceType());
    publicAccessService.enable(body.getResourceIdentifier(), resourceType, resourceScope);
    return Response.status(Response.Status.OK).entity(true).build();
  }

  @Override
  public Response isResourcePublic(@Valid PublicAccessRequest body, String harnessAccount) {
    Scope resourceScope = body.getResourceScope();
    validateAccount(resourceScope.getAccount(), harnessAccount);
    validateIfPublicAccessIsEnabled(harnessAccount);
    ResourceType resourceType = validateAndGetResourceType(body.getResourceType());
    boolean isPublic = publicAccessService.isResourcePublic(body.getResourceIdentifier(), resourceType, resourceScope);
    return Response.status(Response.Status.OK).entity(isPublic).build();
  }

  private void validateIfPublicAccessIsEnabled(String harnessAccount) {
    AccountDTO accountDTO = getResponse(accountClient.getAccountDTO(harnessAccount));
    if (accountDTO == null) {
      throw new InvalidRequestException("Unable to fetch account details. No such account exist with Harness", USER);
    }
    if (!accountDTO.isPublicAccessEnabled()) {
      throw new InvalidRequestException("Public Access is not enabled for this account.", USER);
    }
  }

  private void validateAccount(String accountIdentifier, String harnessAccount) {
    if (harnessAccount == null || accountIdentifier == null || !harnessAccount.equals(accountIdentifier)) {
      throw new InvalidRequestException("harness-account and accountIdentifier should be equal", USER);
    }
  }

  private ResourceType validateAndGetResourceType(String resourceType) {
    final Optional<ResourceType> resourceTypeOptional = resourceTypeService.get(resourceType);
    if (resourceTypeOptional.isEmpty()) {
      throw new InvalidRequestException("Resource type is invalid", USER);
    }
    ResourceType resourceTypeObject = resourceTypeOptional.get();
    if (!Boolean.TRUE.equals(resourceTypeObject.isPublic())) {
      throw new InvalidRequestException("Resource type does not support public access", USER);
    }
    return resourceTypeObject;
  }
}
