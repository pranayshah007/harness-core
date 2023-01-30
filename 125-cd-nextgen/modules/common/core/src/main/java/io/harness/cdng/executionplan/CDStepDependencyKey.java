/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.executionplan;

public enum CDStepDependencyKey {
  SERVICE,
  INFRASTRUCTURE,
  K8S_ROLL_OUT,
  K8S_BLUE_GREEN,
  K8S_APPLY,
  K8S_SCALE,
  K8S_CANARY,
  K8S_DELETE
}
