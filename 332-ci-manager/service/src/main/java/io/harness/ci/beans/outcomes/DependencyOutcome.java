/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.outcomes;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.dependencies.ServiceDependency;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("dependencyOutcome")
@JsonTypeName("dependencyOutcome")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.outcomes.DependencyOutcome")
public class DependencyOutcome implements Outcome {
  List<ServiceDependency> serviceDependencyList;
}
