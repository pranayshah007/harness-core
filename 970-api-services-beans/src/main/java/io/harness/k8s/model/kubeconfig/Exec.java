/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model.kubeconfig;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.K8sConstants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.CDP)
public class Exec {
  @JsonProperty(K8sConstants.KUBECFG_API_VERSION) private String apiVersion;
  @JsonProperty(K8sConstants.KUBECFG_ARGS) private List<String> args;
  @JsonProperty(K8sConstants.KUBECFG_COMMAND) private String command;
  @JsonProperty(K8sConstants.KUBECFG_ENV) private String env;
  @JsonProperty(K8sConstants.KUBECFG_INTERACTIVE_MODE) private String interactiveMode;
  @JsonProperty(K8sConstants.KUBECFG_CLUSTER_INFO) private String provideClusterInfo;
  @JsonProperty(K8sConstants.KUBECFG_INSTALL_HINT) private String installHint;
}
