/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.ChangeHandler;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.changehandlers.CDStageExecutionHandler;
import io.harness.changehandlers.CDStageHelmManifestInfoHandler;
import io.harness.changehandlers.StageExecutionHandler;
import io.harness.changehandlers.TagsInfoNGCDChangeDataHandler;

import com.google.inject.Inject;

public class CDStageExecutionCDCEntity implements CDCEntity<StageExecutionInfo> {
  @Inject private CDStageExecutionHandler cdStageExecutionHandler;
  @Inject private CDStageHelmManifestInfoHandler cdStageHelmManifestInfoHandler;
  @Inject private StageExecutionHandler stageExecutionHandler;
  @Inject private TagsInfoNGCDChangeDataHandler tagsInfoNGCDChangeDataHandler;

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
    }
    return null;
  }

  @Override
  public Class<StageExecutionInfo> getSubscriptionEntity() {
    return StageExecutionInfo.class;
  }
}
