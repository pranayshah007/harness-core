package io.harness.azure.model.kube;

import static io.harness.azure.model.AzureConstants.KUBECFG_API_VERSION;
import static io.harness.azure.model.AzureConstants.KUBECFG_CLUSTER_INFO;
import static io.harness.azure.model.AzureConstants.KUBECFG_COMMAND;
import static io.harness.azure.model.AzureConstants.KUBECFG_INTERACTIVE_MODE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.CDP)
public class Exec {
  @JsonProperty(KUBECFG_API_VERSION) private String apiVersion;
  private String args;
  @JsonProperty(KUBECFG_COMMAND) private String command;
  private String env;
  @JsonProperty(KUBECFG_INTERACTIVE_MODE) private String interactiveMode;
  @JsonProperty(KUBECFG_CLUSTER_INFO) private String provideClusterInfo;
  private String serverId;
  private String clientId;
  private String environment;
  private String tenantId;
}
