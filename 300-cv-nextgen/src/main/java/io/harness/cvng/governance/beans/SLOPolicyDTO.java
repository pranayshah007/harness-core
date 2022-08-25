/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.governance.beans;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.gitsync.beans.YamlDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import javax.validation.Valid;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@ApiModel("SLOPolicy")
@OwnedBy(CV)
public class SLOPolicyDTO implements YamlDTO {
  @JsonProperty("sloErrorBudgetRemainingPercentage") @Valid Double sloErrorBudgetRemainingPercentage;
  @JsonProperty("statusOfMonitoredService") @Valid MonitoredServiceStatus statusOfMonitoredService;
  @JsonProperty("sloMappedToTheirHealthIndicators") @Valid Map<String, SLOHealthIndicator> sloMappedToTheirHealthIndicators;

  public enum MonitoredServiceStatus { CONFIGURED, NOT_CONFIGURED }
}
