/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.DelegateType.DOCKER;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_DELETE_PERMISSION;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_EDIT_PERMISSION;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_RESOURCE_TYPE;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_VIEW_PERMISSION;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_DELEGATES;
import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE;
import static software.wings.service.impl.DelegateServiceImpl.DELEGATE_DIR;
import static software.wings.service.impl.DelegateServiceImpl.DOCKER_DELEGATE;
import static software.wings.service.impl.DelegateServiceImpl.ECS_DELEGATE;
import static software.wings.service.impl.DelegateServiceImpl.HARNESS_DELEGATE_VALUES_YAML;
import static software.wings.service.impl.DelegateServiceImpl.KUBERNETES_DELEGATE;

import static java.util.stream.Collectors.toList;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.validator.Trimmed;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateApproval;
import io.harness.delegate.beans.DelegateGroupListing;
import io.harness.delegate.beans.DelegateSetupDetails;
import io.harness.delegate.beans.DelegateSizeDetails;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.k8s.KubernetesConvention;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.LearningEngineAuth;
import io.harness.security.annotations.PublicApi;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateSetupService;

import software.wings.beans.CEDelegateStatus;
import software.wings.beans.DelegateStatus;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.DelegateScopeService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.DownloadTokenService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Api("/setup/delegates/v3")
@Path("/setup/delegates/v3")
@Produces("application/json")
@Scope(DELEGATE)
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@OwnedBy(DEL)
@Tag(name = "Delegate Management", description = "Contains APIs related to Delegate management")
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
public class DelegateSetupResourceV3 {
  private static final String DOWNLOAD_URL = "downloadUrl";
  private static final String ACCOUNT_ID = "?accountId=";
  private static final String TOKEN = "&token=";
  private static final String DELEGATE = "delegate.";
  private static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
  private static final String BINARY = "binary";
  private static final String APPLICATION_ZIP_CHARSET_BINARY = "application/zip; charset=binary";
  private static final String CONTENT_DISPOSITION = "Content-Disposition";
  private static final String ATTACHMENT_FILENAME = "attachment; filename=";
  public static final String YAML = ".yaml";
  private static final String TAR_GZ = ".tar.gz";
  private static final String SH = ".sh";

  private final DelegateService delegateService;
  private final DelegateCache delegateCache;
  private final DelegateScopeService delegateScopeService;
  private final DownloadTokenService downloadTokenService;
  private final SubdomainUrlHelperIntfc subdomainUrlHelper;
  private final AccessControlClient accessControlClient;
  private final DelegateSetupService delegateSetupService;

  @Inject
  public DelegateSetupResourceV3(DelegateService delegateService, DelegateScopeService delegateScopeService,
      DownloadTokenService downloadTokenService, SubdomainUrlHelperIntfc subdomainUrlHelper,
      DelegateCache delegateCache, AccessControlClient accessControlClient, DelegateSetupService delegateSetupService) {
    this.delegateService = delegateService;
    this.delegateScopeService = delegateScopeService;
    this.downloadTokenService = downloadTokenService;
    this.subdomainUrlHelper = subdomainUrlHelper;
    this.delegateCache = delegateCache;
    this.accessControlClient = accessControlClient;
    this.delegateSetupService = delegateSetupService;
  }

  @GET
  @Path("status2")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @Operation(operationId = "listDelegateStatusWithScalingGroups",
      summary = "Lists statuses of all Delegates for the account. "
          + "Status includes Delegate Config info, heartbeat times and other info, including associated scaling groups",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "A list of statuses of all Delegates for the account")
      })
  public RestResponse<DelegateStatus>
  listDelegateStatusWithScalingGroups(
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.getDelegateStatusWithScalingGroups(accountId));
    }
  }

  @GET
  @Path("available-versions-for-verification")
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  @Operation(operationId = "getAvailableVersions", summary = "Lists versions of all active Delegates for the account.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "A list of versions of all active Delegates for the account.")
      })
  public RestResponse<List<String>>
  getAvailableVersions(@Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.getAvailableVersions(accountId));
    }
  }

  @GET
  @Path("connected-ratio-with-primary")
  @Timed
  @ExceptionMetered
  @PublicApi
  @Operation(operationId = "getConnectedRatioWithPrimary",
      summary = "Calculates ratio of connected Delegates with target version vs Delegates with primary version.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Double value representing the calculated ratio.")
      })
  public RestResponse<Double>
  getConnectedRatioWithPrimary(@Parameter(description = "Target version for which the ratio is calculated") @QueryParam(
                                   "targetVersion") @NotEmpty String targetVersion,
      @QueryParam("accountId") String accountId, @QueryParam("ring") String ringName) {
    return new RestResponse<>(delegateService.getConnectedRatioWithPrimary(targetVersion, accountId, ringName));
  }

  @GET
  @Path("connected-delegate-ratio")
  @Timed
  @ExceptionMetered
  @PublicApi
  @Operation(operationId = "getConnectedDelegatesRatio",
      summary = "Calculates ratio of connected Delegates with specific version.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Double value representing the calculated ratio.")
      })
  public RestResponse<Double>
  getConnectedDelegatesRatio(@Parameter(description = "Target version for which the ratio is calculated") @QueryParam(
                                 "targetVersion") @NotEmpty String targetVersion,
      @QueryParam("accountId") String accountId) {
    return new RestResponse<>(delegateService.getConnectedDelegatesRatio(targetVersion, accountId));
  }

  @GET
  @Path("latest")
  @Timed
  @ExceptionMetered
  @Operation(operationId = "getLatestDelegateVersion", summary = "Returns latest Delegate version, for the account.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "The latest version of Delegates for the account.")
      })
  public RestResponse<String>
  get(@Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.getLatestDelegateVersion(accountId));
    }
  }

  @GET
  @Path("validateDelegateName")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @Operation(operationId = "validateThatDelegateNameIsUnique",
      summary = "Checks whether already exists a Delegate with the same name for the account.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Boolean status whether proposed Delegate name is unique or not.")
      })
  public RestResponse<Boolean>
  validateThatDelegateNameIsUnique(
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Delegate name to be validated") @QueryParam(
          "delegateName") @NotEmpty String delegateName) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.validateThatDelegateNameIsUnique(accountId, delegateName));
    }
  }

  @GET
  @Path("validate-ce-delegate")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @Operation(operationId = "validateCEDelegate",
      summary = "Checks whether already exists a Delegate with the same name for the account. "
          + "If it does, retrieves additional info in the response e.g. active connections and metrics.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "CE Delegate status.")
      })
  public RestResponse<CEDelegateStatus>
  validateCEDelegate(@Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Delegate name to be validated") @QueryParam(
          "delegateName") @NotEmpty String delegateName) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.validateCEDelegate(accountId, delegateName));
    }
  }

  @GET
  @Path("{delegateId}/profile-result")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @Operation(operationId = "delegateProfileResult",
      summary = "Retrieves Delegate Configuration (profile) script execution result (log).",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Delegate profile script execution log listing.")
      })
  public RestResponse<String>
  getProfileResult(@Parameter(description = "Delegate UUID") @PathParam("delegateId") String delegateId,
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.getProfileResult(accountId, delegateId));
    }
  }

  @GET
  @Path("delegate-sizes")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @Operation(operationId = "delegateSizes",
      summary = "Retrieves list of predefined Delegate sizes from the configuration. "
          + "E.g. SMALL, MEDIUM, LARGE etc.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "List of available Delegate sizes.")
      })
  // This NG specific, switching to NG access control. AuthRule to be removed also when NG access control is fully
  // enabled.
  public RestResponse<List<DelegateSizeDetails>>
  delegateSizes(@Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization ID") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project ID") @QueryParam("projectId") String projectId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_VIEW_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.fetchAvailableSizes());
    }
  }

  @PUT
  @Path("{delegateId}/scopes")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @Operation(operationId = "updateDelegateScopes", summary = "Updates Scopes for the Delegate.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default", description = "Updated delegate")
      })
  public RestResponse<Delegate>
  updateScopes(@Parameter(description = "Delegate UUID") @PathParam("delegateId") @NotEmpty String delegateId,
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId,
      @RequestBody(required = true,
          description = "Details of the scopes to be updated. "
              + "Contains list of scope UUIDs to be included (includeScopeIds), "
              + "and a list of scope UUIDs to be excluded (excludeScopeIds).") DelegateScopes delegateScopes) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      Delegate delegate = delegateCache.get(accountId, delegateId, true);
      if (delegateScopes == null) {
        delegate.setIncludeScopes(null);
        delegate.setExcludeScopes(null);
      } else {
        if (isNotEmpty(delegateScopes.getIncludeScopeIds())) {
          delegate.setIncludeScopes(delegateScopes.getIncludeScopeIds()
                                        .stream()
                                        .map(s -> delegateScopeService.get(accountId, s))
                                        .filter(Objects::nonNull)
                                        .collect(toList()));
        } else {
          delegate.setIncludeScopes(null);
        }
        if (isNotEmpty(delegateScopes.getExcludeScopeIds())) {
          delegate.setExcludeScopes(delegateScopes.getExcludeScopeIds()
                                        .stream()
                                        .map(s -> delegateScopeService.get(accountId, s))
                                        .filter(Objects::nonNull)
                                        .collect(toList()));
        } else {
          delegate.setExcludeScopes(null);
        }
      }
      return new RestResponse<>(delegateService.updateScopes(delegate));
    }
  }

  @VisibleForTesting
  protected static class DelegateScopes {
    private List<String> includeScopeIds;
    private List<String> excludeScopeIds;

    public List<String> getIncludeScopeIds() {
      return includeScopeIds;
    }

    public void setIncludeScopeIds(List<String> includeScopeIds) {
      this.includeScopeIds = includeScopeIds;
    }

    public List<String> getExcludeScopeIds() {
      return excludeScopeIds;
    }

    public void setExcludeScopeIds(List<String> excludeScopeIds) {
      this.excludeScopeIds = excludeScopeIds;
    }
  }

  @GET
  @Path("kubernetes-delegates")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @Operation(operationId = "getDelegateNames", summary = "Retrieves a list of all Delegate names for the account.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "List of Delegate names.")
      })
  public RestResponse<List<String>>
  kubernetesDelegateNames(@Context HttpServletRequest request,
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.getKubernetesDelegateNames(accountId));
    }
  }

  /*
    Duplicated method in case of Rollback, soon as "delegate-selectors" is verified and tested from UI,
    below method "delegate-tags" will be removed.
  */
  @GET
  @Path("delegate-selectors")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @Operation(operationId = "getDelegateSelectors",
      summary = "Retrieves all Delegate Selectors (Delegate implicit selectors + tags) for the account.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "A set of selectors (Delegate implicit selectors + tags).")
      })
  public RestResponse<Set<String>>
  delegateSelectors(@Context HttpServletRequest request,
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.getAllDelegateSelectors(accountId));
    }
  }

  @GET
  @Path("delegate-selectors-up-the-hierarchy")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @Operation(operationId = "getDelegateSelectorsUpTheHierarchy",
      summary =
          "Retrieves Delegate selectors (Delegate implicit selectors + tags) for the account, organization and project hierarchy.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "A set of selectors (delegate implicit selectors + tags).")
      })
  public RestResponse<Set<String>>
  delegateSelectorsUpTheHierarchy(@Context HttpServletRequest request,
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization ID") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project ID") @QueryParam("projectId") String projectId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_VIEW_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.getAllDelegateSelectorsUpTheHierarchy(accountId, orgId, projectId));
    }
  }

  @GET
  @Path("delegate-tags")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @Operation(operationId = "getDelegateTags",
      summary = "Retrieves all Delegate selectors (Delegate implicit selectors + tags) for the account.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "A set of selectors (delegate implicit selectors + tags).")
      })
  public RestResponse<Set<String>>
  delegateTags(@Context HttpServletRequest request,
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.getAllDelegateSelectors(accountId));
    }
  }

  @GET
  @Path("{delegateId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @Operation(operationId = "getDelegate", summary = "Retrieves a Delegate object by Delegate UUID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Delegate object representation")
      })
  public RestResponse<Delegate>
  get(@Parameter(description = "Delegate UUID") @PathParam("delegateId") @NotEmpty String delegateId,
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateCache.get(accountId, delegateId));
    }
  }

  @PUT
  @Path("{delegateId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @Operation(operationId = "updateDelegate",
      summary = "Updates Delegate with values from request. "
          + "Checks whether Delegate for given accountId and delegateId exists "
          + "and if so updates it",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default", description = "Updated Delegate")
      })
  public RestResponse<Delegate>
  update(@Parameter(description = "Delegate UUID") @PathParam("delegateId") @NotEmpty String delegateId,
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId,
      @RequestBody(required = true, description = "Update values for the delegate") Delegate delegate) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      delegate.setAccountId(accountId);
      delegate.setUuid(delegateId);

      Delegate existingDelegate = delegateCache.get(accountId, delegateId, true);
      if (existingDelegate != null) {
        delegate.setDelegateType(existingDelegate.getDelegateType());
        delegate.setDelegateGroupName(existingDelegate.getDelegateGroupName());
      }

      return new RestResponse<>(delegateService.update(delegate));
    }
  }

  @PUT
  @Path("{delegateId}/description")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @Operation(operationId = "updateDelegateDescription", summary = "Updates a description for a Delegate.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default", description = "Updated Delegate")
      })
  public RestResponse<Delegate>
  updateDescription(@Parameter(description = "Delegate UUID") @PathParam("delegateId") @NotEmpty String delegateId,
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "New description for the delegate") @Trimmed String newDescription) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.updateDescription(accountId, delegateId, newDescription));
    }
  }

  @PUT
  @Path("{delegateId}/approval")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @Operation(operationId = "updateDelegateApprovalStatus",
      summary = "Updates Delegates approval status. Required values for approval status are: ACTIVATE or REJECT.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default", description = "Updated Delegate")
      })
  public RestResponse<Delegate>
  updateApprovalStatus(@Parameter(description = "Delegate UUID") @PathParam("delegateId") @NotEmpty String delegateId,
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Approval action: ACTIVATE or REJECT") @QueryParam(
          "action") @NotNull DelegateApproval action) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.updateApprovalStatus(accountId, delegateId, action));
    }
  }

  @DELETE
  @Path("{delegateId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @Operation(operationId = "deleteDelegate", summary = "Deletes a Delegate by specified accountId and delegateId",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default", description = "Empty response")
      })
  public RestResponse<Void>
  delete(@Parameter(description = "Delegate UUID") @PathParam("delegateId") @NotEmpty String delegateId,
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      delegateService.delete(accountId, delegateId);
      return new RestResponse<>();
    }
  }
  @DELETE
  @Path("delete-all-except")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @Operation(operationId = "deleteAllDelegatesExcept",
      summary = "Deletes all Delegates for the account except those specified in a request",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default", description = "Empty response")
      })
  public RestResponse<Void>
  deleteAllExcept(@Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId,
      @RequestBody(description = "List of Delegate UUIDs to retain") List<String> delegatesToRetain) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      delegateService.retainOnlySelectedDelegatesAndDeleteRest(accountId, delegatesToRetain);
      return new RestResponse<>();
    }
  }

  @DELETE
  @Path("groups/{identifier}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @Operation(operationId = "deleteDelegateGroup", summary = "Deletes a Delegate group by its ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default", description = "Empty response")
      })
  // This NG specific, switching to NG access control. AuthRule to be removed also when NG access control is fully
  // enabled.
  public RestResponse<Void>
  deleteDelegateGroup(
      @Parameter(description = "Delegate group ID") @PathParam("identifier") @NotEmpty String identifier,
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization ID") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project ID") @QueryParam("projectId") String projectId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, identifier), DELEGATE_DELETE_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      delegateService.deleteDelegateGroupV2(accountId, orgId, projectId, identifier);
      return new RestResponse<>();
    }
  }

  @POST
  @Path(DOWNLOAD_URL)
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @ApiKeyAuthorized(permissionType = MANAGE_DELEGATES)
  @Operation(operationId = "getDownloadUrl", summary = "Retrieves Delegate download url for the account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "A map containing download url for different Delegate types (shell/docker/ecs/k8s)")
      })
  public RestResponse<Map<String, String>>
  downloadUrl(@Context HttpServletRequest request,
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      String url = subdomainUrlHelper.getManagerUrl(request, accountId);

      ImmutableMap.Builder<String, String> mapBuilder =
          ImmutableMap.<String, String>builder()
              .put(DOWNLOAD_URL,
                  url + request.getRequestURI().replace(DOWNLOAD_URL, "download") + ACCOUNT_ID + accountId + TOKEN
                      + downloadTokenService.createDownloadToken(DELEGATE + accountId))
              .put("dockerUrl",
                  url + request.getRequestURI().replace(DOWNLOAD_URL, "docker") + ACCOUNT_ID + accountId + TOKEN
                      + downloadTokenService.createDownloadToken(DELEGATE + accountId))
              .put("ecsUrl",
                  url + request.getRequestURI().replace(DOWNLOAD_URL, "ecs") + ACCOUNT_ID + accountId + TOKEN
                      + downloadTokenService.createDownloadToken(DELEGATE + accountId))
              .put("kubernetesUrl",
                  url + request.getRequestURI().replace(DOWNLOAD_URL, "kubernetes") + ACCOUNT_ID + accountId + TOKEN
                      + downloadTokenService.createDownloadToken(DELEGATE + accountId));

      return new RestResponse<>(mapBuilder.build());
    }
  }

  @PublicApi
  @GET
  @Path("download")
  @Timed
  @ExceptionMetered
  @Operation(operationId = "downloadScriptDelegate",
      summary = "Retrieves tar archive containing shell script Delegate files.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "A tar archive containing shell script Delegate files")
      })
  public Response
  downloadScripts(@Context HttpServletRequest request,
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Delegate name") @QueryParam("delegateName") String delegateName,
      @Parameter(description = "Delegate Configuration UUID") @QueryParam("delegateProfileId") String delegateProfileId,
      @Parameter(description = "token value") @QueryParam("token") @NotEmpty String token,
      @Parameter(description = "token name") @QueryParam("tokenName") String tokenName) throws IOException {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      downloadTokenService.validateDownloadToken(DELEGATE + accountId, token);
      File delegateFile = delegateService.downloadScripts(subdomainUrlHelper.getManagerUrl(request, accountId),
          getVerificationUrl(request), accountId, delegateName, delegateProfileId, tokenName);
      return Response.ok(delegateFile)
          .header(CONTENT_TRANSFER_ENCODING, BINARY)
          .type(APPLICATION_ZIP_CHARSET_BINARY)
          .header(CONTENT_DISPOSITION, ATTACHMENT_FILENAME + DELEGATE_DIR + TAR_GZ)
          .build();
    }
  }

  @PublicApi
  @GET
  @Path("docker")
  @Timed
  @ExceptionMetered
  @Operation(operationId = "downloadDockerDelegate",
      summary = "Retrieves tar archive containing Delegate docker image.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "A tar archive containing Delegate docker image")
      })
  public Response
  downloadDocker(@Context HttpServletRequest request,
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Delegate name") @QueryParam("delegateName") String delegateName,
      @Parameter(description = "Delegate Configuration UUID") @QueryParam("delegateProfileId") String delegateProfileId,
      @Parameter(description = "Token value") @QueryParam("token") @NotEmpty String token,
      @Parameter(description = "Token name") @QueryParam("tokenName") String tokenName) throws IOException {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      downloadTokenService.validateDownloadToken(DELEGATE + accountId, token);
      File delegateFile = delegateService.downloadDocker(subdomainUrlHelper.getManagerUrl(request, accountId),
          getVerificationUrl(request), accountId, delegateName, delegateProfileId, tokenName);
      return Response.ok(delegateFile)
          .header(CONTENT_TRANSFER_ENCODING, BINARY)
          .type(APPLICATION_ZIP_CHARSET_BINARY)
          .header(CONTENT_DISPOSITION, ATTACHMENT_FILENAME + DOCKER_DELEGATE + TAR_GZ)
          .build();
    }
  }

  @PublicApi
  @GET
  @Path("kubernetes")
  @Timed
  @ExceptionMetered
  @Operation(operationId = "downloadKubernetesDelegate",
      summary = "Retrieves tar archive containing Delegate k8s yaml file.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "A tar archive containing Delegate k8s yaml file")
      })
  public Response
  downloadKubernetes(@Context HttpServletRequest request,
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Delegate name") @QueryParam("delegateName") @NotEmpty String delegateName,
      @Parameter(description = "Delegate Configuration UUID") @QueryParam("delegateProfileId") String delegateProfileId,
      @Parameter(description = "Token value") @QueryParam("token") @NotEmpty String token,
      @Parameter(description = "Is Ce enabled") @QueryParam("isCeEnabled") @DefaultValue("false") boolean isCeEnabled,
      @Parameter(description = "Token name") @QueryParam("tokenName") String tokenName,
      @Parameter(description = "root access fot delegate") @QueryParam("runAsRoot") @DefaultValue(
          "true") boolean runAsRoot) throws IOException {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      downloadTokenService.validateDownloadToken(DELEGATE + accountId, token);

      if (isCeEnabled) {
        File delegateFile =
            delegateService.downloadCeKubernetesYaml(subdomainUrlHelper.getManagerUrl(request, accountId),
                getVerificationUrl(request), accountId, delegateName, null, tokenName);
        return Response.ok(delegateFile)
            .header(CONTENT_DISPOSITION, ATTACHMENT_FILENAME + KUBERNETES_DELEGATE + YAML)
            .build();
      } else {
        File delegateFile = delegateService.downloadKubernetes(subdomainUrlHelper.getManagerUrl(request, accountId),
            getVerificationUrl(request), accountId, delegateName, delegateProfileId, tokenName, runAsRoot);
        return Response.ok(delegateFile)
            .header(CONTENT_TRANSFER_ENCODING, BINARY)
            .type(APPLICATION_ZIP_CHARSET_BINARY)
            .header(CONTENT_DISPOSITION, ATTACHMENT_FILENAME + KUBERNETES_DELEGATE + TAR_GZ)
            .build();
      }
    }
  }

  @PublicApi
  @GET
  @Path("kubernetes/account-identifier")
  @Timed
  @ExceptionMetered
  @Operation(operationId = "getAccountIdentifierForKubernetes",
      summary = "Generates a Kubernetes compatible Account UUID",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "String value representing enhanced Account UUID, "
                + "adopting Kubernetes standard for UUIDs ")
      })
  public RestResponse<String>
  getAccountIdentifier(@Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(KubernetesConvention.getAccountIdentifier(accountId));
  }

  @PublicApi
  @GET
  @Path("ecs")
  @Timed
  @ExceptionMetered
  @Operation(operationId = "downloadEcsDelegate", summary = "Retrieves tar archive containing ECS Delegate.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "A tar archive containing ECS Delegate")
      })
  public Response
  downloadEcs(@Context HttpServletRequest request,
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Delegate group name") @QueryParam(
          "delegateGroupName") @NotEmpty String delegateGroupName,
      @Parameter(description = "Is aws Vpc Mode enabled") @QueryParam("awsVpcMode") Boolean awsVpcMode,
      @Parameter(description = "Hostname") @QueryParam("hostname") String hostname,
      @Parameter(description = "Delegate Configuration UUID") @QueryParam("delegateProfileId") String delegateProfileId,
      @Parameter(description = "Token value") @QueryParam("token") @NotEmpty String token,
      @Parameter(description = "Token name") @QueryParam("tokenName") String tokenName) throws IOException {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      downloadTokenService.validateDownloadToken(DELEGATE + accountId, token);
      File delegateFile = delegateService.downloadECSDelegate(subdomainUrlHelper.getManagerUrl(request, accountId),
          getVerificationUrl(request), accountId, awsVpcMode, hostname, delegateGroupName, delegateProfileId,
          tokenName);
      return Response.ok(delegateFile)
          .header(CONTENT_TRANSFER_ENCODING, BINARY)
          .type(APPLICATION_ZIP_CHARSET_BINARY)
          .header(CONTENT_DISPOSITION, ATTACHMENT_FILENAME + ECS_DELEGATE + TAR_GZ)
          .build();
    }
  }

  @PublicApi
  @GET
  @Path("delegate-helm-values-yaml")
  @Timed
  @ExceptionMetered
  @Operation(operationId = "downloadHelmDelegateYaml", summary = "Retrieves delegate yaml file",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Delegate yaml config file")
      })
  public Response
  downloadDelegateValuesYaml(@Context HttpServletRequest request,
      @Parameter(description = "Account UUID") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Name for a new delegate") @QueryParam("delegateName") @NotEmpty String delegateName,
      @Parameter(description = "Delegate Configuration UUID") @QueryParam("delegateProfileId") String delegateProfileId,
      @Parameter(description = "Token value") @QueryParam("token") @NotEmpty String token,
      @Parameter(description = "Token name") @QueryParam("tokenName") String tokenName) throws IOException {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      downloadTokenService.validateDownloadToken(DELEGATE + accountId, token);
      File delegateFile =
          delegateService.downloadDelegateValuesYamlFile(subdomainUrlHelper.getManagerUrl(request, accountId),
              getVerificationUrl(request), accountId, delegateName, delegateProfileId, tokenName);
      return Response.ok(delegateFile)
          .header(CONTENT_TRANSFER_ENCODING, BINARY)
          .type("text/plain; charset=UTF-8")
          .header(CONTENT_DISPOSITION, ATTACHMENT_FILENAME + HARNESS_DELEGATE_VALUES_YAML + YAML)
          .build();
    }
  }

  // TODO: ARPIT remove this api once UI starts using the below newly created api

  @GET
  @Path("/ng/validate-docker-delegate-details")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @Operation(operationId = "validateDockerDelegateDetails",
      summary = "Validates docker delegate details. "
          + "If tokenName is specified in Delegate Setup details in the body, it will be validated as well.",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Validates docker delegate details.") })
  @Deprecated
  public RestResponse<Void>
  validateDockerSetupDetails(@Parameter(description = "Account id") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Delegate name") @QueryParam("delegateName") String delegateName,
      @Parameter(description = "Delegate token") @QueryParam("tokenName") String tokenName) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.warn("using a deprecated api to download ng docker delegate.");
      DelegateSetupDetails delegateSetupDetails =
          DelegateSetupDetails.builder().delegateType(DOCKER).name(delegateName).tokenName(tokenName).build();
      delegateService.validateDockerDelegateSetupDetails(accountId, delegateSetupDetails, DOCKER);
      return new RestResponse<>();
    }
  }

  @GET
  @Path("/ng/validate-docker-delegate-setup")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @Operation(operationId = "validateNGDockerDelegateDetails",
      summary = "Validates docker delegate details. "
          + "If tokenName is specified in Delegate Setup details in the body, it will be validated as well.",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Validates docker delegate details.") })
  public RestResponse<Void>
  validateDockerDelegateSetupDetails(
      @Parameter(description = "Account id") @QueryParam("accountId") @NotEmpty String accountId,
      @RequestBody(required = true, description = "Delegate setup details, containing data to populate file values.")
      DelegateSetupDetails delegateSetupDetails) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, delegateSetupDetails.getOrgIdentifier(),
                                                  delegateSetupDetails.getProjectIdentifier()),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_VIEW_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      delegateService.validateDockerDelegateSetupDetails(accountId, delegateSetupDetails, DOCKER);
      return new RestResponse<>();
    }
  }

  @POST
  @Path("/ng/docker")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @Operation(operationId = "downloadNgDocker",
      summary =
          "Generates docker-compose.yaml or launch-docker-delegate.sh file from the data specified in request body (Delegate setup details). "
          + "If Delegate Token name is provided within Delegate Setup Details its value will be used for account secret in generated docker-compose.yaml file.",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Generated yaml or sh file.") })
  public Response
  downloadNgDocker(@Context HttpServletRequest request,
      @Parameter(description = "Account id") @QueryParam("accountId") @NotEmpty String accountId,
      @RequestBody(required = true, description = "Delegate setup details, containing data to populate file values.")
      DelegateSetupDetails delegateSetupDetails) throws IOException {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, delegateSetupDetails.getOrgIdentifier(),
                                                  delegateSetupDetails.getProjectIdentifier()),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_EDIT_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      String managerUrl = subdomainUrlHelper.getManagerUrl(request, accountId);
      File delegateFile =
          delegateService.downloadNgDocker(managerUrl, getVerificationUrl(request), accountId, delegateSetupDetails);

      return Response.ok(delegateFile)
          .header(CONTENT_TRANSFER_ENCODING, BINARY)
          .type("text/plain; charset=UTF-8")
          .header(CONTENT_DISPOSITION, ATTACHMENT_FILENAME + "docker-compose.yaml")
          .build();
    }
  }

  @POST
  @Path("/ng/delegate-group")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @Operation(operationId = "createDelegateGroup", summary = "Creates delegate group.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns HTTP 200 if delegate group was created successfully.")
      })
  public Response
  createDelegateGroup(@Context HttpServletRequest request,
      @Parameter(description = "Account id") @QueryParam("accountId") @NotEmpty String accountId,
      @RequestBody(required = true, description = "Delegate setup details, containing data to store delegate group.")
      DelegateSetupDetails delegateSetupDetails) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, delegateSetupDetails.getOrgIdentifier(),
                                                  delegateSetupDetails.getProjectIdentifier()),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_EDIT_PERMISSION);
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      delegateService.createDelegateGroup(accountId, delegateSetupDetails);

      return Response.ok().build();
    }
  }

  // TODO: ARPIT remove this api once UI starts a new one from DelegateTokenNgResource
  @GET
  @Path("/ng/delegate-token")
  @Timed
  @ExceptionMetered
  @Operation(operationId = "getDelegateGroups", summary = "Lists Delegate Groups.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "A list of Delegate Groups.")
      })
  public RestResponse<DelegateGroupListing>
  list(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotEmpty @QueryParam(
           "accountId") @NotNull String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Delegate Token name") @QueryParam("delegateTokenName") String delegateTokenName) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_VIEW_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountIdentifier, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateSetupService.listDelegateGroupDetails(
          accountIdentifier, orgIdentifier, projectIdentifier, delegateTokenName));
    }
  }

  private String getVerificationUrl(HttpServletRequest request) {
    return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
  }
}
