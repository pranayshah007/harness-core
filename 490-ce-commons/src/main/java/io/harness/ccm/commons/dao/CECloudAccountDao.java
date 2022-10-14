/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.dao;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.commons.entities.billing.CECloudAccount.CECloudAccountKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.List;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@OwnedBy(CE)
public class CECloudAccountDao {
  private final HPersistence hPersistence;

  @Inject
  public CECloudAccountDao(HPersistence hPersistence) {
    this.hPersistence = hPersistence;
  }

  public boolean deleteAccount(String uuid) {
    return hPersistence.delete(CECloudAccount.class, uuid);
  }

  public boolean create(CECloudAccount ceCloudAccount) {
    return hPersistence.save(ceCloudAccount) != null;
  }

  public String upsert(CECloudAccount ceCloudAccount) {
    Query<CECloudAccount> query = hPersistence.createQuery(CECloudAccount.class)
                                      .filter(CECloudAccountKeys.accountId, ceCloudAccount.getAccountId())
                                      .filter(CECloudAccountKeys.infraAccountId, ceCloudAccount.getInfraAccountId());

    UpdateOperations<CECloudAccount> updateOperations =
        hPersistence.createUpdateOperations(CECloudAccount.class)
            .set(CECloudAccountKeys.accountId, ceCloudAccount.getAccountId())
            .set(CECloudAccountKeys.infraAccountId, ceCloudAccount.getInfraAccountId())
            .set(CECloudAccountKeys.accountArn, ceCloudAccount.getAccountArn())
            .set(CECloudAccountKeys.accountName, ceCloudAccount.getAccountName())
            .set(CECloudAccountKeys.infraMasterAccountId, ceCloudAccount.getInfraMasterAccountId())
            .set(CECloudAccountKeys.accountStatus, ceCloudAccount.getAccountStatus())
            .set(CECloudAccountKeys.masterAccountSettingId, ceCloudAccount.getMasterAccountSettingId())
            .set(CECloudAccountKeys.awsCrossAccountAttributes, ceCloudAccount.getAwsCrossAccountAttributes());
    return hPersistence.upsert(query, updateOperations, HPersistence.upsertReturnNewOptions).getUuid();
  }

  public List<CECloudAccount> getByMasterAccountId(String accountId, String settingId, String infraMasterAccountId) {
    return hPersistence.createQuery(CECloudAccount.class)
        .field(CECloudAccountKeys.accountId)
        .equal(accountId)
        .field(CECloudAccountKeys.infraMasterAccountId)
        .equal(infraMasterAccountId)
        .field(CECloudAccountKeys.masterAccountSettingId)
        .equal(settingId)
        .asList();
  }

  public List<CECloudAccount> getByAWSAccountId(String harnessAccountId) {
    return hPersistence.createQuery(CECloudAccount.class)
        .field(CECloudAccountKeys.accountId)
        .equal(harnessAccountId)
        .asList();
  }

  public List<CECloudAccount> getBySettingId(String harnessAccountId, String masterAccountSettingId) {
    return hPersistence.createQuery(CECloudAccount.class)
        .field(CECloudAccountKeys.accountId)
        .equal(harnessAccountId)
        .field(CECloudAccountKeys.masterAccountSettingId)
        .equal(masterAccountSettingId)
        .asList();
  }

  public List<CECloudAccount> getByInfraAccountId(List<String> accountIds, String harnessAccountId) {
    return hPersistence.createQuery(CECloudAccount.class)
        .field(CECloudAccountKeys.accountId)
        .equal(harnessAccountId)
        .field(CECloudAccountKeys.infraAccountId)
        .in(accountIds)
        .asList();
  }

  public List<CECloudAccount> getByFilterAccountName(String filterAccountName, String harnessAccountId) {
    return hPersistence.createQuery(CECloudAccount.class)
        .field(CECloudAccountKeys.accountId)
        .equal(harnessAccountId)
        .field(CECloudAccountKeys.accountName)
        .containsIgnoreCase(filterAccountName)
        .asList();
  }
}
