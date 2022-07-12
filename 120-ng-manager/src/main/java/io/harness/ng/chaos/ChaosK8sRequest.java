/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.chaos;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChaosK8sRequest {
  String accountId;
  String delegateId;
  String k8sConnectorId;
  String k8sManifest;
  String commandType;
  String commandName;
}
