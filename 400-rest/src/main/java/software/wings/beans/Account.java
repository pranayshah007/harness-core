/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.delegate.beans.DelegateConfiguration.DelegateConfigurationKeys;

import static software.wings.beans.Account.AccountKeys;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.common.VerificationConstants.SERVICE_GUAARD_LIMIT;
import static software.wings.ngmigration.NGMigrationEntityType.ACCOUNT;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.ccm.license.CeLicenseInfo;
import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.encryption.Encrypted;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.account.AccountTrustLevel;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.ng.core.account.ServiceAccountConfig;
import io.harness.security.EncryptionInterface;
import io.harness.security.SimpleEncryption;
import io.harness.validation.Create;

import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.yaml.BaseEntityYaml;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;

@OwnedBy(DX)
@TargetModule(HarnessModule._955_ACCOUNT_MGMT)
@FieldNameConstants(innerTypeName = "AccountKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "accounts", noClassnameStored = true)
@HarnessEntity(exportable = true)
@ChangeDataCapture(table = "accounts", fields = {AccountKeys.accountName, AccountKeys.createdAt}, handler = "")
public class Account extends Base implements PersistentRegularIterable, NGMigrationEntity {
  public static final int DEFAULT_SESSION_TIMEOUT_IN_MINUTES = 1440;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("next_iteration_license_info2")
                 .field(AccountKeys.licenseExpiryCheckIteration)
                 .field(AccountKeys.encryptedLicenseInfo)
                 .build())
        .build();
  }

  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";

  @NotNull private String companyName;

  @Getter(value = AccessLevel.PRIVATE) @Setter private Boolean nextGenEnabled = Boolean.FALSE;

  @FdUniqueIndex @NotNull private String accountName;

  private Set<String> whitelistedDomains = new HashSet<>();

  @JsonIgnore @NotNull(groups = Create.class) @Encrypted(fieldName = "account_key") private String accountKey;

  private String licenseId;

  @Getter @Setter private long dataRetentionDurationMs;

  private List<String> salesContacts;

  @Getter @Setter private LicenseInfo licenseInfo;

  @Getter @Setter private int trustLevel = AccountTrustLevel.UNINITIALIZED;

  @Getter @Setter private CeLicenseInfo ceLicenseInfo;

  private Set<AccountEvent> accountEvents;

  @Getter @Setter private String subdomainUrl;

  @JsonIgnore private byte[] encryptedLicenseInfo;

  @JsonIgnore private boolean emailSentToSales;

  @Getter(onMethod = @__({ @JsonIgnore })) @Setter @JsonIgnore private long lastLicenseExpiryReminderSentAt;

  @Getter(onMethod = @__({ @JsonIgnore })) @Setter @JsonIgnore private List<Long> licenseExpiryRemindersSentAt;

  @JsonIgnore private transient EncryptionInterface encryption;
  private boolean twoFactorAdminEnforced;

  // Set this flag when creating an empty account for data import.
  // It's a transient field and won't be persisted.
  @Transient private boolean forImport;

  @Getter @Setter private String migratedToClusterUrl;

  @Getter @Setter DefaultExperience defaultExperience;

  @Accessors(fluent = true) @Getter @Setter Boolean isCrossGenerationAccessEnabled = Boolean.FALSE;

  @Getter @Setter boolean createdFromNG;

  @Getter @Setter private boolean accountActivelyUsed;

  @Getter @Setter boolean isProductLed;

  @Getter @Setter private boolean smpAccount;
  @Getter @Setter private Integer sessionTimeOutInMinutes = DEFAULT_SESSION_TIMEOUT_IN_MINUTES;
  @Getter @Setter private boolean publicAccessEnabled;

  /**
   * If this flag is set, all encryption/decryption activities will go through LOCAL security manager.
   * No VAULT/KMS secret manager can be configured. This helps for accounts whose delegate can't access
   * internet KMS. This setting is typically needed for some on-prem installations.
   */
  private boolean localEncryptionEnabled;

  private DelegateConfiguration delegateConfiguration;

  private Set<TechStack> techStacks;

  private boolean oauthEnabled;
  private String ringName;
  @Getter @Setter @JsonIgnore private boolean backgroundJobsDisabled;

  @FdIndex @Getter @Setter private boolean isHarnessSupportAccessAllowed = true;

  @Getter @Setter private AccountPreferences accountPreferences;

  private Long serviceGuardDataCollectionIteration;
  private Long serviceGuardDataAnalysisIteration;
  @FdIndex private Long workflowDataCollectionIteration;
  @FdIndex private Long usageMetricsTaskIteration;
  private Long licenseExpiryCheckIteration;
  @FdIndex private Long accountBackgroundJobCheckIteration;
  @FdIndex private Long accountDeletionIteration;
  @FdIndex private Long gitSyncExpiryCheckIteration;
  @FdIndex private Long secretManagerValidationIterator;
  @FdIndex private Long ceLicenseExpiryIteration;
  @FdIndex private Long resourceLookupSyncIteration;
  @FdIndex private long delegateTelemetryPublisherIteration;
  @FdIndex private long delegateTaskRebroadcastIteration;
  @FdIndex private Long perpetualTaskRebalanceIteration;

  // adding this to avoid kryo exception. Its not used anymore, check DEL-5047
  @Deprecated private long delegateTaskFailIteration;

  @Getter private boolean cloudCostEnabled;
  @Getter @Setter private boolean ceAutoCollectK8sEvents;
  @Getter @Setter private TrialSignupOptions trialSignupOptions;

  @Getter @Setter private Long serviceGuardLimit = SERVICE_GUAARD_LIMIT;

  @Getter @Setter ServiceAccountConfig serviceAccountConfig;

  @FdIndex @Getter @Setter boolean globalDelegateAccount;

  @Getter @Setter private boolean immutableDelegateEnabled = true;

  @Getter private Long optionalDelegateTaskLimit;

  @Getter private Long importantDelegateTaskLimit;

  private transient Map<String, String> defaults = new HashMap<>();
  /**
   * Default mechanism is USER_PASSWORD
   */
  private AuthenticationMechanism authenticationMechanism = AuthenticationMechanism.USER_PASSWORD;

  public Map<String, String> getDefaults() {
    return defaults;
  }

  public void setDefaults(Map<String, String> defaults) {
    this.defaults = defaults;
  }

  public boolean isTwoFactorAdminEnforced() {
    return twoFactorAdminEnforced;
  }

  public void setTwoFactorAdminEnforced(boolean twoFactorAdminEnforced) {
    this.twoFactorAdminEnforced = twoFactorAdminEnforced;
  }

  public boolean isForImport() {
    return forImport;
  }

  public void setForImport(boolean forImport) {
    this.forImport = forImport;
  }

  public boolean isLocalEncryptionEnabled() {
    return localEncryptionEnabled;
  }

  @Getter @Setter private boolean isPovAccount;

  public void setLocalEncryptionEnabled(boolean localEncryptionEnabled) {
    this.localEncryptionEnabled = localEncryptionEnabled;
  }

  @JsonIgnore
  @Override
  public String getMigrationEntityName() {
    return getCompanyName() + " " + getAccountName();
  }

  @JsonIgnore
  @Override
  public CgBasicInfo getCgBasicInfo() {
    return CgBasicInfo.builder()
        .id(getUuid())
        .name(getAccountName())
        .type(ACCOUNT)
        .appId(getAppId())
        .accountId(getUuid())
        .build();
  }

  public boolean isNextGenEnabled() {
    return Boolean.TRUE.equals(nextGenEnabled);
  }

  /**
   * Getter for property 'companyName'.
   *
   * @return Value for property 'companyName'.
   */
  public String getCompanyName() {
    return companyName;
  }

  /**
   * Setter for property 'companyName'.
   *
   * @param companyName Value to set for property 'companyName'.
   */
  public void setCompanyName(String companyName) {
    this.companyName = companyName;
  }

  /**
   * Getter for property 'accountKey'.
   *
   * @return Value for property 'accountKey'.
   */
  public String getAccountKey() {
    return accountKey;
  }

  /**
   * Setter for property 'accountKey'.
   *
   * @param accountKey Value to set for property 'accountKey'.
   */
  public void setAccountKey(String accountKey) {
    this.accountKey = accountKey;
  }

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  /**
   * Getter for property 'licenseId'.
   *
   * @return Value for property 'licenseId'.
   */
  public String getLicenseId() {
    return licenseId;
  }

  /**
   * Setter for property 'licenseId'.
   *
   * @param licenseId Value to set for property 'licenseId'.
   */
  public void setLicenseId(String licenseId) {
    this.licenseId = licenseId;
  }

  @JsonIgnore
  public byte[] getEncryptedLicenseInfo() {
    if (encryptedLicenseInfo != null) {
      return Arrays.copyOf(encryptedLicenseInfo, encryptedLicenseInfo.length);
    }

    return null;
  }

  @JsonIgnore
  public void setEncryptedLicenseInfo(byte[] encryptedLicenseInfo) {
    if (encryptedLicenseInfo != null) {
      this.encryptedLicenseInfo = Arrays.copyOf(encryptedLicenseInfo, encryptedLicenseInfo.length);
    }
  }

  public EncryptionInterface getEncryption() {
    return encryption;
  }

  public void setEncryption(EncryptionInterface encryption) {
    this.encryption = encryption;
  }

  public AuthenticationMechanism getAuthenticationMechanism() {
    return authenticationMechanism;
  }

  public void setAuthenticationMechanism(AuthenticationMechanism authenticationMechanism) {
    this.authenticationMechanism = authenticationMechanism;
  }

  @JsonIgnore
  public boolean isCommunity() {
    return licenseInfo != null && AccountType.isCommunity(licenseInfo.getAccountType());
  }

  public DelegateConfiguration getDelegateConfiguration() {
    return delegateConfiguration;
  }

  public void setDelegateConfiguration(DelegateConfiguration delegateConfiguration) {
    this.delegateConfiguration = delegateConfiguration;
  }

  @JsonIgnore
  public List<String> getSalesContacts() {
    return salesContacts;
  }

  @JsonIgnore
  public void setSalesContacts(List<String> salesContacts) {
    this.salesContacts = salesContacts;
  }

  @JsonIgnore
  public boolean isEmailSentToSales() {
    return emailSentToSales;
  }

  @JsonIgnore
  public void setEmailSentToSales(boolean emailSentToSales) {
    this.emailSentToSales = emailSentToSales;
  }

  public Set<String> getWhitelistedDomains() {
    return whitelistedDomains;
  }

  public void setWhitelistedDomains(Set<String> whitelistedDomains) {
    this.whitelistedDomains = whitelistedDomains;
  }

  public Set<TechStack> getTechStacks() {
    return techStacks;
  }

  public void setTechStacks(Set<TechStack> techStacks) {
    this.techStacks = techStacks;
  }
  public String getRingName() {
    return ringName;
  }

  public void setRingName(String ringName) {
    this.ringName = ringName;
  }

  public boolean isOauthEnabled() {
    return this.oauthEnabled;
  }

  public void setOauthEnabled(boolean oauthEnabled) {
    this.oauthEnabled = oauthEnabled;
  }

  public void setCloudCostEnabled(boolean cloudCostEnabled) {
    this.cloudCostEnabled = cloudCostEnabled;
  }

  public Set<AccountEvent> getAccountEvents() {
    return accountEvents;
  }

  public void setEvents(Set<AccountEvent> events) {
    this.accountEvents = events;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    Account account = (Account) o;

    return accountName != null ? accountName.equals(account.accountName) : account.accountName == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (accountName != null ? accountName.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Account{"
        + "companyName='" + companyName + '\'' + ", accountName='" + accountName + '\'' + '}';
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (AccountKeys.workflowDataCollectionIteration.equals(fieldName)) {
      this.workflowDataCollectionIteration = nextIteration;
      return;
    }

    else if (AccountKeys.usageMetricsTaskIteration.equals(fieldName)) {
      this.usageMetricsTaskIteration = nextIteration;
      return;
    }

    else if (AccountKeys.licenseExpiryCheckIteration.equals(fieldName)) {
      this.licenseExpiryCheckIteration = nextIteration;
      return;
    }

    else if (AccountKeys.accountBackgroundJobCheckIteration.equals(fieldName)) {
      this.accountBackgroundJobCheckIteration = nextIteration;
      return;
    }

    else if (AccountKeys.accountDeletionIteration.equals(fieldName)) {
      this.accountDeletionIteration = nextIteration;
      return;
    }

    else if (AccountKeys.gitSyncExpiryCheckIteration.equals(fieldName)) {
      this.gitSyncExpiryCheckIteration = nextIteration;
      return;
    }

    else if (AccountKeys.ceLicenseExpiryIteration.equals(fieldName)) {
      this.ceLicenseExpiryIteration = nextIteration;
      return;
    }

    else if (AccountKeys.resourceLookupSyncIteration.equals(fieldName)) {
      this.resourceLookupSyncIteration = nextIteration;
      return;
    }

    else if (AccountKeys.delegateTelemetryPublisherIteration.equals(fieldName)) {
      this.delegateTelemetryPublisherIteration = nextIteration;
      return;
    }

    else if (AccountKeys.delegateTaskRebroadcastIteration.equals(fieldName)) {
      this.delegateTaskRebroadcastIteration = nextIteration;
      return;
    }

    else if (AccountKeys.perpetualTaskRebalanceIteration.equals(fieldName)) {
      this.perpetualTaskRebalanceIteration = nextIteration;
      return;
    }

    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (AccountKeys.workflowDataCollectionIteration.equals(fieldName)) {
      return this.workflowDataCollectionIteration;
    }

    else if (AccountKeys.usageMetricsTaskIteration.equals(fieldName)) {
      return this.usageMetricsTaskIteration;
    }

    else if (AccountKeys.licenseExpiryCheckIteration.equals(fieldName)) {
      return this.licenseExpiryCheckIteration;
    }

    else if (AccountKeys.accountBackgroundJobCheckIteration.equals(fieldName)) {
      return this.accountBackgroundJobCheckIteration;
    }

    else if (AccountKeys.accountDeletionIteration.equals(fieldName)) {
      return this.accountDeletionIteration;
    }

    else if (AccountKeys.gitSyncExpiryCheckIteration.equals(fieldName)) {
      return this.gitSyncExpiryCheckIteration;
    }

    else if (AccountKeys.ceLicenseExpiryIteration.equals(fieldName)) {
      return this.ceLicenseExpiryIteration;
    }

    else if (AccountKeys.resourceLookupSyncIteration.equals(fieldName)) {
      return this.resourceLookupSyncIteration;
    }

    else if (AccountKeys.delegateTelemetryPublisherIteration.equals(fieldName)) {
      return this.delegateTelemetryPublisherIteration;
    }

    else if (AccountKeys.delegateTaskRebroadcastIteration.equals(fieldName)) {
      return this.delegateTaskRebroadcastIteration;
    }

    else if (AccountKeys.perpetualTaskRebalanceIteration.equals(fieldName)) {
      return this.perpetualTaskRebalanceIteration;
    }

    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  public long getNumberOfDaysSinceExpiry(long currentTime) {
    return TimeUnit.MILLISECONDS.toDays(currentTime - getLicenseInfo().getExpiryTime());
  }

  public static final class Builder {
    private String companyName;
    private String accountName;
    private String accountKey;
    private String uuid;
    private String appId = GLOBAL_APP_ID;
    private EncryptionInterface encryption = new SimpleEncryption();
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private AuthenticationMechanism authenticationMechanism;
    private DelegateConfiguration delegateConfiguration;
    private Map<String, String> defaults = new HashMap<>();
    private LicenseInfo licenseInfo;
    private CeLicenseInfo ceLicenseInfo;
    private boolean emailSentToSales;
    private Set<String> whitelistedDomains = new HashSet<>();
    private long lastLicenseExpiryReminderSentAt;
    private List<Long> licenseExpiryRemindersSentAt;
    private boolean oauthEnabled;
    private Boolean nextGenEnabled;
    private boolean cloudCostEnabled;
    private boolean ceK8sEventCollectionEnabled;
    private String subdomainUrl;
    private String ringName;
    private boolean backgroundJobsDisabled;
    private boolean isHarnessSupportAccessAllowed = true;

    private boolean immutableDelegateEnabled = true;
    private AccountPreferences accountPreferences;
    private DefaultExperience defaultExperience;
    private Boolean isCrossGenerationAccessEnabled = Boolean.FALSE;
    private boolean createdFromNG;
    private boolean isProductLed;
    private boolean accountActivelyUsed;
    private ServiceAccountConfig serviceAccountConfig;
    private boolean globalDelegateAccount;
    private int trustLevel = AccountTrustLevel.UNINITIALIZED;
    private boolean publicAccessEnabled;

    private Builder() {}

    public static Builder anAccount() {
      return new Builder();
    }

    public Builder withCompanyName(String companyName) {
      this.companyName = companyName;
      return this;
    }

    public Builder withNextGenEnabled(boolean enabled) {
      this.nextGenEnabled = enabled;
      return this;
    }

    public Builder withAccountName(String accountName) {
      this.accountName = accountName;
      return this;
    }

    public Builder withDefaultExperience(DefaultExperience defaultExperience) {
      this.defaultExperience = defaultExperience;
      return this;
    }

    public Builder withIsCrossGenerationAccessEnabled(Boolean isCrossGenerationAccessEnabled) {
      this.isCrossGenerationAccessEnabled = isCrossGenerationAccessEnabled;
      return this;
    }

    public Builder withCreatedFromNG(boolean createdFromNG) {
      this.createdFromNG = createdFromNG;
      return this;
    }

    public Builder withIsProductLed(boolean isProductLed) {
      this.isProductLed = isProductLed;
      return this;
    }

    public Builder withAccountActivelyUsed(boolean accountActivelyUsed) {
      this.accountActivelyUsed = accountActivelyUsed;
      return this;
    }

    public Builder withWhitelistedDomains(Set<String> whitelistedDomains) {
      this.whitelistedDomains = whitelistedDomains;
      return this;
    }

    public Builder withAccountKey(String accountKey) {
      this.accountKey = accountKey;
      return this;
    }

    public Builder withUuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    public Builder withAppId(String appId) {
      this.appId = appId;
      return this;
    }

    public Builder withEncryption(EncryptionInterface encryption) {
      this.encryption = encryption;
      return this;
    }

    public Builder withCreatedBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder withCreatedAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder withLastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    public Builder withLastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    public Builder withAuthenticationMechanism(AuthenticationMechanism mechanism) {
      this.authenticationMechanism = mechanism;
      return this;
    }

    public Builder withDelegateConfiguration(DelegateConfiguration delegateConfiguration) {
      this.delegateConfiguration = delegateConfiguration;
      return this;
    }

    public Builder withDefaults(Map<String, String> defaults) {
      this.defaults = defaults;
      return this;
    }

    public Builder withLicenseInfo(LicenseInfo licenseInfo) {
      this.licenseInfo = licenseInfo;
      return this;
    }

    public Builder withCeLicenseInfo(CeLicenseInfo ceLicenseInfo) {
      this.ceLicenseInfo = ceLicenseInfo;
      return this;
    }

    public Builder withRingName(String ringName) {
      this.ringName = ringName;
      return this;
    }

    public Builder withEmailSentToSales(boolean emailSentToSales) {
      this.emailSentToSales = emailSentToSales;
      return this;
    }

    public Builder withLastLicenseExpiryReminderSentAt(long lastLicenseExpiryReminderSentAt) {
      this.lastLicenseExpiryReminderSentAt = lastLicenseExpiryReminderSentAt;
      return this;
    }

    public Builder withLicenseExpiryRemindersSentAt(List<Long> licenseExpiryRemindersSentAt) {
      this.licenseExpiryRemindersSentAt = licenseExpiryRemindersSentAt;
      return this;
    }

    public Builder withOauthEnabled(boolean oauthEnabled) {
      this.oauthEnabled = oauthEnabled;
      return this;
    }

    public Builder withCloudCostEnabled(boolean cloudCostEnabled) {
      this.cloudCostEnabled = cloudCostEnabled;
      return this;
    }

    public Builder withCeK8sEventCollectionEnabled(boolean ceK8sEventCollectionEnabled) {
      this.ceK8sEventCollectionEnabled = ceK8sEventCollectionEnabled;
      return this;
    }

    public Builder withSubdomainUrl(String subdomainUrl) {
      this.subdomainUrl = subdomainUrl;
      return this;
    }

    public Builder withHarnessGroupAccessAllowed(boolean isAllowed) {
      this.isHarnessSupportAccessAllowed = isAllowed;
      return this;
    }

    public Builder withBackgroundJobsDisabled(boolean isDisabled) {
      this.backgroundJobsDisabled = isDisabled;
      return this;
    }

    public Builder withAccountPreferences(AccountPreferences accountPreferences) {
      this.accountPreferences = accountPreferences;
      return this;
    }

    public Builder withServiceAccountConfig(ServiceAccountConfig serviceAccountConfig) {
      this.serviceAccountConfig = serviceAccountConfig;
      return this;
    }

    public Builder withGlobalDelegateAccount(boolean globalDelegateAccount) {
      this.globalDelegateAccount = globalDelegateAccount;
      return this;
    }

    public Builder withTrustLevel(int trustLevel) {
      this.trustLevel = trustLevel;
      return this;
    }

    public Builder withPublicAccessEnabled(boolean publicAccessEnabled) {
      this.publicAccessEnabled = publicAccessEnabled;
      return this;
    }

    public Builder but() {
      return anAccount()
          .withCompanyName(companyName)
          .withAccountKey(accountKey)
          .withUuid(uuid)
          .withAppId(appId)
          .withEncryption(encryption)
          .withCreatedBy(createdBy)
          .withAccountName(accountName)
          .withCreatedAt(createdAt)
          .withLastUpdatedBy(lastUpdatedBy)
          .withLastUpdatedAt(lastUpdatedAt)
          .withAuthenticationMechanism(authenticationMechanism)
          .withDelegateConfiguration(delegateConfiguration)
          .withDefaults(defaults)
          .withLicenseInfo(licenseInfo)
          .withCeLicenseInfo(ceLicenseInfo)
          .withEmailSentToSales(emailSentToSales)
          .withWhitelistedDomains(whitelistedDomains)
          .withLastLicenseExpiryReminderSentAt(lastLicenseExpiryReminderSentAt)
          .withLicenseExpiryRemindersSentAt(licenseExpiryRemindersSentAt)
          .withOauthEnabled(oauthEnabled)
          .withSubdomainUrl(subdomainUrl)
          .withRingName(ringName)
          .withBackgroundJobsDisabled(backgroundJobsDisabled)
          .withDefaultExperience(defaultExperience)
          .withIsCrossGenerationAccessEnabled(isCrossGenerationAccessEnabled)
          .withCreatedFromNG(createdFromNG)
          .withIsProductLed(isProductLed)
          .withAccountActivelyUsed(accountActivelyUsed)
          .withAccountPreferences(accountPreferences)
          .withServiceAccountConfig(serviceAccountConfig)
          .withTrustLevel(trustLevel);
    }

    public Account build() {
      Account account = new Account();
      account.setCompanyName(companyName);
      account.setAccountName(accountName);
      account.setAccountKey(accountKey);
      account.setUuid(uuid);
      account.setAppId(appId);
      account.setEncryption(encryption);
      account.setCreatedBy(createdBy);
      account.setCreatedAt(createdAt);
      account.setLastUpdatedBy(lastUpdatedBy);
      account.setLastUpdatedAt(lastUpdatedAt);
      account.setAuthenticationMechanism(authenticationMechanism);
      account.setDelegateConfiguration(delegateConfiguration);
      account.setDefaults(defaults);
      account.setLicenseInfo(licenseInfo);
      account.setCeLicenseInfo(ceLicenseInfo);
      account.setEmailSentToSales(emailSentToSales);
      account.setWhitelistedDomains(whitelistedDomains);
      account.setLastLicenseExpiryReminderSentAt(lastLicenseExpiryReminderSentAt);
      account.setLicenseExpiryRemindersSentAt(licenseExpiryRemindersSentAt);
      account.setOauthEnabled(oauthEnabled);
      account.setCloudCostEnabled(cloudCostEnabled);
      account.setCeAutoCollectK8sEvents(ceK8sEventCollectionEnabled);
      account.setSubdomainUrl(subdomainUrl);
      account.setRingName(ringName);
      account.setHarnessSupportAccessAllowed(isHarnessSupportAccessAllowed);
      account.setBackgroundJobsDisabled(backgroundJobsDisabled);
      account.setDefaultExperience(defaultExperience);
      account.isCrossGenerationAccessEnabled(isCrossGenerationAccessEnabled);
      account.setCreatedFromNG(createdFromNG);
      account.setProductLed(isProductLed);
      account.setAccountActivelyUsed(accountActivelyUsed);
      account.setAccountPreferences(accountPreferences);
      account.setNextGenEnabled(nextGenEnabled);
      account.setServiceAccountConfig(serviceAccountConfig);
      account.setTrustLevel(trustLevel);
      return account;
    }
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends BaseEntityYaml {
    private List<NameValuePair.Yaml> defaults = new ArrayList<>();

    @lombok.Builder
    public Yaml(String type, String harnessApiVersion, List<NameValuePair.Yaml> defaults) {
      super(type, harnessApiVersion);
      this.defaults = defaults;
    }
  }

  public List<String> toAccountIds(List<Account> accounts) {
    return accounts.stream().map(account -> getUuid()).collect(Collectors.toList());
  }

  @UtilityClass
  public static final class AccountKeys {
    // Temporary
    public static final String createdAt = "createdAt";
    public static final String uuid = "uuid";
    public static final String name = "name";
    public static final String licenseExpiryCheckIteration = "licenseExpiryCheckIteration";
    public static final String accountDeletionIteration = "accountDeletionIteration";
    public static final String subdomainUrl = "subdomainUrl";
    public static final String gitSyncExpiryCheckIteration = "gitSyncExpiryCheckIteration";
    public static final String ceLicenseExpiryIteration = "ceLicenseExpiryIteration";
    public static final String delegateTelemetryPublisherIteration = "delegateTelemetryPublisherIteration";
    public static final String ceLicenseInfo = "ceLicenseInfo";
    public static final String isHarnessSupportAccessAllowed = "isHarnessSupportAccessAllowed";
    public static final String resourceLookupSyncIteration = "resourceLookupSyncIteration";
    public static final String instanceStatsMetricsPublisherInteration = "instanceStatsMetricsPublisherIteration";
    public static final String delegateTaskRebroadcastIteration = "delegateTaskRebroadcastIteration";
    public static final String DELEGATE_CONFIGURATION_DELEGATE_VERSIONS =
        delegateConfiguration + "." + DelegateConfigurationKeys.delegateVersions;
  }
}
