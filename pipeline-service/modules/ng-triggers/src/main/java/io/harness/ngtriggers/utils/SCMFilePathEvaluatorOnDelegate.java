/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.DelegateTaskRequest.DelegateTaskRequestBuilder;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskParams;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskResponse;
import io.harness.exception.TriggerException;
import io.harness.exception.WingsException;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@OwnedBy(HarnessTeam.CI)
public class SCMFilePathEvaluatorOnDelegate extends SCMFilePathEvaluator {
  private TaskExecutionUtils taskExecutionUtils;
  private KryoSerializer kryoSerializer;

  @Override
  public ScmPathFilterEvaluationTaskResponse execute(FilterRequestData filterRequestData,
      TriggerEventDataCondition pathCondition, ConnectorDetails connectorDetails, ScmConnector scmConnector) {
    ScmPathFilterEvaluationTaskParams params =
        getScmPathFilterEvaluationTaskParams(filterRequestData, pathCondition, connectorDetails, scmConnector);

    DelegateTaskRequestBuilder delegateTaskRequestBuilder =
        DelegateTaskRequest.builder()
            .accountId(filterRequestData.getAccountId())
            .taskType(TaskType.SCM_PATH_FILTER_EVALUATION_TASK.toString())
            .taskParameters(params)
            .executionTimeout(Duration.ofMinutes(1l))
            .taskSetupAbstraction("ng", "true");

    if (connectorDetails.getOrgIdentifier() != null) {
      delegateTaskRequestBuilder.taskSetupAbstraction("orgIdentifier", connectorDetails.getOrgIdentifier());
    }

    if (connectorDetails.getProjectIdentifier() != null) {
      delegateTaskRequestBuilder
          .taskSetupAbstraction(
              "owner", connectorDetails.getOrgIdentifier() + "/" + connectorDetails.getProjectIdentifier())
          .taskSetupAbstraction("projectIdentifier", connectorDetails.getProjectIdentifier());
    }

    if (connectorDetails.getDelegateSelectors() != null) {
      delegateTaskRequestBuilder.taskSelectors(connectorDetails.getDelegateSelectors());
    }

    ResponseData responseData = taskExecutionUtils.executeSyncTask(delegateTaskRequestBuilder.build());

    if (BinaryResponseData.class.isAssignableFrom(responseData.getClass())) {
      BinaryResponseData binaryResponseData = (BinaryResponseData) responseData;
      Object object = kryoSerializer.asInflatedObject(binaryResponseData.getData());
      if (object instanceof ScmPathFilterEvaluationTaskResponse) {
        return (ScmPathFilterEvaluationTaskResponse) object;
      } else if (object instanceof ErrorResponseData) {
        ErrorResponseData errorResponseData = (ErrorResponseData) object;
        throw new TriggerException(
            format("Failed to fetch PR Details. Reason: {%s}", errorResponseData.getErrorMessage()),
            WingsException.SRE);
      }
    }

    return null;
  }
}
