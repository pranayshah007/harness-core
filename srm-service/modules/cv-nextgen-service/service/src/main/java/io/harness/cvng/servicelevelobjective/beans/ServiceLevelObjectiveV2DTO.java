/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import static io.harness.cvng.CVConstants.SLO_SPEC;

import io.harness.cvng.notification.beans.NotificationRuleRefDTO;
import io.harness.cvng.servicelevelobjective.beans.slospec.ServiceLevelObjectiveSpec;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.gitsync.beans.YamlDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AbstractServiceLevelObjective",
    description = "This is the Service Level Objective V2 entity defined in Harness")
public class ServiceLevelObjectiveV2DTO implements YamlDTO {
  @ApiModelProperty(required = true) @EntityIdentifier(allowBlank = true) String orgIdentifier;
  @ApiModelProperty(required = true) @EntityIdentifier(allowBlank = true) String projectIdentifier;
  @ApiModelProperty(required = true) @NotNull @EntityIdentifier String identifier;
  @ApiModelProperty(required = true) @NotNull @NGEntityName String name;
  String description;
  @Size(max = 128) Map<String, String> tags;
  @ApiModelProperty(required = true) @NotNull List<String> userJourneyRefs;
  @Valid @NotNull SLOTargetDTO sloTarget;
  @ApiModelProperty(required = true) @NotNull ServiceLevelObjectiveType type;
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = SLO_SPEC, include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
  @Valid
  @NotNull
  ServiceLevelObjectiveSpec spec;
  @Valid List<NotificationRuleRefDTO> notificationRuleRefs;

  public List<NotificationRuleRefDTO> getNotificationRuleRefs() {
    if (notificationRuleRefs == null) {
      return Collections.emptyList();
    }
    return notificationRuleRefs;
  }

  public List<String> getUserJourneyRefs() {
    if (userJourneyRefs == null) {
      return Collections.emptyList();
    }
    return userJourneyRefs;
  }
}
