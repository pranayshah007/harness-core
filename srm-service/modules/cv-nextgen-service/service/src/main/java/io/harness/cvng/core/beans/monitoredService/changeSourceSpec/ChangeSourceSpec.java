/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.changeSourceSpec;

import static io.harness.cvng.CVConstants.DATA_SOURCE_TYPE;

import io.harness.cvng.beans.change.ChangeSourceType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = HarnessCDChangeSourceSpec.class, name = "HarnessCDNextGen")
  , @JsonSubTypes.Type(value = PagerDutyChangeSourceSpec.class, name = "PagerDuty"),
      @JsonSubTypes.Type(value = KubernetesChangeSourceSpec.class, name = "K8sCluster"),
      @JsonSubTypes.Type(value = HarnessCDCurrentGenChangeSourceSpec.class, name = "HarnessCD")
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = DATA_SOURCE_TYPE, include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
public abstract class ChangeSourceSpec {
  @JsonIgnore public abstract ChangeSourceType getType();
  @JsonIgnore public abstract boolean connectorPresent();
}
