/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.fullSync;

import io.harness.gitsync.core.fullsync.entity.GitFullSyncJob;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

public interface FullSyncJobRepositoryCustom {
  UpdateResult update(Criteria criteria, Update update);

  GitFullSyncJob find(Criteria criteria);

  DeleteResult deleteAll(Criteria criteria);
}
