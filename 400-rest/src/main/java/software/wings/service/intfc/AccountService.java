/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessModule._945_ACCOUNT_MGMT;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.authenticationservice.beans.AuthenticationInfo;
import io.harness.beans.FeatureFlag;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.cvng.beans.ServiceGuardLimitDTO;
import io.harness.datahandler.models.AccountDetails;
import io.harness.dataretention.AccountDataRetentionEntity;
import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.managerclient.HttpsCertRequirement.CertRequirement;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.user.SessionTimeoutSettings;
import io.harness.validation.Create;
import io.harness.validation.Update;

import software.wings.beans.Account;
import software.wings.beans.AccountEvent;
import software.wings.beans.Service;
import software.wings.beans.SubdomainUrl;
import software.wings.beans.TechStack;
import software.wings.beans.User;
import software.wings.scheduler.AccountJobProperties;
import software.wings.scheduler.AccountJobType;
import software.wings.security.authentication.AccountSettingsResponse;
import software.wings.service.impl.analysis.CVEnabledService;

import dev.morphia.query.Query;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotBlank;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

/**
 * Created by peeyushaggarwal on 10/11/16.
 */
@OwnedBy(PL)
@TargetModule(_945_ACCOUNT_MGMT)
public interface AccountService {
  @ValidationGroups(Create.class) Account save(@Valid Account account, boolean fromDataGen);

  @ValidationGroups(Create.class)
  Account save(@Valid Account account, boolean fromDataGen, boolean shouldCreateSampleApp);

  @ValidationGroups(Update.class) Account update(@Valid Account account);

  Account getByName(String companyName);

  Account get(String accountId);

  boolean isNextGenEnabled(String accountId);

  Boolean updateNextGenEnabled(String accountId, boolean enabled);

  Boolean updateIsProductLed(String accountId, boolean isProductLed);

  AccountDetails getAccountDetails(String accountId);

  List<Account> getAccounts(List<String> identifiers);

  Account getFromCache(String accountId);

  String getAccountStatus(String accountId);

  Account getFromCacheWithFallback(String accountId);

  boolean delete(String accountId);

  void handleNonExistentAccount(String accountId);

  boolean deleteExportableAccountData(String accountId);

  boolean getTwoFactorEnforceInfo(String accountId);

  void updateTwoFactorEnforceInfo(String accountId, boolean enabled);

  Integer getSessionTimeoutInMinutes(String accountId);

  void setSessionTimeoutInMinutes(String accountId, @NotNull @Valid SessionTimeoutSettings sessionTimeoutSettings);

  String suggestAccountName(@NotNull String accountName);

  boolean updateTechStacks(String accountId, Set<TechStack> techStacks);

  void updateAccountEvents(String accountId, AccountEvent accountEvent);

  boolean exists(String accountName);

  Optional<String> getAccountType(String accountId);

  boolean isPaidAccount(String accountId);

  /**
   * List.
   *
   * @param request the request
   * @return the list of System Catalogs
   */
  List<Account> list(@NotNull PageRequest<Account> request);

  List<Account> listHarnessSupportAccounts(Set<String> excludedAccountIds, Set<String> fieldsToBeIncluded);

  DelegateConfiguration getDelegateConfiguration(String accountId);

  String getWatcherVersion(String accountId);

  String getAccountPrimaryDelegateVersion(String accountId);

  List<Account> listAllAccountsWithoutTheGlobalAccount();

  long countAccountsWithoutTheGlobalAccount();

  List<Account> listAllActiveAccounts();

  List<Account> getAccountsWithBasicInfo(boolean includeLicenseInfo);

  Query<Account> getBasicAccountQuery();

  Query<Account> getBasicAccountWithLicenseInfoQuery();

  PageResponse<Account> getAccounts(PageRequest<Account> pageRequest);

  Account getByAccountName(String accountName);

  Account getAccountWithDefaults(String accountId);

  /**
   * List all feaature flags and their statuses
   *
   * @param accountId account id
   * @return list of feature flags
   */
  Collection<FeatureFlag> getFeatureFlags(@NotBlank String accountId);

  boolean setAuthenticationMechanism(String accountId, AuthenticationMechanism authenticationMechanism);

  boolean isFeatureFlagEnabled(String featureName, String accountId);

  Set<String> getFeatureFlagEnabledAccountIds(String featureName);

  PageResponse<CVEnabledService> getServices(
      String accountId, User user, PageRequest<String> request, String serviceId);

  List<Service> getServicesBreadCrumb(String accountId, User user);

  /**
   * Start account migration from one cluster to another. Once this step completed, the logged in user can only read,
   * but not update the account configurations.
   */
  boolean enableAccount(String accountId);

  /**
   *  Schedule account level jobs for target account
   *
   * @param targetAccountId target account id
   * @param jobTypes job types
   * @param jobProperties job properties
   */
  void scheduleAccountLevelJobs(
      String targetAccountId, List<AccountJobType> jobTypes, AccountJobProperties jobProperties);

  /**
   * Once the account migration completed. All existing delegates belonging to this account will be redirected to the
   * new cluster that the account has been migrated into.
   */
  boolean disableAccount(String accountId, String migratedToClusterUrl);

  /**
   * If an account is inactivated and a 'migratedToClusterUrl' field is set, the the account has been migrated.
   */
  boolean isAccountMigrated(String accountId);

  boolean isCommunityAccount(String accountId);

  Set<String> getWhitelistedDomains(String accountId);

  Account updateWhitelistedDomains(String accountId, Set<String> whitelistedDomains);

  Account updateAccountName(String accountId, String accountName, String companyName);

  boolean updateAccountName(String accountId, String accountName);

  boolean updateCompanyName(String accountId, String companyName);

  boolean updateAccountActivelyUsed(String accountId, boolean accountActivelyUsed);

  boolean updateRingName(String accountId, String ringName);

  Boolean updateCloudCostEnabled(String accountId, boolean cloudCostEnabled);

  boolean updateCeAutoCollectK8sEvents(String accountId, boolean ceK8sEventCollectionEnabled);

  AccountSettingsResponse getAuthSettingsByAccountId(String accountId);

  Optional<Account> getOnPremAccount();

  boolean postCustomEvent(String accountId, AccountEvent accountEvent, boolean oneTimeOnly, boolean trialOnly);

  boolean isSSOEnabled(Account account);

  /**
   * Set subdomainUrl of Account
   */
  void setSubdomainUrl(Account account, SubdomainUrl subdomainUrl);

  boolean isCertValidationRequired(String accountId);

  /**
   * Add subDomainUrl to the account
   */
  Boolean addSubdomainUrl(String userId, String accountId, SubdomainUrl subdomainUrl);

  /**
   * Checks whether subdomain URL is taken by another account
   */
  boolean checkDuplicateSubdomainUrl(SubdomainUrl subdomainUrl);

  void validateAccount(Account account);

  boolean updatePovFlag(String accountId, boolean isPov);

  boolean updateAccountStatus(String accountId, String accountStatus);

  Optional<String> getCeAccountType(String accountId);

  Set<String> getAccountsWithDisabledHarnessUserGroupAccess();

  void setServiceGuardAccount(String accountId, ServiceGuardLimitDTO serviceGuardLimitDTO);

  void updateBackgroundJobsDisabled(String accountId, boolean isDisabled);

  CertRequirement getHttpsCertificateRequirement(String accountId);

  Map<String, Long> obtainAccountDataRetentionMap();

  void ensureDataRetention(List<Class<? extends AccountDataRetentionEntity>> entityClasses);

  boolean updateAccountPreference(String accountId, String preferenceKey, Object value);

  void updateFeatureFlagsForOnPremAccount();

  boolean enableHarnessUserGroupAccess(String accountId);

  boolean disableHarnessUserGroupAccess(String accountId);

  boolean isHarnessSupportAccessDisabled(String accountId);

  boolean isAutoInviteAcceptanceEnabled(String accountId);

  boolean isPLNoEmailForSamlAccountInvitesEnabled(String accountId);

  Void setDefaultExperience(String accountId, DefaultExperience defaultExperience);

  AuthenticationInfo getAuthenticationInfo(String accountId);

  boolean isAccountActivelyUsed(String accountId);

  boolean isImmutableDelegateEnabled(String accountId);

  boolean doMultipleAccountsExist();

  List<AccountDTO> getAllAccounts();

  PageResponse<AccountDTO> listAccounts(int offset, int pageSize);

  Integer getTrustLevel(String accountId);

  boolean updateTrustLevel(String accountId, Integer trustLevel);

  Boolean updateIsSmpAccount(String customerAccountId, boolean isSmpAccount);

  Account updateDefaultExperience(String accountIdentifier, DefaultExperience defaultExperience);

  Account updateCrossGenerationAccessEnabled(
      String accountIdentifier, boolean isCrossGenerationAccessEnabled, boolean isNextGen);
}