/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.datadeletion;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Getter;

@OwnedBy(HarnessTeam.CE)
public enum DataDeletionBucket {
  AWS_DELETION(1),
  AZURE_DELETION(2),
  GCP_DELETION(3),
  DKRON_DELETION(4),
  MONGO_EVENTS_DB_DELETION(5),
  TIMESCALE_DB_DELETION(6),
  AUTOSTOPPING_DELETION(7),
  AUTOCUD_DELETION(8);

  @Getter private final Integer priorityOrder;

  DataDeletionBucket(Integer priorityOrder) {
    this.priorityOrder = priorityOrder;
  }
}
