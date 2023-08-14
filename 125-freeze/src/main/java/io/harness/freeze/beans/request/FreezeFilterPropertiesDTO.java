/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.beans.request;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.freeze.beans.FreezeStatus;

import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_DEPLOYMENT_FREEZE})
@Data
public class FreezeFilterPropertiesDTO {
  @Parameter(description = "List of freezeIdentifiers") List<String> freezeIdentifiers;
  @Parameter(
      description =
          "Specifies the sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
  List<String> sort;
  FreezeStatus freezeStatus;
  Long startTime;
  Long endTime;
  @Parameter(description = "The word to be searched and included in the list response") String searchTerm;
}
