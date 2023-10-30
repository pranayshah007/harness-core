/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import io.harness.configuration.DeployMode;
import io.harness.exception.InvalidRequestException;
import io.harness.security.EncryptionUtils;

import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.LicenseInfo;
import software.wings.beans.account.AccountStatus;
import software.wings.service.intfc.instance.licensing.InstanceLimitProvider;

import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class that has object to string and string to object representations.
 * @author rktummala on 09/10/2018
 */

@UtilityClass
@Slf4j
public class LicenseUtils {
  /**
   * Trial expires end of day - 14 days from the date of creation.
   */
  private final int TRIAL_PERIOD = 14;

  private final int PAID_PERIOD_IN_YEARS = 1;

  private final int ESSENTIALS_PERIOD_IN_YEARS = 1;

  public String generateLicense(LicenseInfo licenseInfo) {
    if (licenseInfo == null) {
      throw new InvalidRequestException("Invalid license info", USER);
    }

    return encodeBase64(LicenseUtils.getEncryptedLicenseInfo(licenseInfo));
  }

  public void addLicenseInfo(Account account) {
    String deployMode = System.getenv().get(DeployMode.DEPLOY_MODE);
    LicenseInfo licenseInfo = account.getLicenseInfo();
    if (licenseInfo == null) {
      if (!DeployMode.isOnPrem(deployMode)) {
        throw new InvalidRequestException("Invalid / Null license info", USER);
      } else {
        return;
      }
    }

    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    byte[] encryptedLicenseInfo = getEncryptedLicenseInfo(licenseInfo);
    account.setEncryptedLicenseInfo(encryptedLicenseInfo);
  }

  public byte[] getEncryptedLicenseInfo(LicenseInfo licenseInfo) {
    if (licenseInfo == null) {
      throw new InvalidRequestException("Invalid / Null license info", USER);
    }

    if (!AccountStatus.isValid(licenseInfo.getAccountStatus())) {
      throw new InvalidRequestException("Invalid / Null license info account status", USER);
    }

    if (!AccountType.isValid(licenseInfo.getAccountType())) {
      throw new InvalidRequestException("Invalid / Null license info account type", USER);
    }

    if (licenseInfo.getAccountType().equals(AccountType.COMMUNITY)) {
      licenseInfo.setExpiryTime(-1L);
    } else {
      int expiryInDays = licenseInfo.getExpireAfterDays();
      if (expiryInDays > 0) {
        licenseInfo.setExpiryTime(getExpiryTime(expiryInDays));
      } else if (licenseInfo.getExpiryTime() <= System.currentTimeMillis()) {
        if (licenseInfo.getAccountType().equals(AccountType.TRIAL)) {
          licenseInfo.setExpiryTime(LicenseUtils.getDefaultTrialExpiryTime());
        } else if (licenseInfo.getAccountType().equals(AccountType.PAID)) {
          licenseInfo.setExpiryTime(LicenseUtils.getDefaultPaidExpiryTime());
        } else if (licenseInfo.getAccountType().equals(AccountType.ESSENTIALS)) {
          licenseInfo.setExpiryTime(LicenseUtils.getDefaultEssentialsExpiryTime());
        }
      }
    }

    if (licenseInfo.getExpiryTime() == 0L) {
      throw new InvalidRequestException("No expiry set. Cannot proceed.", USER);
    }

    if (licenseInfo.getAccountType().equals(AccountType.TRIAL)) {
      licenseInfo.setLicenseUnits(InstanceLimitProvider.defaults(AccountType.TRIAL));
    } else if (licenseInfo.getAccountType().equals(AccountType.COMMUNITY)) {
      licenseInfo.setLicenseUnits(InstanceLimitProvider.defaults(AccountType.COMMUNITY));
    }

    if (licenseInfo.getLicenseUnits() <= 0) {
      throw new InvalidRequestException("Invalid number of license units. Cannot proceed.", USER);
    }

    return EncryptionUtils.encrypt(LicenseUtils.convertToString(licenseInfo).getBytes(StandardCharsets.UTF_8), null);
  }

  public byte[] getEncryptedLicenseInfoForUpdate(LicenseInfo currentLicenseInfo, LicenseInfo newLicenseInfo) {
    if (newLicenseInfo == null) {
      throw new InvalidRequestException("Invalid / Null license info for update", USER);
    }

    if (currentLicenseInfo == null) {
      return getEncryptedLicenseInfo(newLicenseInfo);
    }

    if (isNotEmpty(newLicenseInfo.getAccountStatus())) {
      if (!AccountStatus.isValid(newLicenseInfo.getAccountStatus())) {
        throw new InvalidRequestException("Invalid / Null license info account status", USER);
      }
      currentLicenseInfo.setAccountStatus(newLicenseInfo.getAccountStatus());
    }

    int resetLicenseUnitsCount = 0;
    long resetExpiryTime = 0;
    if (isNotEmpty(newLicenseInfo.getAccountType())) {
      if (!AccountType.isValid(newLicenseInfo.getAccountType())) {
        throw new InvalidRequestException("Invalid / Null license info account type", USER);
      }

      if (isNotEmpty(newLicenseInfo.getAccountType())
          && !currentLicenseInfo.getAccountType().equals(newLicenseInfo.getAccountType())) {
        if (AccountType.TRIAL.equals(newLicenseInfo.getAccountType())) {
          resetLicenseUnitsCount = InstanceLimitProvider.defaults(AccountType.TRIAL);
          resetExpiryTime = LicenseUtils.getDefaultTrialExpiryTime();
        } else if (AccountType.COMMUNITY.equals(newLicenseInfo.getAccountType())) {
          resetLicenseUnitsCount = InstanceLimitProvider.defaults(AccountType.COMMUNITY);
          resetExpiryTime = -1L;
        } else if (AccountType.PAID.equals(newLicenseInfo.getAccountType())) {
          resetExpiryTime = LicenseUtils.getDefaultPaidExpiryTime();
        } else if (AccountType.ESSENTIALS.equals(newLicenseInfo.getAccountType())) {
          resetExpiryTime = LicenseUtils.getDefaultEssentialsExpiryTime();
        }
      }
      currentLicenseInfo.setAccountType(newLicenseInfo.getAccountType());
    }

    if (isEmpty(currentLicenseInfo.getAccountStatus())) {
      throw new InvalidRequestException("Null license info account status. Cannot proceed with update", USER);
    }

    if (isEmpty(currentLicenseInfo.getAccountType())) {
      throw new InvalidRequestException("Null license info account type. Cannot proceed with update", USER);
    }

    if (currentLicenseInfo.getAccountType().equals(AccountType.COMMUNITY)) {
      currentLicenseInfo.setExpiryTime(-1L);
    } else {
      int expiryInDays = newLicenseInfo.getExpireAfterDays();
      if (expiryInDays > 0) {
        currentLicenseInfo.setExpiryTime(getExpiryTime(expiryInDays));
      } else if (newLicenseInfo.getExpiryTime() > 0
          && newLicenseInfo.getExpiryTime() != currentLicenseInfo.getExpiryTime()) {
        currentLicenseInfo.setExpiryTime(newLicenseInfo.getExpiryTime());
      } else {
        if (resetExpiryTime > 0) {
          currentLicenseInfo.setExpiryTime(resetExpiryTime);
        }
      }
    }

    if (currentLicenseInfo.getExpiryTime() == 0L) {
      throw new InvalidRequestException("No expiry set. Cannot proceed with update", USER);
    }

    if (newLicenseInfo.getLicenseUnits() > 0) {
      currentLicenseInfo.setLicenseUnits(newLicenseInfo.getLicenseUnits());
    } else if (resetLicenseUnitsCount > 0) {
      currentLicenseInfo.setLicenseUnits(resetLicenseUnitsCount);
    }

    if (currentLicenseInfo.getLicenseUnits() <= 0) {
      throw new InvalidRequestException("Invalid number of license units. Cannot proceed with update", USER);
    }

    return EncryptionUtils.encrypt(
        LicenseUtils.convertToString(currentLicenseInfo).getBytes(StandardCharsets.UTF_8), null);
  }

  private long getExpiryTime(int numberOfDays) {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.DATE, numberOfDays);
    calendar.set(Calendar.HOUR, 11);
    calendar.set(Calendar.MINUTE, 59);
    calendar.set(Calendar.SECOND, 59);
    calendar.set(Calendar.MILLISECOND, 0);
    calendar.set(Calendar.AM_PM, Calendar.PM);
    return calendar.getTimeInMillis();
  }

  public Account decryptLicenseInfo(Account account, boolean setExpiry) {
    if (account == null) {
      return null;
    }

    byte[] encryptedLicenseInfo = account.getEncryptedLicenseInfo();
    if (isNotEmpty(encryptedLicenseInfo)) {
      byte[] decryptedBytes = EncryptionUtils.decrypt(encryptedLicenseInfo, null);
      if (isNotEmpty(decryptedBytes)) {
        LicenseInfo licenseInfo = LicenseUtils.convertToObject(decryptedBytes, setExpiry);
        account.setLicenseInfo(licenseInfo);
      } else {
        log.error("Error while decrypting license info. Deserialized object is not instance of LicenseInfo");
      }
    }

    return account;
  }

  public String convertToString(LicenseInfo licenseInfo) {
    StringBuilder builder = new StringBuilder();

    if (licenseInfo == null) {
      licenseInfo = new LicenseInfo();
    }

    String accountType = licenseInfo.getAccountType();
    if (isEmpty(accountType)) {
      builder.append("NONE");
    } else {
      builder.append(accountType);
    }
    builder.append('_');

    String accountStatus = licenseInfo.getAccountStatus();
    if (isEmpty(accountStatus)) {
      builder.append("NONE");
    } else {
      builder.append(accountStatus);
    }
    builder.append('_');

    long expiryTime = licenseInfo.getExpiryTime();
    builder.append(expiryTime).append('_');

    int licenseUnits = licenseInfo.getLicenseUnits();
    builder.append(licenseUnits);

    return builder.toString();
  }

  public LicenseInfo convertToObject(byte[] decryptedBytes, boolean checkAndSetDefaultExpiry) {
    if (isEmpty(decryptedBytes)) {
      return null;
    }

    String licenseInfoStr = new String(decryptedBytes, StandardCharsets.UTF_8);

    String[] segments = licenseInfoStr.split("_");
    if (isEmpty(segments)) {
      return null;
    }

    boolean newFormat = segments.length == 4;
    boolean oldFormat = segments.length == 3;

    // The old format didn't have license units, some on-prem accounts might be using old license.
    if (!(oldFormat || newFormat)) {
      return null;
    }

    LicenseInfo licenseInfo = new LicenseInfo();
    String accountType = segments[0];
    String accountStatus = segments[1];
    String expiryTimeString = segments[2];

    if (AccountType.isValid(accountType)) {
      licenseInfo.setAccountType(accountType);
    }

    if (AccountStatus.isValid(accountStatus)) {
      licenseInfo.setAccountStatus(accountStatus);
    }

    long defaultExpiryTime = -1L;
    if (AccountType.TRIAL.equals(accountType)) {
      defaultExpiryTime = getDefaultTrialExpiryTime();
    } else if (AccountType.PAID.equals(accountType)) {
      defaultExpiryTime = getDefaultPaidExpiryTime();
    } else if (AccountType.ESSENTIALS.equals(accountType)) {
      defaultExpiryTime = getDefaultEssentialsExpiryTime();
    }

    long expiryTime;
    if ("DEFAULT".equals(expiryTimeString)) {
      expiryTime = defaultExpiryTime;
    } else {
      try {
        expiryTime = Long.parseLong(expiryTimeString);
        if (checkAndSetDefaultExpiry && expiryTime < System.currentTimeMillis()) {
          expiryTime = defaultExpiryTime;
        }
      } catch (NumberFormatException ex) {
        expiryTime = defaultExpiryTime;
      }
    }

    licenseInfo.setExpiryTime(expiryTime);

    int licenseUnits = 0;
    if (newFormat) {
      String licenseUnitsString = segments[3];
      try {
        licenseUnits = Integer.parseInt(licenseUnitsString);
      } catch (NumberFormatException ex) {
        licenseUnits = 0;
      }
    }

    licenseInfo.setLicenseUnits(licenseUnits);

    return licenseInfo;
  }

  public long getDefaultTrialExpiryTime() {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.DATE, TRIAL_PERIOD);
    calendar.set(Calendar.HOUR, 11);
    calendar.set(Calendar.MINUTE, 59);
    calendar.set(Calendar.SECOND, 59);
    calendar.set(Calendar.MILLISECOND, 0);
    calendar.set(Calendar.AM_PM, Calendar.PM);
    return calendar.getTimeInMillis();
  }

  public long getDefaultPaidExpiryTime() {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.YEAR, PAID_PERIOD_IN_YEARS);
    calendar.set(Calendar.HOUR, 11);
    calendar.set(Calendar.MINUTE, 59);
    calendar.set(Calendar.SECOND, 59);
    calendar.set(Calendar.MILLISECOND, 0);
    calendar.set(Calendar.AM_PM, Calendar.PM);
    return calendar.getTimeInMillis();
  }

  public long getDefaultEssentialsExpiryTime() {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.YEAR, ESSENTIALS_PERIOD_IN_YEARS);
    calendar.set(Calendar.HOUR, 11);
    calendar.set(Calendar.MINUTE, 59);
    calendar.set(Calendar.SECOND, 59);
    calendar.set(Calendar.MILLISECOND, 0);
    calendar.set(Calendar.AM_PM, Calendar.PM);
    return calendar.getTimeInMillis();
  }
}
