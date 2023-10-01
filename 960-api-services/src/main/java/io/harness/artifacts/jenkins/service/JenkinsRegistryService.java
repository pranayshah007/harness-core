/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.jenkins.service;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.network.Http.connectableJenkinsHttpUrl;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.jenkins.beans.JenkinsInternalConfig;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.offbytwo.jenkins.model.JobWithDetails;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(CDC)
@Singleton
@Slf4j
public class JenkinsRegistryService {
  @Inject private JenkinsRegistryUtils jenkinsRegistryUtils;

  public boolean validateCredentials(JenkinsInternalConfig jenkinsInternalConfig) {
    if (JenkinsRegistryUtils.TOKEN_FIELD.equals(jenkinsInternalConfig.getAuthMechanism())) {
      if (isEmpty(new String(jenkinsInternalConfig.getToken()))) {
        throw NestedExceptionUtils.hintWithExplanationException("Token should not be empty",
            "Check if the token is provided", new ArtifactServerException("Token should not be empty", USER));
      }
    } else {
      if (isEmpty(jenkinsInternalConfig.getUsername()) || isEmpty(new String(jenkinsInternalConfig.getPassword()))) {
        throw NestedExceptionUtils.hintWithExplanationException("UserName/Password should be not empty",
            "Check if the UserName/Password is provided",
            new ArtifactServerException("Token should not be empty", USER));
      }
    }

    if (!connectableJenkinsHttpUrl(jenkinsInternalConfig.getJenkinsUrl())) {
      throw NestedExceptionUtils.hintWithExplanationException(
          String.format(JenkinsRegistryUtils.ERROR_MESSAGE, jenkinsInternalConfig.getJenkinsUrl()),
          JenkinsRegistryUtils.ERROR_HINT,
          new ArtifactServerException(
              String.format(JenkinsRegistryUtils.ERROR_MESSAGE, jenkinsInternalConfig.getJenkinsUrl()), USER));
    }

    return jenkinsRegistryUtils.isRunning(jenkinsInternalConfig);
  }

  public List<JobDetails> getJobs(JenkinsInternalConfig jenkinsInternalConfig, String parentJobName) {
    return jenkinsRegistryUtils.getJobs(jenkinsInternalConfig, parentJobName);
  }

  public JobWithDetails getJobWithDetails(JenkinsInternalConfig jenkinsInternalConfig, String jobName) {
    return jenkinsRegistryUtils.getJobWithDetails(jenkinsInternalConfig, jobName);
  }

  public List<BuildDetails> getBuildsForJob(
      JenkinsInternalConfig jenkinsInternalConfig, String jobname, List<String> artifactPaths, int lastN) {
    try {
      return jenkinsRegistryUtils.getBuildsForJob(jenkinsInternalConfig, jobname, artifactPaths, lastN);
    } catch (WingsException e) {
      throw e;
    } catch (IOException ex) {
      throw NestedExceptionUtils.hintWithExplanationException("Failed to fetch build details jenkins server ",
          "Check if the permissions are scoped for the authenticated user & check if the right connector chosen for fetching the Jobs.",
          new InvalidRequestException(
              "Failed to fetch build details jenkins server " + ExceptionUtils.getMessage(ex), USER));
    }
  }
  public BuildDetails verifyBuildForJob(
      JenkinsInternalConfig jenkinsInternalConfig, String jobname, List<String> artifactPaths, String buildNumber) {
    try {
      return jenkinsRegistryUtils.verifyBuildForJob(jenkinsInternalConfig, jobname, artifactPaths, buildNumber);
    } catch (WingsException e) {
      throw e;
    } catch (IOException ex) {
      throw NestedExceptionUtils.hintWithExplanationException("Failed to fetch build details",
          "Check if the permissions are scoped for the authenticated user & check if the right connector chosen for fetching the Jobs.",
          new InvalidRequestException(
              "Failed to fetch build details jenkins server " + ExceptionUtils.getMessage(ex), USER));
    }
  }

  public BuildDetails getLastSuccessfulBuildForJob(
      JenkinsInternalConfig jenkinsInternalConfig, String jobName, List<String> artifactPaths) {
    try {
      return jenkinsRegistryUtils.getLastSuccessfulBuildForJob(jenkinsInternalConfig, jobName, artifactPaths);
    } catch (IOException ex) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Check if the permissions are scoped for the authenticated user & check if the right connector chosen for fetching the Jobs.",
          "Failed to fetch last successful build details jenkins server ",
          new InvalidRequestException(
              "Failed to fetch last successful build details jenkins server " + ExceptionUtils.getMessage(ex), USER));
    }
  }

  public JobDetails getJobWithParamters(JenkinsInternalConfig jenkinsInternalConfig, String jobName) {
    return jenkinsRegistryUtils.getJobWithParamters(jobName, jenkinsInternalConfig);
  }
}
