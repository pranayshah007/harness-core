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
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class InputMetadata {
  DependencyDetails dependencyDetails;
  List<InputDetailsPerField> inputDetailsPerFieldList;

  public InputMetadata() {
    dependencyDetails = new DependencyDetails();
  }

  @Data
  @AllArgsConstructor
  public class InputDetailsPerField {
    String inputType;
    String internalAPIType;
  }

  public void addInputDetailsPerField(String inputType, String internalAPIType) {
    if (this.inputDetailsPerFieldList == null) {
      this.inputDetailsPerFieldList = new ArrayList<>();
    }
    this.inputDetailsPerFieldList.add(new InputDetailsPerField(inputType, internalAPIType));
  }

  public void addDependencyDetails(DependencyDetails dependencyDetails) {
    if (this.dependencyDetails == null) {
      this.dependencyDetails = new DependencyDetails();
    }
    this.dependencyDetails.addRuntimeInputDependency(dependencyDetails.getRuntimeInputDependencyDetailsList());
    this.dependencyDetails.addFixedValueDependency(dependencyDetails.getFixedValueDependencyDetailsList());
  }
}
