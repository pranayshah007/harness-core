/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.cdng.sample.cd.creator.filters;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.pipeline.filter.PipelineFilter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class CdFilter implements PipelineFilter {
  @Singular Set<String> deploymentTypes;
  @Singular Set<String> environmentNames;
  @Singular Set<String> serviceNames;
  @Singular Set<String> infrastructureTypes;

  public void addDeploymentTypes(Set<String> deploymentTypes) {
    if (this.deploymentTypes == null) {
      this.deploymentTypes = new HashSet<>();
    } else if (!(this.deploymentTypes instanceof HashSet)) {
      this.deploymentTypes = new HashSet<>(this.deploymentTypes);
    }

    this.deploymentTypes.addAll(deploymentTypes);
  }

  public void addInfrastructureTypes(Set<String> infrastructureTypes) {
    if (this.infrastructureTypes == null) {
      this.infrastructureTypes = new HashSet<>();
    } else if (!(this.infrastructureTypes instanceof HashSet)) {
      this.infrastructureTypes = new HashSet<>(this.infrastructureTypes);
    }
    this.infrastructureTypes.addAll(infrastructureTypes);
  }
  public void addServiceNames(Set<String> serviceNames) {
    if (this.serviceNames == null) {
      this.serviceNames = new HashSet<>();
    } else if (!(this.serviceNames instanceof HashSet)) {
      this.serviceNames = new HashSet<>(this.serviceNames);
    }

    this.serviceNames.addAll(serviceNames);
  }

  public void addEnvironmentNames(Set<String> environmentNames) {
    if (this.environmentNames == null) {
      this.environmentNames = new HashSet<>();
    } else if (!(this.environmentNames instanceof HashSet)) {
      this.environmentNames = new HashSet<>(this.environmentNames);
    }

    this.environmentNames.addAll(environmentNames);
  }
}
