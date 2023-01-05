/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.mongo.MongoUtils.setUnset;

import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SortOrder.OrderType;
import io.harness.migrations.Migration;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData.ContinuousVerificationExecutionMetaDataKeys;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MigrateCVMetadataApplicationId implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    PageRequest<ContinuousVerificationExecutionMetaData> cvMetadataRequest = PageRequestBuilder.aPageRequest()
                                                                                 .withLimit("999")
                                                                                 .withOffset("0")
                                                                                 .addOrder("createdAt", OrderType.DESC)
                                                                                 .build();
    PageResponse<ContinuousVerificationExecutionMetaData> cvMetadataResponse =
        wingsPersistence.query(ContinuousVerificationExecutionMetaData.class, cvMetadataRequest);
    int previousOffset = 0;
    while (!cvMetadataResponse.isEmpty()) {
      List<ContinuousVerificationExecutionMetaData> cvList = cvMetadataResponse.getResponse();
      for (ContinuousVerificationExecutionMetaData cvMetadata : cvList) {
        if (isEmpty(cvMetadata.getAppId())) {
          cvMetadata.setAppId(cvMetadata.getApplicationId());
          UpdateOperations<ContinuousVerificationExecutionMetaData> op =
              wingsPersistence.createUpdateOperations(ContinuousVerificationExecutionMetaData.class);
          setUnset(op, "appId", cvMetadata.getApplicationId());
          wingsPersistence.update(wingsPersistence.createQuery(ContinuousVerificationExecutionMetaData.class)
                                      .filter(ContinuousVerificationExecutionMetaDataKeys.stateExecutionId,
                                          cvMetadata.getStateExecutionId()),
              op);
        }
      }
      log.info("Updated appId for {} CVExecutionMetadata records", cvList.size());
      previousOffset += cvList.size();
      cvMetadataRequest.setOffset(String.valueOf(previousOffset));
      cvMetadataResponse = wingsPersistence.query(ContinuousVerificationExecutionMetaData.class, cvMetadataRequest);
    }
  }
}
