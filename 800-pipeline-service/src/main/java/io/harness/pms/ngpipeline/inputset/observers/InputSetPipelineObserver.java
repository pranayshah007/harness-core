/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.observers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.events.PipelineDeleteEvent;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.observer.PipelineActionObserver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class InputSetPipelineObserver implements PipelineActionObserver {
  @Inject PMSInputSetService inputSetService;

  @Override
  public void onDelete(PipelineDeleteEvent pipelineDeleteEvent) {
    PipelineEntity pipelineEntity = pipelineDeleteEvent.getPipeline();
    inputSetService.deleteInputSetsOnPipelineDeletion(pipelineEntity);
    log.info("All inputSets of pipeline {} deleted", pipelineEntity.getIdentifier());
  }
}
