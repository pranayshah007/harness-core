/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.changeSourceSpec;

import io.harness.cvng.beans.change.ChangeSourceType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel(description = "This is the change Source entity defined in Harness",
    subTypes = {HarnessCDCurrentGenChangeSourceSpec.class, PagerDutyChangeSourceSpec.class,
        KubernetesChangeSourceSpec.class, CustomChangeSourceSpec.class})
public abstract class ChangeSourceSpec {
  @JsonIgnore public abstract ChangeSourceType getType();
  @JsonIgnore public abstract boolean connectorPresent();
}
