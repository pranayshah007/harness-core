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
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.changehandlers.CDStageExecutionHandler;
import io.harness.changehandlers.CDStageHelmManifestInfoHandler;
import io.harness.changehandlers.CustomStageExecutionHandler;
import io.harness.changehandlers.StageExecutionHandler;
import io.harness.changehandlers.TagsInfoNGCDChangeDataHandler;

import com.google.inject.Inject;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
public class CDStageExecutionCDCEntity implements CDCEntity<StageExecutionInfo> {
  @Inject private CDStageExecutionHandler cdStageExecutionHandler;
  @Inject private CDStageHelmManifestInfoHandler cdStageHelmManifestInfoHandler;
  @Inject private StageExecutionHandler stageExecutionHandler;
  @Inject private TagsInfoNGCDChangeDataHandler tagsInfoNGCDChangeDataHandler;
  @Inject private CustomStageExecutionHandler customStageExecutionHandler;

  @Override
  public ChangeHandler getChangeHandler(String handlerClass) {
    if (handlerClass.contentEquals("CDStageExecutionHandler")) {
      return cdStageExecutionHandler;
    } else if (handlerClass.contentEquals("StageExecutionHandler")) {
      return stageExecutionHandler;
    } else if (handlerClass.contentEquals("StageTagsInfoNGCD")) {
      return tagsInfoNGCDChangeDataHandler;
    } else if (handlerClass.contentEquals("CDStageHelmManifestInfoHandler")) {
      return cdStageHelmManifestInfoHandler;
    } else if (handlerClass.contentEquals("CustomStageExecutionHandler")) {
      return customStageExecutionHandler;
    }
    return null;
  }

  @Override
  public Class<StageExecutionInfo> getSubscriptionEntity() {
    return StageExecutionInfo.class;
  }
}
