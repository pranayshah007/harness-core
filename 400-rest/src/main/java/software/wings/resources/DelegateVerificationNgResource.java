/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_EDIT_PERMISSION;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_RESOURCE_TYPE;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateHeartbeatDetails;
import io.harness.delegate.beans.DelegateInitializationDetails;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.rest.RestResponse;

import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.hibernate.validator.constraints.NotEmpty;

@Api("delegates-verification")
@Path("/delegates-verification")
@Produces(MediaType.APPLICATION_JSON)
@AuthRule(permissionType = LOGGED_IN)
//@Scope(DELEGATE)
// This NG specific, switching to NG access control
@Slf4j
@OwnedBy(DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@Tag(name = "Delegate Verification",
    description = "Contains APIs related to Delegate initialization, connectivity and heartbeat verification.")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
public class DelegateVerificationNgResource {
  private final DelegateService delegateService;
  private final AccessControlClient accessControlClient;

  @Inject
  public DelegateVerificationNgResource(DelegateService delegateService, AccessControlClient accessControlClient) {
    this.delegateService = delegateService;
    this.accessControlClient = accessControlClient;
  }

  @GET
  @Path("/heartbeatV2")
  @Operation(operationId = "getDelegatesHeartbeatDetailsV2",
      summary =
          "Retrieves number of registered Delegates and number of connected Delegates, filtered by Account Id and Delegate name.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Number of registered and number of connected Delegates, "
                + "filtered by the Account id and Delegate name.")
      })
  public RestResponse<DelegateHeartbeatDetails>
  getDelegatesHeartbeatDetailsV2(
      @Parameter(description = "Account id") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id") @QueryParam("projectId") String projectId,
      @Parameter(description = "Delegate name used to filter out delegates") @QueryParam(
          "delegateName") @NotEmpty String delegateName) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_EDIT_PERMISSION);

    List<Delegate> registeredDelegates = delegateService.obtainDelegatesUsingName(accountId, delegateName);

    if (CollectionUtils.isNotEmpty(registeredDelegates)) {
      List<Delegate> connectedDelegates = delegateService.getConnectedDelegates(accountId, registeredDelegates);

      return new RestResponse<>(DelegateHeartbeatDetails.builder()
                                    .numberOfRegisteredDelegates(registeredDelegates.size())
                                    .numberOfConnectedDelegates(connectedDelegates.size())
                                    .build());
    }

    return new RestResponse<>(DelegateHeartbeatDetails.builder().build());
  }

  @GET
  @Path("/initializedV2")
  @Operation(operationId = "getDelegatesInitializationDetails",
      summary = "Retrieves Delegate initialization details filtered by Delegate name. "
          + "The details include delegateId, hostname, initialized status, profileError and profileExecutedAt time.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Delegate initialization details, "
                + "including delegateId, hostname, initialized status, profileError and profileExecutedAt time.")
      })
  public RestResponse<List<DelegateInitializationDetails>>
  getDelegatesInitializationDetailsV2(
      @Parameter(description = "Account id") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id") @QueryParam("projectId") String projectId,
      @Parameter(description = "Delegate name used to filter out delegates") @QueryParam(
          "delegateName") @NotEmpty String delegateName) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_EDIT_PERMISSION);

    List<String> registeredDelegateIds = delegateService.obtainDelegateIdsUsingName(accountId, delegateName);

    if (CollectionUtils.isNotEmpty(registeredDelegateIds)) {
      return new RestResponse<>(delegateService.obtainDelegateInitializationDetails(accountId, registeredDelegateIds));
    }

    return new RestResponse<>(Collections.emptyList());
  }
}
