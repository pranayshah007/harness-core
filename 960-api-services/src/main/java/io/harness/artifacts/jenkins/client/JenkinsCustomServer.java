/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.jenkins.client;
import static software.wings.helpers.ext.jenkins.JenkinsJobPathBuilder.getJenkinsJobPath;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.jenkins.beans.JenkinsInternalConfig;

import software.wings.helpers.ext.jenkins.model.JobWithExtendedDetails;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.client.JenkinsHttpClient;
import com.offbytwo.jenkins.client.util.EncodingUtils;
import com.offbytwo.jenkins.model.BuildWithDetails;
import com.offbytwo.jenkins.model.FolderJob;
import com.offbytwo.jenkins.model.Job;
import com.offbytwo.jenkins.model.JobWithDetails;
import java.io.IOException;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
public class JenkinsCustomServer extends JenkinsServer {
  private JenkinsHttpClient client;

  public JenkinsCustomServer(JenkinsHttpClient client) {
    super(client);
    this.client = client;
  }

  @Override
  public JobWithDetails getJob(FolderJob folder, String jobName) throws IOException {
    try {
      JobWithExtendedDetails jobWithExtendedDetails =
          client.get(toJobUrl(folder, jobName), JobWithExtendedDetails.class);
      jobWithExtendedDetails.setClient(client);

      return jobWithExtendedDetails;
    } catch (HttpResponseException e) {
      if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return null;
      }
      throw e;
    }
  }

  public BuildWithDetails getBuildDetail(FolderJob folder, String jobName, String buildNumber) throws IOException {
    try {
      JobWithExtendedDetails jobWithExtendedDetails =
          client.get(toJobUrl(folder, jobName) + "/" + buildNumber, JobWithExtendedDetails.class);
      String url = jobWithExtendedDetails.getUrl();
      BuildWithDetails buildWithDetails = client.get(url, BuildWithDetails.class);
      return buildWithDetails;
    } catch (HttpResponseException e) {
      if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return null;
      }
      throw e;
    }
  }

  public String getJenkinsConsoleLogs(FolderJob folder, String jobName, String jobId) throws IOException {
    try {
      String consoleLogs = client.get(toConsoleLogs(folder, jobName, jobId));
      return consoleLogs;
    } catch (HttpResponseException e) {
      if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return null;
      }
      throw e;
    }
  }

  public Job createJob(FolderJob folder, String jobName, JenkinsInternalConfig jenkinsInternalConfig)
      throws IOException {
    try {
      Job job;
      JobWithExtendedDetails jobWithExtendedDetails =
          client.get(toJobUrl(folder, jobName), JobWithExtendedDetails.class);

      if (jenkinsInternalConfig.isUseConnectorUrlForJobExecution()) {
        job = createJob(jobWithExtendedDetails, folder, jenkinsInternalConfig);
      } else {
        job = new Job(jobWithExtendedDetails.getName(), jobWithExtendedDetails.getUrl());
      }

      job.setClient(client);
      return job;

    } catch (HttpResponseException e) {
      if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return null;
      }
      throw e;
    }
  }

  /**
   * Creates new job with Jenkins connector URL
   *
   * @param jobWithExtendedDetails the job with extended details
   * @param folder                 the folder or {@code null}
   * @param jenkinsInternalConfig          the jenkins configuration
   * @return new job.
   */
  private Job createJob(
      JobWithExtendedDetails jobWithExtendedDetails, FolderJob folder, JenkinsInternalConfig jenkinsInternalConfig) {
    String jenkinsConnectorUrl = jenkinsInternalConfig.getJenkinsUrl();
    if (jenkinsConnectorUrl.endsWith("/")) {
      jenkinsConnectorUrl = jenkinsConnectorUrl.substring(0, jenkinsConnectorUrl.length() - 1);
    }

    String jenkinsJobUrl;
    if (folder != null) {
      String folderUrl = folder.getUrl();
      if (folderUrl.endsWith("/")) {
        folderUrl = folderUrl.substring(0, folderUrl.length() - 1);
      }
      jenkinsJobUrl = folderUrl.concat(getJenkinsJobPath(jobWithExtendedDetails.getName()));
    } else {
      jenkinsJobUrl = getJenkinsJobPath(jobWithExtendedDetails.getName());
    }
    return new Job(jobWithExtendedDetails.getName(), jenkinsConnectorUrl.concat(jenkinsJobUrl));
  }

  /**
   * Helper to create the base url for a job, with or without a given folder
   *
   * @param folder the folder or {@code null}
   * @param jobName the name of the job.
   * @return converted base url.
   */
  private String toJobUrl(FolderJob folder, String jobName) {
    return toBaseJobUrl(folder) + "job/" + EncodingUtils.encode(jobName);
  }

  private String toConsoleLogs(FolderJob folder, String jobName, String jobId) {
    return toBaseJobUrl(folder) + "job/" + EncodingUtils.encode(jobName) + "/" + jobId + "/logText/progressiveText";
  }

  /**
   * Helper to create a base url in case a folder is given
   *
   * @param folder the folder or {@code null}
   * @return The created base url.
   */
  private String toBaseJobUrl(FolderJob folder) {
    String path = "/";
    if (folder != null) {
      path = folder.getUrl();
    }
    return path;
  }
}
