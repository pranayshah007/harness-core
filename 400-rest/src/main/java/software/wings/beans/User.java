/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static java.util.stream.Collectors.toList;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.user.UserAccountLevelData;

import software.wings.beans.loginSettings.UserLockoutInfo;
import software.wings.beans.security.UserGroup;
import software.wings.beans.utm.UtmInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserRequestInfo;
import software.wings.security.authentication.TwoFactorAuthenticationMechanism;
import software.wings.security.authentication.totp.RateLimitProtection;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Reference;
import dev.morphia.annotations.Transient;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.security.auth.Subject;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * User bean class.
 *
 * @author Rishi
 */
@OwnedBy(PL)
@JsonInclude(NON_EMPTY)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "users", noClassnameStored = true)
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "UserKeys")
@TargetModule(HarnessModule._957_CG_BEANS)
@JsonIgnoreProperties(ignoreUnknown = true)
public class User extends Base implements Principal {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("accountsIdx_disabled_lastUpdatedAt")
                 .field(UserKeys.accounts)
                 .field(UserKeys.disabled)
                 .descSortField("lastUpdatedAt")
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("pendingAccountsIdx_disabled_lastUpdatedAt")
                 .field(UserKeys.pendingAccounts)
                 .field(UserKeys.disabled)
                 .descSortField("lastUpdatedAt")
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("userIdAccountIdx")
                 .field(UserKeys.accounts)
                 .field(UserKeys.externalUserId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("name_accounts_disabled")
                 .field(UserKeys.name)
                 .field(UserKeys.accounts)
                 .field(UserKeys.disabled)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("email_accounts_disabled")
                 .field(UserKeys.email)
                 .field(UserKeys.accounts)
                 .field(UserKeys.disabled)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("email_pendingAccounts_disabled")
                 .field(UserKeys.email)
                 .field(UserKeys.pendingAccounts)
                 .field(UserKeys.disabled)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountsCreatedAt")
                 .field(UserKeys.accounts)
                 .descSortField("createdAt")
                 .build())
        .build();
  }

  public static final String EMAIL_KEY = "email";
  public static final String ROLES_KEY = "roles";

  @NotEmpty private String name;

  @FdIndex private String externalUserId;

  private String givenName;

  private String familyName;

  @FdUniqueIndex @Email private String email;

  @JsonIgnore private String passwordHash;

  @Transient private String companyName;

  @Transient private String accountName;

  @JsonIgnore @Reference(idOnly = true, ignoreMissing = true) private List<Role> roles = new ArrayList<>();

  @Transient private List<UserGroup> userGroups = new ArrayList<>();

  @Reference(idOnly = true, ignoreMissing = true) private List<Account> accounts = new ArrayList<>();

  @Getter
  @Setter
  @Reference(idOnly = true, ignoreMissing = true)
  private List<Account> pendingAccounts = new ArrayList<>();

  @Transient private List<Account> supportAccounts = new ArrayList<>();

  private long lastLogin;

  @Transient private boolean firstLogin;

  @Getter @Setter @Transient private char[] password;
  @Transient private String token;
  @Transient private String twoFactorJwtToken;

  private boolean emailVerified;

  private boolean passwordExpired;

  private boolean userLocked;

  private long statsFetchedOn;

  private String lastAccountId;

  private String defaultAccountId;

  private String lastAppId;

  private boolean disabled;

  private boolean imported;

  private UserLockoutInfo userLockoutInfo = new UserLockoutInfo();

  @Getter private RateLimitProtection rateLimitProtection;

  @JsonIgnore private long passwordChangedAt;

  @JsonIgnore @Transient private UserRequestInfo userRequestInfo;
  @JsonIgnore @Transient private UserRequestContext userRequestContext;

  private boolean twoFactorAuthenticationEnabled;
  private TwoFactorAuthenticationMechanism twoFactorAuthenticationMechanism;
  @JsonIgnore private String totpSecretKey;
  @JsonIgnore private long marketoLeadId;
  @JsonIgnore private String segmentIdentity;
  private String oauthProvider;
  @JsonIgnore private Set<String> reportedMarketoCampaigns = new HashSet<>();
  private Set<String> reportedSegmentTracks = new HashSet<>();
  private UtmInfo utmInfo;

  @Getter @Setter private Map<String, UserAccountLevelData> userAccountLevelDataMap = new HashMap<>();

  /**
   * Return partial user object without sensitive information.
   *
   * @return Partial User object without sensitive information.
   */
  @JsonIgnore
  public User getPublicUser(boolean includeSupportAccounts) {
    User publicUser = new User();
    publicUser.setUuid(getUuid());
    publicUser.setName(getName());
    publicUser.setEmail(getEmail());
    publicUser.setDefaultAccountId(getDefaultAccountId());
    publicUser.setAccounts(getAccounts());
    publicUser.setTwoFactorAuthenticationEnabled(isTwoFactorAuthenticationEnabled());
    publicUser.setTwoFactorAuthenticationMechanism(getTwoFactorAuthenticationMechanism());
    publicUser.setFirstLogin(isFirstLogin());
    publicUser.setLastLogin(getLastLogin());
    publicUser.setPasswordExpired(isPasswordExpired());
    publicUser.setUserLocked(isUserLocked());
    publicUser.setImported(isImported());
    // publicUser.setCompanyName(getCompanyName());
    if (includeSupportAccounts) {
      publicUser.setSupportAccounts(getSupportAccounts());
    }
    return publicUser;
  }
  public boolean isAccountAdmin(String accountId) {
    return roles != null
        && roles.stream()
               .filter(role
                   -> role.getRoleType() == RoleType.ACCOUNT_ADMIN && role.getAccountId() != null
                       && role.getAccountId().equals(accountId))
               .findFirst()
               .isPresent();
  }

  public boolean isAllAppAdmin(String accountId) {
    return roles != null
        && roles.stream()
               .filter(role
                   -> role.getRoleType() == RoleType.APPLICATION_ADMIN && role.getAccountId() != null
                       && role.getAccountId().equals(accountId) && role.isAllApps())
               .findFirst()
               .isPresent();
  }

  public boolean isAppAdmin(String accountId, String appId) {
    return roles != null
        && roles.stream()
               .filter(role
                   -> role.getRoleType() == RoleType.APPLICATION_ADMIN && role.getAccountId() != null
                       && role.getAccountId().equals(accountId) && (role.isAllApps() || appId.equals(role.getAppId())))
               .findFirst()
               .isPresent();
  }

  @JsonIgnore
  public List<Role> getRolesByAccountId(String accountId) {
    if (roles == null) {
      return null;
    }
    return roles.stream()
        .filter(role -> role.getAccountId() != null && role.getAccountId().equals(accountId))
        .collect(toList());
  }

  @Override
  public String getName() {
    return name;
  }

  public String getGivenName() {
    return givenName;
  }

  public boolean getDisabled() {
    return disabled;
  }

  public String getFamilyName() {
    return familyName;
  }

  /**
   * Sets name.
   *
   * @param name the name
   */
  public void setName(String name) {
    this.name = name;
  }

  public void setGivenName(String givenName) {
    this.givenName = givenName;
  }

  public void setFamilyName(String familyName) {
    this.familyName = familyName;
  }

  public boolean isImported() {
    return imported;
  }

  public void setImported(boolean imported) {
    this.imported = imported;
  }

  /* (non-Javadoc)
   * @see java.security.Principal#implies(javax.security.auth.Subject)
   */
  @Override
  public boolean implies(Subject subject) {
    return false;
  }

  /**
   * Gets email.
   *
   * @return the email
   */
  public String getEmail() {
    return isEmpty(email) ? email : email.toLowerCase();
  }

  /**
   * Sets email.
   *
   * @param email the email
   */
  public void setEmail(String email) {
    this.email = isEmpty(email) ? email : email.toLowerCase();
  }

  public String getExternalUserId() {
    return isNotEmpty(externalUserId) ? externalUserId : null;
  }

  public void setExternalUserId(String externalUserId) {
    this.externalUserId = externalUserId;
  }

  /**
   * Gets password hash.
   *
   * @return the password hash
   */
  public String getPasswordHash() {
    return passwordHash;
  }

  /**
   * Sets password hash.
   *
   * @param passwordHash the password hash
   */
  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public boolean isPasswordExpired() {
    return passwordExpired;
  }

  public void setPasswordExpired(boolean passwordExpired) {
    this.passwordExpired = passwordExpired;
  }

  public boolean isUserLocked() {
    return userLocked;
  }

  public void setUserLocked(boolean userLocked) {
    this.userLocked = userLocked;
  }

  public UserLockoutInfo getUserLockoutInfo() {
    return userLockoutInfo;
  }

  public void setUserLockoutInfo(UserLockoutInfo userLockoutInfo) {
    this.userLockoutInfo = userLockoutInfo;
  }

  /**
   * Gets last login.
   *
   * @return the last login
   */
  public long getLastLogin() {
    return lastLogin;
  }

  /**
   * Sets last login.
   *
   * @param lastLogin the last login
   */
  public void setLastLogin(long lastLogin) {
    this.lastLogin = lastLogin;
  }

  public boolean isFirstLogin() {
    return firstLogin;
  }

  public void setFirstLogin(boolean firstLogin) {
    this.firstLogin = firstLogin;
  }

  /**
   * Gets token.
   *
   * @return the token
   */
  public String getToken() {
    return token;
  }

  /**
   * Sets token.
   *
   * @param token the token
   */
  public void setToken(String token) {
    this.token = token;
  }

  /**
   * Gets roles.
   *
   * @return the roles
   */
  public List<Role> getRoles() {
    return roles;
  }

  /**
   * Sets roles.
   *
   * @param roles the roles
   */
  public void setRoles(List<Role> roles) {
    this.roles = roles;
  }

  /**
   * Adds role to User object.
   *
   * @param role role to assign to User.
   */
  public void addRole(Role role) {
    if (roles == null) {
      roles = new ArrayList<>();
    }
    roles.add(role);
  }

  /**
   * Is email verified boolean.
   *
   * @return the boolean
   */
  public boolean isEmailVerified() {
    return emailVerified;
  }

  /**
   * Sets email verified.
   *
   * @param emailVerified the email verified
   */
  public void setEmailVerified(boolean emailVerified) {
    this.emailVerified = emailVerified;
  }

  /**
   * Gets stats fetched on.
   *
   * @return the stats fetched on
   */
  public long getStatsFetchedOn() {
    return statsFetchedOn;
  }

  /**
   * Sets stats fetched on.
   *
   * @param statsFetchedOn the stats fetched on
   */
  public void setStatsFetchedOn(long statsFetchedOn) {
    this.statsFetchedOn = statsFetchedOn;
  }

  /**
   * Getter for property 'accounts'.
   *
   * @return Value for property 'accounts'.
   */
  public List<Account> getAccounts() {
    return accounts;
  }

  public List<String> getAccountIds() {
    return isNotEmpty(accounts) ? accounts.stream().map(Account::getUuid).collect(Collectors.toList())
                                : Collections.emptyList();
  }

  /**
   * Setter for property 'accounts'.
   *
   * @param accounts Value to set for property 'accounts'.
   */
  public void setAccounts(List<Account> accounts) {
    this.accounts = isNotEmpty(accounts) ? accounts.stream().distinct().collect(Collectors.toList()) : accounts;
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

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  public String getLastAccountId() {
    if (isEmpty(lastAccountId) && isNotEmpty(accounts)) {
      // The first account will be considered as last account if not set. It will be used for encoding AuthToken.
      lastAccountId = accounts.get(0).getUuid();
    }
    return lastAccountId;
  }

  public void setLastAccountId(String lastAccountId) {
    this.lastAccountId = lastAccountId;
  }

  public String getDefaultAccountId() {
    if (defaultAccountId == null && isNotEmpty(accounts)) {
      return getDefaultAccountCandidate();
    }
    return defaultAccountId;
  }

  @JsonIgnore
  public String getDefaultAccountCandidate() {
    final List<String> accountStatusesOrderedByPriority = Arrays.asList(AccountStatus.ACTIVE, AccountStatus.EXPIRED,
        AccountStatus.INACTIVE, AccountStatus.MARKED_FOR_DELETION, AccountStatus.DELETED);
    final List<String> accountTypesOrderedByPriority =
        Arrays.asList(AccountType.PAID, AccountType.ESSENTIALS, AccountType.TRIAL, AccountType.COMMUNITY);

    List<Account> userAccounts = getAccounts();

    if (isEmpty(userAccounts)) {
      return null;
    }

    Comparator<Account> byStatus =
        Comparator.comparingInt(account -> accountStatusesOrderedByPriority.indexOf(getAccountStatus(account)));
    Comparator<Account> byType =
        Comparator.comparingInt(account -> accountTypesOrderedByPriority.indexOf(getAccountType(account)));

    userAccounts.sort(byStatus.thenComparing(byType));

    return userAccounts.get(0).getUuid();
  }

  @JsonIgnore
  private String getAccountStatus(Account account) {
    LicenseInfo licenseInfo = account.getLicenseInfo();
    return licenseInfo == null || licenseInfo.getAccountStatus() == null ? AccountStatus.ACTIVE
                                                                         : licenseInfo.getAccountStatus();
  }

  @JsonIgnore
  private String getAccountType(Account account) {
    LicenseInfo licenseInfo = account.getLicenseInfo();
    return licenseInfo == null || licenseInfo.getAccountType() == null ? AccountType.PAID
                                                                       : licenseInfo.getAccountType();
  }

  public void setDefaultAccountId(String accountId) {
    this.defaultAccountId = accountId;
  }

  public String getLastAppId() {
    return lastAppId;
  }

  public void setLastAppId(String lastAppId) {
    this.lastAppId = lastAppId;
  }

  public UserRequestInfo getUserRequestInfo() {
    return userRequestInfo;
  }

  public void setUserRequestInfo(UserRequestInfo userRequestInfo) {
    this.userRequestInfo = userRequestInfo;
  }

  public List<Account> getSupportAccounts() {
    return supportAccounts;
  }

  public void setSupportAccounts(List<Account> supportAccounts) {
    this.supportAccounts = supportAccounts;
  }

  public List<UserGroup> getUserGroups() {
    return userGroups;
  }

  public void setUserGroups(List<UserGroup> userGroups) {
    this.userGroups = userGroups;
  }

  public boolean isDisabled() {
    return disabled;
  }

  public void setDisabled(boolean disabled) {
    this.disabled = disabled;
  }

  @JsonIgnore
  public long getMarketoLeadId() {
    return marketoLeadId;
  }

  @JsonIgnore
  public void setMarketoLeadId(long marketoLeadId) {
    this.marketoLeadId = marketoLeadId;
  }

  @JsonIgnore
  public String getSegmentIdentity() {
    return segmentIdentity;
  }

  @JsonIgnore
  public void setSegmentIdentity(String segmentIdentity) {
    this.segmentIdentity = segmentIdentity;
  }

  @JsonIgnore
  public Set<String> getReportedMarketoCampaigns() {
    return reportedMarketoCampaigns;
  }

  @JsonIgnore
  public void setReportedMarketoCampaigns(Set<String> reportedMarketoCampaigns) {
    this.reportedMarketoCampaigns = reportedMarketoCampaigns;
  }

  public Set<String> getReportedSegmentTracks() {
    return reportedSegmentTracks;
  }

  public void setReportedSegmentTracks(Set<String> reportedSegmentTracks) {
    this.reportedSegmentTracks = reportedSegmentTracks;
  }

  public String getOauthProvider() {
    return oauthProvider;
  }

  public void setOauthProvider(String oauthProvider) {
    this.oauthProvider = oauthProvider;
  }

  public UtmInfo getUtmInfo() {
    return utmInfo;
  }

  public void setUtmInfo(UtmInfo utmInfo) {
    this.utmInfo = utmInfo;
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

    User user = (User) o;

    if (name != null ? !name.equals(user.name) : user.name != null) {
      return false;
    }
    if (email != null ? !email.equals(user.email) : user.email != null) {
      return false;
    }
    if (companyName != null ? !companyName.equals(user.companyName) : user.companyName != null) {
      return false;
    }
    return accountName != null ? accountName.equals(user.accountName) : user.accountName == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (email != null ? email.hashCode() : 0);
    result = 31 * result + (companyName != null ? companyName.hashCode() : 0);
    result = 31 * result + (accountName != null ? accountName.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "User{"
        + "name='" + name + '\'' + ", email='" + email + '\'' + ", companyName='" + companyName + '\''
        + ", accountName='" + accountName + '\'' + ", roles=" + roles + ", accounts=" + accounts
        + ", lastLogin=" + lastLogin + ", emailVerified=" + emailVerified + ", statsFetchedOn=" + statsFetchedOn + '}';
  }

  public long getPasswordChangedAt() {
    return passwordChangedAt;
  }

  public void setPasswordChangedAt(long passwordChangedAt) {
    this.passwordChangedAt = passwordChangedAt;
  }

  public UserRequestContext getUserRequestContext() {
    return userRequestContext;
  }

  public void setUserRequestContext(UserRequestContext userRequestContext) {
    this.userRequestContext = userRequestContext;
  }

  public boolean isTwoFactorAuthenticationEnabled() {
    return twoFactorAuthenticationEnabled;
  }

  public void setTwoFactorAuthenticationEnabled(boolean twoFactorAuthenticationEnabled) {
    this.twoFactorAuthenticationEnabled = twoFactorAuthenticationEnabled;
  }

  public TwoFactorAuthenticationMechanism getTwoFactorAuthenticationMechanism() {
    return twoFactorAuthenticationMechanism;
  }

  public void setTwoFactorAuthenticationMechanism(TwoFactorAuthenticationMechanism twoFactorAuthenticationMechanism) {
    this.twoFactorAuthenticationMechanism = twoFactorAuthenticationMechanism;
  }

  public String getTotpSecretKey() {
    return totpSecretKey;
  }

  public void setTotpSecretKey(String totpSecretKey) {
    this.totpSecretKey = totpSecretKey;
  }

  public String getTwoFactorJwtToken() {
    return twoFactorJwtToken;
  }

  public void setTwoFactorJwtToken(String twoFactorJwtToken) {
    this.twoFactorJwtToken = twoFactorJwtToken;
  }

  public static final class Builder {
    private String name;
    private String email;
    private String passwordHash;
    private String companyName;
    private String accountName;
    private String givenName;
    private String familyName;
    private List<Role> roles = new ArrayList<>();
    private List<UserGroup> userGroups = new ArrayList<>();
    private List<Account> accounts = new ArrayList<>();
    private List<Account> pendingAccounts = new ArrayList<>();
    private List<Account> supportAccounts = new ArrayList<>();
    private String defaultAccountId;
    private long lastLogin;
    private boolean firstLogin;
    private char[] password;
    private String token;
    private boolean emailVerified;
    private long statsFetchedOn;
    private String uuid;
    private String appId;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private boolean twoFactorAuthenticationEnabled;
    private TwoFactorAuthenticationMechanism twoFactorAuthenticationMechanism;
    private String totpSecretKey;
    private String twoFactorJwtToken;
    private String oauthProvider;
    private boolean passwordExpired;
    private boolean userLocked;
    private boolean imported;
    private UtmInfo utmInfo;
    private RateLimitProtection rateLimitProtection;
    private UserRequestContext userRequestContext;
    private String externalUserId;

    private Builder() {}

    public static Builder anUser() {
      return new Builder();
    }

    Builder passwordExpired(boolean passwordExpired) {
      this.passwordExpired = passwordExpired;
      return this;
    }

    public Builder userLocked(boolean userLocked) {
      this.userLocked = userLocked;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder externalUserId(String externalUserId) {
      this.externalUserId = externalUserId;
      return this;
    }

    public Builder givenName(String givenName) {
      this.givenName = givenName;
      return this;
    }

    public Builder familyName(String familyName) {
      this.familyName = familyName;
      return this;
    }

    public Builder email(String email) {
      this.email = email;
      return this;
    }

    public Builder userRequestContext(UserRequestContext userRequestContext) {
      this.userRequestContext = userRequestContext;
      return this;
    }

    public Builder rateLimitProtection(RateLimitProtection rateLimitProtection) {
      this.rateLimitProtection = rateLimitProtection;
      return this;
    }

    public Builder passwordHash(String passwordHash) {
      this.passwordHash = passwordHash;
      return this;
    }

    public Builder companyName(String companyName) {
      this.companyName = companyName;
      return this;
    }

    public Builder accountName(String accountName) {
      this.accountName = accountName;
      return this;
    }

    public Builder roles(List<Role> roles) {
      this.roles = roles;
      return this;
    }

    public Builder userGroups(List<UserGroup> userGroups) {
      this.userGroups = userGroups;
      return this;
    }

    public Builder accounts(List<Account> accounts) {
      this.accounts = accounts;
      return this;
    }

    public Builder pendingAccounts(List<Account> accounts) {
      this.pendingAccounts = accounts;
      return this;
    }

    public Builder supportAccounts(List<Account> supportAccounts) {
      this.supportAccounts = supportAccounts;
      return this;
    }

    public Builder defaultAccountId(String accountId) {
      this.defaultAccountId = accountId;
      return this;
    }

    Builder lastLogin(long lastLogin) {
      this.lastLogin = lastLogin;
      return this;
    }

    public Builder password(char[] password) {
      this.password = password == null ? null : password.clone();
      return this;
    }

    public Builder token(String token) {
      this.token = token;
      return this;
    }

    public Builder emailVerified(boolean emailVerified) {
      this.emailVerified = emailVerified;
      return this;
    }

    Builder firstLogin(boolean firstLogin) {
      this.firstLogin = firstLogin;
      return this;
    }

    Builder statsFetchedOn(long statsFetchedOn) {
      this.statsFetchedOn = statsFetchedOn;
      return this;
    }

    public Builder uuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    public Builder appId(String appId) {
      this.appId = appId;
      return this;
    }

    public Builder createdBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder createdAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder lastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    public Builder lastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    public Builder twoFactorAuthenticationEnabled(boolean twoFactorAuthenticationEnabled) {
      this.twoFactorAuthenticationEnabled = twoFactorAuthenticationEnabled;
      return this;
    }

    public Builder twoFactorAuthenticationMechanism(TwoFactorAuthenticationMechanism twoFactorAuthenticationMechanism) {
      this.twoFactorAuthenticationMechanism = twoFactorAuthenticationMechanism;
      return this;
    }

    Builder totpSecretKey(String totpSecretKey) {
      this.totpSecretKey = totpSecretKey;
      return this;
    }

    public Builder twoFactorJwtToken(String twoFactorJwtToken) {
      this.twoFactorJwtToken = twoFactorJwtToken;
      return this;
    }

    public Builder oauthProvider(String oauthProvider) {
      this.oauthProvider = oauthProvider;
      return this;
    }

    public Builder imported(boolean imported) {
      this.imported = imported;
      return this;
    }

    public Builder utmInfo(UtmInfo utmInfo) {
      this.utmInfo = utmInfo;
      return this;
    }

    public Builder but() {
      return anUser()
          .name(name)
          .email(email)
          .externalUserId(externalUserId)
          .passwordHash(passwordHash)
          .companyName(companyName)
          .roles(roles)
          .userGroups(userGroups)
          .accounts(accounts)
          .pendingAccounts(pendingAccounts)
          .supportAccounts(supportAccounts)
          .defaultAccountId(defaultAccountId)
          .lastLogin(lastLogin)
          .password(password)
          .token(token)
          .emailVerified(emailVerified)
          .firstLogin(firstLogin)
          .statsFetchedOn(statsFetchedOn)
          .uuid(uuid)
          .accountName(accountName)
          .appId(appId)
          .createdBy(createdBy)
          .createdAt(createdAt)
          .lastUpdatedBy(lastUpdatedBy)
          .lastUpdatedAt(lastUpdatedAt)
          .twoFactorAuthenticationEnabled(twoFactorAuthenticationEnabled)
          .twoFactorAuthenticationMechanism(twoFactorAuthenticationMechanism)
          .totpSecretKey(totpSecretKey)
          .twoFactorJwtToken(twoFactorJwtToken)
          .passwordExpired(passwordExpired)
          .oauthProvider(oauthProvider)
          .rateLimitProtection(rateLimitProtection)
          .utmInfo(utmInfo)
          .imported(imported);
    }

    public User build() {
      User user = new User();
      user.setName(name);
      user.setEmail(email);
      user.setExternalUserId(externalUserId);
      user.setPasswordHash(passwordHash);
      user.setCompanyName(companyName);
      user.setAccountName(accountName);
      user.setRoles(roles);
      user.setUserGroups(userGroups);
      user.setAccounts(accounts);
      user.setPendingAccounts(pendingAccounts);
      user.setSupportAccounts(supportAccounts);
      user.setDefaultAccountId(defaultAccountId);
      user.setLastLogin(lastLogin);
      user.setPassword(password);
      user.setToken(token);
      user.setEmailVerified(emailVerified);
      user.setFirstLogin(firstLogin);
      user.setStatsFetchedOn(statsFetchedOn);
      user.setUuid(uuid);
      user.setAppId(appId);
      user.setCreatedBy(createdBy);
      user.setCreatedAt(createdAt);
      user.setLastUpdatedBy(lastUpdatedBy);
      user.setLastUpdatedAt(lastUpdatedAt);
      user.setTwoFactorAuthenticationEnabled(twoFactorAuthenticationEnabled);
      user.setTwoFactorAuthenticationMechanism(twoFactorAuthenticationMechanism);
      user.setTotpSecretKey(totpSecretKey);
      user.setTwoFactorJwtToken(twoFactorJwtToken);
      user.setOauthProvider(oauthProvider);
      user.setPasswordExpired(passwordExpired);
      user.setUserLocked(userLocked);
      user.setImported(imported);
      user.setUtmInfo(utmInfo);
      user.setGivenName(givenName);
      user.setFamilyName(familyName);
      user.setUserRequestContext(userRequestContext);

      user.rateLimitProtection = rateLimitProtection;

      return user;
    }
  }
}
