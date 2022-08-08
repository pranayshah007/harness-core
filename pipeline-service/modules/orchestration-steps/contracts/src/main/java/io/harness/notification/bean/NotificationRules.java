/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.bean;

import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationRules {
  String name;
  boolean enabled;

  List<PipelineEvent> pipelineEvents;

  @ApiModelProperty(dataType = "io.harness.notification.bean.NotificationChannelWrapper")
  @JsonProperty("notificationMethod")
  ParameterField<NotificationChannelWrapper> notificationChannelWrapper;
}
