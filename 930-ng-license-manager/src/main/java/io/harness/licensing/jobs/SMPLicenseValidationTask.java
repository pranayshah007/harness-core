/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.jobs;

import io.harness.ModuleType;
import io.harness.account.services.AccountService;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.user.UserInfo;
import io.harness.notification.Team;
import io.harness.notification.channeldetails.EmailChannel;
import io.harness.notification.channeldetails.EmailChannel.EmailChannelBuilder;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.remote.client.CGRestUtils;
import io.harness.smp.license.models.SMPLicense;
import io.harness.smp.license.v1.LicenseValidator;
import io.harness.user.remote.UserClient;
import io.harness.user.remote.UserFilterNG;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SMPLicenseValidationTask implements LicenseValidationTask {
  public static final String EMAIL_SMP_LICENSE_ALERT = "email_smp_license_alert";
  public static final int SECONDS_IN_A_DAY = 86400000;

  private final Function<String, SMPLicense> licenseProvider;
  private final AccountService accountService;
  private final UserClient userClient;
  private final NotificationClient notificationClient;
  private long lastValidTimeMs;

  @Setter private String accountIdentifier;
  @Setter private SMPLicense license;

  @Inject
  public SMPLicenseValidationTask(LicenseValidator licenseValidator, NotificationClient notificationClient,
      AccountService accountService, UserClient userClient, @Assisted("accountId") String accountIdentifier,
      @Assisted("license") SMPLicense license,
      @Assisted("licenseProvider") Function<String, SMPLicense> licenseProvider) {
    this.accountIdentifier = accountIdentifier;
    this.license = license;
    this.licenseProvider = licenseProvider;
    this.accountService = accountService;
    this.userClient = userClient;
    this.notificationClient = notificationClient;
    this.lastValidTimeMs = System.currentTimeMillis();
  }

  @Override
  public void run() {
    SMPLicense smpLicenseFromDb = licenseProvider.apply(accountIdentifier);
    boolean licenseMatch = doesDBLicenseMatchSMPLicense(smpLicenseFromDb);
    notify(licenseMatch);
  }

  private boolean doesDBLicenseMatchSMPLicense(SMPLicense smpLicenseFromDb) {
    if (smpLicenseFromDb.getModuleLicenses().size() != license.getModuleLicenses().size()) {
      return false;
    }
    Map<ModuleType, ModuleLicenseDTO> licensesFromDb = smpLicenseFromDb.getModuleLicenses().stream().collect(
        Collectors.toMap(ModuleLicenseDTO::getModuleType, a -> a));
    Map<ModuleType, ModuleLicenseDTO> licenses =
        license.getModuleLicenses().stream().collect(Collectors.toMap(ModuleLicenseDTO::getModuleType, a -> a));

    List<ModuleType> moduleTypesInLicense = new ArrayList<>(licenses.keySet());

    for (ModuleType moduleType : moduleTypesInLicense) {
      if (!licensesFromDb.containsKey(moduleType)) {
        return false;
      }
      ModuleLicenseDTO dbModuleLicense = licensesFromDb.get(moduleType);
      ModuleLicenseDTO smpModuleLicense = licenses.get(moduleType);
      if (Objects.isNull(dbModuleLicense)) {
        log.error("empty license found in smp db for account {} and module type {}", accountIdentifier, moduleType);
        return false;
      }
      if (Objects.isNull(smpModuleLicense)) {
        log.error("empty license found in smp license string for account {} and module type {}", accountIdentifier,
            moduleType);
        return false;
      }
      if (dbModuleLicense.getExpiryTime() != smpModuleLicense.getExpiryTime()) {
        log.error(
            "smp license expiry times do not match for account {} and module type {}. db license expiry time {}, license string expiry time {}",
            accountIdentifier, moduleType, dbModuleLicense.getExpiryTime(), smpModuleLicense.getExpiryTime());
        return false;
      }
    }
    return true;
  }

  private void notify(boolean validationResult) {
    if (validationResult) {
      log.info("SMP License is valid");
      lastValidTimeMs = System.currentTimeMillis();
    } else {
      log.error("SMP License did not match with license in DB");
      if (System.currentTimeMillis() - lastValidTimeMs > SECONDS_IN_A_DAY) {
        log.error("License state has been modified for this on-premise installation. "
            + "Your harness admin can follow the below troubleshooting steps to rectify the error: "
            + "1) Restart manager and ng-manager pods "
            + "2) Do helm upgrade with correct license "
            + "3) Contact harness support");
        try {
          EmailChannelBuilder channelBuilder = EmailChannel.builder();

          List<String> adminUserIds = accountService.getAdminUsers(accountIdentifier);
          List<UserInfo> adminUsers = CGRestUtils.getResponse(
              userClient.listUsers(accountIdentifier, UserFilterNG.builder().userIds(adminUserIds).build()));
          List<String> userEmails = adminUsers.stream().map(UserInfo::getEmail).collect(Collectors.toList());
          channelBuilder.recipients(userEmails);

          Map<String, String> templateData = new HashMap<>();
          AccountDTO account = accountService.getAccount(accountIdentifier);
          templateData.put("accountname", account.getName());
          channelBuilder.templateData(templateData);

          channelBuilder.team(Team.OTHER);
          channelBuilder.accountId(accountIdentifier);
          channelBuilder.templateId(EMAIL_SMP_LICENSE_ALERT);
          notificationClient.sendNotificationAsync(channelBuilder.build());
        } catch (RuntimeException e) {
          log.error("Unable to notify admin of the failure", e);
        }
      }
    }
  }
}
