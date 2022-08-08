/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.artifact.ArtifactFileMetadata;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.SettingAttribute;
import software.wings.utils.ArtifactType;
import software.wings.utils.MappingUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@Data
@Builder(toBuilder = true)
@ToString(exclude = {"serverSetting", "artifactServerEncryptedDataDetails", "enhancedGcrConnectivityCheckEnabled"})
@Slf4j
public class ArtifactStreamAttributes implements ExecutionCapabilityDemander {
  private String jobName;
  private String imageName;
  private String registryHostName;
  private String subscriptionId;
  private String registryName;
  private String repositoryName;
  private String artifactStreamType;
  private SettingAttribute serverSetting;
  private String groupId;
  private String artifactId;
  private String artifactStreamId;
  private String artifactName;
  private ArtifactType artifactType;
  private String artifactPattern;
  private String region;
  private String repositoryType;
  private boolean metadataOnly;
  private Map<String, List<String>> tags;
  private String platform;
  private Map<String, String> filters;
  private List<EncryptedDataDetail> artifactServerEncryptedDataDetails;
  private Map<String, String> metadata = new HashMap<>();
  private List<ArtifactFileMetadata> artifactFileMetadata = new ArrayList<>();
  private String artifactoryDockerRepositoryServer;
  private String nexusDockerPort;
  private String nexusDockerRegistryUrl;
  private String nexusPackageName;
  private String repositoryFormat;
  private String customScriptTimeout;
  private String accountId;
  private String customArtifactStreamScript;
  private String artifactRoot;
  private String buildNoPath;
  private Map<String, String> artifactAttributes;
  private boolean customAttributeMappingNeeded;
  private String extension;
  private String classifier;
  private String protocolType;
  private String project;
  private String feed;
  private String packageId;
  private String packageName;
  private List<String> artifactPaths;
  private String osType;
  private String imageType;
  private String azureImageGalleryName;
  private String azureResourceGroup;
  private String azureImageDefinition;
  private boolean dockerBasedDeployment;
  // These fields are used only during artifact collection and cleanup.
  private boolean isCollection;
  private Set<String> savedBuildDetailsKeys;
  private boolean enhancedGcrConnectivityCheckEnabled;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    if (registryHostName != null) {
      String urlToValidate = getUrlToValidate();
      executionCapabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
          urlToValidate, maskingEvaluator));
      log.info(
          "ARTIFACT_STREAM_HTTP_CAPABILITY - Http connectivity for {} registry host is going to be validated against URL: {}",
          registryHostName, urlToValidate);
    }
    executionCapabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
        artifactServerEncryptedDataDetails, maskingEvaluator));
    return executionCapabilities;
  }

  private String getUrlToValidate() {
    if (enhancedGcrConnectivityCheckEnabled && registryHostName.endsWith("gcr.io")) {
      return String.format("https://%s/v2/%s/tags/list", registryHostName, imageName);
    }
    return "https://" + registryHostName + (registryHostName.endsWith("/") ? "" : "/");
  }

  public void setMetadata(Map<String, String> metadata) {
    // Doing this to avoid ArtifactMetadataEvaluator & Kryo exception using that
    this.metadata = MappingUtils.safeCopy(metadata);
  }
}
