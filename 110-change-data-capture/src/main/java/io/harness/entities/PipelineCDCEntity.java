/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.ChangeHandler;
import io.harness.changehandlers.PipelinesChangeDataHandler;
import io.harness.changehandlers.TagsInfoCDChangeDataHandler;
import io.harness.pms.pipeline.PipelineEntity;

import com.google.inject.Inject;

public class PipelineCDCEntity implements CDCEntity<PipelineEntity> {
  @Inject private TagsInfoCDChangeDataHandler tagsInfoCDChangeDataHandler;
  @Inject private PipelinesChangeDataHandler pipelinesChangeDataHandler;

  @Override
  public ChangeHandler getChangeHandler(String handlerClass) {
    if (handlerClass.contentEquals("TagsInfoCD")) {
      return tagsInfoCDChangeDataHandler;
    } else if (handlerClass.contentEquals("Pipelines")) {
      return pipelinesChangeDataHandler;
    }
    return null;
  }

  @Override
  public Class<PipelineEntity> getSubscriptionEntity() {
    return PipelineEntity.class;
  }
}
