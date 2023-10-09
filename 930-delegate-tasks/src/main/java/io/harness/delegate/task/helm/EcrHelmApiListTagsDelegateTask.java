/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.aws.beans.EcrImageDetailConfig;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.helm.EcrHelmApiListTagsTaskParams;
import io.harness.delegate.beans.connector.helm.OciHelmDockerApiListTagsTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(CDP)
public class EcrHelmApiListTagsDelegateTask extends AbstractDelegateRunnableTask {
  @Inject private OciHelmEcrConfigApiHelper ociHelmEcrConfigApiHelper;
  private int CHART_VERSIONS_PAGE_SIZE_DEFAULT = 20;

  public EcrHelmApiListTagsDelegateTask(DelegateTaskPackage delegateTaskPackage,
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
    EcrHelmApiListTagsTaskParams ecrHelmApiListTagsTaskParams = (EcrHelmApiListTagsTaskParams) parameters;
    int pageSize = ecrHelmApiListTagsTaskParams.getPageSize() > 0 ? ecrHelmApiListTagsTaskParams.getPageSize()
                                                                  : CHART_VERSIONS_PAGE_SIZE_DEFAULT;
    EcrImageDetailConfig ecrImageDetailConfig =
        ociHelmEcrConfigApiHelper.getEcrImageDetailConfig(ecrHelmApiListTagsTaskParams, pageSize);
    return OciHelmDockerApiListTagsTaskResponse.builder()
        .chartName(ecrHelmApiListTagsTaskParams.getChartName())
        .chartVersions(ociHelmEcrConfigApiHelper.getChartVersionsFromImageDetails(ecrImageDetailConfig))
        .lastTag(ecrImageDetailConfig.getNextToken())
        .build();
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
