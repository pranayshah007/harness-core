/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.visitor.helpers.deploymentstage;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.visitor.helpers.deploymentstage.validator.StageValidatorFactory;
import io.harness.cdng.visitor.helpers.deploymentstage.validator.StageValidatorHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractor;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@OwnedBy(CDC)
public class DeploymentStageVisitorHelper implements ConfigValidator, EntityReferenceExtractor {
  @Inject StageValidatorFactory stageValidatorFactory;
  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // Nothing to validate
  }

  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return DeploymentStageConfig.builder().build();
  }
  @Override
  public Set<EntityDetailProtoDTO> addReference(Object object, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, Object> contextMap) {
    StageValidatorHelper stageValidationHelper = stageValidatorFactory.getStageValidationHelper(object);
    stageValidationHelper.validate(object, accountIdentifier, orgIdentifier, projectIdentifier);
    return Collections.emptySet();
  }
}
