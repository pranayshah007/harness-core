/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.terraformcloud.model;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDP)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = RunData.class, name = "runs")
  , @JsonSubTypes.Type(value = OrganizationData.class, name = "organizations"),
      @JsonSubTypes.Type(value = WorkspaceData.class, name = "workspaces"),
      @JsonSubTypes.Type(value = PlanData.class, name = "plans"),
      @JsonSubTypes.Type(value = ApplyData.class, name = "applies"),
      @JsonSubTypes.Type(value = PolicyCheckData.class, name = "policy-checks"),
      @JsonSubTypes.Type(value = StateVersionOutputData.class, name = "state-version-outputs")
})
@JsonIgnoreProperties(ignoreUnknown = true)
@lombok.Data
public abstract class Data {
  private String type;
  private String id;
  Map<String, Relationship> relationships;
  JsonNode links;
}
