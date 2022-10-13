/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.setup.service;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.commons.entities.billing.CECloudAccount.AccountStatus;
import io.harness.ccm.setup.CECloudAccountDao;

import software.wings.beans.SettingAttribute;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CE)
public abstract class CEInfraSetupHandler {
  @Inject protected CECloudAccountDao ceCloudAccountDao;

  public abstract void syncCEInfra(SettingAttribute settingAttribute);

  public abstract boolean updateAccountPermission(CECloudAccount ceCloudAccount);

  @Value
  private static class AccountIdentifierKey {
    String accountId;
    String infraAccountId;
  }

  protected void updateLinkedAccounts(
      String accountId, String settingId, String infraMasterAccountId, List<CECloudAccount> infraAccounts) {
    Map<AccountIdentifierKey, CECloudAccount> infraAccountMap = createAccountMap(infraAccounts);

    List<CECloudAccount> ceExistingAccounts =
        ceCloudAccountDao.getByMasterAccountId(accountId, settingId, infraMasterAccountId);
    Map<AccountIdentifierKey, CECloudAccount> ceExistingAccountMap = createAccountMap(ceExistingAccounts);

    infraAccountMap.forEach((accountIdentifierKey, ceCloudAccount) -> {
      if (!ceExistingAccountMap.containsKey(accountIdentifierKey)) {
        ceCloudAccountDao.create(ceCloudAccount);
      }
    });

    ceExistingAccountMap.forEach((accountIdentifierKey, ceCloudAccount) -> {
      if (!infraAccountMap.containsKey(accountIdentifierKey)) {
        ceCloudAccountDao.deleteAccount(ceCloudAccount.getUuid());
      }
    });
  }

  protected void updateAccountStatus(CECloudAccount ceCloudAccount, boolean verifyAccess) {
    AccountStatus accountStatus = AccountStatus.NOT_CONNECTED;
    if (verifyAccess) {
      accountStatus = AccountStatus.CONNECTED;
    }
    ceCloudAccountDao.updateAccountStatus(ceCloudAccount, accountStatus);
  }

  private Map<AccountIdentifierKey, CECloudAccount> createAccountMap(List<CECloudAccount> cloudAccounts) {
    return cloudAccounts.stream().collect(Collectors.toMap(cloudAccount
        -> new AccountIdentifierKey(cloudAccount.getAccountId(), cloudAccount.getInfraAccountId()),
        Function.identity()));
  }
}
