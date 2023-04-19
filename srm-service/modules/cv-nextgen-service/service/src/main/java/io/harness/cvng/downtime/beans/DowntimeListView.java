/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.beans;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@Builder
public class DowntimeListView {
  String identifier;
  String name;
  DowntimeCategory category;
  List<AffectedEntity> affectedEntities;
  String description;
  DowntimeDuration duration;
  DowntimeStatusDetails downtimeStatusDetails;
  boolean enabled;
  LastModified lastModified;
  DowntimeSpecDTO spec;

  int pastOrActiveInstancesCount;

  @Value
  @SuperBuilder
  public static class LastModified {
    String lastModifiedBy;
    long lastModifiedAt;
  }
}
