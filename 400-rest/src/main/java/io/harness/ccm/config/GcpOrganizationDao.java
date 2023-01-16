/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.config;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.persistence.HPersistence.upsertReturnNewOptions;
import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.config.GcpOrganization.GcpOrganizationKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import org.bson.types.ObjectId;

@OwnedBy(CE)
public class GcpOrganizationDao {
  @Inject private HPersistence persistence;

  public GcpOrganization upsert(GcpOrganization gcpOrganization) {
    Query<GcpOrganization> query = persistence.createQuery(GcpOrganization.class)
                                       .filter(GcpOrganizationKeys.accountId, gcpOrganization.getAccountId());
    UpdateOperations<GcpOrganization> updateOperations = persistence.createUpdateOperations(GcpOrganization.class);

    if (null != gcpOrganization.getOrganizationId()) {
      updateOperations.set(GcpOrganizationKeys.organizationId, gcpOrganization.getOrganizationId());
    }

    if (null != gcpOrganization.getOrganizationName()) {
      updateOperations.set(GcpOrganizationKeys.organizationName, gcpOrganization.getOrganizationName());
    }

    if (null != gcpOrganization.getServiceAccountEmail()) {
      updateOperations.set(GcpOrganizationKeys.serviceAccountEmail, gcpOrganization.getServiceAccountEmail());
    }
    return persistence.upsert(query, updateOperations, upsertReturnNewOptions);
  }

  public String save(GcpOrganization organization) {
    return persistence.save(organization);
  }

  public GcpOrganization get(String uuid) {
    return persistence.createQuery(GcpOrganization.class, excludeValidate)
        .filter(GcpOrganizationKeys.uuid, new ObjectId(uuid))
        .get();
  }

  public List<GcpOrganization> list(String accountId) {
    Query<GcpOrganization> query =
        persistence.createQuery(GcpOrganization.class).field(GcpOrganizationKeys.accountId).equal(accountId);
    return query.asList();
  }

  public long count(String accountId) {
    return persistence.createQuery(GcpOrganization.class).field(GcpOrganizationKeys.accountId).equal(accountId).count();
  }

  public boolean delete(String accountId, String uuid) {
    Query<GcpOrganization> query = persistence.createQuery(GcpOrganization.class, excludeValidate)
                                       .field(GcpOrganizationKeys.accountId)
                                       .equal(accountId)
                                       .filter(GcpOrganizationKeys.uuid, new ObjectId(uuid));
    return persistence.delete(query);
  }
}
