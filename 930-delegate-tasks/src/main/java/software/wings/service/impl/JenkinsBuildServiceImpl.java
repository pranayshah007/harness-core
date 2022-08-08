/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.network.Http.connectableJenkinsHttpUrl;
import static io.harness.validation.Validator.equalCheck;

import static software.wings.helpers.ext.jenkins.JobDetails.JobParameter;
import static software.wings.helpers.ext.jenkins.model.ParamPropertyType.BooleanParameterDefinition;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.jenkins.service.JenkinsRegistryUtils;
import io.harness.delegate.task.artifacts.mappers.JenkinsRequestResponseMapper;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.JenkinsConfig;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.helpers.ext.jenkins.model.JobProperty;
import software.wings.helpers.ext.jenkins.model.JobWithExtendedDetails;
import software.wings.helpers.ext.jenkins.model.ParametersDefinitionProperty;
import software.wings.service.impl.jenkins.JenkinsUtils;
import software.wings.service.intfc.JenkinsBuildService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.utils.ArtifactType;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.offbytwo.jenkins.model.Artifact;
import com.offbytwo.jenkins.model.JobWithDetails;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by peeyushaggarwal on 5/13/16.
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
public class JenkinsBuildServiceImpl implements JenkinsBuildService {
  /**
   * The constant APP_ID.
   */
  public static final String APP_ID = "appId";

  @Inject private EncryptionService encryptionService;
  @Inject private JenkinsUtils jenkinsUtil;
  @Inject private JenkinsRegistryUtils jenkinsRegistryUtils;

  @Override
  public List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      JenkinsConfig config, List<EncryptedDataDetail> encryptionDetails) {
    return wrapNewBuildsWithLabels(
        getBuildDetails(artifactStreamAttributes, appId, config, encryptionDetails, ARTIFACT_RETENTION_SIZE),
        artifactStreamAttributes, config);
  }

  @Override
  public List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      JenkinsConfig config, List<EncryptedDataDetail> encryptionDetails, int limit) {
    return wrapNewBuildsWithLabels(getBuildDetails(artifactStreamAttributes, appId, config, encryptionDetails, limit),
        artifactStreamAttributes, config);
  }

  private List<BuildDetails> getBuildDetails(ArtifactStreamAttributes artifactStreamAttributes, String appId,
      JenkinsConfig jenkinsConfig, List<EncryptedDataDetail> encryptionDetails, int limit) {
    try {
      equalCheck(artifactStreamAttributes.getArtifactStreamType(), ArtifactStreamType.JENKINS.name());

      encryptionService.decrypt(jenkinsConfig, encryptionDetails, false);
      Jenkins jenkins = jenkinsUtil.getJenkins(jenkinsConfig);
      return jenkins.getBuildsForJob(
          artifactStreamAttributes.getJobName(), artifactStreamAttributes.getArtifactPaths(), limit);
    } catch (WingsException e) {
      throw e;
    } catch (IOException ex) {
      throw NestedExceptionUtils.hintWithExplanationException("Failed to fetch build details jenkins server",
          JenkinsRegistryUtils.ERROR_HINT_ARTIFACT_PATH,
          new ArtifactServerException(
              "Failed to fetch build details jenkins server. Reason: " + ExceptionUtils.getMessage(ex), USER));
    }
  }

  @Override
  public List<JobDetails> getJobs(
      JenkinsConfig jenkinsConfig, List<EncryptedDataDetail> encryptionDetails, Optional<String> parentJobName) {
    try {
      encryptionService.decrypt(jenkinsConfig, encryptionDetails, false);
      Jenkins jenkins = jenkinsUtil.getJenkins(jenkinsConfig);
      // Just in case, some one passes null instead of Optional.empty()
      if (parentJobName == null) {
        return jenkins.getJobs(null);
      }
      return jenkins.getJobs(parentJobName.orElse(null));
    } catch (WingsException e) {
      throw e;
    } catch (IOException e) {
      throw NestedExceptionUtils.hintWithExplanationException("Failed to fetch Jobs",
          JenkinsRegistryUtils.ERROR_HINT_ARTIFACT_PATH,
          new ArtifactServerException("Failed to fetch Jobs. Reason:" + ExceptionUtils.getMessage(e), USER));
    }
  }

  @Override
  public List<String> getArtifactPaths(
      String jobName, String groupId, JenkinsConfig jenkinsConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(jenkinsConfig, encryptionDetails, false);
    Jenkins jenkins = jenkinsUtil.getJenkins(jenkinsConfig);
    try {
      JobWithDetails job = jenkins.getJobWithDetails(jobName);
      return Lists.newArrayList(job.getLastSuccessfulBuild()
                                    .details()
                                    .getArtifacts()
                                    .stream()
                                    .map(Artifact::getRelativePath)
                                    .distinct()
                                    .collect(toList()));
    } catch (WingsException e) {
      throw e;
    } catch (Exception ex) {
      throw NestedExceptionUtils.hintWithExplanationException(JenkinsRegistryUtils.ERROR_MESSAGE_ARTIFACT_PATH,
          JenkinsRegistryUtils.ERROR_HINT_ARTIFACT_PATH,
          new ArtifactServerException(
              "Error in fetching build from jenkins server. Reason:" + ExceptionUtils.getMessage(ex), USER));
    }
  }

  @Override
  public BuildDetails getLastSuccessfulBuild(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      JenkinsConfig jenkinsConfig, List<EncryptedDataDetail> encryptionDetails) {
    equalCheck(artifactStreamAttributes.getArtifactStreamType(), ArtifactStreamType.JENKINS.name());
    encryptionService.decrypt(jenkinsConfig, encryptionDetails, false);
    Jenkins jenkins = jenkinsUtil.getJenkins(jenkinsConfig);
    try {
      return wrapLastSuccessfulBuildWithLabels(
          jenkins.getLastSuccessfulBuildForJob(
              artifactStreamAttributes.getJobName(), artifactStreamAttributes.getArtifactPaths()),
          artifactStreamAttributes, jenkinsConfig);
    } catch (WingsException e) {
      throw e;
    } catch (IOException ex) {
      throw NestedExceptionUtils.hintWithExplanationException(JenkinsRegistryUtils.ERROR_MESSAGE_ARTIFACT_PATH,
          JenkinsRegistryUtils.ERROR_HINT_ARTIFACT_PATH,
          new ArtifactServerException(
              "Error in fetching build from jenkins server. Reason:" + ExceptionUtils.getMessage(ex), USER));
    }
  }

  @Override
  public Map<String, String> getPlans(JenkinsConfig jenkinsConfig, List<EncryptedDataDetail> encryptionDetails) {
    List<JobDetails> jobs = getJobs(jenkinsConfig, encryptionDetails, Optional.empty());
    Map<String, String> jobKeyMap = new HashMap<>();
    if (jobs != null) {
      jobs.forEach(jobKey -> jobKeyMap.put(jobKey.getJobName(), jobKey.getJobName()));
    }
    return jobKeyMap;
  }

  @Override
  public Map<String, String> getPlans(JenkinsConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactType artifactType, String repositoryType) {
    return getPlans(config, encryptionDetails);
  }

  @Override
  public List<String> getGroupIds(
      String jobName, JenkinsConfig jenkinsConfig, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Jenkins Artifact Stream", USER);
  }

  @Override
  public boolean validateArtifactServer(JenkinsConfig jenkinsConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    encryptionService.decrypt(jenkinsConfig, encryptedDataDetails, false);

    if (JenkinsUtils.TOKEN_FIELD.equals(jenkinsConfig.getAuthMechanism())) {
      if (isEmpty(new String(jenkinsConfig.getToken()))) {
        throw NestedExceptionUtils.hintWithExplanationException("Token should not be empty",
            "Check if the token is provided", new ArtifactServerException("Token should not be empty", USER));
      }
    } else {
      if (isEmpty(jenkinsConfig.getUsername()) || isEmpty(new String(jenkinsConfig.getPassword()))) {
        throw NestedExceptionUtils.hintWithExplanationException(
            String.format(JenkinsRegistryUtils.ERROR_MESSAGE, jenkinsConfig.getJenkinsUrl()),
            JenkinsRegistryUtils.ERROR_HINT,
            new ArtifactServerException(
                String.format(JenkinsRegistryUtils.ERROR_MESSAGE, jenkinsConfig.getJenkinsUrl()), USER));
      }
    }

    if (!connectableJenkinsHttpUrl(jenkinsConfig.getJenkinsUrl())) {
      throw NestedExceptionUtils.hintWithExplanationException(
          String.format(JenkinsRegistryUtils.ERROR_MESSAGE, jenkinsConfig.getJenkinsUrl()),
          JenkinsRegistryUtils.ERROR_HINT,
          new ArtifactServerException(
              String.format(JenkinsRegistryUtils.ERROR_MESSAGE, jenkinsConfig.getJenkinsUrl()), USER));
    }

    return jenkinsRegistryUtils.isRunning(JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsConfig));
  }

  @Override
  public JobDetails getJob(String jobName, JenkinsConfig jenkinsConfig, List<EncryptedDataDetail> encryptionDetails) {
    try {
      log.info("Retrieving Job with details for Job: {}", jobName);
      encryptionService.decrypt(jenkinsConfig, encryptionDetails, false);
      JobWithDetails jobWithDetails = jenkinsRegistryUtils.getJobWithDetails(
          JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsConfig), jobName);
      List<JobParameter> parameters = new ArrayList<>();
      if (jobWithDetails != null) {
        JobWithExtendedDetails jobWithExtendedDetails = (JobWithExtendedDetails) jobWithDetails;
        List<JobProperty> properties = jobWithExtendedDetails.getProperties();
        if (properties != null) {
          properties.stream()
              .map(JobProperty::getParameterDefinitions)
              .filter(Objects::nonNull)
              .forEach((List<ParametersDefinitionProperty> pds) -> {
                log.info("Job Properties definitions {}", pds.toArray());
                pds.forEach((ParametersDefinitionProperty pdProperty) -> parameters.add(getJobParameter(pdProperty)));
              });
        }
        log.info("Retrieving Job with details for Job: {} success", jobName);
        return new JobDetails(jobWithDetails.getName(), jobWithDetails.getUrl(), parameters);
      }
      return null;
    } catch (WingsException e) {
      throw e;
    } catch (Exception ex) {
      throw new ArtifactServerException(
          "Error in fetching builds from jenkins server. Reason:" + ExceptionUtils.getMessage(ex), ex, USER);
    }
  }

  private JobParameter getJobParameter(ParametersDefinitionProperty pdProperty) {
    JobParameter jobParameter = new JobParameter();
    jobParameter.setName(pdProperty.getName());
    jobParameter.setDescription(pdProperty.getDescription());
    if (pdProperty.getDefaultParameterValue() != null) {
      jobParameter.setDefaultValue(pdProperty.getDefaultParameterValue().getValue());
    }
    if (pdProperty.getChoices() != null) {
      jobParameter.setOptions(pdProperty.getChoices());
    }
    if (BooleanParameterDefinition.name().equals(pdProperty.getType())) {
      List<String> booleanValues = new ArrayList<>();
      booleanValues.add("true");
      booleanValues.add("false");
      jobParameter.setOptions(booleanValues);
    }
    return jobParameter;
  }

  @Override
  public boolean validateArtifactSource(JenkinsConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes) {
    return true;
  }

  @Override
  public Map<String, String> getBuckets(
      JenkinsConfig jenkinsConfig, String projectId, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Jenkins Artifact Stream");
  }

  @Override
  public List<String> getSmbPaths(JenkinsConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Jenkins Build Service", WingsException.USER);
  }

  @Override
  public List<String> getArtifactPathsByStreamType(
      JenkinsConfig config, List<EncryptedDataDetail> encryptionDetails, String streamType) {
    throw new InvalidRequestException("Operation not supported by GCR Build Service", WingsException.USER);
  }
}
