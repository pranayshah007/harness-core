/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.collect.artifacts;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.ExceptionUtils;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.JenkinsConfig;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.command.JenkinsTaskParams;
import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.service.impl.jenkins.JenkinsUtils;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Created by rishi on 12/14/16.
 */
@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class JenkinsCollectionTask extends AbstractDelegateRunnableTask {
  @Inject private JenkinsUtils jenkinsUtil;
  @Inject private EncryptionService encryptionService;
  @Inject private ArtifactCollectionCommonTaskHelper artifactCollectionCommonTaskHelper;

  public JenkinsCollectionTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> postExecute, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, postExecute, preExecute);
  }

  @Override
  public ListNotifyResponseData run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public ListNotifyResponseData run(Object[] parameters) {
    JenkinsTaskParams jenkinsTaskParams = (JenkinsTaskParams) parameters[0];
    return run(jenkinsTaskParams.getJenkinsConfig(), jenkinsTaskParams.getEncryptedDataDetails(),
        jenkinsTaskParams.getJobName(), jenkinsTaskParams.getArtifactPaths(), jenkinsTaskParams.getMetaData());
  }

  public ListNotifyResponseData run(JenkinsConfig jenkinsConfig, List<EncryptedDataDetail> encryptionDetails,
      String jobName, List<String> artifactPaths, Map<String, String> arguments) {
    ListNotifyResponseData res = new ListNotifyResponseData();

    try {
      encryptionService.decrypt(jenkinsConfig, encryptionDetails, false);
      Jenkins jenkins = jenkinsUtil.getJenkins(jenkinsConfig);

      for (String artifactPath : artifactPaths) {
        log.info("Collecting artifact {} of job {}", artifactPath, jobName);
        Pair<String, InputStream> fileInfo =
            jenkins.downloadArtifact(jobName, arguments.get(ArtifactMetadataKeys.buildNo), artifactPath);
        artifactCollectionCommonTaskHelper.addDataToResponse(
            fileInfo, artifactPath, res, getDelegateId(), getTaskId(), getAccountId());
      }
    } catch (Exception e) {
      log.warn("Exception: " + ExceptionUtils.getMessage(e), e);
      // TODO: better error handling

      //      if (e instanceof WingsException)
      //        WingsException ex = (WingsException) e;
      //        errorMessage = Joiner.on(",").join(ex.getResponseMessageList().stream()
      //            .map(responseMessage ->
      //            MessageManager.getInstance().getResponseMessage(responseMessage.getCode(),
      //            ex.getParams()).getMessage()) .collect(toList()));
      //      } else {
      //        errorMessage = e.getMessage();
      //      }
      //      executionStatus = executionStatus.FAILED;
      //      jenkinsExecutionResponse.setErrorMessage(errorMessage);
    }

    return res;
  }
}
