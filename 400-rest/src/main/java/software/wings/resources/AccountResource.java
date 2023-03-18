/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessModule._955_ACCOUNT_MGMT;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_AUTHENTICATION_SETTINGS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_RESTRICTED_ACCESS;
import static software.wings.utils.Utils.urlDecode;

import static org.apache.commons.lang3.StringUtils.substringAfter;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.authenticationservice.beans.AuthenticationInfo;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.cvng.beans.ServiceGuardLimitDTO;
import io.harness.datahandler.models.AccountDetails;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.SMPEncLicenseDTO;
import io.harness.licensing.beans.modules.SMPLicenseRequestDTO;
import io.harness.licensing.beans.modules.SMPValidationResultDTO;
import io.harness.licensing.remote.admin.AdminLicenseHttpClient;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.marketplace.gcp.GcpMarketPlaceApiHandler;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.rest.RestResponse;
import io.harness.scheduler.PersistentScheduler;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.LearningEngineAuth;
import io.harness.security.annotations.PublicApi;
import io.harness.seeddata.SampleDataProviderService;
import io.harness.service.intfc.DelegateAuthService;

import software.wings.beans.Account;
import software.wings.beans.AccountEvent;
import software.wings.beans.AccountMigration;
import software.wings.beans.AccountSalesContactsInfo;
import software.wings.beans.AccountType;
import software.wings.beans.LicenseInfo;
import software.wings.beans.LicenseUpdateInfo;
import software.wings.beans.Service;
import software.wings.beans.SubdomainUrl;
import software.wings.beans.TechStack;
import software.wings.beans.User;
import software.wings.features.api.FeatureService;
import software.wings.licensing.LicenseService;
import software.wings.scheduler.AccountJobProperties;
import software.wings.scheduler.AccountJobType;
import software.wings.scheduler.ServiceInstanceUsageCheckerJob;
import software.wings.security.UserThreadLocal;
import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.AuthRule;
import software.wings.service.impl.LicenseUtils;
import software.wings.service.impl.analysis.CVEnabledService;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.utils.AccountPermissionUtils;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.dropwizard.jersey.PATCH;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
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
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.web.bind.annotation.RequestBody;
import retrofit2.http.Body;

@Api("account")
@Path("/account")
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Singleton
@OwnedBy(PL)
@TargetModule(_955_ACCOUNT_MGMT)
public class AccountResource {
  private final AccountService accountService;
  private final UserService userService;
  private final Provider<LicenseService> licenseServiceProvider;
  private final AccountPermissionUtils accountPermissionUtils;
  private final FeatureService featureService;
  private final PersistentScheduler jobScheduler;
  private final GcpMarketPlaceApiHandler gcpMarketPlaceApiHandler;
  private final Provider<SampleDataProviderService> sampleDataProviderServiceProvider;
  private final AuthService authService;
  private final HarnessUserGroupService harnessUserGroupService;
  private final AdminLicenseHttpClient adminLicenseHttpClient;

  private final DelegateAuthService delegateAuthService;

  @Inject
  public AccountResource(AccountService accountService, UserService userService,
      Provider<LicenseService> licenseServiceProvider, AccountPermissionUtils accountPermissionUtils,
      FeatureService featureService, @Named("BackgroundJobScheduler") PersistentScheduler jobScheduler,
      GcpMarketPlaceApiHandler gcpMarketPlaceApiHandler,
      Provider<SampleDataProviderService> sampleDataProviderServiceProvider, AuthService authService,
      HarnessUserGroupService harnessUserGroupService, AdminLicenseHttpClient adminLicenseHttpClient,
      DelegateAuthService delegateAuthService) {
    this.accountService = accountService;
    this.userService = userService;
    this.licenseServiceProvider = licenseServiceProvider;
    this.accountPermissionUtils = accountPermissionUtils;
    this.featureService = featureService;
    this.jobScheduler = jobScheduler;
    this.gcpMarketPlaceApiHandler = gcpMarketPlaceApiHandler;
    this.sampleDataProviderServiceProvider = sampleDataProviderServiceProvider;
    this.authService = authService;
    this.harnessUserGroupService = harnessUserGroupService;
    this.adminLicenseHttpClient = adminLicenseHttpClient;
    this.delegateAuthService = delegateAuthService;
  }

  @GET
  @Path("{accountId}/status")
  @Timed
  @ExceptionMetered
  @PublicApi
  public RestResponse<String> getStatus(@PathParam("accountId") String accountId) {
    return new RestResponse<>(accountService.getAccountStatus(accountId));
  }

  @GET
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<PageResponse<Account>> getAccounts(@QueryParam("offset") String offset) {
    PageRequest<Account> accountPageRequest =
        aPageRequest().withOffset(offset).withLimit(String.valueOf(PageRequest.DEFAULT_PAGE_SIZE)).build();
    return new RestResponse<>(accountService.getAccounts(accountPageRequest));
  }

  @GET
  @Path("feature-flag-enabled")
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<Boolean> isFeatureEnabled(
      @QueryParam("featureName") String featureName, @QueryParam("accountId") String accountId) {
    return new RestResponse<>(accountService.isFeatureFlagEnabled(featureName, accountId));
  }

  @GET
  @Path("{accountId}/immutable-delegate-enabled")
  public RestResponse<Boolean> isImmutableDelegateEnabled(@PathParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(accountService.isImmutableDelegateEnabled(accountId));
  }

  @GET
  @Path("services-cv-24x7")
  @Timed
  @ExceptionMetered
  public RestResponse<PageResponse<CVEnabledService>> getAllServicesFor24x7(@QueryParam("accountId") String accountId,
      @QueryParam("serviceId") String serviceId, @BeanParam PageRequest<String> request) {
    return new RestResponse<>(
        accountService.getServices(accountId, UserThreadLocal.get().getPublicUser(false), request, serviceId));
  }

  @GET
  @Path("services-cv-24x7-breadcrumb")
  @Timed
  @ExceptionMetered
  public RestResponse<List<Service>> getAllServicesFor24x7(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(
        accountService.getServicesBreadCrumb(accountId, UserThreadLocal.get().getPublicUser(false)));
  }

  @PUT
  @Path("license")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> updateAccountLicense(
      @QueryParam("accountId") @NotEmpty String accountId, @NotNull LicenseUpdateInfo licenseUpdateInfo) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Updating account license");
      String fromAccountType = accountService.getAccountType(accountId).orElse(AccountType.PAID);
      String toAccountType = licenseUpdateInfo.getLicenseInfo().getAccountType();

      AccountMigration migration = null;
      if (!fromAccountType.equals(toAccountType)) {
        migration = AccountMigration.from(fromAccountType, toAccountType)
                        .orElseThrow(() -> new InvalidRequestException("Unsupported migration", WingsException.USER));
      }

      RestResponse<Boolean> response =
          accountPermissionUtils.checkIfHarnessUser("User not allowed to update account license");
      if (response == null
          || (migration != null && migration.isSelfService() && userService.isAccountAdmin(accountId))) {
        Map<String, Map<String, Object>> requiredInfoToComply = licenseUpdateInfo.getRequiredInfoToComply() == null
            ? Collections.emptyMap()
            : licenseUpdateInfo.getRequiredInfoToComply();

        if (migration != null
            && !featureService.complyFeatureUsagesWithRestrictions(accountId, toAccountType, requiredInfoToComply)) {
          throw new WingsException("Can not update account license. Account is using restricted features");
        }
        boolean licenseUpdated =
            licenseServiceProvider.get().updateAccountLicense(accountId, licenseUpdateInfo.getLicenseInfo());
        if (migration != null) {
          featureService.complyFeatureUsagesWithRestrictions(accountId, requiredInfoToComply);
          // Special Case. Enforce Service Instances Limits only for COMMUNITY account
          if (toAccountType.equals(AccountType.COMMUNITY)) {
            ServiceInstanceUsageCheckerJob.add(jobScheduler, accountId);
          }
        }

        if (licenseUpdated) {
          log.info("license updated for account {}, license type is {} and status is {}", accountId,
              licenseUpdateInfo.getLicenseInfo().getAccountType(),
              licenseUpdateInfo.getLicenseInfo().getAccountStatus());
        }
        response = new RestResponse<>(licenseUpdated);
      }

      return response;
    }
  }

  @PUT
  @Path("license/{accountId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> updateAccountLicense(
      @PathParam("accountId") @NotEmpty String accountId, @NotNull LicenseInfo licenseInfo) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Updating account license");
      return updateAccountLicense(accountId, LicenseUpdateInfo.builder().licenseInfo(licenseInfo).build());
    }
  }

  @POST
  @Path("/continuous-efficiency/{accountId}/startTrial")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  public RestResponse<Boolean> startCeTrial(@PathParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Starting CE Trial license.");
      licenseServiceProvider.get().startCeLimitedTrial(accountId);
      return new RestResponse<>(Boolean.TRUE);
    }
  }

  @PUT
  @Path("{accountId}/tech-stacks")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> updateTechStacks(
      @PathParam("accountId") @NotEmpty String accountId, Set<TechStack> techStacks) {
    return new RestResponse<>(accountService.updateTechStacks(accountId, techStacks));
  }

  @PUT
  @Path("{accountId}/is-product-led")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> updateIsProductLed(@PathParam("accountId") String accountId,
      @QueryParam("clientAccountId") @NotNull String clientAccountId,
      @QueryParam("isProductLed") @DefaultValue("false") boolean isProductLed) {
    User existingUser = UserThreadLocal.get();
    if (existingUser == null) {
      throw new InvalidRequestException("Invalid User");
    }

    if (harnessUserGroupService.isHarnessSupportUser(existingUser.getUuid())) {
      return new RestResponse<>(accountService.updateIsProductLed(clientAccountId, isProductLed));
    } else {
      return RestResponse.Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(
              ResponseMessage.builder().message("User not allowed to update account product-led status").build()))
          .build();
    }
  }

  @PUT
  @Path("{accountId}/sales-contacts")
  @Timed
  @ExceptionMetered
  public RestResponse<Account> updateAccountSalesContacts(
      @PathParam("accountId") @NotEmpty String accountId, AccountSalesContactsInfo salesContactsInfo) {
    RestResponse<Account> response =
        accountPermissionUtils.checkIfHarnessUser("User not allowed to update account sales contacts");
    if (response == null) {
      response = new RestResponse<>(
          licenseServiceProvider.get().updateAccountSalesContacts(accountId, salesContactsInfo.getSalesContacts()));
    }
    return response;
  }

  @PUT
  @Path("license/generate/{accountId}")
  @Timed
  @ExceptionMetered
  public RestResponse<String> generateLicense(
      @PathParam("accountId") @NotEmpty String accountId, LicenseInfo licenseInfo) {
    RestResponse<String> response =
        accountPermissionUtils.checkIfHarnessUser("User not allowed to generate a new license");
    if (response == null) {
      response = new RestResponse<>(LicenseUtils.generateLicense(licenseInfo));
    }
    return response;
  }

  @PUT
  @Path("{accountId}/defaultExperience")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  public RestResponse<Void> updateDefaultExperience(
      @PathParam("accountId") @NotEmpty String accountId, Account account) {
    return new RestResponse<>(accountService.setDefaultExperience(accountId, account.getDefaultExperience()));
  }

  @PUT
  @Hidden
  @Path("{accountId}/cross-generation-access")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  @InternalApi
  public RestResponse<Account> updateCrossGenerationAccessEnabled(@PathParam("accountId") @NotEmpty String accountId,
      @QueryParam("crossGenerationAccessEnabled") @DefaultValue("false") boolean isCrossGenerationAccessEnabled) {
    return new RestResponse<>(
        accountService.updateCrossGenerationAccessEnabled(accountId, isCrossGenerationAccessEnabled, false));
  }

  @POST
  @Path("/createSampleApplication")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> createSampleApplication(@QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Creating sample cloud provider and sample application..");
      sampleDataProviderServiceProvider.get().createK8sV2SampleApp(accountService.get(accountId));
      return new RestResponse<>(Boolean.TRUE);
    }
  }

  @POST
  @Path("new")
  @Timed
  @ExceptionMetered
  public RestResponse<Account> createAccount(@NotNull Account account) {
    RestResponse<Account> response = accountPermissionUtils.checkIfHarnessUser("User not allowed to create account");
    if (response == null) {
      account.setAppId(GLOBAL_APP_ID);
      if (account.getDefaultExperience() == null) {
        account.setDefaultExperience(DefaultExperience.CG);
      }
      response = new RestResponse<>(accountService.save(account, false));
    }
    return response;
  }

  @DELETE
  @Path("delete/{accountId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> deleteAccount(@PathParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Deleting account");
      RestResponse<Boolean> response = accountPermissionUtils.checkIfHarnessUser("User not allowed to delete account");
      if (response == null) {
        response = new RestResponse<>(accountService.delete(accountId));
      }
      return response;
    }
  }

  @POST
  @Path("disable")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> disableAccount(
      @QueryParam("accountId") String accountId, @QueryParam("migratedTo") String migratedToClusterUrl) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Disabling account: {}", accountId);
      RestResponse<Boolean> response = accountPermissionUtils.checkIfHarnessUser("User not allowed to disable account");
      if (response == null) {
        response = new RestResponse<>(accountService.disableAccount(accountId, urlDecode(migratedToClusterUrl)));
      }
      return response;
    }
  }

  @POST
  @Path("enable")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> enableAccount(@QueryParam("accountId") String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Enabling account");
      RestResponse<Boolean> response = accountPermissionUtils.checkIfHarnessUser("User not allowed to enable account");
      if (response == null) {
        response = new RestResponse<>(accountService.enableAccount(accountId));
      }
      return response;
    }
  }

  @DELETE
  @Path("export-delete")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> deleteExportableAccountData(@QueryParam("accountId") @NotEmpty String accountId) {
    RestResponse<Boolean> response = accountPermissionUtils.checkIfHarnessUser("User not allowed to delete account");
    if (response == null) {
      response = new RestResponse<>(accountService.deleteExportableAccountData(accountId));
    }
    return response;
  }

  @GET
  @Path("{accountId}")
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Account> getAccount(@PathParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(accountService.get(accountId));
  }

  @GET
  @Path("{accountId}/details")
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<AccountDetails> getAccountDetails(@PathParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(accountService.getAccountDetails(accountId));
  }

  // Fetches account info from DB & not from local manager cache to avoid inconsistencies in UI when account is updated
  @GET
  @Path("{accountId}/latest")
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Account> getLatestAccount(@PathParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(accountService.get(accountId));
  }

  @GET
  @Path("{accountId}/whitelisted-domains")
  @AuthRule(permissionType = MANAGE_AUTHENTICATION_SETTINGS)
  public RestResponse<Set<String>> getWhitelistedDomains(@PathParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(accountService.getWhitelistedDomains(accountId));
  }

  @PUT
  @Path("{accountId}/whitelisted-domains")
  @AuthRule(permissionType = MANAGE_AUTHENTICATION_SETTINGS)
  public RestResponse<Account> updateWhitelistedDomains(
      @PathParam("accountId") @NotEmpty String accountId, @Body Set<String> whitelistedDomains) {
    return new RestResponse<>(accountService.updateWhitelistedDomains(accountId, whitelistedDomains));
  }

  @POST
  @Path("/gcp")
  @Produces(MediaType.TEXT_HTML)
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @PublicApi
  public Response gcpSignUp(@FormParam(value = "x-gcp-marketplace-token") String token,
      @Context HttpServletRequest request, @Context HttpServletResponse response) {
    return gcpMarketPlaceApiHandler.signUp(token);
  }

  @POST
  @Path("custom-event")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Boolean> postCustomEvent(@QueryParam("accountId") @NotEmpty String accountId,
      @NotNull AccountEvent accountEvent, @QueryParam("oneTimeOnly") @DefaultValue("true") boolean oneTimeOnly,
      @QueryParam("trialOnly") @DefaultValue("true") boolean trialOnly) {
    return new RestResponse<>(accountService.postCustomEvent(accountId, accountEvent, oneTimeOnly, trialOnly));
  }

  @PATCH
  @Path("{accountId}/addSubdomainUrl")
  public RestResponse<Boolean> addSubdomainUrl(
      @PathParam("accountId") @NotEmpty String accountId, @NotNull SubdomainUrl subdomainUrl) {
    String userId = UserThreadLocal.get().getUuid();
    return new RestResponse<>(accountService.addSubdomainUrl(userId, accountId, subdomainUrl));
  }

  @PUT
  @Path("{accountId}/set-service-guard-count")
  @Timed
  @ExceptionMetered
  public RestResponse<String> setServiceGuardAccountLimit(@PathParam("accountId") @NotEmpty String accountId,
      @NotNull @RequestBody ServiceGuardLimitDTO serviceGuardLimitDTO) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Updating account service guard counts");
      RestResponse<String> response =
          accountPermissionUtils.checkIfHarnessUser("User not allowed to generate a new license");
      if (response == null) {
        accountService.setServiceGuardAccount(accountId, serviceGuardLimitDTO);
        response = new RestResponse<>("success");
      }
      return response;
    }
  }

  @POST
  @Path("validate-delegate-token")
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  public RestResponse<Boolean> validateDelegateToken(@QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("delegateToken") @NotNull String delegateToken, @QueryParam("delegateId") String delegateId,
      @QueryParam("delegateTokenName") String delegateTokenName,
      @QueryParam("agentMtlsAuthority") String agentMtlsAuthority) {
    delegateAuthService.validateDelegateToken(accountId, substringAfter(delegateToken, "Delegate "), delegateId,
        delegateTokenName, agentMtlsAuthority, false);
    return new RestResponse<>(true);
  }

  @POST
  @Path("updateAccountPreference")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> updateAccountPreference(@QueryParam("accountId") String accountId,
      @QueryParam("preferenceKey") String preferenceKey, @Body @NotNull Object value) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Attempting to set AccountPreference: {} to Value {}", preferenceKey, value);
      RestResponse<Boolean> response =
          accountPermissionUtils.checkIfHarnessUser("User not allowed to set the cache Value");
      if (response == null) {
        response = new RestResponse<>(accountService.updateAccountPreference(accountId, preferenceKey, value));
      }
      return response;
    }
  }

  @PUT
  @Path("{accountId}/disableRestrictedAccess")
  @AuthRule(permissionType = MANAGE_RESTRICTED_ACCESS)
  public RestResponse<Boolean> enableHarnessUserGroupAccess(@PathParam("accountId") String accountId) {
    return new RestResponse<>(accountService.enableHarnessUserGroupAccess(accountId));
  }

  @PUT
  @Path("{accountId}/enableRestrictedAccess")
  @AuthRule(permissionType = MANAGE_RESTRICTED_ACCESS)
  public RestResponse<Boolean> disableHarnessUserGroupAccess(@PathParam("accountId") String accountId) {
    return new RestResponse<>(accountService.disableHarnessUserGroupAccess(accountId));
  }

  @GET
  @Path("{accountId}/isRestrictedAccessEnabled")
  @AuthRule(permissionType = MANAGE_RESTRICTED_ACCESS)
  public RestResponse<Boolean> isRestrictedAccessEnabled(@PathParam("accountId") String accountId) {
    return new RestResponse<>(accountService.isHarnessSupportAccessDisabled(accountId));
  }

  @GET
  @PublicApi
  @Path("/authentication-info")
  public RestResponse<AuthenticationInfo> getAuthenticationInfo(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(accountService.getAuthenticationInfo(accountId));
  }

  // TODO: EndPoint to be deleted once UI is created for AccessRequest
  @PUT
  @Path("{accountId}/enableHarnessUserGroupAccessWorkflow/{enableAccountId}")
  @ApiKeyAuthorized(permissionType = ACCOUNT_MANAGEMENT)
  public RestResponse<Boolean> enableHarnessUserGroupAccessWorkflow(
      @PathParam("accountId") String accountId, @PathParam("enableAccountId") String enableAccountId) {
    return new RestResponse<>(accountService.enableHarnessUserGroupAccess(enableAccountId));
  }

  // TODO: EndPoint to be deleted once UI is created for AccessRequest
  @PUT
  @Path("{accountId}/disableHarnessUserGroupAccessWorkflow/{disableAccountId}")
  @ApiKeyAuthorized(permissionType = ACCOUNT_MANAGEMENT)
  public RestResponse<Boolean> disableHarnessUserGroupAccessWorkflow(
      @PathParam("accountId") String accountId, @PathParam("disableAccountId") String disableAccountId) {
    return new RestResponse<>(accountService.disableHarnessUserGroupAccess(disableAccountId));
  }

  @POST
  @Path("{accountId}/ng/license")
  public RestResponse<ModuleLicenseDTO> createNgLicense(
      @PathParam("accountId") String accountId, @Body ModuleLicenseDTO moduleLicenseDTO) {
    User existingUser = UserThreadLocal.get();
    if (existingUser == null) {
      throw new InvalidRequestException("Invalid User");
    }

    if (harnessUserGroupService.isHarnessSupportUser(existingUser.getUuid())) {
      return new RestResponse<>(getResponse(adminLicenseHttpClient.createAccountLicense(accountId, moduleLicenseDTO)));
    } else {
      return RestResponse.Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(
              ResponseMessage.builder().message("User not allowed to create module license").build()))
          .build();
    }
  }

  @PUT
  @Path("{accountId}/ng/license")
  public RestResponse<ModuleLicenseDTO> updateNgLicense(
      @PathParam("accountId") String accountId, @Body ModuleLicenseDTO moduleLicenseDTO) {
    User existingUser = UserThreadLocal.get();
    if (existingUser == null) {
      throw new InvalidRequestException("Invalid User");
    }

    if (harnessUserGroupService.isHarnessSupportUser(existingUser.getUuid())) {
      return new RestResponse<>(getResponse(
          adminLicenseHttpClient.updateModuleLicense(moduleLicenseDTO.getId(), accountId, moduleLicenseDTO)));
    } else {
      return RestResponse.Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(
              ResponseMessage.builder().message("User not allowed to update module license").build()))
          .build();
    }
  }

  @DELETE
  @Path("{accountId}/ng/license")
  public RestResponse<Void> deleteNgLicense(
      @PathParam("accountId") String accountId, @QueryParam("identifier") String identifier) {
    User existingUser = UserThreadLocal.get();
    if (existingUser == null) {
      throw new InvalidRequestException("Invalid User");
    }

    if (harnessUserGroupService.isHarnessSupportUser(existingUser.getUuid())) {
      return new RestResponse<>(getResponse(adminLicenseHttpClient.deleteModuleLicense(identifier, accountId)));
    } else {
      return RestResponse.Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(
              ResponseMessage.builder().message("User not allowed to delete module license").build()))
          .build();
    }
  }

  @GET
  @Path("{accountId}/ng/license")
  public RestResponse<AccountLicenseDTO> getNgAccountLicense(@PathParam("accountId") String accountId) {
    User existingUser = UserThreadLocal.get();
    if (existingUser == null) {
      throw new InvalidRequestException("Invalid User");
    }

    if (harnessUserGroupService.isHarnessSupportUser(existingUser.getUuid())) {
      return new RestResponse<>(getResponse(adminLicenseHttpClient.getAccountLicense(accountId)));
    } else {
      return RestResponse.Builder.aRestResponse()
          .withResponseMessages(
              Lists.newArrayList(ResponseMessage.builder().message("User not allowed to query licenses").build()))
          .build();
    }
  }

  @POST
  @Path("{accountId}/smp/licenses/generate")
  @Hidden
  public RestResponse<SMPEncLicenseDTO> generateSMPLicense(
      @PathParam("accountId") String accountId, @Body SMPLicenseRequestDTO licenseRequestDTO) {
    User existingUser = UserThreadLocal.get();
    if (existingUser == null) {
      throw new InvalidRequestException("Invalid User");
    }

    if (harnessUserGroupService.isHarnessSupportUser(existingUser.getUuid())) {
      return new RestResponse<>(getResponse(
          adminLicenseHttpClient.generateSMPLicense(licenseRequestDTO.getCustomerAccountId(), licenseRequestDTO)));
    } else {
      return RestResponse.Builder.aRestResponse()
          .withResponseMessages(
              Lists.newArrayList(ResponseMessage.builder().message("User not allowed to generate smp license").build()))
          .build();
    }
  }

  @POST
  @Path("{accountId}/smp/licenses/validate")
  @Hidden
  public RestResponse<SMPValidationResultDTO> validateSMPLicense(
      @PathParam("accountId") String accountId, @Body SMPEncLicenseDTO licenseDTO) {
    User existingUser = UserThreadLocal.get();
    if (existingUser == null) {
      throw new InvalidRequestException("Invalid User");
    }

    if (harnessUserGroupService.isHarnessSupportUser(existingUser.getUuid())) {
      return new RestResponse<>(getResponse(adminLicenseHttpClient.validateSMPLicense(licenseDTO)));
    } else {
      return RestResponse.Builder.aRestResponse()
          .withResponseMessages(
              Lists.newArrayList(ResponseMessage.builder().message("User not allowed to validate smp license").build()))
          .build();
    }
  }

  @POST
  @Path("{accountId}/is-smp-account")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> updateIsSmpAccount(@PathParam("accountId") String accountId,
      @QueryParam("customerAccountId") @NotNull String customerAccountId,
      @QueryParam("isSmpAccount") @DefaultValue("false") boolean isSmpAccount) {
    User existingUser = UserThreadLocal.get();
    if (existingUser == null) {
      throw new InvalidRequestException("Invalid User");
    }

    if (harnessUserGroupService.isHarnessSupportUser(existingUser.getUuid())) {
      return new RestResponse<>(accountService.updateIsSmpAccount(customerAccountId, isSmpAccount));
    } else {
      return RestResponse.Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(
              ResponseMessage.builder().message("User not allowed to update account smp status").build()))
          .build();
    }
  }

  @GET
  @Path("{accountId}/reset-cache")
  public RestResponse<Boolean> resetCache(@PathParam("accountId") String accountId) {
    User existingUser = UserThreadLocal.get();
    if (existingUser == null) {
      throw new InvalidRequestException("Invalid User");
    }

    if (harnessUserGroupService.isHarnessSupportUser(existingUser.getUuid())) {
      authService.evictUserPermissionAndRestrictionCacheForAccount(accountId, true, true);
      log.info("Reset cache successful for account id {}", accountId);
      return new RestResponse<>(Boolean.TRUE);
    } else {
      return RestResponse.Builder.aRestResponse()
          .withResponseMessages(
              Lists.newArrayList(ResponseMessage.builder().message("User not allowed to reset cache").build()))
          .build();
    }
  }

  @POST
  @Path("{accountId}/schedule-jobs")
  public RestResponse<Boolean> scheduleAccountLevelJobs(@PathParam("accountId") String accountId,
      @NotNull @QueryParam("targetAccountId") String targetAccountId,
      @NotNull @QueryParam("jobTypes") List<AccountJobType> jobTypes,
      @RequestBody @Valid @Parameter(description = "Properties of account jobs") AccountJobProperties jobProperties) {
    User existingUser = UserThreadLocal.get();
    if (existingUser == null) {
      throw new InvalidRequestException("Invalid User");
    }

    if (harnessUserGroupService.isHarnessSupportUser(existingUser.getUuid())) {
      accountService.scheduleAccountLevelJobs(targetAccountId, jobTypes, jobProperties);
      return new RestResponse<>(Boolean.TRUE);
    } else {
      return RestResponse.Builder.aRestResponse()
          .withResponseMessages(
              Lists.newArrayList(ResponseMessage.builder().message("User not allowed to schedule jobs").build()))
          .build();
    }
  }
}
