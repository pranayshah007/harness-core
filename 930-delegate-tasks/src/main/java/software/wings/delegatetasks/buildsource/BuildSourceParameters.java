/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.buildsource;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.artifact.ArtifactStreamType.ACR;
import static software.wings.beans.artifact.ArtifactStreamType.AZURE_ARTIFACTS;
import static software.wings.beans.artifact.ArtifactStreamType.GCR;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.settings.SettingValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDC)
@Data
@Builder
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class BuildSourceParameters implements TaskParameters, ExecutionCapabilityDemander {
  public enum BuildSourceRequestType { GET_BUILDS, GET_LAST_SUCCESSFUL_BUILD, GET_BUILD }

  @NotNull private BuildSourceRequestType buildSourceRequestType;
  @NotEmpty private String accountId;
  @NotEmpty private String appId;
  @NotNull private SettingValue settingValue;
  @NotNull private ArtifactStreamAttributes artifactStreamAttributes;
  @NotNull private List<EncryptedDataDetail> encryptedDataDetails;
  @NotEmpty private String artifactStreamType;
  private String artifactStreamId;
  private int limit;
  private boolean shouldFetchSecretFromCache;
  private BuildCollectParameters buildCollectParameters;

  // These fields are used only during artifact collection and cleanup.
  private boolean isCollection;
  // Unique key representing build numbers already present in the DB. It stores different things for different artifact
  // stream types like buildNo, revision or artifactPath.
  private Set<String> savedBuildDetailsKeys;

  private boolean timeoutSupported;
  private Integer timeout;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    if (settingValue == null) {
      return emptyList();
    }

    List<ExecutionCapability> executionCapabilities = getExecutionCapabilities(maskingEvaluator);
    if (BuildSourceRequestType.GET_BUILD.equals(buildSourceRequestType) && buildCollectParameters != null
        && isNotEmpty(buildCollectParameters.getDelegateSelectors())) {
      if (executionCapabilities == null) {
        executionCapabilities = new ArrayList<>();
      } else {
        executionCapabilities = new ArrayList<>(executionCapabilities);
      }
      executionCapabilities.add(
          SelectorCapability.builder().selectors(buildCollectParameters.getDelegateSelectors()).build());
    }
    return executionCapabilities;
  }

  private List<ExecutionCapability> getExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    switch (settingValue.getSettingType()) {
      case JENKINS:
      case BAMBOO:
      case DOCKER:
      case NEXUS:
      case ARTIFACTORY:
        return settingValue.fetchRequiredExecutionCapabilities(maskingEvaluator);
      default:
        return getExecutionCapabilitiesFromArtifactStreamType(maskingEvaluator);
    }
  }

  private List<ExecutionCapability> getExecutionCapabilitiesFromArtifactStreamType(
      ExpressionEvaluator maskingEvaluator) {
    if (artifactStreamType.equals(GCR.name())) {
      return artifactStreamAttributes.fetchRequiredExecutionCapabilities(maskingEvaluator);
    } else if (artifactStreamType.equals(AZURE_ARTIFACTS.name())) {
      return settingValue.fetchRequiredExecutionCapabilities(maskingEvaluator);
    } else if (artifactStreamType.equals(ACR.name())) {
      final String default_server = "azure.microsoft.com";
      String loginServer = isNotEmpty(artifactStreamAttributes.getRegistryHostName())
          ? artifactStreamAttributes.getRegistryHostName()
          : default_server;
      return Collections.singletonList(
          HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
              getUrl(loginServer), maskingEvaluator));
    }
    return emptyList();
  }

  private String getUrl(String hostName) {
    return "https://" + hostName + (hostName.endsWith("/") ? "" : "/");
  }
}
