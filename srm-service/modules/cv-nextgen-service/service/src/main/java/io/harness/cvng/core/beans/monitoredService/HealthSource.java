/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService;

import static io.harness.cvng.CVConstants.DATA_SOURCE_TYPE;

import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceDeserializer;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceVersion;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NextGenHealthSourceSpec;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.data.validator.EntityIdentifier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@Schema
@JsonDeserialize(using = HealthSourceDeserializer.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@FieldNameConstants(innerTypeName = "HealthSourceKeys")
public class HealthSource {
  @NotEmpty String name;
  @NotEmpty @EntityIdentifier String identifier;
  @JsonProperty(DATA_SOURCE_TYPE) MonitoredServiceDataSourceType type;

  HealthSourceVersion version;

  @Valid
  @NotNull
  @ApiModelProperty(
      dataType = "io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NextGenHealthSourceSpec", name = "spec")
  HealthSourceSpec spec;

  @Value
  @Builder
  public static class CVConfigUpdateResult {
    @Builder.Default List<CVConfig> updated = new ArrayList<>();
    @Builder.Default List<CVConfig> deleted = new ArrayList<>();
    @Builder.Default List<CVConfig> added = new ArrayList<>();
  }

  public HealthSourceVersion getVersion() {
    if (spec instanceof NextGenHealthSourceSpec) {
      return HealthSourceVersion.V2;
    }
    return version;
  }
}
