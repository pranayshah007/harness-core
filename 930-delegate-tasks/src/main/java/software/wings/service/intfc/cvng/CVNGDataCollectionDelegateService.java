/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.intfc.cvng;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.TaskType;
import software.wings.delegatetasks.DelegateTaskType;

import java.util.List;

public interface CVNGDataCollectionDelegateService {
  @DelegateTaskType(TaskType.GET_DATA_COLLECTION_RESULT)
  String getDataCollectionResult(String accountId, DataCollectionRequest dataCollectionRequest,
      List<List<EncryptedDataDetail>> encryptedDataDetails);
}
