/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.jenkins;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;

import java.net.URLDecoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class JenkinsJobPathBuilder {
  private static final String JOB_PATH = "/job/";

  private JenkinsJobPathBuilder() {
    throw new IllegalStateException("Utility class");
  }

  public static String getJenkinsJobPath(String jobName) {
    try {
      if (jobName == null || StringUtils.isBlank(jobName)) {
        throw new InvalidRequestException("Job name cannot be null or empty");
      }

      String decodedJobName = URLDecoder.decode(jobName, "UTF-8");
      String jenkinsJobPath;

      String[] jobNameSplit = decodedJobName.split("/");
      int parts = jobNameSplit.length;
      if (parts > 1) {
        jenkinsJobPath = constructJobPath(jobNameSplit);
      } else {
        String sufix = "/";

        if (jobName.endsWith("/")) {
          jenkinsJobPath = JOB_PATH.concat(decodedJobName);
        } else {
          jenkinsJobPath = JOB_PATH.concat(decodedJobName).concat(sufix);
        }
      }

      log.info("Retrieving job {} success", jobName);
      return jenkinsJobPath;

    } catch (Exception e) {
      throw new ArtifactServerException("Failure in fetching job: " + ExceptionUtils.getMessage(e), e, USER);
    }
  }

  public static String constructJobPath(String[] jobNameSplit) {
    if (isEmpty(jobNameSplit)) {
      return "/";
    }

    int parts = jobNameSplit.length;
    int currentIndex = 0;
    StringBuilder sb = new StringBuilder();
    for (String jobName : jobNameSplit) {
      if (currentIndex++ < parts) {
        sb.append(JOB_PATH);
        sb.append(jobName);
      }
    }

    sb.append('/');
    return sb.toString();
  }

  public static String constructParentJobPath(String[] jobNameSplit) {
    if (isEmpty(jobNameSplit)) {
      return "/";
    }

    int parts = jobNameSplit.length;
    int currentIndex = 0;
    StringBuilder sb = new StringBuilder();
    for (String jobName : jobNameSplit) {
      if (currentIndex++ < (parts - 1)) {
        sb.append(JOB_PATH);
        sb.append(jobName);
      }
    }

    sb.append('/');
    return sb.toString();
  }

  public static String getJobPathFromJenkinsJobUrl(String url) {
    if (url == null || StringUtils.isBlank(url)) {
      throw new InvalidRequestException("Job URL cannot be null or empty");
    }

    String[] list = url.split(JOB_PATH);
    String jobPath = "";
    if (list.length > 2) {
      for (int i = 1; i < list.length; i++) {
        if (i == list.length - 1) {
          jobPath = jobPath.concat(list[i]);
        } else {
          jobPath = jobPath.concat(list[i]).concat("/");
        }
      }
    } else {
      jobPath = list[1];
    }
    return jobPath;
  }
}
