/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.publicaccess.api;

import static io.harness.exception.WingsException.USER;

import io.harness.accesscontrol.publicaccess.PublicAccessService;
import io.harness.accesscontrol.resources.resourcetypes.ResourceType;
import io.harness.accesscontrol.resources.resourcetypes.ResourceTypeService;
import io.harness.exception.InvalidRequestException;
import io.harness.spec.server.accesscontrol.v1.PublicAccessApi;
import io.harness.spec.server.accesscontrol.v1.model.PublicAccessRequest;
import io.harness.spec.server.accesscontrol.v1.model.ResourceScope;

import com.google.inject.Inject;
import java.util.Optional;
import javax.validation.Valid;
import javax.ws.rs.core.Response;

public class PublicAccessApiImpl implements PublicAccessApi {
  private final ResourceTypeService resourceTypeService;
  private final PublicAccessService publicAccessService;

  @Inject
  public PublicAccessApiImpl(ResourceTypeService resourceTypeService, PublicAccessService publicAccessService) {
    this.resourceTypeService = resourceTypeService;
    this.publicAccessService = publicAccessService;
  }

  @Override
  public Response enablePublicAccess(@Valid PublicAccessRequest body, String harnessAccount) {
    ResourceScope resourceScope = body.getResourceScope();
    validateAccount(resourceScope.getAccountIdentifier(), harnessAccount);
    ResourceType resourceType = validateAndGetResourceType(body.getResourceType());
    publicAccessService.enable(body.getResourceIdentifier(), resourceType, resourceScope);

    return null;
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
