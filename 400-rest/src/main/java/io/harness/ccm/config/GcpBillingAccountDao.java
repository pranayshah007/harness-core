/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.config;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HPersistence.upsertReturnNewOptions;

import static com.google.common.base.Preconditions.checkArgument;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.config.GcpBillingAccount.GcpBillingAccountKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import org.bson.types.ObjectId;

@OwnedBy(CE)
public class GcpBillingAccountDao {
  @Inject private HPersistence persistence;

  public GcpBillingAccount get(String uuid) {
    return persistence.createQuery(GcpBillingAccount.class).field(GcpBillingAccountKeys.uuid).equal(uuid).get();
  }

  public List<GcpBillingAccount> list(String accountId, String organizationSettingId) {
    Query<GcpBillingAccount> query =
        persistence.createQuery(GcpBillingAccount.class).field(GcpBillingAccountKeys.accountId).equal(accountId);
    if (isNotEmpty(organizationSettingId)) {
      query.field(GcpBillingAccountKeys.organizationSettingId).equal(organizationSettingId);
    }
    return query.asList();
  }

  public boolean delete(String accountId, String organizationSettingId) {
    return delete(accountId, organizationSettingId, null);
  }

  public boolean delete(String accountId, String organizationSettingId, String billingAccountId) {
    checkArgument(isNotEmpty(organizationSettingId));
    Query<GcpBillingAccount> query = persistence.createQuery(GcpBillingAccount.class)
                                         .field(GcpBillingAccountKeys.accountId)
                                         .equal(accountId)
                                         .field(GcpBillingAccountKeys.organizationSettingId)
                                         .equal(organizationSettingId);
    if (isNotEmpty(billingAccountId)) {
      query.field(GcpBillingAccountKeys.uuid).equal(new ObjectId(billingAccountId));
    }
    return persistence.delete(query);
  }

  public GcpBillingAccount upsert(GcpBillingAccount billingAccount) {
    Query<GcpBillingAccount> query =
        persistence.createQuery(GcpBillingAccount.class)
            .filter(GcpBillingAccountKeys.accountId, billingAccount.getAccountId())
            .filter(GcpBillingAccountKeys.organizationSettingId, billingAccount.getOrganizationSettingId());

    UpdateOperations<GcpBillingAccount> updateOperations =
        persistence.createUpdateOperations(GcpBillingAccount.class)
            .set(GcpBillingAccountKeys.exportEnabled, billingAccount.isExportEnabled());
    if (null != billingAccount.getGcpBillingAccountId()) {
      updateOperations.set(GcpBillingAccountKeys.gcpBillingAccountId, billingAccount.getGcpBillingAccountId());
    }

    if (null != billingAccount.getGcpBillingAccountName()) {
      updateOperations.set(GcpBillingAccountKeys.gcpBillingAccountName, billingAccount.getGcpBillingAccountName());
    }

    if (null != billingAccount.getBqProjectId()) {
      updateOperations.set(GcpBillingAccountKeys.bqProjectId, billingAccount.getBqProjectId());
    }

    if (null != billingAccount.getBqDatasetId()) {
      updateOperations.set(GcpBillingAccountKeys.bqDatasetId, billingAccount.getBqDatasetId());
    }

    if (null != billingAccount.getBqDataSetRegion()) {
      updateOperations.set(GcpBillingAccountKeys.bqDataSetRegion, billingAccount.getBqDataSetRegion());
    }

    return persistence.upsert(query, updateOperations, upsertReturnNewOptions);
  }

  public void update(String uuid, GcpBillingAccount billingAccount) {
    Query<GcpBillingAccount> query =
        persistence.createQuery(GcpBillingAccount.class).field(GcpBillingAccountKeys.uuid).equal(uuid);
    UpdateOperations<GcpBillingAccount> updateOperations =
        persistence.createUpdateOperations(GcpBillingAccount.class)
            .set(GcpBillingAccountKeys.exportEnabled, billingAccount.isExportEnabled());

    if (null != billingAccount.getGcpBillingAccountId()) {
      updateOperations.set(GcpBillingAccountKeys.gcpBillingAccountId, billingAccount.getGcpBillingAccountId());
    }

    if (null != billingAccount.getGcpBillingAccountName()) {
      updateOperations.set(GcpBillingAccountKeys.gcpBillingAccountName, billingAccount.getGcpBillingAccountName());
    }

    if (null != billingAccount.getBqProjectId()) {
      updateOperations.set(GcpBillingAccountKeys.bqProjectId, billingAccount.getBqProjectId());
    }

    if (null != billingAccount.getBqDatasetId()) {
      updateOperations.set(GcpBillingAccountKeys.bqDatasetId, billingAccount.getBqDatasetId());
    }

    if (null != billingAccount.getBqDataSetRegion()) {
      updateOperations.set(GcpBillingAccountKeys.bqDataSetRegion, billingAccount.getBqDataSetRegion());
    }

    persistence.update(query, updateOperations);
  }

  public String save(GcpBillingAccount billingAccount) {
    return persistence.save(billingAccount);
  }
}
