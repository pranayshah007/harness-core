/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.custom;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import com.mongodb.client.result.DeleteResult;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;

import com.mongodb.client.result.UpdateResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
public interface NGTriggerRepositoryCustom {
  Page<NGTriggerEntity> findAll(Criteria criteria, Pageable pageable);
  NGTriggerEntity update(Criteria criteria, NGTriggerEntity ngTriggerEntity);
  NGTriggerEntity updateValidationStatus(Criteria criteria, NGTriggerEntity ngTriggerEntity);
  NGTriggerEntity updateValidationStatusAndMetadata(Criteria criteria, NGTriggerEntity ngTriggerEntity);
  UpdateResult delete(Criteria criteria);
  DeleteResult hardDelete(Criteria criteria);
}
