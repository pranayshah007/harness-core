/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;
import io.harness.ChangeHandler;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.changehandlers.CustomStageExecutionHandler;
import io.harness.changehandlers.PipelineStageExecutionHandler;
import io.harness.changehandlers.TagsInfoNGCDChangeDataHandler;
import io.harness.execution.stage.StageExecutionEntity;

import com.google.inject.Inject;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
public class PipelineStageExecutionCDCEntity implements CDCEntity<StageExecutionEntity> {
  @Inject private PipelineStageExecutionHandler pipelineStageExecutionHandler;
  @Inject private CustomStageExecutionHandler customStageExecutionHandler;
  @Inject private TagsInfoNGCDChangeDataHandler tagsInfoNGCDChangeDataHandler;

  @Override
  public ChangeHandler getChangeHandler(String handlerClass) {
    if (handlerClass.contentEquals("PipelineStageExecutionHandler")) {
      return pipelineStageExecutionHandler;
    } else if (handlerClass.contentEquals("CustomStageExecutionHandler")) {
      return customStageExecutionHandler;
    } else if (handlerClass.contentEquals("PipelineStageTagsInfoNG")) {
      return tagsInfoNGCDChangeDataHandler;
    }
    return null;
  }

  @Override
  public Class<StageExecutionEntity> getSubscriptionEntity() {
    return StageExecutionEntity.class;
  }
}
