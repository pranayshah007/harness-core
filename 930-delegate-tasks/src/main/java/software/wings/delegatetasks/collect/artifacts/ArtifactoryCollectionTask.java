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
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.ExceptionUtils;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.artifact.ArtifactoryCollectionTaskParameters;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.helpers.ext.artifactory.ArtifactoryService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.mappers.artifact.ArtifactoryConfigToArtifactoryRequestMapper;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by srinivas on 4/4/17.
 */
@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ArtifactoryCollectionTask extends AbstractDelegateRunnableTask {
  @Inject private ArtifactoryService artifactoryService;
  @Inject private EncryptionService encryptionService;

  public ArtifactoryCollectionTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> postExecute,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, postExecute, preExecute);
  }

  @Override
  public ListNotifyResponseData run(TaskParameters parameters) {
    try {
      ArtifactoryCollectionTaskParameters artifactoryCollectionTaskParameters =
          (ArtifactoryCollectionTaskParameters) parameters;

      return run(artifactoryCollectionTaskParameters.getArtifactoryConfig(),
          artifactoryCollectionTaskParameters.getEncryptedDataDetails(),
          artifactoryCollectionTaskParameters.getJobName(), artifactoryCollectionTaskParameters.getMetadata());
    } catch (Exception e) {
      log.error("Exception occurred while collecting artifact", e);
      return new ListNotifyResponseData();
    }
  }

  @Override
  public ListNotifyResponseData run(Object[] parameters) {
    return run((ArtifactoryCollectionTaskParameters) parameters[0]);
  }

  public ListNotifyResponseData run(ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptedDataDetails,
      String repositoryName, Map<String, String> metadata) {
    try {
      ArtifactoryConfigRequest artifactoryRequest = ArtifactoryConfigToArtifactoryRequestMapper.toArtifactoryRequest(
          artifactoryConfig, encryptionService, encryptedDataDetails);
      return artifactoryService.downloadArtifacts(
          artifactoryRequest, repositoryName, metadata, getDelegateId(), getTaskId(), getAccountId());
    } catch (Exception e) {
      log.warn("Exception occurred while collecting artifact for artifact server {} : {}",
          artifactoryConfig.getArtifactoryUrl(), ExceptionUtils.getMessage(e), e);
    }
    return new ListNotifyResponseData();
  }
}
