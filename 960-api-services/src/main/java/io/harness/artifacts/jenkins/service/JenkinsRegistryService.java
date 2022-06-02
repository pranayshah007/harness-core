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

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.jenkins.beans.JenkinsInternalConfig;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.offbytwo.jenkins.model.JobWithDetails;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class JenkinsRegistryService {
  @Inject private JenkinsRegistryUtils jenkinsRegistryUtils;

  public boolean validateCredentials(JenkinsInternalConfig jenkinsInternalConfig) {
    if (JenkinsRegistryUtils.TOKEN_FIELD.equals(jenkinsInternalConfig.getAuthMechanism())) {
      if (isEmpty(new String(jenkinsInternalConfig.getToken()))) {
        throw new ArtifactServerException("Token should be not empty", USER);
      }
    } else {
      if (isEmpty(jenkinsInternalConfig.getUsername()) || isEmpty(new String(jenkinsInternalConfig.getPassword()))) {
        throw new ArtifactServerException("UserName/Password should be not empty", USER);
      }
    }

    if (!connectableJenkinsHttpUrl(jenkinsInternalConfig.getJenkinsUrl())) {
      throw new ArtifactServerException(
          "Could not reach Jenkins Server at : " + jenkinsInternalConfig.getJenkinsUrl(), USER);
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
      throw new InvalidRequestException(
          "Failed to fetch build details jenkins server. Reason:" + ExceptionUtils.getMessage(ex), USER);
    }
  }
}
