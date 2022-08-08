/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.variables;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.plan.YamlProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
@Schema(name = "VariableMergeServiceResponse", description = "This contains Pipeline YAML with the version.")
public class VariableMergeServiceResponse {
  String yaml;
  Map<String, VariableResponseMapValue> metadataMap;
  List<String> errorResponses;
  // List of all the expressions registered by other services
  List<ServiceExpressionProperties> serviceExpressionPropertiesList;

  @Data
  @Builder
  public static class VariableResponseMapValue {
    YamlProperties yamlProperties;
    // Deprecated
    YamlOutputProperties yamlOutputProperties;
    // these properties when shown in variables screen, are not editable
    YamlExtraProperties yamlExtraProperties;
  }

  @Data
  @Builder
  public static class ServiceExpressionProperties {
    String serviceName;
    String expression;
  }
}
