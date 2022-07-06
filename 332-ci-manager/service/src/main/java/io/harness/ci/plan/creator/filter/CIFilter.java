/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator.filter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.pipeline.filter.PipelineFilter;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@OwnedBy(HarnessTeam.CI)
public class CIFilter implements PipelineFilter {
  @Singular Set<String> repoNames;

  public void addRepoNames(Set<String> repoNames) {
    if (this.repoNames == null) {
      this.repoNames = new HashSet<>();
    } else if (!(this.repoNames instanceof HashSet)) {
      this.repoNames = new HashSet<>(this.repoNames);
    }

    this.repoNames.addAll(repoNames);
  }
}
