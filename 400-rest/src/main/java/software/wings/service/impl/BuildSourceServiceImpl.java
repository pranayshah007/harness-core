/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.TaskType.GCB;
import static software.wings.beans.artifact.ArtifactStreamType.AMAZON_S3;
import static software.wings.beans.artifact.ArtifactStreamType.AMI;
import static software.wings.beans.artifact.ArtifactStreamType.ARTIFACTORY;
import static software.wings.beans.artifact.ArtifactStreamType.BAMBOO;
import static software.wings.beans.artifact.ArtifactStreamType.CUSTOM;
import static software.wings.beans.artifact.ArtifactStreamType.DOCKER;
import static software.wings.beans.artifact.ArtifactStreamType.ECR;
import static software.wings.beans.artifact.ArtifactStreamType.GCS;
import static software.wings.beans.artifact.ArtifactStreamType.JENKINS;
import static software.wings.beans.artifact.ArtifactStreamType.SMB;
import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;
import static software.wings.service.impl.AssignDelegateServiceImpl.SCOPE_WILDCARD;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureContainerRegistry;
import software.wings.beans.AzureImageDefinition;
import software.wings.beans.AzureImageGallery;
import software.wings.beans.AzureResourceGroup;
import software.wings.beans.GcpConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.SyncTaskContext.SyncTaskContextBuilder;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.GcsArtifactStream;
import software.wings.beans.command.GcbTaskParams;
import software.wings.beans.settings.azureartifacts.AzureArtifactsConfig;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.delegatetasks.GcbDelegateResponse;
import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureDevopsProject;
import software.wings.helpers.ext.gcs.GcsService;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.ArtifactStreamHelper;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.intfc.ArtifactCollectionService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.BuildService;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.artifact.CustomBuildSourceService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.utils.RepositoryFormat;
import software.wings.utils.RepositoryType;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.util.Assert;

@ValidateOnExecution
@Singleton
@TargetModule(_870_CG_ORCHESTRATION)
@OwnedBy(CDC)
@Slf4j
public class BuildSourceServiceImpl implements BuildSourceService {
  @Inject private Map<Class<? extends SettingValue>, Class<? extends BuildService>> buildServiceMap;
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private SettingsService settingsService;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private ServiceClassLocator serviceLocator;
  @Inject private SecretManager secretManager;
  @Inject @Named("AsyncArtifactCollectionService") private ArtifactCollectionService artifactCollectionServiceAsync;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private GcsService gcsService;
  @Inject private CustomBuildSourceService customBuildSourceService;
  @Inject private UsageRestrictionsService usageRestrictionsService;
  @Inject private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Inject private ArtifactStreamHelper artifactStreamHelper;
  @Inject private DelegateServiceImpl delegateService;
  @Inject private ArtifactCollectionUtils artifactCollectionUtils;
  @Inject private ArtifactService artifactService;

  @Override
  public Set<JobDetails> getJobs(String appId, String settingId, String parentJobName) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    List<JobDetails> jobs = getBuildService(settingAttribute, appId)
                                .getJobs(settingValue, encryptedDataDetails, Optional.ofNullable(parentJobName));
    // Sorting the job details by name before returning
    Set<JobDetails> jobDetailsSet = Sets.newTreeSet(Comparator.comparing(JobDetails::getJobName, String::compareTo));
    jobDetailsSet.addAll(jobs);
    return jobDetailsSet;
  }

  @Override
  public String getProject(String appId, String settingId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    if (settingAttribute == null) {
      throw new InvalidRequestException("GCP Cloud provider Settings Attribute is null", USER);
    }
    SettingValue settingValue = settingAttribute.getValue();
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);

    GcpConfig gcpConfig = (GcpConfig) settingValue;
    if (gcpConfig.isUseDelegateSelectors()) {
      return getBuildService(settingAttribute, appId, GCS.name()).getProjectId(gcpConfig);
    }
    return gcsService.getProject(gcpConfig, encryptedDataDetails);
  }

  @Override
  public Map<String, String> getBuckets(String appId, String projectId, String settingId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    return getBuildService(settingAttribute, appId, GCS.name())
        .getBuckets(
            getSettingValue(settingAttribute), projectId, getEncryptedDataDetails((EncryptableSetting) settingValue));
  }

  @Override
  public List<String> getArtifactPathsByStreamType(String appId, String settingId, String streamType) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    return getBuildService(settingAttribute, appId, streamType)
        .getArtifactPathsByStreamType(
            getSettingValue(settingAttribute), getEncryptedDataDetails((EncryptableSetting) settingValue), streamType);
  }

  @Override
  public List<String> getSmbPaths(String appId, String settingId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    return getBuildService(settingAttribute, appId, SMB.name())
        .getSmbPaths(getSettingValue(settingAttribute), getEncryptedDataDetails((EncryptableSetting) settingValue));
  }

  @Override
  public Map<String, String> getPlans(String appId, String settingId, String artifactStreamType) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    return getBuildService(settingAttribute, appId, artifactStreamType)
        .getPlans(getSettingValue(settingAttribute), getEncryptedDataDetails((EncryptableSetting) settingValue));
  }

  @Override
  public Map<String, String> getPlans(
      String appId, String settingId, String serviceId, String artifactStreamType, String repositoryType) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue value = getSettingValue(settingAttribute);
    Service service = serviceResourceService.get(appId, serviceId, false);
    notNullCheck("Service", service);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) value);
    return getBuildService(settingAttribute, appId)
        .getPlans(value, encryptedDataDetails, service.getArtifactType(), repositoryType);
  }

  @Override
  public Set<String> getArtifactPaths(
      String appId, String jobName, String settingId, String groupId, String artifactStreamType) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue value = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) value);
    return Sets.newTreeSet(getBuildService(settingAttribute, appId, artifactStreamType)
                               .getArtifactPaths(jobName, groupId, value, encryptedDataDetails));
  }

  @Override
  public Set<String> getArtifactPaths(String appId, String jobName, String settingId, String groupId,
      String artifactStreamType, String repositoryFormat) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue value = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) value);
    return Sets.newTreeSet(getBuildService(settingAttribute, appId, artifactStreamType)
                               .getArtifactPaths(jobName, groupId, value, encryptedDataDetails, repositoryFormat));
  }

  @Override
  public BuildDetails getBuild(
      String appId, String artifactStreamId, String settingId, Map<String, Object> runtimeValues) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    if (settingAttribute == null) {
      log.warn("Artifact Server {} was deleted of artifactStreamId {}", settingId, artifactStreamId);
      return null;
    }
    SettingValue settingValue = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);

    ArtifactStream artifactStream = getArtifactStream(artifactStreamId);
    artifactStreamHelper.resolveArtifactStreamRuntimeValues(artifactStream, runtimeValues);
    ArtifactStreamAttributes artifactStreamAttributes;
    if (!GLOBAL_APP_ID.equals(appId)) {
      Service service = artifactStreamServiceBindingService.getService(appId, artifactStream.getUuid(), true);
      artifactStreamAttributes = getArtifactStreamAttributes(artifactStream, service);
    } else {
      artifactStreamAttributes = artifactStream.fetchArtifactStreamAttributes(featureFlagService);
    }
    return getBuildService(settingAttribute, appId, artifactStream.getArtifactStreamType())
        .getBuild(appId, artifactStreamAttributes, settingValue, encryptedDataDetails,
            String.valueOf(runtimeValues.get("buildNo")));
  }

  @Override
  public List<BuildDetails> getBuilds(String appId, String artifactStreamId, String settingId) {
    return getBuilds(appId, artifactStreamId, settingId, -1);
  }

  @Override
  public List<BuildDetails> getBuilds(String artifactStreamId, String settingId, int limit) {
    return getBuilds(GLOBAL_APP_ID, artifactStreamId, settingId, limit);
  }

  @Override
  public List<BuildDetails> getBuilds(String appId, String artifactStreamId, String settingId, int limit) {
    return getBuildDetails(appId, artifactStreamId, settingId, limit);
  }

  /**
   * Get New builds only returns new artifacts. Let us assume that a artifact source has v1, v2, v3 & v4 versions.
   * And we have already collected v1, v2 & v3. This method returns only v4.
   * And we had already collected v1, v2, v3 & v4. This method returns only empty list.
   */
  @Override
  public List<BuildDetails> getNewBuilds(String appId, String artifactStreamId, String settingId) {
    return getBuildDetails(appId, artifactStreamId, settingId, -1, true);
  }

  /**
   *
   */
  private List<BuildDetails> getBuildDetails(String appId, String artifactStreamId, String settingId, int limit) {
    return getBuildDetails(appId, artifactStreamId, settingId, limit, false);
  }

  /**
   * The shouldSetSavedBuildKeys flag is used to pass the existing collected artifacts versions to the delegate.
   * Set this to true when trying to do an artifact collection. To get the list of all artifacts then pass false.
   * Checkout method - getNewBuilds
   */
  private List<BuildDetails> getBuildDetails(
      String appId, String artifactStreamId, String settingId, int limit, boolean shouldSetSavedBuildKeys) {
    ArtifactStream artifactStream = getArtifactStream(artifactStreamId);
    if (artifactStream.isArtifactStreamParameterized()) {
      throw new InvalidRequestException("Manually pull artifact not supported for parameterized artifact stream");
    }
    if (CUSTOM.name().equals(artifactStream.getArtifactStreamType())) {
      // Labels not needed for custom artifact source.
      return customBuildSourceService.getBuilds(artifactStreamId);
    }
    SettingAttribute settingAttribute = settingsService.get(settingId);
    if (settingAttribute == null) {
      log.warn("Artifact Server {} was deleted of artifactStreamId {}", settingId, artifactStreamId);
      return new ArrayList<>();
    }
    SettingValue settingValue = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);

    String artifactStreamType = artifactStream.getArtifactStreamType();
    ArtifactStreamAttributes artifactStreamAttributes;
    if (!GLOBAL_APP_ID.equals(appId)) {
      Service service = artifactStreamServiceBindingService.getService(appId, artifactStream.getUuid(), true);
      artifactStreamAttributes = getArtifactStreamAttributes(artifactStream, service);
    } else {
      artifactStreamAttributes = artifactStream.fetchArtifactStreamAttributes(featureFlagService);
    }
    if (GCS.name().equals(artifactStreamType)) {
      limit = (limit != -1) ? limit : 100;
    }
    if (shouldSetSavedBuildKeys) {
      artifactStreamAttributes.setSavedBuildDetailsKeys(
          artifactCollectionUtils.getArtifactsKeys(artifactStream, artifactStreamAttributes));
    }
    return getBuildDetails(appId, limit, settingAttribute, settingValue, encryptedDataDetails, artifactStreamType,
        artifactStreamAttributes);
  }

  private List<BuildDetails> getBuildDetails(String appId, int limit, SettingAttribute settingAttribute,
      SettingValue settingValue, List<EncryptedDataDetail> encryptedDataDetails, String artifactStreamType,
      ArtifactStreamAttributes artifactStreamAttributes) {
    if (limit != -1 && (ARTIFACTORY.name().equals(artifactStreamType) || GCS.name().equals(artifactStreamType))) {
      return getBuildService(settingAttribute, appId, artifactStreamType)
          .getBuilds(appId, artifactStreamAttributes, settingValue, encryptedDataDetails, limit);
    } else if (AMAZON_S3.name().equals(artifactStreamType) || AMI.name().equals(artifactStreamType)) {
      return getBuildService(settingAttribute, appId, artifactStreamType)
          .getBuilds(appId, artifactStreamAttributes, settingValue, encryptedDataDetails);
    } else if (JENKINS.name().equals(artifactStreamType) || BAMBOO.name().equals(artifactStreamType)) {
      if (appId.equals(GLOBAL_APP_ID)) {
        if (limit == -1) {
          limit = 500;
        }
        return getBuildService(settingAttribute, appId)
            .getBuilds(appId, artifactStreamAttributes, settingValue, encryptedDataDetails, limit);
      } else {
        return getBuildService(settingAttribute, appId)
            .getBuilds(appId, artifactStreamAttributes, settingValue, encryptedDataDetails);
      }
    } else {
      return getBuildService(settingAttribute, appId, artifactStreamType)
          .getBuilds(appId, artifactStreamAttributes, settingValue, encryptedDataDetails);
    }
  }

  @Override
  public List<Map<String, String>> getLabels(ArtifactStream artifactStream, List<String> buildNos) {
    String appId = artifactStream.fetchAppId();
    String artifactStreamId = artifactStream.getUuid();
    String settingId = artifactStream.getSettingId();
    // Collect labels for only DOCKER.
    if (!DOCKER.name().equals(artifactStream.getArtifactStreamType())
        && !ECR.name().equals(artifactStream.getArtifactStreamType())
        && !ARTIFACTORY.name().equals(artifactStream.getArtifactStreamType())) {
      return Collections.emptyList();
    }

    SettingAttribute settingAttribute = settingsService.get(settingId);
    if (settingAttribute == null) {
      log.warn("Artifact server: [{}] was deleted for artifact stream: [{}]", settingId, artifactStreamId);
      return Collections.emptyList();
    }
    SettingValue settingValue = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);

    ArtifactStreamAttributes artifactStreamAttributes;
    if (!GLOBAL_APP_ID.equals(appId)) {
      Service service = artifactStreamServiceBindingService.getService(appId, artifactStream.getUuid(), true);
      artifactStreamAttributes = getArtifactStreamAttributes(artifactStream, service);
    } else {
      artifactStreamAttributes = artifactStream.fetchArtifactStreamAttributes(featureFlagService);
    }

    return getBuildService(settingAttribute, appId)
        .getLabels(artifactStreamAttributes, buildNos, settingValue, encryptedDataDetails);
  }

  @Override
  public ArtifactMetaInfo getArtifactMetaInfo(ArtifactStream artifactStream, String buildNo) {
    String appId = artifactStream.fetchAppId();
    String artifactStreamId = artifactStream.getUuid();
    String settingId = artifactStream.getSettingId();
    // Collect metainfo for only DOCKER.
    if (Lists.newArrayList(DOCKER).stream().noneMatch(
            type -> type.name().equals(artifactStream.getArtifactStreamType()))) {
      return new ArtifactMetaInfo();
    }
    SettingAttribute settingAttribute = settingsService.get(settingId);
    if (settingAttribute == null) {
      log.warn("Artifact server: [{}] was deleted for artifact stream: [{}]", settingId, artifactStreamId);
      return new ArtifactMetaInfo();
    }
    SettingValue settingValue = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    Service service = artifactStreamServiceBindingService.getService(appId, artifactStream.getUuid(), true);
    ArtifactStreamAttributes artifactStreamAttributes = getArtifactStreamAttributes(artifactStream, service);
    return getBuildService(settingAttribute, appId)
        .getArtifactMetaInfo(artifactStreamAttributes, buildNo, settingValue, encryptedDataDetails);
  }

  private ArtifactStreamAttributes getArtifactStreamAttributes(ArtifactStream artifactStream, Service service) {
    ArtifactStreamAttributes artifactStreamAttributes =
        artifactStream.fetchArtifactStreamAttributes(featureFlagService);
    artifactStreamAttributes.setArtifactType(service.getArtifactType());
    return artifactStreamAttributes;
  }

  @Override
  public BuildDetails getLastSuccessfulBuild(String appId, String artifactStreamId, String settingId) {
    ArtifactStream artifactStream = getArtifactStream(artifactStreamId);
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    Service service = artifactStreamServiceBindingService.getService(appId, artifactStream.getUuid(), true);
    ArtifactStreamAttributes artifactStreamAttributes = getArtifactStreamAttributes(artifactStream, service);
    if (AMAZON_S3.name().equals(artifactStream.getArtifactStreamType())) {
      return getBuildService(settingAttribute, appId, artifactStream.getArtifactStreamType())
          .getLastSuccessfulBuild(appId, artifactStreamAttributes, settingValue, encryptedDataDetails);
    } else {
      return getBuildService(settingAttribute, appId)
          .getLastSuccessfulBuild(appId, artifactStreamAttributes, settingValue, encryptedDataDetails);
    }
  }

  @Override
  public Set<String> getGroupIds(String appId, String repoType, String settingId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    return Sets.newTreeSet(
        getBuildService(settingAttribute, appId).getGroupIds(repoType, null, settingValue, encryptedDataDetails));
  }

  @Override
  public Set<String> getGroupIds(String appId, String repoType, String settingId, String repositoryFormat) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    return Sets.newTreeSet(getBuildService(settingAttribute, appId)
                               .getGroupIds(repoType, repositoryFormat, settingValue, encryptedDataDetails));
  }

  @Override
  public boolean validateArtifactSource(
      String appId, String settingId, ArtifactStreamAttributes artifactStreamAttributes) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    return getBuildService(settingAttribute, appId, artifactStreamAttributes.getArtifactStreamType())
        .validateArtifactSource(settingValue, encryptedDataDetails, artifactStreamAttributes);
  }

  @Override
  public boolean validateArtifactSource(ArtifactStream artifactStream) {
    return customBuildSourceService.validateArtifactSource(artifactStream);
  }

  @Override
  public void validateAndInferArtifactSource(ArtifactStream artifactStream) {
    SettingAttribute settingAttribute = settingsService.get(artifactStream.getSettingId());
    SettingValue settingValue = getSettingValue(settingAttribute);
    ArtifactStreamAttributes attributes = artifactStream.fetchArtifactStreamAttributes(featureFlagService);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    ArtifactStreamAttributes newAttributes =
        getBuildService(settingAttribute, artifactStream.fetchAppId(), attributes.getArtifactStreamType())
            .validateThenInferAttributes(settingValue, encryptedDataDetails, attributes);
    artifactStream.inferProperties(newAttributes);
  }

  @Override
  public JobDetails getJob(String appId, String settingId, String jobName) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    return getBuildService(settingAttribute, appId).getJob(jobName, settingValue, encryptedDataDetails);
  }

  private SettingValue getSettingValue(SettingAttribute settingAttribute) {
    notNullCheck("Artifact server was deleted", settingAttribute, USER);
    return settingAttribute.getValue();
  }

  @Override
  public BuildService getBuildService(SettingAttribute settingAttribute, String appId) {
    SyncTaskContextBuilder syncTaskContextBuilder =
        SyncTaskContext.builder()
            .accountId(settingAttribute.getAccountId())
            .appId(isBlank(appId) || appId.equals(GLOBAL_APP_ID) ? SCOPE_WILDCARD : appId)
            .timeout(settingAttribute.getValue().getType().equals(SettingVariableTypes.JENKINS.name())
                        || settingAttribute.getValue().getType().equals(SettingVariableTypes.BAMBOO.name())
                    ? 120 * 1000
                    : DEFAULT_SYNC_CALL_TIMEOUT);
    SyncTaskContext syncTaskContext = areDelegateSelectorsRequired(settingAttribute)
        ? appendDelegateSelector(settingAttribute, syncTaskContextBuilder)
        : syncTaskContextBuilder.build();
    return delegateProxyFactory.getV2(buildServiceMap.get(settingAttribute.getValue().getClass()), syncTaskContext);
  }

  @Override
  public Artifact collectArtifact(String appId, String artifactStreamId, BuildDetails buildDetails) {
    if (buildDetails == null) {
      throw new InvalidRequestException("Build details can not null", USER);
    }
    String displayName = buildDetails.getUiDisplayName() == null ? "null" : buildDetails.getUiDisplayName();
    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    if (artifactStream == null) {
      throw new InvalidRequestException("Artifact stream was deleted", USER);
    }
    if (artifactStream.isArtifactStreamParameterized()) {
      throw new InvalidRequestException("Manually pull artifact not supported for parameterized artifact stream");
    }
    log.info(format("Manually pulling for artifact stream: [%s] with artifact: [%s]", artifactStreamId, displayName));
    return artifactCollectionServiceAsync.collectArtifact(artifactStreamId, buildDetails);
  }

  private BuildService getBuildService(SettingAttribute settingAttribute, String appId, String artifactStreamType) {
    if (artifactStreamType == null) {
      return getBuildService(settingAttribute, appId);
    }
    Class<? extends BuildService> buildServiceClass = serviceLocator.getBuildServiceClass(artifactStreamType);
    SyncTaskContextBuilder syncTaskContextBuilder =
        SyncTaskContext.builder()
            .accountId(settingAttribute.getAccountId())
            .appId(isBlank(appId) || appId.equals(GLOBAL_APP_ID) ? SCOPE_WILDCARD : appId)
            .timeout(120 * 1000);
    SyncTaskContext syncTaskContext = areDelegateSelectorsRequired(settingAttribute)
        ? appendDelegateSelector(settingAttribute, syncTaskContextBuilder)
        : syncTaskContextBuilder.build();
    return delegateProxyFactory.getV2(buildServiceClass, syncTaskContext);
  }

  private List<EncryptedDataDetail> getEncryptedDataDetails(EncryptableSetting settingValue) {
    return secretManager.getEncryptionDetails(settingValue, null, null);
  }

  @Override
  public Set<JobDetails> getJobs(String settingId, String parentJobName) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    List<JobDetails> jobs = getBuildService(settingAttribute)
                                .getJobs(settingValue, encryptedDataDetails, Optional.ofNullable(parentJobName));
    // Sorting the job details by name before returning
    Set<JobDetails> jobDetailsSet = Sets.newTreeSet(Comparator.comparing(JobDetails::getJobName, String::compareTo));
    jobDetailsSet.addAll(jobs);
    return jobDetailsSet;
  }

  @Override
  public Set<String> getArtifactPaths(String jobName, String settingId, String groupId, String artifactStreamType) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue value = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) value);
    return Sets.newTreeSet(getBuildService(artifactStreamType, settingAttribute)
                               .getArtifactPaths(jobName, groupId, value, encryptedDataDetails));
  }

  @Override
  public Set<String> getArtifactPathsForRepositoryFormat(@NotEmpty String jobName, @NotEmpty String settingId,
      String groupId, String artifactStreamType, String repositoryFormat) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue value = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) value);
    return Sets.newTreeSet(getBuildService(artifactStreamType, settingAttribute)
                               .getArtifactPaths(jobName, groupId, value, encryptedDataDetails, repositoryFormat));
  }

  @Override
  public BuildService getBuildService(SettingAttribute settingAttribute) {
    SyncTaskContextBuilder syncTaskContextBuilder =
        SyncTaskContext.builder()
            .accountId(settingAttribute.getAccountId())
            .appId(SCOPE_WILDCARD)
            .timeout(settingAttribute.getValue().getType().equals(SettingVariableTypes.JENKINS.name())
                    ? 120 * 1000
                    : DEFAULT_SYNC_CALL_TIMEOUT);
    SyncTaskContext syncTaskContext = areDelegateSelectorsRequired(settingAttribute)
        ? appendDelegateSelector(settingAttribute, syncTaskContextBuilder)
        : syncTaskContextBuilder.build();
    return delegateProxyFactory.getV2(buildServiceMap.get(settingAttribute.getValue().getClass()), syncTaskContext);
  }

  private BuildService getBuildService(String artifactStreamType, SettingAttribute settingAttribute) {
    if (artifactStreamType == null) {
      return getBuildService(settingAttribute);
    }
    Class<? extends BuildService> buildServiceClass = serviceLocator.getBuildServiceClass(artifactStreamType);
    SyncTaskContextBuilder syncTaskContextBuilder =
        SyncTaskContext.builder().accountId(settingAttribute.getAccountId()).timeout(120 * 1000);
    SyncTaskContext syncTaskContext = areDelegateSelectorsRequired(settingAttribute)
        ? appendDelegateSelector(settingAttribute, syncTaskContextBuilder)
        : syncTaskContextBuilder.build();
    return delegateProxyFactory.getV2(buildServiceClass, syncTaskContext);
  }

  @Override
  public BuildDetails getLastSuccessfulBuild(String artifactStreamId, String settingId) {
    ArtifactStream artifactStream = getArtifactStream(artifactStreamId);
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    ArtifactStreamAttributes artifactStreamAttributes =
        artifactStream.fetchArtifactStreamAttributes(featureFlagService);
    if (AMAZON_S3.name().equals(artifactStream.getArtifactStreamType())) {
      return getBuildService(settingAttribute, artifactStream.getAppId(), artifactStream.getArtifactStreamType())
          .getLastSuccessfulBuild(
              artifactStream.fetchAppId(), artifactStreamAttributes, settingValue, encryptedDataDetails);
    } else {
      return getBuildService(settingAttribute, artifactStream.getAppId())
          .getLastSuccessfulBuild(
              artifactStream.fetchAppId(), artifactStreamAttributes, settingValue, encryptedDataDetails);
    }
  }

  private ArtifactStream getArtifactStream(String artifactStreamId) {
    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    notNullCheck("Artifact Stream", artifactStream);
    return artifactStream;
  }

  @Override
  public Artifact collectArtifact(String artifactStreamId, BuildDetails buildDetails) {
    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    notNullCheck("Artifact Stream does not exist ", artifactStream);
    if (GLOBAL_APP_ID.equals(artifactStream.fetchAppId())) {
      usageRestrictionsService.validateUsageRestrictionsOnEntityUpdate(artifactStream.getAccountId(),
          ACCOUNT_MANAGEMENT, settingsService.getUsageRestrictionsForSettingId(artifactStream.getSettingId()),
          settingsService.getUsageRestrictionsForSettingId(artifactStream.getSettingId()), false);
    }
    if (buildDetails == null) {
      throw new InvalidRequestException("Build details can not null", USER);
    }
    return artifactCollectionServiceAsync.collectArtifact(artifactStreamId, buildDetails);
  }

  @Override
  public Map<String, String> getPlans(String settingId, String artifactStreamType) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    return getBuildService(artifactStreamType, settingAttribute)
        .getPlans(getSettingValue(settingAttribute), getEncryptedDataDetails((EncryptableSetting) settingValue));
  }

  @Override
  public Map<String, String> getPlansForRepositoryFormat(
      String settingId, String streamType, RepositoryFormat repositoryFormat) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue value = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) value);
    return getBuildService(settingAttribute).getPlans(value, encryptedDataDetails, repositoryFormat);
  }

  @Override
  public Map<String, String> getPlansForRepositoryType(
      String settingId, String streamType, RepositoryType repositoryType) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue value = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) value);
    return getBuildService(settingAttribute).getPlans(value, encryptedDataDetails, repositoryType);
  }

  @Override
  public Set<String> getGroupIds(String repoType, String settingId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    return Sets.newTreeSet(getBuildService(settingAttribute).getGroupIds(repoType, settingValue, encryptedDataDetails));
  }

  @Override
  public Set<String> getGroupIdsForRepositoryFormat(
      @NotEmpty String jobName, @NotEmpty String settingId, @NotEmpty String repositoryFormat) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    return Sets.newTreeSet(
        getBuildService(settingAttribute).getGroupIds(jobName, repositoryFormat, settingValue, encryptedDataDetails));
  }

  @Override
  public String getProject(String settingId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    if (settingAttribute == null) {
      throw new InvalidRequestException("GCP Cloud provider Settings Attribute is null", USER);
    }
    SettingValue settingValue = settingAttribute.getValue();
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);

    GcpConfig gcpConfig = (GcpConfig) settingValue;
    if (gcpConfig.isUseDelegateSelectors()) {
      return getBuildService(settingAttribute).getProjectId(gcpConfig);
    }
    return gcsService.getProject(gcpConfig, encryptedDataDetails);
  }

  @Override
  public Map<String, String> getBuckets(String projectId, String settingId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    return getBuildService(GCS.name(), settingAttribute)
        .getBuckets(
            getSettingValue(settingAttribute), projectId, getEncryptedDataDetails((EncryptableSetting) settingValue));
  }

  @Override
  public List<String> getSmbPaths(String settingId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    return getBuildService(SMB.name(), settingAttribute)
        .getSmbPaths(getSettingValue(settingAttribute), getEncryptedDataDetails((EncryptableSetting) settingValue));
  }

  @Override
  public List<String> getArtifactPathsByStreamType(String settingId, String streamType) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    return getBuildService(streamType, settingAttribute)
        .getArtifactPathsByStreamType(
            getSettingValue(settingAttribute), getEncryptedDataDetails((EncryptableSetting) settingValue), streamType);
  }

  @Override
  public Set<String> fetchNexusPackageNames(@NotEmpty String appId, @NotEmpty String repositoryName,
      @NotEmpty String repositoryFormat, @NotEmpty String settingId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    return Sets.newTreeSet(getBuildService(settingAttribute, appId)
                               .getGroupIds(repositoryName, repositoryFormat, settingValue, encryptedDataDetails));
  }

  @Override
  public Set<String> fetchNexusPackageNames(
      @NotEmpty String repositoryName, @NotEmpty String repositoryFormat, @NotEmpty String settingId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    return Sets.newTreeSet(getBuildService(settingAttribute)
                               .getGroupIds(repositoryName, repositoryFormat, settingValue, encryptedDataDetails));
  }

  @Override
  public List<AzureDevopsProject> getProjects(String settingId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    if (!(settingValue instanceof AzureArtifactsConfig)) {
      return Collections.emptyList();
    }
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    return getBuildService(settingAttribute).getProjects((AzureArtifactsConfig) settingValue, encryptedDataDetails);
  }

  @Override
  public List<AzureArtifactsFeed> getFeeds(String settingId, String project) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    if (!(settingValue instanceof AzureArtifactsConfig)) {
      return Collections.emptyList();
    }
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    return getBuildService(settingAttribute)
        .getFeeds((AzureArtifactsConfig) settingValue, encryptedDataDetails, project);
  }

  @Override
  public List<AzureArtifactsPackage> getPackages(String settingId, String project, String feed, String protocolType) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    if (!(settingValue instanceof AzureArtifactsConfig)) {
      return Collections.emptyList();
    }
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    return getBuildService(settingAttribute)
        .getPackages((AzureArtifactsConfig) settingValue, encryptedDataDetails, project, feed, protocolType);
  }

  @Override
  public List<String> getGcbTriggers(String settingId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    if (settingAttribute == null) {
      throw new InvalidRequestException("GCP Clo∂ud provider Settings Attribute is null", USER);
    }
    SettingValue settingValue = settingAttribute.getValue();
    if (settingValue == null) {
      throw new InvalidRequestException("GCP Cloud provider Settings Value is null", USER);
    }
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    GcpConfig gcpConfig = (GcpConfig) settingValue;
    GcbDelegateResponse delegateResponseData = null;
    try {
      delegateResponseData = delegateService.executeTaskV2(
          DelegateTask.builder()
              .accountId(gcpConfig.getAccountId())
              .data(TaskData.builder()
                        .async(true)
                        .taskType(GCB.name())
                        .parameters(new Object[] {GcbTaskParams.builder()
                                                      .gcpConfig(gcpConfig)
                                                      .encryptedDataDetails(encryptedDataDetails)
                                                      .type(GcbTaskParams.GcbTaskType.FETCH_TRIGGERS)
                                                      .build()})
                        .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                        .build())
              .build());
    } catch (InterruptedException e) {
      log.error("Exception on getGcbTriggers: ", e);
    }
    Assert.notNull(delegateResponseData, "Delegate Response data should not be null!");
    if (delegateResponseData.getErrorMsg() != null) {
      log.error("Exception on getGcbTriggers: " + delegateResponseData.getErrorMsg());
      throw new InvalidRequestException(
          "Exception while fetching GcbTriggers from the delegate: " + delegateResponseData.getErrorMsg(), USER);
    }
    return delegateResponseData.getTriggers();
  }

  @Override
  public List<String> listAcrRepositories(String settingId, String subscriptionId, String registryName) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    if (!(settingValue instanceof AzureConfig)) {
      return Collections.emptyList();
    }
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    return getBuildService(settingAttribute)
        .listRepositories((AzureConfig) settingValue, encryptedDataDetails, subscriptionId, registryName);
  }

  @Override
  public List<AzureContainerRegistry> listAzureContainerRegistries(String settingId, String subscriptionId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    if (!(settingValue instanceof AzureConfig)) {
      return Collections.emptyList();
    }
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    return getBuildService(settingAttribute)
        .listContainerRegistries((AzureConfig) settingValue, encryptedDataDetails, subscriptionId);
  }

  @Override
  public List<String> listAzureContainerRegistryNames(String settingId, String subscriptionId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    if (!(settingValue instanceof AzureConfig)) {
      return Collections.emptyList();
    }
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    return getBuildService(settingAttribute)
        .listContainerRegistryNames((AzureConfig) settingValue, encryptedDataDetails, subscriptionId);
  }

  @Override
  public Map<String, String> listSubscriptions(String settingId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    if (!(settingValue instanceof AzureConfig)) {
      return Collections.emptyMap();
    }
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    return getBuildService(settingAttribute).listSubscriptions((AzureConfig) settingValue, encryptedDataDetails);
  }

  @Override
  public List<AzureImageGallery> listImageGalleries(String settingId, String subscriptionId, String resourceGroupName) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    if (!(settingValue instanceof AzureConfig)) {
      return Collections.emptyList();
    }
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    return getBuildService(settingAttribute)
        .listImageGalleries((AzureConfig) settingValue, encryptedDataDetails, subscriptionId, resourceGroupName);
  }

  @Override
  public List<AzureResourceGroup> listResourceGroups(String settingId, String subscriptionId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    if (!(settingValue instanceof AzureConfig)) {
      return Collections.emptyList();
    }
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    return getBuildService(settingAttribute)
        .listResourceGroups((AzureConfig) settingValue, encryptedDataDetails, subscriptionId);
  }

  @Override
  public List<AzureImageDefinition> listImageDefinitions(
      String settingId, String subscriptionId, String resourceGroupName, String galleryName) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    SettingValue settingValue = getSettingValue(settingAttribute);
    if (!(settingValue instanceof AzureConfig)) {
      return Collections.emptyList();
    }
    List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails((EncryptableSetting) settingValue);
    return getBuildService(settingAttribute)
        .listImageDefinitions(
            (AzureConfig) settingValue, encryptedDataDetails, subscriptionId, resourceGroupName, galleryName);
  }

  private boolean areDelegateSelectorsRequired(SettingAttribute settingAttribute) {
    if (settingsService.isSettingValueGcp(settingAttribute)) {
      return ((GcpConfig) settingAttribute.getValue()).isUseDelegateSelectors();
    }
    return settingsService.hasDelegateSelectorProperty(settingAttribute);
  }

  private SyncTaskContext appendDelegateSelector(
      SettingAttribute settingAttribute, SyncTaskContextBuilder syncTaskContextBuilder) {
    List<String> tags = settingsService.getDelegateSelectors(settingAttribute);
    log.info("[Delegate Selection] Appending delegate selectors to - {} with selectors {}", settingAttribute.getName(),
        tags);
    return syncTaskContextBuilder.tags(tags).build();
  }

  @Override
  public List<Artifact> listArtifactByArtifactStreamAndFilterPath(
      List<Artifact> artifacts, ArtifactStream artifactStream) {
    return artifacts.stream()
        .filter(artifact -> {
          List<String> paths = ((GcsArtifactStream) artifactStream).getArtifactPaths();
          return paths.stream().anyMatch(path -> {
            String[] parts = path.split("\\*");
            if (parts.length == 2) {
              return artifact.getArtifactPath().startsWith(parts[0]) && artifact.getArtifactPath().endsWith(parts[1]);
            }
            return artifact.getArtifactPath().startsWith(parts[0]);
          });
        })
        .collect(toList());
  }
}
