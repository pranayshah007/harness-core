/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.entities;

import io.harness.ccm.commons.entities.batch.BatchJobScheduledData;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.entities.events.PublishedMessage;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

public class BatchProcessingMorphiaClasses {
  public static final Set<Class> classes =
      ImmutableSet.<Class>of(InstanceData.class, PublishedMessage.class, BatchJobScheduledData.class);

  private BatchProcessingMorphiaClasses() {}
}
