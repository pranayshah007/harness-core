/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.dao;

import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.ccm.commons.entities.batch.CEDataCleanupRequest;
import io.harness.ccm.commons.entities.batch.CEDataCleanupRequest.CEDataCleanupRequestKeys;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;

public class CEDataCleanupRequestDao {
  private final HPersistence persistence;

  @Inject
  public CEDataCleanupRequestDao(HPersistence persistence) {
    this.persistence = persistence;
  }

  public List<CEDataCleanupRequest> getNotProcessedDataCleanupRequests() {
    return persistence.createQuery(CEDataCleanupRequest.class, excludeValidate)
        .field(CEDataCleanupRequestKeys.processedRequest)
        .equal(false)
        .order(CEDataCleanupRequestKeys.createdAt)
        .asList();
  }

  public CEDataCleanupRequest updateRequestStatus(CEDataCleanupRequest ceDataCleanupRequest) {
    Query<CEDataCleanupRequest> query = persistence.createQuery(CEDataCleanupRequest.class, excludeValidate)
                                            .filter(CEDataCleanupRequestKeys.uuid, ceDataCleanupRequest.getUuid());

    UpdateOperations<CEDataCleanupRequest> updateOperations =
        persistence.createUpdateOperations(CEDataCleanupRequest.class);
    updateOperations.set(CEDataCleanupRequestKeys.processedRequest, true);

    FindAndModifyOptions findAndModifyOptions = new FindAndModifyOptions().upsert(true).returnNew(true);
    return persistence.upsert(query, updateOperations, findAndModifyOptions);
  }

  public String save(CEDataCleanupRequest ceDataCleanupRequest) {
    if (ceDataCleanupRequest.getAccountId() == null || ceDataCleanupRequest.getBatchJobType() == null
        || ceDataCleanupRequest.getStartAt() == null) {
      throw new InvalidRequestException("Not all required details were entered");
    }
    return persistence.save(ceDataCleanupRequest);
  }

  public boolean delete(String uuid) {
    return persistence.delete(CEDataCleanupRequest.class, uuid);
  }
}
