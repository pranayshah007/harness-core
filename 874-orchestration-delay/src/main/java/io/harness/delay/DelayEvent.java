/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delay;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.queue.Queuable;

import java.util.Date;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.mongodb.morphia.annotations.Entity;

@OwnedBy(CDC)
@Value
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.ALL)
@Entity(value = "delayQueue", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class DelayEvent extends Queuable {
  private String resumeId;
  private Map<String, String> context;

  public DelayEvent(String resumeId, Date earliestGet, Map<String, String> context) {
    super(earliestGet);
    this.resumeId = resumeId;
    this.context = context;
  }
}
