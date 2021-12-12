package io.harness.delegate.beans.connector;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.EntitySubtype;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(DX)
public enum ConnectorType implements EntitySubtype {
  @JsonProperty("K8sCluster") KUBERNETES_CLUSTER("K8sCluster"),
  @JsonProperty("Git") GIT("Git"),
  @JsonProperty("Splunk") SPLUNK("Splunk"),
  @JsonProperty("AppDynamics") APP_DYNAMICS("AppDynamics"),
  @JsonProperty("Prometheus") PROMETHEUS("Prometheus"),
  @JsonProperty("Dynatrace") DYNATRACE("Dynatrace"),
  @JsonProperty("Vault") VAULT("Vault"),
  @JsonProperty("AzureKeyVault") AZURE_KEY_VAULT("AzureKeyVault"),
  @JsonProperty("DockerRegistry") DOCKER("DockerRegistry"),
  @JsonProperty("Local") LOCAL("Local"),
  @JsonProperty("AwsKms") AWS_KMS("AwsKms"),
  @JsonProperty("GcpKms") GCP_KMS("GcpKms"),
  @JsonProperty("AwsSecretManager") AWS_SECRET_MANAGER("AwsSecretManager"),
  //  @JsonProperty("Cyberark") CYBERARK("Cyberark"),
  //  @JsonProperty("CustomSecretManager") CUSTOM("CustomSecretManager"),
  @JsonProperty("Gcp") GCP("Gcp"),
  @JsonProperty("Aws") AWS("Aws"),
  @JsonProperty("Artifactory") ARTIFACTORY("Artifactory"),
  @JsonProperty("Jira") JIRA("Jira"),
  @JsonProperty("Nexus") NEXUS("Nexus"),
  @JsonProperty("Github") GITHUB("Github"),
  @JsonProperty("Gitlab") GITLAB("Gitlab"),
  @JsonProperty("Bitbucket") BITBUCKET("Bitbucket"),
  @JsonProperty("Codecommit") CODECOMMIT("Codecommit"),
  @JsonProperty("CEAws") CE_AWS("CEAws"),
  @JsonProperty("CEAzure") CE_AZURE("CEAzure"),
  @JsonProperty("GcpCloudCost") GCP_CLOUD_COST("GcpCloudCost"),
  @JsonProperty("CEK8sCluster") CE_KUBERNETES_CLUSTER("CEK8sCluster"),
  @JsonProperty("HttpHelmRepo") HTTP_HELM_REPO("HttpHelmRepo"),
  @JsonProperty("NewRelic") NEW_RELIC("NewRelic"),
  @JsonProperty("Datadog") DATADOG("Datadog"),
  @JsonProperty("SumoLogic") SUMOLOGIC("SumoLogic"),
  @JsonProperty("PagerDuty") PAGER_DUTY("PagerDuty"),
  @JsonProperty("CustomHealth") CUSTOM_HEALTH("CustomHealth"),
  @JsonProperty("ServiceNow") SERVICENOW("ServiceNow");

  private final String displayName;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static ConnectorType getConnectorType(@JsonProperty("type") String displayName) {
    for (ConnectorType connectorType : ConnectorType.values()) {
      if (connectorType.displayName.equalsIgnoreCase(displayName)) {
        return connectorType;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + displayName);
  }

  ConnectorType(String displayName) {
    this.displayName = displayName;
  }

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }

  public static ConnectorType fromString(final String s) {
    return ConnectorType.getConnectorType(s);
  }
}
