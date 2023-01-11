/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.observer;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.events.PipelineDeleteEvent;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PipelineMetadataService;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
@OwnedBy(PIPELINE)
public class PipelineMetadataObserver implements PipelineActionObserver {
  @Inject PipelineMetadataService pipelineMetadataService;

  @Override
  public void onDelete(PipelineDeleteEvent pipelineDeleteEvent) {
    // Deletes related pipelineMetadata as well
    PipelineEntity pipelineEntity = pipelineDeleteEvent.getPipeline();
    pipelineMetadataService.deletePipelineMetadata(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
        pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier());
  }
}
