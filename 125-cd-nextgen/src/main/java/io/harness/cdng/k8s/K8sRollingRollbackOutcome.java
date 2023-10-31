/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(CDP)
@Value
@Builder
@TypeAlias("k8sRollingRollbackOutcome")
@JsonTypeName("k8sRollingRollbackOutcome")
@RecasterAlias("io.harness.cdng.k8s.K8sRollingRollbackOutcome")
public class K8sRollingRollbackOutcome implements Outcome, ExecutionSweepingOutput {
  Set<KubernetesResourceId> recreatedResourceIds;
  List<String> podIps;
}
