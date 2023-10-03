/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.schema.inputs.beans;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DependencyDetails {
  List<RuntimeInputDependencyDetails> runtimeInputDependencyDetailsList;
  List<FixedValueDependencyDetails> fixedValueDependencyDetailsList;

  public void addFixedValueDependency(FixedValueDependencyDetails fixedValueDependencyDetails) {
    if (fixedValueDependencyDetailsList == null) {
      fixedValueDependencyDetailsList = new ArrayList<>();
    }
    fixedValueDependencyDetailsList.add(fixedValueDependencyDetails);
  }

  public void addFixedValueDependency(List<FixedValueDependencyDetails> fixedValueDependencyDetailsList) {
    if (this.fixedValueDependencyDetailsList == null) {
      this.fixedValueDependencyDetailsList = new ArrayList<>();
    }
    this.fixedValueDependencyDetailsList.addAll(fixedValueDependencyDetailsList);
  }

  public void addRuntimeInputDependency(RuntimeInputDependencyDetails runtimeInputDependencyDetails) {
    if (runtimeInputDependencyDetailsList == null) {
      runtimeInputDependencyDetailsList = new ArrayList<>();
    }
    runtimeInputDependencyDetailsList.add(runtimeInputDependencyDetails);
  }

  public void addRuntimeInputDependency(List<RuntimeInputDependencyDetails> runtimeInputDependencyDetailsList) {
    if (this.runtimeInputDependencyDetailsList == null) {
      this.runtimeInputDependencyDetailsList = new ArrayList<>();
    }
    this.runtimeInputDependencyDetailsList.addAll(runtimeInputDependencyDetailsList);
  }
}
