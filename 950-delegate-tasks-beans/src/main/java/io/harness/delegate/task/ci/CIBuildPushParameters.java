/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.ci;

import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.scm.GitCapabilityHelper;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public abstract class CIBuildPushParameters implements TaskParameters, ExecutionCapabilityDemander {
  private String buildNumber;
  private String detailsUrl;
  private String repo;
  private String owner;
  private String sha;
  private String prNumber;
  private String identifier;
  private String target_url;
  private String key; // TODO it will come via github app connector with encrypted details
  private String installId; // TODO it will come via github app connector with encrypted details
  private String appId; //  TODO it will come via github app connector
  private String token; //  TODO it will come via bitbucket/gitlab  connector details
  private String userName; //  TODO it will come via bitbucket/gitlab connector details
  private ConnectorDetails connectorDetails; // Use connectorDetails to retrieve all information
  private GitSCMType gitSCMType;
  @NotEmpty CIBuildPushTaskType commandType;
  public enum CIBuildPushTaskType { STATUS, CHECKS }

  public CIBuildPushParameters(String buildNumber, String detailsUrl, String repo, String owner, String sha,
      String prNumber, String identifier, String target_url, String key, String installId, String appId, String token,
      String userName, GitSCMType gitSCMType, ConnectorDetails connectorDetails) {
    this.buildNumber = buildNumber;
    this.detailsUrl = detailsUrl;
    this.repo = repo;
    this.owner = owner;
    this.sha = sha;
    this.prNumber = prNumber;
    this.identifier = identifier;
    this.target_url = target_url;
    this.key = key;
    this.installId = installId;
    this.appId = appId;
    this.gitSCMType = gitSCMType;
    this.token = token;
    this.userName = userName;
    this.connectorDetails = connectorDetails;
  }
  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    ConnectorConfigDTO connectorConfigDTO = connectorDetails.getConnectorConfig();
    ScmConnector scmConnector = (ScmConnector) connectorConfigDTO;
    GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(scmConnector);
    return GitCapabilityHelper.fetchRequiredExecutionCapabilitiesSimpleCheck(gitConfigDTO);
  }
}
