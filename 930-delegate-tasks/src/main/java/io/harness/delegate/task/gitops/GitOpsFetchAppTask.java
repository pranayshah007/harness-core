/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.gitops;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.jooq.tools.StringUtils;
import org.jose4j.lang.JoseException;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@OwnedBy(GITOPS)
public class GitOpsFetchAppTask extends AbstractDelegateRunnableTask {
  @Inject public GitOpsTaskHelper gitOpsTaskHelper;
  public static final String LOG_KEY_SUFFIX = "EXECUTE";

  public GitOpsFetchAppTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    return null;
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    try {
      log.info("Started executing GitOps Fetch App task");
      GitOpsFetchAppTaskParams taskParams = (GitOpsFetchAppTaskParams) parameters;
      NGDelegateLogCallback ngDelegateLogCallback =
          new NGDelegateLogCallback(getLogStreamingTaskClient(), LOG_KEY_SUFFIX, false, null);
      FetchFilesResult fetchFilesResult = gitOpsTaskHelper.getFetchFilesResult(taskParams.getGitFetchFilesConfig(),
          taskParams.getAccountId(), ngDelegateLogCallback, taskParams.isCloseLogStream());
      if (fetchFilesResult == null || CollectionUtils.isEmpty(fetchFilesResult.getFiles())) {
        log.error("No files found");
        return GitOpsFetchAppTaskResponse.builder()
            .taskStatus(TaskStatus.FAILURE)
            .errorMessage("No files found")
            .build();
      }
      GitFile gitFile = fetchFilesResult.getFiles().get(0);
      String appName = getAppName(gitFile);
      if (appName == null || StringUtils.isEmpty(appName)) {
        return GitOpsFetchAppTaskResponse.builder()
            .taskStatus(TaskStatus.FAILURE)
            .errorMessage("Found empty app name")
            .build();
      }
      ngDelegateLogCallback.saveExecutionLog(String.format("App set Name: %s", appName));
      return GitOpsFetchAppTaskResponse.builder().taskStatus(TaskStatus.SUCCESS).appName(appName).build();
    } catch (WingsException ex) {
      log.error("Failed to Fetch App Task", ex);
      return GitOpsFetchAppTaskResponse.builder().taskStatus(TaskStatus.FAILURE).errorMessage(ex.getMessage()).build();
    } catch (Exception ex) {
      log.error("Failed to Fetch App Task", ex);
      return GitOpsFetchAppTaskResponse.builder()
          .taskStatus(TaskStatus.FAILURE)
          .errorMessage("Failed to fetch App Task")
          .build();
    }
  }

  private String getAppName(GitFile gitFile) {
    try {
      if (gitFile == null || gitFile.getFileContent() == null) {
        return null;
      }
      Yaml yaml = new Yaml();
      Map<String, Object> yamlMap = yaml.load(gitFile.getFileContent());
      Map<String, Object> metadataMap = (Map) yamlMap.get("metadata");
      return (String) metadataMap.get("name");

    } catch (Exception ex) {
      throw new InvalidRequestException("Failed to parse yaml file", ex);
    }
  }
}
