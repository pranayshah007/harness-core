/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfraMapping;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.intfc.OverridesApplier;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@OwnedBy(CDC)
@JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
public interface Infrastructure extends StepParameters, PassThroughData, OverridesApplier<Infrastructure> {
  @JsonIgnore InfraMapping getInfraMapping();
  @JsonIgnore String getKind();
  @JsonIgnore ParameterField<String> getConnectorReference();
  default @JsonIgnore List<ParameterField<String>> getConnectorReferences() {
    return new ArrayList<>(Collections.singletonList(getConnectorReference()));
  }
  @JsonIgnore String[] getInfrastructureKeyValues();
  @JsonIgnore boolean isDynamicallyProvisioned();
  @JsonIgnore String getProvisionerStepIdentifier();
}
