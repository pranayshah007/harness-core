/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import io.harness.cvng.notification.beans.NotificationRuleRefDTO;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.gitsync.beans.YamlDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.apache.commons.collections4.CollectionUtils;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceLevelObjectiveDTO implements YamlDTO {
  @ApiModelProperty(required = true) @NotNull @EntityIdentifier String orgIdentifier;
  @ApiModelProperty(required = true) @NotNull @EntityIdentifier String projectIdentifier;
  @ApiModelProperty(required = true) @NotNull @EntityIdentifier String identifier;
  @ApiModelProperty(required = true) @NotNull @NGEntityName String name;
  String description;
  @Size(max = 128) Map<String, String> tags;
  @ApiModelProperty(required = true) @NotNull String userJourneyRef;
  @ApiModelProperty(required = true) @NotNull String monitoredServiceRef;
  @ApiModelProperty(required = true) @NotNull String healthSourceRef;
  ServiceLevelIndicatorType type;
  @Valid @NotNull List<ServiceLevelIndicatorDTO> serviceLevelIndicators;
  @Valid @NotNull SLOTarget target;
  List<NotificationRuleRefDTO> notificationRuleRefs;

  public ServiceLevelIndicatorType getType() {
    if (type == null && CollectionUtils.isNotEmpty(serviceLevelIndicators)) {
      return serviceLevelIndicators.get(0).getType();
    }
    return type;
  }

  public List<NotificationRuleRefDTO> getNotificationRuleRefs() {
    if (notificationRuleRefs == null) {
      return Collections.emptyList();
    }
    return notificationRuleRefs;
  }
}
