/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
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

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.data.validator.Trimmed;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateApproval;
import io.harness.delegate.beans.DelegateApprovalResponse;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.beans.DelegateSelector;
import io.harness.delegate.beans.DelegateSetupDetails;
import io.harness.delegate.beans.DelegateSizeDetails;
import io.harness.delegate.beans.DelegateTags;
import io.harness.delegate.service.intfc.DelegateInstallationCommandService;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.delegate.utilities.DelegateDeleteResponse;
import io.harness.delegate.utils.DelegateEntityOwnerHelper;
import io.harness.k8s.KubernetesConvention;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.LearningEngineAuth;
import io.harness.security.annotations.PublicApi;
import io.harness.service.intfc.DelegateCache;

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
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Api("/setup/delegates")
@Path("/setup/delegates")
@Produces("application/json")
@Scope(DELEGATE)
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@OwnedBy(DEL)
public class DelegateSetupResource {
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

  private final DelegateService delegateService;
  private final DelegateCache delegateCache;
  private final DelegateScopeService delegateScopeService;
  private final DownloadTokenService downloadTokenService;
  private final SubdomainUrlHelperIntfc subdomainUrlHelper;
  private final AccessControlClient accessControlClient;
  private final DelegateInstallationCommandService delegateInstallationCommandService;

  @Inject
  public DelegateSetupResource(DelegateService delegateService, DelegateScopeService delegateScopeService,
      DownloadTokenService downloadTokenService, SubdomainUrlHelperIntfc subdomainUrlHelper,
      DelegateCache delegateCache, AccessControlClient accessControlClient,
      DelegateInstallationCommandService delegateInstallationCommandService) {
    this.delegateService = delegateService;
    this.delegateScopeService = delegateScopeService;
    this.downloadTokenService = downloadTokenService;
    this.subdomainUrlHelper = subdomainUrlHelper;
    this.delegateCache = delegateCache;
    this.accessControlClient = accessControlClient;
    this.delegateInstallationCommandService = delegateInstallationCommandService;
  }

  @GET
  @ApiImplicitParams(
      { @ApiImplicitParam(name = "accountId", required = true, dataType = "string", paramType = "query") })
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<PageResponse<Delegate>>
  list(@BeanParam PageRequest<Delegate> pageRequest) {
    return new RestResponse<>(delegateService.list(pageRequest));
  }

  /**
   * @deprecated  After feature DELEGATE_SCALING_GROUP is turned on, we should use listDelegateStatusWithScalingGroup
   *     method instead
   */
  @GET
  @Path("status")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @Deprecated
  public RestResponse<DelegateStatus> listDelegateStatus(@QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.getDelegateStatus(accountId));
    }
  }

  @GET
  @Path("status2")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<DelegateStatus> listDelegateStatusWithScalingGroups(
      @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.getDelegateStatusWithScalingGroups(accountId));
    }
  }

  @GET
  @Path("available-versions-for-verification")
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<List<String>> getAvailableVersions(@QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.getAvailableVersions(accountId));
    }
  }

  @GET
  @Path("connected-ratio-with-primary")
  @Timed
  @ExceptionMetered
  @PublicApi
  public RestResponse<Double> getConnectedRatioWithPrimary(@QueryParam("targetVersion") @NotEmpty String targetVersion,
      @QueryParam("accountId") String accountId, @QueryParam("ringName") String ringName) {
    return new RestResponse<>(delegateService.getConnectedRatioWithPrimary(targetVersion, accountId, ringName));
  }

  @GET
  @Path("latest")
  @Timed
  @ExceptionMetered
  public RestResponse<String> get(@QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.getLatestDelegateVersion(accountId));
    }
  }

  @GET
  @Path("validateDelegateName")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Boolean> validateThatDelegateNameIsUnique(
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("delegateName") @NotEmpty String delegateName) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.validateThatDelegateNameIsUnique(accountId, delegateName));
    }
  }

  @GET
  @Path("validate-ce-delegate")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<CEDelegateStatus> validateCEDelegate(
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("delegateName") @NotEmpty String delegateName) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.validateCEDelegate(accountId, delegateName));
    }
  }

  @GET
  @Path("{delegateId}/profile-result")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<String> getProfileResult(
      @PathParam("delegateId") String delegateId, @QueryParam("accountId") @NotEmpty String accountId) {
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
  // This NG specific, switching to NG access control. AuthRule to be removed also when NG access control is fully
  // enabled.
  public RestResponse<List<DelegateSizeDetails>> delegateSizes(@QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("orgId") String orgId, @QueryParam("projectId") String projectId) {
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
  public RestResponse<Delegate> updateScopes(@PathParam("delegateId") @NotEmpty String delegateId,
      @QueryParam("accountId") @NotEmpty String accountId, DelegateScopes delegateScopes) {
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

  @PUT
  @Path("{delegateId}/tags")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATES)
  public RestResponse<Delegate> updateTags(@PathParam("delegateId") @NotEmpty String delegateId,
      @QueryParam("accountId") @NotEmpty String accountId, DelegateTags delegateTags) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      Delegate delegate = delegateCache.get(accountId, delegateId, true);
      return new RestResponse<>(delegateService.updateTagsFromUI(delegate, delegateTags));
    }
  }

  @GET
  @Path("kubernetes-delegates")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<List<String>> kubernetesDelegateNames(
      @Context HttpServletRequest request, @QueryParam("accountId") @NotEmpty String accountId) {
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
  public RestResponse<Set<String>> delegateSelectors(
      @Context HttpServletRequest request, @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.getAllDelegateSelectors(accountId));
    }
  }

  @GET
  @Path("delegate-selectors-up-the-hierarchy")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Set<String>> delegateSelectorsUpTheHierarchy(@Context HttpServletRequest request,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("orgId") String orgId,
      @QueryParam("projectId") String projectId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_VIEW_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.getAllDelegateSelectorsUpTheHierarchy(accountId, orgId, projectId));
    }
  }

  @GET
  @Path("delegate-selectors-up-the-hierarchy-v2")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<List<DelegateSelector>> delegateSelectorsUpTheHierarchyV2(@Context HttpServletRequest request,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("orgId") String orgId,
      @QueryParam("projectId") String projectId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_VIEW_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.getAllDelegateSelectorsUpTheHierarchyV2(accountId, orgId, projectId));
    }
  }

  @GET
  @Path("delegate-tags")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Set<String>> delegateTags(
      @Context HttpServletRequest request, @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.getAllDelegateSelectors(accountId));
    }
  }

  @GET
  @Path("{delegateId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Delegate> get(
      @PathParam("delegateId") @NotEmpty String delegateId, @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateCache.get(accountId, delegateId));
    }
  }

  @POST
  @Path("validate-kubernetes-yaml")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  // This NG specific, switching to NG access control. AuthRule to be removed also when NG access control is fully
  // enabled.
  public RestResponse<DelegateSetupDetails> validateKubernetesYaml(@Context HttpServletRequest request,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("orgId") String orgId,
      @QueryParam("projectId") String projectId, DelegateSetupDetails delegateSetupDetails) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_EDIT_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.validateKubernetesSetupDetails(accountId, delegateSetupDetails));
    }
  }

  @POST
  @Path("generate-kubernetes-yaml")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  // This NG specific, switching to NG access control. AuthRule to be removed also when NG access control is fully
  // enabled.
  public Response generateKubernetesYaml(@Context HttpServletRequest request,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("orgId") String orgId,
      @QueryParam("projectId") String projectId, DelegateSetupDetails delegateSetupDetails,
      @QueryParam("fileFormat") MediaType fileFormat) throws IOException {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_EDIT_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      File delegateFile = delegateService.generateKubernetesYaml(accountId, delegateSetupDetails,
          subdomainUrlHelper.getManagerUrl(request, accountId), getVerificationUrl(request), fileFormat);

      if (fileFormat != null && fileFormat.equals(MediaType.TEXT_PLAIN_TYPE)) {
        return Response.ok(delegateFile)
            .header(CONTENT_TRANSFER_ENCODING, BINARY)
            .type("text/plain; charset=UTF-8")
            .header(CONTENT_DISPOSITION, ATTACHMENT_FILENAME + KUBERNETES_DELEGATE + YAML)
            .build();
      }

      return Response.ok(delegateFile)
          .header(CONTENT_TRANSFER_ENCODING, BINARY)
          .type(APPLICATION_ZIP_CHARSET_BINARY)
          .header(CONTENT_DISPOSITION, ATTACHMENT_FILENAME + KUBERNETES_DELEGATE + TAR_GZ)
          .build();
    }
  }

  @PUT
  @Path("{delegateId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATES)
  public RestResponse<Delegate> update(@PathParam("delegateId") @NotEmpty String delegateId,
      @QueryParam("accountId") @NotEmpty String accountId, Delegate delegate) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      delegate.setAccountId(accountId);
      delegate.setUuid(delegateId);
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
  public RestResponse<Delegate> updateDescription(@PathParam("delegateId") @NotEmpty String delegateId,
      @QueryParam("accountId") @NotEmpty String accountId, @Trimmed String newDescription) {
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
  public RestResponse<Delegate> updateApprovalStatus(@PathParam("delegateId") @NotEmpty String delegateId,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("action") @NotNull DelegateApproval action) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.updateApprovalStatus(accountId, delegateId, action));
    }
  }

  @PUT
  @Path("/delegate-profile/{delegateProfileId}/approve")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @ApiKeyAuthorized(permissionType = MANAGE_DELEGATES)
  public RestResponse<DelegateApprovalResponse> approveDelegatesUsingProfile(
      @PathParam("delegateProfileId") @NotEmpty String delegateProfileId,
      @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(
          delegateService.approveDelegatesUsingProfile(accountId, delegateProfileId, DelegateApproval.ACTIVATE));
    }
  }

  @PUT
  @Path("/delegate-token/{delegateTokenName}/approve")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @ApiKeyAuthorized(permissionType = MANAGE_DELEGATES)
  public RestResponse<DelegateApprovalResponse> approveDelegatesUsingToken(
      @PathParam("delegateTokenName") @NotEmpty String delegateTokenName,
      @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(
          delegateService.approveDelegatesUsingToken(accountId, delegateTokenName, DelegateApproval.ACTIVATE));
    }
  }

  @PUT
  @Path("/delegate-profile/{delegateProfileId}/reject")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @ApiKeyAuthorized(permissionType = MANAGE_DELEGATES)
  public RestResponse<DelegateApprovalResponse> rejectDelegatesUsingProfile(
      @PathParam("delegateProfileId") @NotEmpty String delegateProfileId,
      @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(
          delegateService.approveDelegatesUsingProfile(accountId, delegateProfileId, DelegateApproval.REJECT));
    }
  }

  @PUT
  @Path("/delegate-token/{delegateTokenName}/reject")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @ApiKeyAuthorized(permissionType = MANAGE_DELEGATES)
  public RestResponse<DelegateApprovalResponse> rejectDelegatesUsingToken(
      @PathParam("delegateTokenName") @NotEmpty String delegateTokenName,
      @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(
          delegateService.approveDelegatesUsingToken(accountId, delegateTokenName, DelegateApproval.REJECT));
    }
  }

  @DELETE
  @Path("{delegateId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @ApiKeyAuthorized(permissionType = MANAGE_DELEGATES)
  public RestResponse<DelegateDeleteResponse> delete(
      @PathParam("delegateId") @NotEmpty String delegateId, @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.delete(accountId, delegateId));
    }
  }
  @DELETE
  @Path("delete-all-except")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATES)
  public RestResponse<Void> deleteAllExcept(
      @QueryParam("accountId") @NotEmpty String accountId, List<String> delegatesToRetain) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      delegateService.retainOnlySelectedDelegatesAndDeleteRest(accountId, delegatesToRetain);
      return new RestResponse<>();
    }
  }

  @DELETE
  @Path("groups/{delegateGroupId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  // This NG specific, switching to NG access control. AuthRule to be removed also when NG access control is fully
  // enabled.
  public RestResponse<Void> deleteDelegateGroup(@PathParam("delegateGroupId") @NotEmpty String delegateGroupId,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("orgId") String orgId,
      @QueryParam("projectId") String projectId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, delegateGroupId), DELEGATE_DELETE_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      delegateService.deleteDelegateGroup(accountId, delegateGroupId);
      return new RestResponse<>();
    }
  }

  // TODO: ARPIT remove this api once UI starts using delegateSetupResourceV3 api

  @GET
  @Path(DOWNLOAD_URL)
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = MANAGE_DELEGATES)
  @Deprecated
  public RestResponse<Map<String, String>> downloadUrl(
      @Context HttpServletRequest request, @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.warn("Using a deprecated api to download cg delegates.");
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
  public Response downloadScripts(@Context HttpServletRequest request,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("delegateName") String delegateName,
      @QueryParam("delegateProfileId") String delegateProfileId, @QueryParam("token") @NotEmpty String token,
      @QueryParam("tokenName") String tokenName) throws IOException {
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
  public Response downloadDocker(@Context HttpServletRequest request,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("delegateName") String delegateName,
      @QueryParam("delegateProfileId") String delegateProfileId, @QueryParam("token") @NotEmpty String token,
      @QueryParam("tokenName") String tokenName) throws IOException {
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
  public Response downloadKubernetes(@Context HttpServletRequest request,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("delegateName") @NotEmpty String delegateName,
      @QueryParam("delegateProfileId") String delegateProfileId, @QueryParam("token") @NotEmpty String token,
      @QueryParam("isCeEnabled") @DefaultValue("false") boolean isCeEnabled, @QueryParam("tokenName") String tokenName,
      @QueryParam("runAsRoot") @DefaultValue("true") boolean runAsRoot) throws IOException {
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
  public RestResponse<String> getAccountIdentifier(@QueryParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(KubernetesConvention.getAccountIdentifier(accountId));
  }

  @PublicApi
  @GET
  @Path("ecs")
  @Timed
  @ExceptionMetered
  public Response downloadEcs(@Context HttpServletRequest request, @QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("delegateGroupName") @NotEmpty String delegateGroupName, @QueryParam("awsVpcMode") Boolean awsVpcMode,
      @QueryParam("hostname") String hostname, @QueryParam("delegateProfileId") String delegateProfileId,
      @QueryParam("token") @NotEmpty String token, @QueryParam("tokenName") String tokenName) throws IOException {
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
  public Response downloadDelegateValuesYaml(@Context HttpServletRequest request,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("delegateName") @NotEmpty String delegateName,
      @QueryParam("delegateProfileId") String delegateProfileId, @QueryParam("token") @NotEmpty String token,
      @QueryParam("tokenName") String tokenName) throws IOException {
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

  @GET
  @Path("installation-command")
  @Timed
  @AuthRule(permissionType = LOGGED_IN)
  @ExceptionMetered
  public RestResponse<Map<String, String>> getInstallationCommand(@Context HttpServletRequest request,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("orgId") String orgId,
      @QueryParam("projectId") String projectId, @QueryParam("commandType") @NotEmpty String commandType)
      throws IOException {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      final String managerUrl = subdomainUrlHelper.getManagerUrl(request, accountId);
      final DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgId, projectId);
      final String command = delegateInstallationCommandService.getCommand(commandType, managerUrl, accountId, owner);
      ImmutableMap<String, String> commandResponse =
          ImmutableMap.<String, String>builder().put("command", command).build();
      return new RestResponse(commandResponse);
    }
  }

  private String getVerificationUrl(HttpServletRequest request) {
    return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
  }
}
