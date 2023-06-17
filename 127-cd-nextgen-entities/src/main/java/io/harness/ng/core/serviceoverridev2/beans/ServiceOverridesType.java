/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverridev2.beans;

public enum ServiceOverridesType {
  /*
  Order of these enum must in reverse priority order for override
   */
  ENV_GLOBAL_OVERRIDE,
  ENV_SERVICE_OVERRIDE,
  INFRA_GLOBAL_OVERRIDE,
  INFRA_SERVICE_OVERRIDE,
  CLUSTER_GLOBAL_OVERRIDE,
  CLUSTER_SERVICE_OVERRIDE
}
