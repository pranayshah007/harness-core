/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.commons.dao.CEDataCleanupRequestDao;
import io.harness.ccm.commons.entities.batch.CEDataCleanupRequest;
import io.harness.ccm.license.CeLicenseInfo;
import io.harness.datahandler.models.AccountSummary;
import io.harness.datahandler.models.FeatureFlagBO;
import io.harness.datahandler.services.AdminAccountService;
import io.harness.datahandler.services.AdminUserService;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.remote.admin.AdminLicenseHttpClient;
import io.harness.limits.ActionType;
import io.harness.limits.ConfiguredLimit;
import io.harness.limits.impl.model.RateLimit;
import io.harness.limits.impl.model.StaticLimit;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.common.beans.Generation;
import io.harness.rest.RestResponse;

import software.wings.beans.Account;
import software.wings.beans.CeLicenseUpdateInfo;
import software.wings.beans.LicenseInfo;
import software.wings.beans.LicenseUpdateInfo;
import software.wings.security.annotations.AdminPortalAuth;
import software.wings.service.intfc.DelegateService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.http.Body;

@OwnedBy(PL)
@Path("/admin/accounts")
@Slf4j
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@AdminPortalAuth
@TargetModule(HarnessModule._955_ACCOUNT_MGMT)
public class AdminAccountResource {
  private AdminAccountService adminAccountService;
  private AdminUserService adminUserService;
  private AccessControlAdminClient accessControlAdminClient;
  private DelegateService delegateService;
  private AdminLicenseHttpClient adminLicenseHttpClient;

  @Inject
  public AdminAccountResource(AdminAccountService adminAccountService, AdminUserService adminUserService,
      AccessControlAdminClient accessControlAdminClient, DelegateService delegateService,
      AdminLicenseHttpClient adminLicenseHttpClient) {
    this.adminAccountService = adminAccountService;
    this.adminUserService = adminUserService;
    this.accessControlAdminClient = accessControlAdminClient;
    this.delegateService = delegateService;
    this.adminLicenseHttpClient = adminLicenseHttpClient;
  }

  @Inject CEDataCleanupRequestDao ceDataCleanupRequestDao;

  @GET
  @Path("summary")
  public RestResponse<List<AccountSummary>> getAccounts(
      @QueryParam("pageSize") Integer pageSize, @QueryParam("offset") String offset) {
    return new RestResponse<>(adminAccountService.getPaginatedAccountSummaries(offset, pageSize));
  }

  @GET
  @Path("summary/{accountId}")
  public RestResponse<AccountSummary> getAccountSummaryByAccountId(@PathParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(adminAccountService.getAccountSummaryByAccountId(accountId));
  }

  @GET
  @Path("{accountId}/license")
  public RestResponse<LicenseInfo> getLicenseInfoForAccount(@PathParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(adminAccountService.getLicense(accountId));
  }

  @PUT
  @Path("{accountId}/license")
  public RestResponse<LicenseInfo> updateAccountLicense(
      @PathParam("accountId") @NotEmpty String accountId, @NotNull LicenseUpdateInfo licenseUpdateInfo) {
    return new RestResponse<>(adminAccountService.updateLicense(accountId, licenseUpdateInfo));
  }

  @PUT
  @Path("{accountId}/nextgen-accesscontrol-enabled")
  public RestResponse<Boolean> updateNextgenAccessControlPreference(
      @PathParam("accountId") String accountId, @QueryParam("enabled") @DefaultValue("false") boolean enabled) {
    return new RestResponse<>(getResponse(accessControlAdminClient.updateAccessControlPreference(accountId, enabled)));
  }

  @PUT
  @Path("{accountId}/nextgen-enabled")
  public RestResponse<Boolean> updateNextgenEnabled(
      @PathParam("accountId") String accountId, @QueryParam("enabled") @DefaultValue("false") boolean enabled) {
    return new RestResponse<>(adminAccountService.enableOrDisableNextGen(accountId, enabled));
  }

  @PUT
  @Path("{accountId}/sync-with-cg")
  public RestResponse<Boolean> syncNextgen(@PathParam("accountId") String accountId) {
    return new RestResponse<>(adminAccountService.syncNextgenWithCG(accountId));
  }

  @PUT
  @Path("{accountId}/nextgen-cleanup")
  public RestResponse<Boolean> cleanupNextgenUser(@PathParam("accountId") String accountId) {
    return new RestResponse<>(adminAccountService.cleanUpNextGen(accountId));
  }

  @POST
  @Path("{accountId}/disable-ip-allowlist")
  public Response disableIpAllowList(@PathParam("accountId") String accountId) {
    return Response.status(Response.Status.OK).entity(adminAccountService.disableIpAllowList(accountId)).build();
  }

  @PUT
  @Path("{accountId}/is-product-led")
  public RestResponse<Boolean> updateIsProductLed(@PathParam("accountId") String accountId,
      @QueryParam("isProductLed") @DefaultValue("false") boolean isProductLed) {
    return new RestResponse<>(adminAccountService.updateIsProductLed(accountId, isProductLed));
  }

  @PUT
  @Path("{accountId}/license/continuous-efficiency/")
  @Timed
  @ExceptionMetered
  public RestResponse<CeLicenseInfo> updateCeLicense(
      @PathParam("accountId") @NotEmpty String accountId, @NotNull CeLicenseUpdateInfo ceLicenseUpdateInfo) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Updating CE license.");
      return new RestResponse<>(adminAccountService.updateCeLicense(accountId, ceLicenseUpdateInfo.getCeLicenseInfo()));
    }
  }

  @POST
  @Path("{accountId}/ceDataCleanUpRequest")
  public RestResponse addDataCleanUpRequest(
      @PathParam("accountId") @NotEmpty String accountId, @NotNull CEDataCleanupRequest ceDataCleanupRequest) {
    ceDataCleanupRequest.setAccountId(accountId);
    return new RestResponse<>(ceDataCleanupRequestDao.save(ceDataCleanupRequest));
  }

  @DELETE
  @Path("{accountId}/ceDataCleanUpRequest/{id}")
  public RestResponse removeDataCleanUpRequest(
      @PathParam("accountId") @NotEmpty String accountId, @PathParam("id") @NotEmpty String ceDataCleanupRequestId) {
    return new RestResponse<>(ceDataCleanupRequestDao.delete(ceDataCleanupRequestId));
  }

  @GET
  @Path("{accountId}/limits")
  public RestResponse<List<ConfiguredLimit>> getLimitsForAccount(@PathParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(adminAccountService.getLimitsConfiguredForAccount(accountId));
  }

  @PUT
  @Path("{accountId}/limits/static-limit")
  public RestResponse<ConfiguredLimit> configureStaticLimit(@PathParam("accountId") String accountId,
      @QueryParam("actionType") ActionType actionType, @Body StaticLimit limit) {
    return new RestResponse<>(adminAccountService.updateLimit(accountId, actionType, limit));
  }

  @PUT
  @Path("{accountId}/limits/rate-limit")
  public RestResponse<ConfiguredLimit> configureRateLimit(@PathParam("accountId") String accountId,
      @QueryParam("actionType") ActionType actionType, @Body RateLimit limit) {
    return new RestResponse<>(adminAccountService.updateLimit(accountId, actionType, limit));
  }

  @POST
  @Path("")
  public RestResponse<Account> createAccount(
      @Body Account account, @QueryParam("adminUserEmail") String adminUserEmail) {
    return new RestResponse<>(adminAccountService.createAccount(account, adminUserEmail));
  }

  @POST
  @Path("/global")
  public RestResponse<Account> createGlobalDelegateAccount(
      @Body Account account, @QueryParam("adminUserEmail") String adminUserEmail) {
    return new RestResponse<>(adminAccountService.createAccount(account, adminUserEmail));
  }

  @PUT
  @Path("/{accountId}/enable")
  public RestResponse<Boolean> enableAccount(@PathParam("accountId") String accountId) {
    return new RestResponse<>(adminAccountService.enableAccount(accountId));
  }

  @PUT
  @Path("/{accountId}/disable")
  public RestResponse<Boolean> disableAccount(
      @PathParam("accountId") String accountId, @QueryParam("newClusterUrl") String newClusterUrl) {
    return new RestResponse<>(adminAccountService.disableAccount(accountId, newClusterUrl));
  }

  @PUT
  @Path("/{accountId}/users/{userIdOrEmail}")
  public RestResponse<Boolean> enableOrDisableUser(@PathParam("accountId") String accountId,
      @PathParam("userIdOrEmail") String userIdOrEmail, @QueryParam("enable") boolean enabled) {
    return new RestResponse<>(adminUserService.enableOrDisableUser(accountId, userIdOrEmail, enabled));
  }

  @PUT
  @Path("/{accountId}/users/{userIdOrEmail}/account-admin")
  public RestResponse<Boolean> assignAdminRoleToUserInNG(
      @PathParam("accountId") String accountId, @PathParam("userIdOrEmail") String userIdOrEmail) {
    return new RestResponse<>(adminUserService.assignAdminRoleToUserInNG(accountId, userIdOrEmail));
  }

  @PUT
  @Path("/{accountId}/cloudCost")
  public RestResponse<Boolean> enableOrDisableCloudCost(
      @PathParam("accountId") String accountId, @QueryParam("enable") boolean enabled) {
    return new RestResponse<>(adminAccountService.enableOrDisableCloudCost(accountId, enabled));
  }

  @PUT
  @Path("/{accountId}/ceAutoCollectK8sEvents")
  public RestResponse<Boolean> enableOrDisableCeAutoCollectK8sEvents(
      @PathParam("accountId") String accountId, @QueryParam("enable") boolean enabled) {
    return new RestResponse<>(false);
  }

  @DELETE
  @Path("{accountId}")
  public RestResponse<Boolean> deleteAccount(@PathParam("accountId") String accountId) {
    return new RestResponse<>(adminAccountService.delete(accountId));
  }

  @PUT
  @Path("{accountId}/feature-flags")
  public RestResponse<FeatureFlagBO> updateFeatureFlagForAccount(@PathParam("accountId") String accountId,
      @QueryParam("featureName") String featureName, @QueryParam("enable") boolean enable) {
    return new RestResponse<>(
        FeatureFlagBO.fromFeatureFlag(adminAccountService.updateFeatureFlagForAccount(accountId, featureName, enable)));
  }

  @PUT
  @Path("{accountId}/pov")
  public RestResponse<Boolean> updatePovFlag(
      @PathParam("accountId") @NotEmpty String accountId, @QueryParam("isPov") boolean isPov) {
    return new RestResponse<>(adminAccountService.updatePovFlag(accountId, isPov));
  }

  @PUT
  @Path("{accountId}/details")
  public RestResponse<Boolean> updateAccountDetails(@PathParam("accountId") String accountId,
      @QueryParam("account-name") String accountName, @QueryParam("company-name") String companyName,
      @QueryParam("ring-name") String ringName) {
    boolean accountNameUpdateSuccess = true;
    boolean companyNameUpdateStatus = true;
    boolean ringNameUpdateStatus = true;
    if (!StringUtils.isEmpty(accountName)) {
      accountNameUpdateSuccess = adminAccountService.updateAccountName(accountId, accountName);
    }
    if (!StringUtils.isEmpty(companyName)) {
      companyNameUpdateStatus = adminAccountService.updateCompanyName(accountId, companyName);
    }
    if (!StringUtils.isEmpty(ringName)) {
      ringNameUpdateStatus = adminAccountService.updateRingName(accountId, companyName);
    }
    return new RestResponse<>(accountNameUpdateSuccess && companyNameUpdateStatus && ringNameUpdateStatus);
  }

  @GET
  @Path("delegates-with-version")
  public RestResponse<Map<String, List<String>>> getActiveDelegatesWithPrimary(@QueryParam("version") String version) {
    return new RestResponse<>(delegateService.getActiveDelegatesPerAccount(version));
  }

  @POST
  @Path("{accountId}/ng/license")
  public RestResponse<ModuleLicenseDTO> createNgLicense(
      @PathParam("accountId") String accountId, @Body ModuleLicenseDTO moduleLicenseDTO) {
    return new RestResponse<>(getResponse(adminLicenseHttpClient.createAccountLicense(accountId, moduleLicenseDTO)));
  }

  @PUT
  @Path("{accountId}/ng/license")
  public RestResponse<ModuleLicenseDTO> updateNgLicense(
      @PathParam("accountId") String accountId, @Body ModuleLicenseDTO moduleLicenseDTO) {
    return new RestResponse<>(
        getResponse(adminLicenseHttpClient.updateModuleLicense(moduleLicenseDTO.getId(), accountId, moduleLicenseDTO)));
  }

  @GET
  @Path("{accountId}/ng/license")
  public RestResponse<AccountLicenseDTO> getNgAccountLicenses(@PathParam("accountId") String accountId) {
    return new RestResponse<>(getResponse(adminLicenseHttpClient.getAccountLicense(accountId)));
  }

  @DELETE
  @Path("{accountId}/ng/license")
  public RestResponse<Void> deleteNgLicense(
      @PathParam("accountId") String accountId, @QueryParam("identifier") String identifier) {
    return new RestResponse<>(getResponse(adminLicenseHttpClient.deleteModuleLicense(identifier, accountId)));
  }

  @PUT
  @Path("update-externally-managed/{userId}")
  public RestResponse<Boolean> updateScimStatusNG(@PathParam("userId") String userId,
      @QueryParam("generation") Generation generation, @Body Boolean externallyManaged) {
    return new RestResponse<>(adminUserService.updateExternallyManaged(userId, generation, externallyManaged));
  }
}
