/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.beans.errortracking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Scorecard {
  String organizationIdentifier;
  String accountIdentifier;
  String projectIdentifier;
  String serviceIdentifier;
  String versionIdentifier;
  String environmentIdentifier;
  Integer criticalHitCountThreshold;
  Integer uniqueHitCountThreshold;
  Integer newHitCountThreshold;
  Integer hitCountThreshold;
  NewEventDefinition newEventDefinition;
  List<String> criticalExceptions = new ArrayList<>();
  Integer criticalHitCount = 0;
  Integer uniqueHitCount = 0;
  Integer newHitCount = 0;
  Integer resurfacedHitCount = 0;
  Integer hitCount = 0;
  EventType eventType;
  List<Scorecard> children;
  List<InvocationSummary> events;
}
