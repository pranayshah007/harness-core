/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.jobs;

import io.harness.account.services.AccountService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SMPLicenseValidationTask implements LicenseValidationTask {
  public static final String EMAIL_SMP_LICENSE_ALERT = "email_smp_license_alert";

  private final LicenseValidator licenseValidator;
  private final Function<String, SMPLicense> licenseProvider;
  private final AccountService accountService;
  private final UserClient userClient;
  private final NotificationClient notificationClient;
  private long lastValidTimeMs;

  @Setter private String accountIdentifier;
  @Setter private String licenseSign;

  @Inject
  public SMPLicenseValidationTask(LicenseValidator licenseValidator, NotificationClient notificationClient,
      AccountService accountService, UserClient userClient, @Assisted("accountId") String accountIdentifier,
      @Assisted("licenseSign") String licenseSign,
      @Assisted("licenseProvider") Function<String, SMPLicense> licenseProvider) {
    this.licenseValidator = licenseValidator;
    this.accountIdentifier = accountIdentifier;
    this.licenseSign = licenseSign;
    this.licenseProvider = licenseProvider;
    this.accountService = accountService;
    this.userClient = userClient;
    this.notificationClient = notificationClient;
    this.lastValidTimeMs = System.currentTimeMillis();
  }

  @Override
  public void run() {
    SMPLicense smpLicense = licenseProvider.apply(accountIdentifier);
    boolean licenseMatch = licenseValidator.verifySign(smpLicense, licenseSign);
    notify(licenseMatch);
  }

  private void notify(boolean validationResult) {
    if (validationResult) {
      log.info("SMP License is valid");
      lastValidTimeMs = System.currentTimeMillis();
    } else {
      if (System.currentTimeMillis() - lastValidTimeMs > 86400000) {
        log.error("License validation is failing for past 1 day. Possible resolutions: "
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
