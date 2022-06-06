/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.jenkins;

import static io.harness.annotations.dev.HarnessModule._960_API_SERVICES;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.JenkinsConfig;
import software.wings.beans.command.JenkinsTaskParams;

import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.Job;
import com.offbytwo.jenkins.model.JobWithDetails;
import com.offbytwo.jenkins.model.QueueReference;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Created by peeyushaggarwal on 5/12/16.
 */
@OwnedBy(CDC)
@TargetModule(_960_API_SERVICES)
public interface Jenkins {
  /**
   * Gets the job with details.
   *
   * @param jobname the jobname
   * @return the job
   */
  JobWithDetails getJobWithDetails(String jobname);

  /**
   * Gets the job.
   *
   * @param jobname the jobname
   * @param timeout
   * @return the job
   */
  Job getJob(String jobname, JenkinsConfig jenkinsConfig, long timeout);

  /**
   * Gets the child jobs for the given parent folder job. For the root level jobs, pass null.
   * @param parentFolderJobName parent folder job name. To get the root level jobs, pass null.
   * @return
   */
  List<JobDetails> getJobs(@Nullable String parentFolderJobName) throws IOException;

  /**
   * Gets the builds for job.
   *
   * @param jobname the jobname
   * @param artifactPaths
   * @param lastN   the last n
   * @return the builds for job
   * @throws IOException Signals that an I/O exception has occurred.
   */
  List<BuildDetails> getBuildsForJob(String jobname, List<String> artifactPaths, int lastN) throws IOException;

  /**
   * Gets the builds for job.
   *
   * @param jobname the jobname
   * @param artifactPaths
   * @param lastN   the last n
   * @param allStatuses statuses to include
   * @return the builds for job
   * @throws IOException Signals that an I/O exception has occurred.
   */
  List<BuildDetails> getBuildsForJob(String jobname, List<String> artifactPaths, int lastN, boolean allStatuses)
      throws IOException;

  /**
   * Gets last successful build for job.
   *
   * @param jobName the job name
   * @return the last successful build for job
   * @throws IOException the io exception
   */
  BuildDetails getLastSuccessfulBuildForJob(String jobName, List<String> artifactPaths) throws IOException;

  /**
   * Trigger queue reference.
   *
   * @param jobname  the jobname
   * @param jenkinsTaskParams  the parameters
   * @return  the queue reference
   * @throws IOException  the io exception
   */
  QueueReference trigger(String jobname, JenkinsTaskParams jenkinsTaskParams) throws IOException;

  /**
   * Check status.
   *
   * @param jobname the jobname
   * @return the string
   */
  String checkStatus(String jobname);

  /**
   * Check status.
   *
   * @param jobname the jobname
   * @param buildNo the build no
   * @return the string
   */
  String checkStatus(String jobname, String buildNo);

  /**
   * Check artifact status.
   *
   * @param jobname           the jobname
   * @param artifactpathRegex the artifactpath regex
   * @return the string
   */
  String checkArtifactStatus(String jobname, String artifactpathRegex);

  /**
   * Check artifact status.
   *
   * @param jobname           the jobname
   * @param buildNo           the build no
   * @param artifactpathRegex the artifactpath regex
   * @return the string
   */
  String checkArtifactStatus(String jobname, String buildNo, String artifactpathRegex);

  /**
   * Download artifact.
   *
   * @param jobname           the jobname
   * @param artifactpathRegex the artifactpath regex
   * @return the pair
   * @throws IOException        Signals that an I/O exception has occurred.
   * @throws URISyntaxException the URI syntax exception
   */
  Pair<String, InputStream> downloadArtifact(String jobname, String artifactpathRegex)
      throws IOException, URISyntaxException;

  /**
   * Download artifact.
   *
   * @param jobname           the jobname
   * @param buildNo           the build no
   * @param artifactpathRegex the artifactpath regex
   * @return the pair
   * @throws IOException        Signals that an I/O exception has occurred.
   * @throws URISyntaxException the URI syntax exception
   */
  Pair<String, InputStream> downloadArtifact(String jobname, String buildNo, String artifactpathRegex)
      throws IOException, URISyntaxException;

  /**
   * Gets build.
   *
   * @param queueItem      the queue item
   * @param jenkinsConfig  the jenkins configuration
   * @return               the build
   * @throws IOException   the io exception
   */
  Build getBuild(QueueReference queueItem, JenkinsConfig jenkinsConfig) throws IOException;

  boolean isRunning();

  Map<String, String> getEnvVars(String buildUrl);

  long getFileSize(String jobName, String buildNo, String artifactPath);
}
