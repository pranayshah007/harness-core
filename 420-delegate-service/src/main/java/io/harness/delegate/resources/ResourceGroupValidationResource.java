/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.resources;

import static io.harness.delegate.utils.RbacConstants.DELEGATE_CONFIG_RESOURCE_TYPE;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_CONFIG_VIEW_PERMISSION;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_RESOURCE_TYPE;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_VIEW_PERMISSION;
import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.ng.core.delegate.DelegateConfigResourceValidationResponse;
import io.harness.ng.core.delegate.DelegateResourceValidationResponse;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.service.intfc.DelegateSetupService;

import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@Api(value = "/ng/delegate-service/resource-validation", hidden = true)
@Path("/ng/delegate-service/resource-validation")
@Produces("application/json")
@Consumes("application/json")
@AuthRule(permissionType = LOGGED_IN)
@NextGenManagerAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class ResourceGroupValidationResource {
  private final DelegateSetupService delegateSetupService;
  private final AccessControlClient accessControlClient;

  @GET
  @Path("/delegates")
  public RestResponse<DelegateResourceValidationResponse> validateDelegateGroups(
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("orgIdentifier") String orgIdentifier,
      @QueryParam("projectIdentifier") String projectIdentifier, @QueryParam("identifiers") List<String> identifiers) {
    log.info("Manager called to validate {} delegate groups", identifiers.size());
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_VIEW_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      List<Boolean> delegateGroupValidityData =
          delegateSetupService.validateDelegateGroups(accountId, orgIdentifier, projectIdentifier, identifiers);

      log.info("Delegate group validation result {}", delegateGroupValidityData.toString());

      return new RestResponse<>(
          DelegateResourceValidationResponse.builder().delegateValidityData(delegateGroupValidityData).build());
    }
  }

  @GET
  @Path("/delegate-configs")
  public RestResponse<DelegateConfigResourceValidationResponse> validateDelegateConfigurations(
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("orgIdentifier") String orgIdentifier,
      @QueryParam("projectIdentifier") String projectIdentifier, @QueryParam("identifiers") List<String> identifiers) {
    log.info("Manager called to validate {} delegate configs", identifiers.size());

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(DELEGATE_CONFIG_RESOURCE_TYPE, null), DELEGATE_CONFIG_VIEW_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      List<Boolean> delegateConfigValidityData =
          delegateSetupService.validateDelegateConfigurations(accountId, orgIdentifier, projectIdentifier, identifiers);

      log.info("Delegate config validation result {}", delegateConfigValidityData.toString());

      return new RestResponse<>(DelegateConfigResourceValidationResponse.builder()
                                    .delegateConfigValidityData(delegateConfigValidityData)
                                    .build());
    }
  }
}
