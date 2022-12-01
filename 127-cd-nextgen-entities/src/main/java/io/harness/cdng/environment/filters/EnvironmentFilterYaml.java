/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.environment.filters;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.environment.BaseFilterYaml;
import io.harness.walktree.visitor.SimpleVisitorHelper;

import org.codehaus.jackson.annotate.JsonProperty;
import org.springframework.data.annotation.TypeAlias;

@SimpleVisitorHelper(helperClass = EnvironmentFilterVisitorHelper.class)
@TypeAlias("environmentFilterYaml")
@RecasterAlias("io.harness.cdng.environment.filters.EnvironmentFilterYaml")
@OwnedBy(HarnessTeam.CDC)
public class EnvironmentFilterYaml extends BaseFilterYaml<EnvironmentFilterYaml.Entity> {
  public enum Entity {
    @JsonProperty("infrastructures") infrastructures,
    @JsonProperty("gitOpsClusters") gitOpsClusters,
  }
}
