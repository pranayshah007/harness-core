/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.custom;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.source.TriggerUpdateCount;

import com.mongodb.client.result.DeleteResult;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
public interface NGTriggerRepositoryCustom {
  Page<NGTriggerEntity> findAll(Criteria criteria, Pageable pageable);
  NGTriggerEntity update(Criteria criteria, NGTriggerEntity ngTriggerEntity);
  NGTriggerEntity updateValidationStatus(Criteria criteria, NGTriggerEntity ngTriggerEntity);
  NGTriggerEntity updateValidationStatusAndMetadata(Criteria criteria, NGTriggerEntity ngTriggerEntity);
  DeleteResult hardDelete(Criteria criteria);
  TriggerUpdateCount updateTriggerYaml(List<NGTriggerEntity> ngTriggerEntityList);
  boolean updateManyTriggerPollingSubscriptionStatusBySignatures(String accountId, List<String> signatures,
      boolean status, String errorMessage, List<String> versions, Long timestamp);
}
