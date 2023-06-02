/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.beans;

import static io.harness.cvng.CVConstants.DOWNTIME_SPEC_TYPE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = OnetimeDowntimeSpec.class, name = "Onetime")
  , @JsonSubTypes.Type(value = RecurringDowntimeSpec.class, name = "Recurring"),
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = DOWNTIME_SPEC_TYPE, include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
@EqualsAndHashCode()
public abstract class DowntimeSpec {
  @ApiModelProperty(required = true) @NotNull String timezone;
  @Deprecated long startTime;
  String startDateTime;

  @JsonIgnore public abstract DowntimeType getType();
}
