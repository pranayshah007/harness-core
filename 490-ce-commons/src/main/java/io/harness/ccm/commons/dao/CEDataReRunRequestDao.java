/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.dao;

import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.ccm.commons.entities.batch.CEDataReRunRequest;
import io.harness.ccm.commons.entities.batch.CEDataReRunRequest.CEDataReRunRequestKeys;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.List;
import org.mongodb.morphia.FindAndModifyOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

public class CEDataReRunRequestDao {
  private final HPersistence persistence;

  @Inject
  public CEDataReRunRequestDao(HPersistence persistence) {
    this.persistence = persistence;
  }

  public List<CEDataReRunRequest> getNotProcessedDataReRunRequests(String accountId, String batchJobType) {
    return persistence.createQuery(CEDataReRunRequest.class, excludeValidate)
        .field(CEDataReRunRequestKeys.accountId)
        .equal(accountId)
        .field(CEDataReRunRequestKeys.batchJobType)
        .equal(batchJobType)
        .field(CEDataReRunRequestKeys.processedRequest)
        .equal(false)
        .order(CEDataReRunRequestKeys.createdAt)
        .asList();
  }

  public CEDataReRunRequest updateRequestStatus(CEDataReRunRequest ceDataReRunRequest) {
    Query<CEDataReRunRequest> query = persistence.createQuery(CEDataReRunRequest.class, excludeValidate)
                                          .filter(CEDataReRunRequestKeys.uuid, ceDataReRunRequest.getUuid());

    UpdateOperations<CEDataReRunRequest> updateOperations =
        persistence.createUpdateOperations(CEDataReRunRequest.class);
    updateOperations.set(CEDataReRunRequestKeys.processedRequest, true);

    FindAndModifyOptions findAndModifyOptions = new FindAndModifyOptions().upsert(true).returnNew(true);
    return persistence.upsert(query, updateOperations, findAndModifyOptions);
  }

  public CEDataReRunRequest updateProcessedTime(String uuid, Instant processedStartAt, Instant processedEndAt) {
    Query<CEDataReRunRequest> query =
        persistence.createQuery(CEDataReRunRequest.class, excludeValidate).filter(CEDataReRunRequestKeys.uuid, uuid);

    UpdateOperations<CEDataReRunRequest> updateOperations =
        persistence.createUpdateOperations(CEDataReRunRequest.class);
    updateOperations.set(CEDataReRunRequestKeys.processedStartAt, processedStartAt);
    updateOperations.set(CEDataReRunRequestKeys.processedEndAt, processedEndAt);

    FindAndModifyOptions findAndModifyOptions = new FindAndModifyOptions().upsert(true).returnNew(true);
    return persistence.upsert(query, updateOperations, findAndModifyOptions);
  }

  public String save(CEDataReRunRequest ceDataReRunRequest) {
    if (ceDataReRunRequest.getAccountId() == null || ceDataReRunRequest.getBatchJobType() == null
        || ceDataReRunRequest.getStartAt() == null || ceDataReRunRequest.getEndAt() == null) {
      throw new InvalidRequestException("Not all required details were entered");
    }
    return persistence.save(ceDataReRunRequest);
  }

  public boolean delete(String uuid) {
    return persistence.delete(CEDataReRunRequest.class, uuid);
  }
}
