/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.helm.OciHelmDockerApiListTagsTaskParams;
import io.harness.delegate.beans.connector.helm.OciHelmDockerApiListTagsTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.OciHelmDockerApiException;

import com.google.inject.Inject;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@OwnedBy(CDP)
public class OciHelmDockerApiListTagsDelegateTask extends AbstractDelegateRunnableTask {
  @Inject private OciHelmDockerApiHelper ociHelmDockerApiHelper;
  private int CHART_VERSIONS_PAGE_SIZE_DEFAULT = 20;

  public OciHelmDockerApiListTagsDelegateTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("This method is deprecated. Use run(TaskParameters) instead.");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    try {
      OciHelmDockerApiListTagsTaskParams ociHelmDockerApiListTagsTaskParams =
          (OciHelmDockerApiListTagsTaskParams) parameters;
      int pageSize = ociHelmDockerApiListTagsTaskParams.getPageSize() > 0
          ? ociHelmDockerApiListTagsTaskParams.getPageSize()
          : CHART_VERSIONS_PAGE_SIZE_DEFAULT;
      List<String> versions =
          ociHelmDockerApiHelper.getChartVersions(getAccountId(), ociHelmDockerApiListTagsTaskParams, pageSize);
      String lastTag = versions.size() == pageSize ? versions.get(versions.size() - 1) : null;

      return OciHelmDockerApiListTagsTaskResponse.builder()
          .chartName(ociHelmDockerApiListTagsTaskParams.getChartName())
          .chartVersions(versions)
          .lastTag(lastTag)
          .build();
    } catch (OciHelmDockerApiException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          e.getMessage(), "Failed to query Oci Helm Docker API List Tags", e);
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
