/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.demo;

import io.harness.cvng.core.entities.demo.CVNGDemoDataIndex;
import io.harness.cvng.core.entities.demo.CVNGDemoDataIndex.cvngDemoDataIndexKeys;
import io.harness.cvng.core.services.api.demo.CVNGDemoDataIndexService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.mongodb.morphia.query.UpdateOperations;
@Singleton
public class CVNGDemoDataIndexServiceImpl implements CVNGDemoDataIndexService {
  @Inject HPersistence hPersistence;
  @Override
  public int readIndexForDemoData(String accountId, String dataCollectionWorkerId, String verificationTaskId) {
    int index = 0;
    CVNGDemoDataIndex cvngDemoDataIndex =
        hPersistence.createQuery(CVNGDemoDataIndex.class)
            .filter(cvngDemoDataIndexKeys.accountId, accountId)
            .filter(cvngDemoDataIndexKeys.dataCollectionWorkerId, dataCollectionWorkerId)
            .filter(cvngDemoDataIndexKeys.verificationTaskId, verificationTaskId)
            .get();
    if (cvngDemoDataIndex != null) {
      index = cvngDemoDataIndex.getLastIndex();
    }
    return index;
  }

  @Override
  public void saveIndexForDemoData(
      String accountId, String dataCollectionWorkerId, String verificationTaskId, int index) {
    UpdateOperations<CVNGDemoDataIndex> updateOperations =
        hPersistence.createUpdateOperations(CVNGDemoDataIndex.class).set(cvngDemoDataIndexKeys.lastIndex, index);
    hPersistence.upsert(hPersistence.createQuery(CVNGDemoDataIndex.class)
                            .filter(cvngDemoDataIndexKeys.accountId, accountId)
                            .filter(cvngDemoDataIndexKeys.dataCollectionWorkerId, dataCollectionWorkerId)
                            .filter(cvngDemoDataIndexKeys.verificationTaskId, verificationTaskId),
        updateOperations);
  }
}
