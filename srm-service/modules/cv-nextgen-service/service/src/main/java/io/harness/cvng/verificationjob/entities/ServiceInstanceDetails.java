/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.entities;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceInstanceDetails {
  // Determined by customer, whether these instances are valid for this analysis
  private boolean valid;
  private List<String> deployedServiceInstances;
  private List<String> serviceInstancesBeforeDeployment;
  private List<String> serviceInstancesAfterDeployment;
}
