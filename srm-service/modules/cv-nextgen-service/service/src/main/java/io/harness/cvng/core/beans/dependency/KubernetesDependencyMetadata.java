/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.dependency;

import static io.harness.cvng.core.beans.dependency.DependencyMetadataType.KUBERNETES;

import io.harness.cvng.beans.change.ChangeSourceType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.collect.Sets;
import de.danielbechler.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("KUBERNETES")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "KubernetesDependencyMetadataKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KubernetesDependencyMetadata extends ServiceDependencyMetadata {
  String namespace;
  String workload;
  List<String> workloads;

  public void setWorkload(String workload) {
    this.workload = workload;
    if (!Objects.isNull(workload)) {
      this.workloads = java.util.Collections.singletonList(workload);
    }
  }
  public String getWorkload() {
    if (workload == null && !Collections.isEmpty(this.workloads)) {
      return this.workloads.get(0);
    }
    return workload;
  }

  public List<String> getWorkloads() {
    if (Collections.isEmpty(workloads) && Objects.nonNull(workload)) {
      return java.util.Collections.singletonList(workload);
    }
    return workloads;
  }

  @Override
  public DependencyMetadataType getType() {
    return KUBERNETES;
  }

  @Override
  public Set<ChangeSourceType> getSupportedChangeSourceTypes() {
    return Sets.newHashSet(ChangeSourceType.KUBERNETES);
  }
}
