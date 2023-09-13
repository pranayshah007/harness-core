/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.dto;

import static io.harness.filter.FilterConstants.TRIGGER_FILTER;

import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.ngtriggers.beans.source.NGTriggerType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("TriggerFilterProperties")
@Schema(name = "TriggerFilterProperties", description = "This contains details of the Trigger Filter")
@JsonTypeName(TRIGGER_FILTER)
public class NGTriggersFilterPropertiesDTO extends FilterPropertiesDTO {
  @Schema(description = "This is the list of the Trigger names on which the filter will be applied.")
  List<String> triggerNames;
  @Schema(description = "This is the list of the Trigger identifiers on which the filter will be applied.")
  List<String> triggerIdentifiers;
  @Schema(description = "This is the list of the Trigger types on which the filter will be applied.")
  List<NGTriggerType> triggerTypes;

  @Override
  public FilterType getFilterType() {
    return FilterType.TRIGGER;
  }
}
