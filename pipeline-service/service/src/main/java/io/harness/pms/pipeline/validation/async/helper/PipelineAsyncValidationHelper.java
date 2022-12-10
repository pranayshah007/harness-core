/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.async.helper;

import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent.PipelineValidationEventKeys;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;

import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@UtilityClass
public class PipelineAsyncValidationHelper {
  public String buildFQN(PipelineEntity pipelineEntity, String branch) {
    String accountId = pipelineEntity.getAccountIdentifier();
    String orgIdentifier = pipelineEntity.getOrgIdentifier();
    String projectIdentifier = pipelineEntity.getProjectIdentifier();
    String identifier = pipelineEntity.getIdentifier();
    if (EmptyPredicate.isEmpty(branch)) {
      return accountId + "/" + orgIdentifier + "/" + projectIdentifier + "/" + identifier;
    }
    return accountId + "/" + orgIdentifier + "/" + projectIdentifier + "/" + identifier + "/" + branch;
  }

  public Criteria getCriteriaForUpdate(String uuid) {
    return Criteria.where(PipelineValidationEventKeys.uuid).is(uuid);
  }

  public Update getUpdateOperations(ValidationStatus status, ValidationResult result) {
    Update update = new Update();
    update.set(PipelineValidationEventKeys.status, status);
    update.set(PipelineValidationEventKeys.result, result);
    return update;
  }
}
