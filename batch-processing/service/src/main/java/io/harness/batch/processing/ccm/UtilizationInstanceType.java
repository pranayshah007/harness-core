/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.ccm;

public class UtilizationInstanceType {
  public static final String ECS_CLUSTER = "ECS_CLUSTER";
  public static final String ECS_SERVICE = "ECS_SERVICE";
  public static final String K8S_POD = "K8S_POD";
  public static final String K8S_NODE = "K8S_NODE";
  public static final String K8S_PV = "K8S_PV";

  private UtilizationInstanceType() {}
}
