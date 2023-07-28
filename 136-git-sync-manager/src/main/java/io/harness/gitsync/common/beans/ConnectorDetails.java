/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.beans;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;

import java.util.Objects;
import lombok.Builder;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_GITX})
@Value
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class ConnectorDetails {
  Scope scope;
  String connectorRef;
  String repo;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ConnectorDetails that = (ConnectorDetails) o;
    return Objects.equals(getScope(), that.getScope()) && Objects.equals(getConnectorRef(), that.getConnectorRef())
        && Objects.equals(getRepo(), that.getRepo());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getScope(), getConnectorRef(), getRepo());
  }
}
