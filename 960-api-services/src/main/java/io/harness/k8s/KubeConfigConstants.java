/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_FIRST_GEN, HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
public interface KubeConfigConstants {
  String KUBERNETES_KUBECONFIG_FILE = "kubeconfig";
  String KUBERNETES_SERVICE_HOST_PROPERTY = "KUBERNETES_SERVICE_HOST";
  String KUBERNETES_SERVICE_PORT_PROPERTY = "KUBERNETES_SERVICE_PORT";
  String KUBERNETES_SERVICE_ACCOUNT_TOKEN_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/token";
  String KUBERNETES_SERVICE_ACCOUNT_CA_CRT_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";
  String KUBERNETES_BACKWARD_COMPATIBILITY_INTERCEPTOR_DISABLE = "kubernetes.backwardsCompatibilityInterceptor.disable";
  String DEFAULT_CLIENT_KEY_PASSPHRASE = "changeit";
  String DEFAULT_CLIENT_KEY_ALGORITHM = "RSA";
  String TOKEN_KEY = "token";
}
