/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.licensing;

import static io.harness.annotations.dev.HarnessTeam.GTM;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.license.CeLicenseInfo;
import io.harness.ccm.license.CeLicenseType;
import io.harness.configuration.DeployVariant;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.response.CheckExpiryResultDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.licensing.remote.admin.AdminLicenseHttpClient;
import io.harness.observer.Subject;
import io.harness.persistence.HIterator;

import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.beans.AccountStatus;
import software.wings.beans.AccountType;
import software.wings.beans.DefaultSalesContacts;
import software.wings.beans.DefaultSalesContacts.AccountTypeDefault;
import software.wings.beans.License;
import software.wings.beans.LicenseInfo;
import software.wings.beans.User;
import software.wings.beans.security.UserGroup;
import software.wings.dl.GenericDbCache;
import software.wings.dl.WingsPersistence;
import software.wings.persistence.mail.EmailData;
import software.wings.security.authentication.MarketPlaceConfig;
import software.wings.service.impl.AccountDao;
import software.wings.service.impl.LicenseUtils;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.EmailNotificationService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.account.AccountLicenseObserver;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.CountOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;

/**
 *
 * @author rktummala on 11/10/18
 */
@OwnedBy(GTM)
@Singleton
@Slf4j
@TargetModule(HarnessModule._820_PLATFORM_SERVICE)
public class LicenseServiceImpl implements LicenseService {
  private static final Long FREE_CLIENT_MAUS = 25000L;
  private static final Long TEAM_CLIENT_MAUS = 100000L;
  private static final Long ENTERPRISE_CLIENT_MAUS = 1000000L;
  private static final String PREMIER = "PRMR";
  private static final String TEAM = "TEAM";
  private static final String TEAM_SHORT = "TM";
  private static final String ENTERPRISE = "ENTERPRISE";
  private static final String ENTERPRISE_SHORT = "EN";
  private static final String PAID = "PAID";
  private static final String EMAIL_SUBJECT_ACCOUNT_EXPIRED = "Harness License Expired!";
  private static final String EMAIL_SUBJECT_ACCOUNT_ABOUT_TO_EXPIRE = "Harness License about to Expire!";

  private static final String EMAIL_BODY_ACCOUNT_EXPIRED = "Customer License has Expired";
  private static final String EMAIL_BODY_ACCOUNT_ABOUT_TO_EXPIRE = "Customer License is about to Expire";

  private static final String TRIAL_EXPIRATION_DAY_0_TEMPLATE = "trial_expiration_day0";
  private static final String TRIAL_EXPIRATION_DAY_30_TEMPLATE = "trial_expiration_day30";
  private static final String TRIAL_EXPIRATION_DAY_60_TEMPLATE = "trial_expiration_day60";
  private static final String TRIAL_EXPIRATION_DAY_89_TEMPLATE = "trial_expiration_day89";
  private static final String TRIAL_EXPIRATION_BEFORE_DELETION_TEMPLATE = "trial_expiration_before_deletion";

  private final AccountService accountService;
  private final WingsPersistence wingsPersistence;
  private final GenericDbCache dbCache;
  private final ExecutorService executorService;
  private final LicenseProvider licenseProvider;
  private final EmailNotificationService emailNotificationService;
  private final EventPublishHelper eventPublishHelper;
  private final UserService userService;
  private final UserGroupService userGroupService;

  private final AdminLicenseHttpClient adminLicenseHttpClient;
  private final AccountDao accountDao;
  private final NgLicenseHttpClient ngLicenseHttpClient;
  private List<String> trialDefaultContacts;
  private List<String> paidDefaultContacts;

  @Inject private MainConfiguration mainConfiguration;
  private final Cache<String, CheckExpiryResultDTO> licenseExpiryCache;
  @Getter private final Subject<AccountLicenseObserver> accountLicenseObserverSubject;
  @Inject
  public LicenseServiceImpl(AccountService accountService, AccountDao accountDao, WingsPersistence wingsPersistence,
      GenericDbCache dbCache, ExecutorService executorService, LicenseProvider licenseProvider,
      EmailNotificationService emailNotificationService, EventPublishHelper eventPublishHelper,
      MainConfiguration mainConfiguration, UserService userService, UserGroupService userGroupService,
      NgLicenseHttpClient ngLicenseHttpClient, AdminLicenseHttpClient adminLicenseHttpClient,
      Subject<AccountLicenseObserver> accountLicenseObserverSubject) {
    this.accountService = accountService;
    this.accountDao = accountDao;
    this.wingsPersistence = wingsPersistence;
    this.dbCache = dbCache;
    this.executorService = executorService;
    this.licenseProvider = licenseProvider;
    this.emailNotificationService = emailNotificationService;
    this.eventPublishHelper = eventPublishHelper;
    this.userService = userService;
    this.userGroupService = userGroupService;
    this.ngLicenseHttpClient = ngLicenseHttpClient;
    this.adminLicenseHttpClient = adminLicenseHttpClient;
    this.accountLicenseObserverSubject = accountLicenseObserverSubject = new Subject<>();
    this.licenseExpiryCache = Caffeine.newBuilder().expireAfterWrite(6, TimeUnit.HOURS).build();

    DefaultSalesContacts defaultSalesContacts = mainConfiguration.getDefaultSalesContacts();
    if (defaultSalesContacts != null && defaultSalesContacts.isEnabled()) {
      List<AccountTypeDefault> accountTypeDefaults = defaultSalesContacts.getAccountTypeDefaults();

      if (isNotEmpty(accountTypeDefaults)) {
        for (AccountTypeDefault accountTypeDefault : accountTypeDefaults) {
          switch (accountTypeDefault.getAccountType()) {
            case AccountType.ESSENTIALS:
            case AccountType.PAID:
              paidDefaultContacts = getEmailIds(accountTypeDefault.getEmailIds());
              break;
            case AccountType.TRIAL:
              trialDefaultContacts = getEmailIds(accountTypeDefault.getEmailIds());
              break;
            default:
              break;
          }
        }
      }
    }
  }

  @Override
  public void checkForLicenseExpiry(Account account) {
    try {
      account = accountService.get(account.getUuid());

      // Check if all ng licenses inactive before decides expire account
      CheckExpiryResultDTO ngLicenseDecision = CheckExpiryResultDTO.builder().shouldDelete(false).build();
      try {
        ngLicenseDecision = licenseExpiryCache.get(
            account.getUuid(), accountId -> getResponse(ngLicenseHttpClient.checkExpiry(accountId)));
      } catch (Exception e) {
        log.warn("Error occurred during check NG license allInactive flag for account {}, due to {}", account.getUuid(),
            e.getMessage());
      }

      LicenseInfo licenseInfo = account.getLicenseInfo();

      checkAtLeastOneModuleLicenseActive(account, licenseInfo, ngLicenseDecision);

      if (licenseInfo == null) {
        return;
      }

      String accountStatus = licenseInfo.getAccountStatus();
      String accountType = licenseInfo.getAccountType();
      if (isEmpty(accountType)) {
        return;
      }

      if (accountType.equals(AccountType.COMMUNITY)) {
        return;
      }

      long expiryTime = licenseInfo.getExpiryTime();
      long currentTime = System.currentTimeMillis();
      if (currentTime < expiryTime) {
        if (accountType.equals(AccountType.PAID) || accountType.equals(AccountType.ESSENTIALS)) {
          if (!account.isEmailSentToSales() && ((expiryTime - currentTime) <= Duration.ofDays(30).toMillis())) {
            sendEmailToSales(account, expiryTime, accountType, EMAIL_SUBJECT_ACCOUNT_ABOUT_TO_EXPIRE,
                EMAIL_BODY_ACCOUNT_ABOUT_TO_EXPIRE, paidDefaultContacts);
          }
        } else if (accountType.equals(AccountType.TRIAL)) {
          boolean lessThan7DaysLeftForLicenseExpiry = (expiryTime - currentTime) <= Duration.ofDays(7).toMillis();
          if (!account.isEmailSentToSales() && lessThan7DaysLeftForLicenseExpiry) {
            sendEmailToSales(account, expiryTime, accountType, EMAIL_SUBJECT_ACCOUNT_ABOUT_TO_EXPIRE,
                EMAIL_BODY_ACCOUNT_ABOUT_TO_EXPIRE, trialDefaultContacts);
          }
        }
      } else {
        if (AccountStatus.ACTIVE.equals(accountStatus)) {
          expireLicense(account.getUuid(), licenseInfo);
          sendEmailToSales(account, expiryTime, accountType, EMAIL_SUBJECT_ACCOUNT_EXPIRED, EMAIL_BODY_ACCOUNT_EXPIRED,
              accountType.equals(AccountType.PAID) ? paidDefaultContacts : trialDefaultContacts);
        }

        if (accountType.equals(AccountType.TRIAL) && !AccountStatus.DELETED.equals(accountStatus)
            && !account.isPovAccount() && !account.isCloudCostEnabled() && ngLicenseDecision.isShouldDelete()) {
          long lastExpiryTime = Math.max(expiryTime, ngLicenseDecision.getExpiryTime());
          handleTrialAccountExpiration(account, lastExpiryTime);
        }
      }
    } catch (Exception e) {
      log.warn("Failed to check license info", e);
    }
  }

  @VisibleForTesting
  void handleTrialAccountExpiration(Account account, long expiryTime) {
    long expiredSinceDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - expiryTime);
    LicenseInfo licenseInfo = account.getLicenseInfo();
    if (expiredSinceDays >= 90 && !AccountStatus.MARKED_FOR_DELETION.equals(licenseInfo.getAccountStatus())
        && allRemindersSent(account)) {
      updateAccountStatusToMarkedForDeletion(account);
      getResponse(ngLicenseHttpClient.softDelete(account.getUuid()));
    } else {
      String templateName = getEmailTemplateName(account, System.currentTimeMillis(), expiryTime);
      if (templateName != null) {
        log.info("Sending trial account expiration email with template name {} to account {}", templateName,
            account.getUuid());
        boolean emailSent = sendEmailToAccountAdmin(account, templateName);
        if (emailSent) {
          updateLastLicenseExpiryReminderSentAt(account.getUuid(), System.currentTimeMillis());
        } else {
          log.warn("Couldn't send trial expiration email to customer for account {}", account.getUuid());
        }
      }
    }
  }

  private boolean allRemindersSent(Account account) {
    List<Long> remindersSentAt = account.getLicenseExpiryRemindersSentAt();
    return remindersSentAt != null
        && remindersSentAt.size() >= mainConfiguration.getNumberOfRemindersBeforeAccountDeletion();
  }

  private void updateAccountStatusToMarkedForDeletion(Account account) {
    LicenseInfo licenseInfo = account.getLicenseInfo();
    licenseInfo.setAccountStatus(AccountStatus.MARKED_FOR_DELETION);
    updateAccountLicense(account.getUuid(), licenseInfo);
    accountLicenseObserverSubject.fireInform(AccountLicenseObserver::onLicenseChange, account);
  }

  @VisibleForTesting
  String getEmailTemplateName(Account account, long currentTime, long expiryTime) {
    long lastLicenseExpiryReminderSentAt = account.getLastLicenseExpiryReminderSentAt();
    long expiredSinceDays = TimeUnit.MILLISECONDS.toDays(currentTime - expiryTime);
    long lastReminderSentSinceDays = TimeUnit.MILLISECONDS.toDays(currentTime - lastLicenseExpiryReminderSentAt);
    String templateName = null;

    if (lastReminderSentSinceDays > 0) {
      if (expiredSinceDays <= 1) {
        templateName = TRIAL_EXPIRATION_DAY_0_TEMPLATE;
      } else if (expiredSinceDays == 30) {
        templateName = TRIAL_EXPIRATION_DAY_30_TEMPLATE;
      } else if (expiredSinceDays == 60) {
        templateName = TRIAL_EXPIRATION_DAY_60_TEMPLATE;
      } else if (expiredSinceDays == 89) {
        templateName = TRIAL_EXPIRATION_DAY_89_TEMPLATE;
      } else if (expiredSinceDays >= 90) {
        templateName = TRIAL_EXPIRATION_BEFORE_DELETION_TEMPLATE;
      }
    }
    return templateName;
  }

  @VisibleForTesting
  void updateLastLicenseExpiryReminderSentAt(String accountId, long time) {
    Query<Account> query = wingsPersistence.createQuery(Account.class).field(AccountKeys.uuid).equal(accountId);
    UpdateOperations<Account> updateOperations = wingsPersistence.createUpdateOperations(Account.class);
    updateOperations.push(AccountKeys.licenseExpiryRemindersSentAt, time);
    updateOperations.set(AccountKeys.lastLicenseExpiryReminderSentAt, time);
    wingsPersistence.update(query, updateOperations);

    dbCache.invalidate(Account.class, accountId);
  }

  private List<String> getEmailIds(String emailIdsStr) {
    if (isEmpty(emailIdsStr)) {
      return null;
    }

    String[] emailIdArr = emailIdsStr.split(",");

    if (isEmpty(emailIdArr)) {
      return null;
    }

    List<String> emailIds = new ArrayList<>();

    for (String emailId : emailIdArr) {
      emailIds.add(emailId.trim());
    }

    return emailIds;
  }

  private void sendEmailToSales(
      Account account, long expiryTime, String accountType, String subject, String body, List<String> defaultContacts) {
    if (isEmpty(account.getSalesContacts()) && isEmpty(defaultContacts)) {
      log.info("Skipping the sending of email since no sales contacts were configured");
      return;
    }

    List<String> mailingList = isEmpty(account.getSalesContacts()) ? defaultContacts : account.getSalesContacts();

    Date expiryDate = new Date(expiryTime);
    Map<String, String> templateModel = new HashMap<>();
    templateModel.put("emailSubject", subject);
    templateModel.put("emailBody", body);
    templateModel.put("accountName", account.getAccountName());
    templateModel.put("companyName", account.getCompanyName());
    templateModel.put("accountType", accountType);
    templateModel.put("expiry", expiryDate.toString());

    EmailData emailData = EmailData.builder()
                              .system(true)
                              .to(mailingList)
                              .templateName("send_email_to_sales")
                              .templateModel(templateModel)
                              .accountId(account.getUuid())
                              .build();
    emailData.setCc(Collections.emptyList());
    emailData.setRetries(3);
    boolean sent = emailNotificationService.send(emailData);
    if (sent) {
      updateEmailSentToSales(account.getUuid(), true);
    } else {
      updateEmailSentToSales(account.getUuid(), false);
      log.warn("Couldn't send email to sales for account {}", account.getUuid());
    }
  }

  /**
   * Send email to the members of account's admin user group. If email is sent successfully to any one member of the
   * group, then return true.
   * @param account
   * @param templateName
   * @return
   */
  @VisibleForTesting
  boolean sendEmailToAccountAdmin(Account account, String templateName) {
    List<User> users = getUsersToSendTrialExpirationReminderTo(account.getUuid());
    String accountId = account.getUuid();
    boolean emailSent = users.isEmpty();
    for (User user : users) {
      String name = !user.getName().isEmpty() ? user.getName() : "there";
      Map<String, String> templateModel = new HashMap<>();
      templateModel.put("name", name);
      templateModel.put("accountName", account.getAccountName());
      templateModel.put("accountId", account.getUuid());
      String createdAt = getCreationDateForAccount(account);
      templateModel.put("accountCreationDate", createdAt);
      templateModel.put("accountType", "Trial");
      EmailData emailData = EmailData.builder()
                                .to(Collections.singletonList(user.getEmail()))
                                .templateName(templateName)
                                .templateModel(templateModel)
                                .accountId(accountId)
                                .build();
      emailData.setCc(Collections.emptyList());
      emailData.setRetries(3);
      log.info("Sending trial expiration reminder for account {} to account admin {}", accountId, user.getEmail());
      boolean emailSentToAdmin = emailNotificationService.send(emailData);
      emailSent = emailSent || emailSentToAdmin;
      log.info("Trial expiration reminder for account {} sent to account admin {} successfully {}", accountId,
          user.getEmail(), emailSentToAdmin);
    }
    log.info("Trial account expiration email with template name {} sent successfully {}", templateName, emailSent);
    return emailSent;
  }

  private String getCreationDateForAccount(Account account) {
    Date createdAt = new Date(account.getCreatedAt());
    String dateFormat = "dd-MMM-yyyy";
    final SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    return sdf.format(createdAt);
  }

  private List<User> getUsersToSendTrialExpirationReminderTo(String accountId) {
    UserGroup adminUserGroup = userGroupService.getAdminUserGroup(accountId);
    return isEmpty(adminUserGroup.getMemberIds()) ? Collections.emptyList()
                                                  : adminUserGroup.getMemberIds()
                                                        .stream()
                                                        .filter(userService::isUserPresent)
                                                        .map(userService::get)
                                                        .collect(Collectors.toList());
  }

  @Override
  public boolean updateAccountLicense(@NotEmpty String accountId, LicenseInfo licenseInfo) {
    Account accountInDB = accountService.get(accountId);
    notNullCheck("Invalid Account for the given Id: " + accountId, accountInDB);

    LicenseInfo oldLicenseInfo = accountInDB.getLicenseInfo();
    String oldAccountType = null;
    if (oldLicenseInfo != null) {
      oldAccountType = oldLicenseInfo.getAccountType();
    }

    UpdateOperations<Account> updateOperations = wingsPersistence.createUpdateOperations(Account.class);

    byte[] encryptedLicenseInfo = LicenseUtils.getEncryptedLicenseInfoForUpdate(oldLicenseInfo, licenseInfo);

    updateOperations.set("encryptedLicenseInfo", encryptedLicenseInfo);
    updateOperations.set(AccountKeys.licenseInfo, licenseInfo);

    wingsPersistence.update(accountInDB, updateOperations);
    updateEmailSentToSales(accountId, false);
    dbCache.invalidate(Account.class, accountId);
    Account updatedAccount = wingsPersistence.get(Account.class, accountId);
    LicenseUtils.decryptLicenseInfo(updatedAccount, false);
    accountLicenseObserverSubject.fireInform(AccountLicenseObserver::onLicenseChange, accountId);

    eventPublishHelper.publishLicenseChangeEvent(accountId, oldAccountType, licenseInfo.getAccountType());
    return true;
  }

  @Override
  public boolean startCeLimitedTrial(@NotEmpty String accountId) {
    Account account = accountDao.get(accountId);
    Preconditions.checkNotNull(account);

    CeLicenseInfo currCeLicenseInfo = account.getCeLicenseInfo();
    if (currCeLicenseInfo != null) {
      throw new InvalidRequestException("CE Limited Trial license has already started");
    }

    CeLicenseInfo ceLicenseInfo = CeLicenseInfo.builder()
                                      .licenseType(CeLicenseType.LIMITED_TRIAL)
                                      .expiryTime(Math.max(CeLicenseType.LIMITED_TRIAL.getDefaultExpiryTime(),
                                          CeLicenseType.getEndOfYearAsMillis(2020)))
                                      .build();
    updateCeLicense(accountId, ceLicenseInfo);
    return true;
  }

  @Override
  public boolean updateCeLicense(@NotEmpty String accountId, CeLicenseInfo ceLicenseInfo) {
    accountDao.updateCeLicense(accountId, ceLicenseInfo);
    if (Instant.now().toEpochMilli() < ceLicenseInfo.getExpiryTime()) {
      accountService.updateCloudCostEnabled(accountId, true);
    } else {
      accountService.updateCloudCostEnabled(accountId, false);
    }
    return true;
  }

  @Override
  public Account updateAccountSalesContacts(@NotEmpty String accountId, List<String> salesContacts) {
    Account accountInDB = accountService.get(accountId);

    notNullCheck("Invalid Account for the given Id: " + accountId, accountInDB);

    UpdateOperations<Account> updateOperations = wingsPersistence.createUpdateOperations(Account.class);

    if (isNotEmpty(salesContacts)) {
      updateOperations.set("salesContacts", salesContacts);
    } else {
      updateOperations.unset("salesContacts");
    }

    wingsPersistence.update(accountInDB, updateOperations);
    updateEmailSentToSales(accountId, false);
    dbCache.invalidate(Account.class, accountId);
    Account updatedAccount = wingsPersistence.get(Account.class, accountId);
    LicenseUtils.decryptLicenseInfo(updatedAccount, false);
    return updatedAccount;
  }

  @Override
  public void updateAccountLicenseForOnPrem(String encryptedLicenseInfoBase64String) {
    try {
      if (isEmpty(encryptedLicenseInfoBase64String)) {
        String msg = "Couldn't find license info";
        throw new InvalidRequestException(msg);
      }

      Query<Account> query = accountService.getBasicAccountWithLicenseInfoQuery();
      if (query.count(new CountOptions().limit(1)) == 0) {
        String msg = "Couldn't find any accounts in DB";
        throw new InvalidRequestException(msg);
      }

      try (HIterator<Account> accountList = new HIterator<>(query.limit(NO_LIMIT).fetch())) {
        accountList.forEach(account -> {
          if (!account.getAccountName().equalsIgnoreCase("Global")) {
            byte[] encryptedLicenseInfo = Base64.getDecoder().decode(encryptedLicenseInfoBase64String.getBytes());
            byte[] encryptedLicenseInfoFromDB = account.getEncryptedLicenseInfo();

            boolean noLicenseInfoInDB = isEmpty(encryptedLicenseInfoFromDB);

            if (noLicenseInfoInDB || !Arrays.equals(encryptedLicenseInfo, encryptedLicenseInfoFromDB)) {
              account.setEncryptedLicenseInfo(encryptedLicenseInfo);
              LicenseUtils.decryptLicenseInfo(account, true);
              LicenseInfo licenseInfo = account.getLicenseInfo();
              if (licenseInfo != null) {
                updateAccountLicense(account.getUuid(), licenseInfo);
              } else {
                throw new InvalidRequestException("No license info could be extracted from the encrypted license info");
              }
            }
          }
        });
      }
    } catch (Exception ex) {
      if (DeployVariant.isCommunity(mainConfiguration.getDeployVariant().name())) {
        log.info("Skip for updating on-prem license with community edition");
      } else {
        throw new InvalidRequestException("Error while updating account license for on-prem", ex);
      }
    }
  }

  @Override
  public boolean isAccountDeleted(String accountId) {
    // We get the account status from local cache even though its eventually consistent,
    // Since this is called by delegate service frequently, we are referring to cache.
    return AccountStatus.DELETED.equals(accountService.getAccountStatus(accountId));
  }

  @Override
  public boolean isAccountExpired(String accountId) {
    // TODO when we have distributed cache, account should be cached and referred.
    Account account = dbCache.get(Account.class, accountId);
    notNullCheck("Invalid account with id: " + accountId, account);
    return isAccountExpired(account);
  }

  @Override
  public boolean isAccountExpired(Account account) {
    String accountId = account.getUuid();
    LicenseInfo licenseInfo = account.getLicenseInfo();

    if (licenseInfo == null) {
      return false;
    }

    String accountType = licenseInfo.getAccountType();
    String accountStatus = licenseInfo.getAccountStatus();

    if (isEmpty(accountType)) {
      throw new InvalidRequestException("Account type is null for account :" + accountId);
    }

    if (isEmpty(accountStatus)) {
      throw new InvalidRequestException("Account status is null for account :" + accountId);
    }

    if (AccountType.COMMUNITY.equals(accountType)) {
      return false;
    }

    if (AccountStatus.EXPIRED.equals(accountStatus) || AccountStatus.DELETED.equals(accountStatus)
        || AccountStatus.MARKED_FOR_DELETION.equals(accountStatus)) {
      return true;
    }

    if (System.currentTimeMillis() > licenseInfo.getExpiryTime()) {
      executorService.submit(() -> expireLicense(accountId, licenseInfo));
      return true;
    }

    return false;
  }

  private void expireLicense(String accountId, LicenseInfo licenseInfo) {
    licenseInfo.setAccountStatus(AccountStatus.EXPIRED);
    updateAccountLicense(accountId, licenseInfo);
    accountLicenseObserverSubject.fireInform(AccountLicenseObserver::onLicenseChange, accountId);
  }

  private void updateEmailSentToSales(String accountId, boolean status) {
    wingsPersistence.updateField(Account.class, accountId, "emailSentToSales", status);
  }

  @Override
  public void validateLicense(String accountId, String operation) {
    Account account = accountService.get(accountId);
    LicenseInfo licenseInfo = account.getLicenseInfo();
    if (licenseInfo == null) {
      throw new InvalidRequestException("license Info not present");
    }
    if (licenseInfo.getExpiryTime() > 0 && System.currentTimeMillis() > licenseInfo.getExpiryTime()) {
      licenseProvider.get(account.getLicenseId());
    } else {
      throw new InvalidRequestException("Invalid expiry time");
    }
  }

  @Override
  public void setLicense(Account account) {
    List<License> licenseList = licenseProvider.getActiveLicenses();
    account.setLicenseId(licenseList.get(0).getUuid());
    if (account.getLicenseInfo() == null) {
      account.setLicenseInfo(new LicenseInfo());
    }

    account.getLicenseInfo().setExpiryTime(System.currentTimeMillis() + licenseList.get(0).getExpiryDuration());
  }

  @Override
  public boolean updateLicenseForProduct(
      String productCode, String accountId, Integer orderQuantity, long expirationTime, String dimension) {
    final MarketPlaceConfig marketPlaceConfig = mainConfiguration.getMarketPlaceConfig();
    log.info("productCode:{}, accountId:{}, orderQuantity:{}, expirationTime:{}, dimension:{}", productCode, accountId,
        orderQuantity, expirationTime, dimension);

    boolean premiumSupport = hasPremierSupport(dimension);
    Edition plan = getDimensionPlan(dimension);
    LicenseType licenseType = getModuleLicenseType(plan);
    // HARNESS_TEST_2_PRODUCT_CODE is an internal way for us to test dimensions that belong not to the same listing
    // because when published, prices are high
    String HARNESS_TEST_2_PRODUCT_CODE = "9g3jcw6iic4tpylipcti8vwcx";

    log.info("plan:{}, premiumSupport:{}, licenseType:{}", plan, premiumSupport, licenseType);
    if (marketPlaceConfig.getAwsMarketPlaceCdProductCode().equals(productCode)
        || marketPlaceConfig.getAwsMarketPlaceCcmProductCode().equals(productCode)
        || marketPlaceConfig.getAwsMarketPlaceFfProductCode().equals(productCode)
        || marketPlaceConfig.getAwsMarketPlaceCiProductCode().equals(productCode)
        || marketPlaceConfig.getAwsMarketPlaceSrmProductCode().equals(productCode)
        || marketPlaceConfig.getAwsMarketPlaceStoProductCode().equals(productCode)
        || HARNESS_TEST_2_PRODUCT_CODE.equals(productCode)) {
      updateAccountLicense(accountId,
          LicenseInfo.builder()
              .accountType(AccountType.PAID)
              .licenseUnits(orderQuantity)
              .accountStatus(AccountStatus.ACTIVE)
              .expiryTime(expirationTime)
              .build());

    } else {
      log.error("Invalid AWS productcode received:[{}],", productCode);
      return false;
    }
    accountLicenseObserverSubject.fireInform(AccountLicenseObserver::onLicenseChange, accountId);
    return true;
  }

  // Gets plan from dimension string
  public Edition getDimensionPlan(String dimension) {
    Edition plan = null;
    if (StringUtils.isNotBlank(dimension)) {
      String[] result = dimension.split("_");
      if (result.length < 2) {
        log.error("Failed to get plan for dimension:[{}]", dimension);
        return plan;
      }

      String tempPlan = result[1];
      if (TEAM.equals(tempPlan) || TEAM_SHORT.equals(tempPlan)) {
        plan = Edition.TEAM;
      } else if (ENTERPRISE.equals(tempPlan) || ENTERPRISE_SHORT.equals(tempPlan)) {
        plan = Edition.ENTERPRISE;
      } else {
        log.error("Unresolved plan for dimension:[{}]", dimension, tempPlan);
      }
    }
    return plan;
  }

  // Gets Number of Client MAUs from plan
  public Long getNumberOfClientMAUs(Edition plan) {
    Long numberOfClientMAUs = FREE_CLIENT_MAUS;
    if (Edition.TEAM.equals(plan)) {
      numberOfClientMAUs = TEAM_CLIENT_MAUS;
    } else if (Edition.ENTERPRISE.equals(plan)) {
      numberOfClientMAUs = ENTERPRISE_CLIENT_MAUS;
    } else {
      log.error("Failed to get Number of Client MAUs for plan:[{}]", plan);
    }

    return numberOfClientMAUs;
  }

  // Gets Module License from dimension/plan
  public LicenseType getModuleLicenseType(Edition plan) {
    LicenseType licenseType = null;
    if (Edition.TEAM.equals(plan) || Edition.ENTERPRISE.equals(plan)) {
      licenseType = LicenseType.PAID;
    }
    return licenseType;
  }

  // Gets Module License from dimension/plan
  public boolean hasPremierSupport(String dimension) {
    boolean premierSupport = false;
    if (StringUtils.isNotBlank(dimension)) {
      String[] result = dimension.split("_");

      if (result.length < 4) {
        return premierSupport;
      }

      if (PREMIER.equals(result[2])) {
        premierSupport = true;
      }
    }

    return premierSupport;
  }

  /**
   * Check ng licenses, cg cd license and ce license to determine if user account is actively used
   * @param account
   * @param licenseInfo
   * @param ngLicenseDecision
   */
  private void checkAtLeastOneModuleLicenseActive(
      Account account, LicenseInfo licenseInfo, CheckExpiryResultDTO ngLicenseDecision) {
    CeLicenseInfo ceLicenseInfo = account.getCeLicenseInfo();
    if (ngLicenseDecision.isNgAccountActive()
        || (licenseInfo != null && licenseInfo.getAccountStatus().equals(AccountStatus.ACTIVE))
        || (ceLicenseInfo != null && ceLicenseInfo.getExpiryTime() > System.currentTimeMillis())) {
      accountService.updateAccountActivelyUsed(account.getUuid(), true);
    } else {
      accountService.updateAccountActivelyUsed(account.getUuid(), false);
    }
  }
}
