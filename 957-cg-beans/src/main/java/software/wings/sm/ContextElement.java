/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.context.ContextElementType;

/**
 * Interface for all RepeatElements.
 */
@OwnedBy(CDC)
public interface ContextElement {
  String WORKFLOW = "workflow";
  String DEPLOYMENT_URL = "deploymentUrl";
  String APP = "app";
  String ACCOUNT = "account";
  String SERVICE = "service";
  String SERVICE_TEMPLATE = "serviceTemplate";
  String ENV = "env";
  String HOST = "host";
  String INSTANCE = "instance";
  String PCF_INSTANCE = "pcfinstance";
  String ARTIFACT = "artifact";
  String ROLLBACK_ARTIFACT = "rollbackArtifact";
  String HELM_CHART = "helmChart";
  String SERVICE_VARIABLE = "serviceVariable";
  String ENVIRONMENT_VARIABLE = "environmentVariable";
  String SAFE_DISPLAY_SERVICE_VARIABLE = "safeDisplayServiceVariable";
  String TIMESTAMP_ID = "timestampId";
  String PIPELINE = "pipeline";
  String INFRA = "infra";
  String KUBERNETES = "kubernetes";
  String NAMESPACE = "namespace";
  String KUBECONFIG = "kubeconfig";
  String SHELL = "shell";
  String RANCHER = "rancher";

  ContextElementType getElementType();

  String getUuid();

  String getName();

  ContextElement cloneMin();
}