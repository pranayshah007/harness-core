/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageResponse;
import io.harness.mappers.AccountMapper;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.user.SessionTimeoutSettings;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;

import software.wings.beans.Account;
import software.wings.beans.AccountStatus;
import software.wings.beans.AccountType;
import software.wings.beans.LicenseInfo;
import software.wings.beans.security.UserGroup;
import software.wings.helpers.ext.url.SubdomainUrlHelper;
import software.wings.security.authentication.TwoFactorAuthenticationManager;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.http.Body;

@Api(value = "/ng/accounts", hidden = true)
@Path("/ng/accounts")
@Produces("application/json")
@Consumes("application/json")
@NextGenManagerAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._955_ACCOUNT_MGMT)
public class AccountResourceNG {
  private final AccountService accountService;
  private SubdomainUrlHelper subdomainUrlHelper;
  private TwoFactorAuthenticationManager twoFactorAuthenticationManager;
  private UserGroupService userGroupService;

  @POST
  public RestResponse<AccountDTO> create(@NotNull AccountDTO dto) {
    Account account = AccountMapper.fromAccountDTO(dto);
    account.setCreatedFromNG(true);

    account.setLicenseInfo(LicenseInfo.builder()
                               .accountType(AccountType.TRIAL)
                               .accountStatus(AccountStatus.ACTIVE)
                               .licenseUnits(50)
                               .build());

    return new RestResponse<>(AccountMapper.toAccountDTO(accountService.save(account, false)));
  }

  @GET
  @Path("/list")
  @Deprecated
  public RestResponse<List<AccountDTO>> getAllAccounts() {
    List<AccountDTO> accountList = accountService.getAllAccounts();
    return new RestResponse<>(accountList);
  }

  @GET
  @Path("/listV2")
  public RestResponse<PageResponse<AccountDTO>> listAccounts(
      @QueryParam("offset") int offset, @QueryParam("pageSize") int pageSize) {
    PageResponse<AccountDTO> accountList = accountService.listAccounts(offset, pageSize);
    return new RestResponse<>(accountList);
  }
  @GET
  @Path("{accountId}")
  public RestResponse<AccountDTO> getDTO(@PathParam("accountId") String accountId) {
    Account account = accountService.get(accountId);
    return new RestResponse<>(AccountMapper.toAccountDTO(account));
  }

  @GET
  public RestResponse<List<AccountDTO>> getDTOs(@QueryParam("accountIds") @Size(max = 100) List<String> accountIds) {
    List<Account> accounts = accountService.getAccounts(accountIds);
    return new RestResponse<>(accounts.stream().map(AccountMapper::toAccountDTO).collect(Collectors.toList()));
  }

  @PUT
  @Path("/{accountId}/name")
  public RestResponse<Account> updateAccountName(
      @PathParam("accountId") @NotEmpty String accountId, @Body AccountDTO dto) {
    return new RestResponse<>(accountService.updateAccountName(accountId, dto.getName(), null));
  }

  @GET
  @Path("/feature-flag-enabled")
  public RestResponse<Boolean> isFeatureFlagEnabled(
      @QueryParam("featureName") String featureName, @QueryParam("accountId") String accountId) {
    return new RestResponse<>(accountService.isFeatureFlagEnabled(featureName, accountId));
  }

  @GET
  @Path("immutable-delegate-enabled")
  public RestResponse<Boolean> isImmutableDelegateEnabled(@QueryParam("accountId") @NotNull String accountId) {
    return new RestResponse<>(accountService.isImmutableDelegateEnabled(accountId));
  }

  @GET
  @Path("/feature-flag-enabled-accounts")
  public RestResponse<Set<String>> getFeatureFlagEnabledAccountIds(@QueryParam("featureName") String featureName) {
    return new RestResponse<>(accountService.getFeatureFlagEnabledAccountIds(featureName));
  }

  @GET
  @Path("/{accountId}/nextgen-enabled")
  public RestResponse<Boolean> isNextGenEnabled(@PathParam("accountId") String accountId) {
    return new RestResponse<>(accountService.isNextGenEnabled(accountId));
  }

  @GET
  @Path("/baseUrl")
  public RestResponse<String> getBaseUrl(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(subdomainUrlHelper.getPortalBaseUrl(accountId));
  }

  @GET
  @Path("/vanityUrl")
  public RestResponse<String> getVanityUrl(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(subdomainUrlHelper.getVanityUrl(accountId));
  }

  @GET
  @Path("/gatewayBaseUrl")
  public RestResponse<String> getGatewayBaseUrl(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(subdomainUrlHelper.getGatewayBaseUrl(accountId));
  }

  @GET
  @Path("/account-admins")
  public RestResponse<List<String>> getAccountAdmins(@QueryParam("accountId") String accountId) {
    UserGroup userGroup = userGroupService.getAdminUserGroup(accountId);
    return new RestResponse<>(userGroup != null ? userGroup.getMemberIds() : Collections.emptyList());
  }

  @GET
  @Path("/get-whitelisted-domains")
  public RestResponse<Set<String>> getWhitelistedDomains(@QueryParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(accountService.getWhitelistedDomains(accountId));
  }

  @PUT
  @Path("/whitelisted-domains")
  public RestResponse<Account> updateWhitelistedDomains(
      @QueryParam("accountId") @NotEmpty String accountId, @Body Set<String> whitelistedDomains) {
    return new RestResponse<>(accountService.updateWhitelistedDomains(accountId, whitelistedDomains));
  }

  @GET
  @Path("two-factor-enabled")
  public RestResponse<Boolean> getTwoFactorAuthAdminEnforceInfo(@QueryParam("accountId") @NotEmpty String accountId) {
    return new RestResponse(twoFactorAuthenticationManager.getTwoFactorAuthAdminEnforceInfo(accountId));
  }

  @GET
  @Path("session-timeout-account-level")
  public RestResponse<Integer> getSessionTimeoutAtAccountLevel(@QueryParam("accountId") @NotEmpty String accountId) {
    return new RestResponse(accountService.getSessionTimeoutInMinutes(accountId));
  }

  @PUT
  @Path("session-timeout-account-level")
  public RestResponse<Boolean> setSessionTimeoutAtAccountLevel(@QueryParam("accountId") @NotEmpty String accountId,
      @Valid @NotNull SessionTimeoutSettings sessionTimeoutSettings) {
    accountService.setSessionTimeoutInMinutes(accountId, sessionTimeoutSettings);
    return new RestResponse(Boolean.TRUE);
  }

  @GET
  @Hidden
  @Path("is-auto-invite-acceptance-enabled")
  @InternalApi
  public RestResponse<Boolean> isAutoInviteAcceptanceEnabled(@QueryParam("accountId") @NotEmpty String accountId) {
    return new RestResponse(accountService.isAutoInviteAcceptanceEnabled(accountId));
  }

  @GET
  @Hidden
  @Path("is-pl-no-email-invite-acceptance-enabled")
  @InternalApi
  public RestResponse<Boolean> isPLNoEmailForSamlAccountInvitesEnabled(
      @QueryParam("accountId") @NotEmpty String accountId) {
    return new RestResponse(accountService.isPLNoEmailForSamlAccountInvitesEnabled(accountId));
  }

  @GET
  @Path("is-sso-enabled")
  public RestResponse<Boolean> isSSOEnabled(@QueryParam("accountId") @NotEmpty String accountId) {
    Account account = accountService.get(accountId);
    return new RestResponse(accountService.isSSOEnabled(account));
  }

  @Path("/exists/{accountName}")
  public RestResponse<Boolean> doesAccountExist(@PathParam("accountName") String accountName) {
    return new RestResponse<>(accountService.exists(accountName));
  }

  @PUT
  @Path("/{accountId}/default-experience-if-applicable")
  public RestResponse<Boolean> updateDefaultExperienceIfApplicable(
      @PathParam("accountId") @AccountIdentifier String accountId,
      @QueryParam("defaultExperience") DefaultExperience defaultExperience) {
    Account account = accountService.get(accountId);
    if (canUpdateDefaultExperience(defaultExperience, account)) {
      account.setDefaultExperience(defaultExperience);
      accountService.update(account);
      log.info("Updated default experience to {} for Account {}", defaultExperience, accountId);
    }
    return new RestResponse(true);
  }

  private boolean canUpdateDefaultExperience(DefaultExperience defaultExperience, Account account) {
    // due to CD trial started by default, new NG user with CD trial can be updated
    return DefaultExperience.isNGExperience(defaultExperience) // accept changing defaultExperience to NG only
        && account.getDefaultExperience() == null // don't overwrite current defaultExperience
        && account.isCreatedFromNG() // new NG account only
        && account.getCeLicenseInfo() == null // Verify account doesn't work on CG
        && (account.getLicenseInfo() == null || AccountType.TRIAL.equals(account.getLicenseInfo().getAccountType()));
  }

  /**
   * This is only intended for an NG user to switch their account experience
   * Please use updateDefaultExperienceIfApplicable for all internal calls / side effects
   * @param accountId
   * @param dto
   * @return
   */
  @PUT
  @Path("/{accountId}/default-experience")
  public RestResponse<AccountDTO> updateDefaultExperience(
      @PathParam("accountId") @AccountIdentifier String accountId, @Body AccountDTO dto) {
    return new RestResponse(
        AccountMapper.toAccountDTO(accountService.updateDefaultExperience(accountId, dto.getDefaultExperience())));
  }

  @PUT
  @Hidden
  @Path("/{accountId}/cross-generation-access")
  @InternalApi
  public RestResponse<AccountDTO> updateCrossGenerationAccessEnabled(
      @PathParam("accountId") @AccountIdentifier String accountId, @Body AccountDTO dto) {
    return new RestResponse(AccountMapper.toAccountDTO(
        accountService.updateCrossGenerationAccessEnabled(accountId, dto.isCrossGenerationAccessEnabled(), true)));
  }

  @GET
  @Path("/trustLevel")
  public RestResponse<Integer> getAccountTrustLevel(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(accountService.getTrustLevel(accountId));
  }

  @GET
  @Path("/update-trust-level")
  public RestResponse<Boolean> updateAccountTrustLevel(
      @QueryParam("accountId") String accountId, @QueryParam("trustLevel") Integer trustLevel) {
    return new RestResponse<>(accountService.updateTrustLevel(accountId, trustLevel));
  }
}
