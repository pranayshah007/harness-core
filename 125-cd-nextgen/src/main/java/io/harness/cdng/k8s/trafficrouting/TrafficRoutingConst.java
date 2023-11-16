/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s.trafficrouting;

public interface TrafficRoutingConst {
  String SMI = "smi";
  String ISTIO = "istio";
  String HTTP = "http";
  String URI = "uri";
  String METHOD = "method";
  String HEADER = "header";
  String PORT = "port";
  String SCHEME = "scheme";
  String AUTHORITY = "authority";
}
