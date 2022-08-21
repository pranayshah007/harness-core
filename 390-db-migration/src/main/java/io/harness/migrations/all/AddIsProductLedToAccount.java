/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;
import io.harness.remote.client.NGRestUtils;

import software.wings.beans.Account;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.GTM)
public class AddIsProductLedToAccount implements Migration {
  public static final String isProductLed = "isProductLed";
  @Inject private WingsPersistence wingsPersistence;
  @Inject private NgLicenseHttpClient ngLicenseHttpClient;

  @Override
  public void migrate() {
    try (HIterator<Account> accounts = new HIterator<>(wingsPersistence.createQuery(Account.class).fetch())) {
      for (Account account : accounts) {
        List<ModuleLicenseDTO> moduleLicenses =
            NGRestUtils.getResponse(ngLicenseHttpClient.getModuleLicenses(account.getUuid()));
        String accountID = account.getUuid();
        if (moduleLicenses.size() == 0) {
          wingsPersistence.updateField(Account.class, accountID, isProductLed, Boolean.TRUE);
        } else {
          boolean salesLedFound = false;
          for (ModuleLicenseDTO moduleLicense : moduleLicenses) {
            if (moduleLicense.getEdition() == Edition.TEAM || moduleLicense.getEdition() == Edition.ENTERPRISE) {
              salesLedFound = true;
              wingsPersistence.updateField(Account.class, accountID, isProductLed, Boolean.FALSE);
              break;
            }
          }
          if (!salesLedFound) {
            wingsPersistence.updateField(Account.class, accountID, isProductLed, Boolean.TRUE);
          }
        }
      }
    }
  }
}
