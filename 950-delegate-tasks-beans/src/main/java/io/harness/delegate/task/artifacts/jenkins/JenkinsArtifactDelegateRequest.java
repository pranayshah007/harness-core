/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.jenkins;

import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;
import static io.harness.delegate.task.artifacts.ArtifactSourceType.JENKINS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.sm.states.FilePathAssertionEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@OwnedBy(HarnessTeam.PIPELINE)
public class JenkinsArtifactDelegateRequest implements ArtifactSourceDelegateRequest {
  String buildRegex;
  /** List of buildNumbers/artifactPaths */
  List<String> artifactPaths;
  String connectorRef;
  List<JobDetails> jobDetails;
  String parentJobName;
  String jobName;
  /** DockerHub Connector*/
  JenkinsConnectorDTO jenkinsConnectorDTO;
  /** Encrypted details for decrypting.*/
  List<EncryptedDataDetail> encryptedDataDetails;
  /** Artifact Source type.*/
  ArtifactSourceType sourceType;
  Map<String, String> jobParameter;
  boolean unstableStatusAsSuccess;
  boolean captureEnvironmentVariable;
  boolean useConnectorUrlForJobExecution;
  private long timeout;
  private long startTs;
  List<String> delegateSelectors;
  private String queuedBuildUrl;
  private boolean injectEnvVars;
  private String unitName;
  private Map<String, String> metadata;
  private String buildNumber;
  private String buildDisplayName;
  private String buildFullDisplayName;
  private String description;
  private List<FilePathAssertionEntry> filePathAssertionMap;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            encryptedDataDetails, maskingEvaluator));
    populateDelegateSelectorCapability(capabilities, jenkinsConnectorDTO.getDelegateSelectors());
    capabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        jenkinsConnectorDTO.getJenkinsUrl().endsWith("/") ? jenkinsConnectorDTO.getJenkinsUrl()
                                                          : jenkinsConnectorDTO.getJenkinsUrl().concat("/"),
        maskingEvaluator));
    return capabilities;
  }

  @Override
  public ArtifactSourceType getSourceType() {
    return JENKINS;
  }
}
