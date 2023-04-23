/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;

import com.mongodb.BulkWriteResult;
import java.util.List;

public interface SRMPersistence extends HPersistence {
  <T extends PersistentEntity> BulkWriteResult upsertBatch(Class<T> cls, List<T> entities, List<String> excludeFields)
      throws IllegalAccessException;
}
