/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.observer;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.events.PipelineCreateEvent;
import io.harness.pms.events.PipelineDeleteEvent;
import io.harness.pms.events.PipelineUpdateEvent;

@OwnedBy(PIPELINE)
public interface PipelineActionObserver {
  default void onDelete(PipelineDeleteEvent pipelineDeleteEvent) {
    // do nothing
  }

  default void onUpdate(PipelineUpdateEvent pipelineUpdateEvent) {
    // do nothing
  }

  default void onCreate(PipelineCreateEvent pipelineCreateEvent) {
    // do Nothing
  }
}
