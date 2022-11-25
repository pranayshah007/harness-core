package io.harness.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ActiveServiceInstanceInfoV2 {
  private String serviceIdentifier;
  private String serviceName;
  private String envIdentifier;
  private String envName;
  private String infraIdentifier;
  private String infraName;
  private String clusterIdentifier;
  private String agentIdentifier;
  private String lastPipelineExecutionId;
  private String lastPipelineExecutionName;
  private Long lastDeployedAt;
  private String tag;
  private String displayName;
  private int count;

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ActiveServiceInstanceInfoV2 other = (ActiveServiceInstanceInfoV2) obj;
    if (serviceIdentifier == null) {
      if (other.serviceIdentifier != null)
        return false;
    } else if (!serviceIdentifier.equals(other.serviceIdentifier))
      return false;
    if (serviceName == null) {
      if (other.serviceName != null)
        return false;
    } else if (!serviceName.equals(other.serviceName))
      return false;
    if (envIdentifier == null) {
      if (other.envIdentifier != null)
        return false;
    } else if (!envIdentifier.equals(other.envIdentifier))
      return false;
    if (envName == null) {
      if (other.envName != null)
        return false;
    } else if (!envName.equals(other.envName))
      return false;
    if (infraIdentifier == null) {
      if (other.infraIdentifier != null)
        return false;
    } else if (!infraIdentifier.equals(other.infraIdentifier))
      return false;
    if (infraName == null) {
      if (other.infraName != null)
        return false;
    } else if (!infraName.equals(other.infraName))
      return false;
    if (clusterIdentifier == null) {
      if (other.clusterIdentifier != null)
        return false;
    } else if (!clusterIdentifier.equals(other.clusterIdentifier))
      return false;
    if (agentIdentifier == null) {
      if (other.agentIdentifier != null)
        return false;
    } else if (!agentIdentifier.equals(other.agentIdentifier))
      return false;
    if (lastPipelineExecutionId == null) {
      if (other.lastPipelineExecutionId != null)
        return false;
    } else if (!lastPipelineExecutionId.equals(other.lastPipelineExecutionId))
      return false;
    if (lastPipelineExecutionName == null) {
      if (other.lastPipelineExecutionName != null)
        return false;
    } else if (!lastPipelineExecutionName.equals(other.lastPipelineExecutionName))
      return false;
    if (lastDeployedAt == null) {
      if (other.lastDeployedAt != null)
        return false;
    } else if (!lastDeployedAt.equals(other.lastDeployedAt))
      return false;
    if (tag == null) {
      if (other.tag != null)
        return false;
    } else if (!tag.equals(other.tag))
      return false;
    if (displayName == null) {
      if (other.displayName != null)
        return false;
    } else if (!displayName.equals(other.displayName))
      return false;
    if (!(count == other.count))
      return false;
    return true;
  }
}
