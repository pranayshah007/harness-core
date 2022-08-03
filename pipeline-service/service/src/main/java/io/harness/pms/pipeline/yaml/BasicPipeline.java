/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.yaml;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.WRAPPER_OBJECT;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.notification.bean.NotificationRules;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@OwnedBy(PIPELINE)
@Data
@Builder
@FieldNameConstants(innerTypeName = "BasicPipelineKeys")
@JsonTypeInfo(use = NAME, include = WRAPPER_OBJECT)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonTypeName("pipeline")
public class BasicPipeline {
  @EntityName String name;
  @EntityIdentifier String identifier;

  String description;
  Map<String, String> tags;
  List<NotificationRules> notificationRules;

  String orgIdentifier;
  String projectIdentifier;

  boolean allowStageExecutions;
}
