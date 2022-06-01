/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.jenkins;

import static io.harness.annotations.dev.HarnessModule._960_API_SERVICES;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.helpers.ext.jenkins.JenkinsJobPathBuilder.getJenkinsJobPath;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.JenkinsConfig;
import software.wings.helpers.ext.jenkins.model.JobWithExtendedDetails;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.client.JenkinsHttpClient;
import com.offbytwo.jenkins.client.util.EncodingUtils;
import com.offbytwo.jenkins.model.FolderJob;
import com.offbytwo.jenkins.model.Job;
import com.offbytwo.jenkins.model.JobWithDetails;
import java.io.IOException;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;

@OwnedBy(CDC)
@TargetModule(_960_API_SERVICES)
public class CustomJenkinsServer extends JenkinsServer {
  private JenkinsHttpClient client;

  public CustomJenkinsServer(JenkinsHttpClient client) {
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

  public Job createJob(FolderJob folder, String jobName, JenkinsConfig jenkinsConfig) throws IOException {
    try {
      Job job;
      JobWithExtendedDetails jobWithExtendedDetails =
          client.get(toJobUrl(folder, jobName), JobWithExtendedDetails.class);

      if (jenkinsConfig.isUseConnectorUrlForJobExecution()) {
        job = createJob(jobWithExtendedDetails, folder, jenkinsConfig);
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
   * @param jenkinsConfig          the jenkins configuration
   * @return new job.
   */
  private Job createJob(JobWithExtendedDetails jobWithExtendedDetails, FolderJob folder, JenkinsConfig jenkinsConfig) {
    String jenkinsConnectorUrl = jenkinsConfig.getJenkinsUrl();
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
