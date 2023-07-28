/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectivityTaskParams;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectivityTaskResponse;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmValidationParams;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.HelmClientException;
import io.harness.exception.HelmClientRuntimeException;
import io.harness.exception.NestedExceptionUtils;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(CDP)
public class HttpHelmConnectivityDelegateTask extends AbstractDelegateRunnableTask {
  @Inject private HttpHelmValidationHandler httpHelmValidationHandler;

  public HttpHelmConnectivityDelegateTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    try {
      HttpHelmConnectivityTaskParams httpHelmConnectivityTaskParams = (HttpHelmConnectivityTaskParams) parameters;
      HttpHelmConnectorDTO httpHelmConnectorDTO = httpHelmConnectivityTaskParams.getHelmConnector();
      final HttpHelmValidationParams httpHelmValidationParams =
          HttpHelmValidationParams.builder()
              .encryptionDataDetails(httpHelmConnectivityTaskParams.getEncryptionDetails())
              .httpHelmConnectorDTO(httpHelmConnectorDTO)
              .ignoreResponseCode(httpHelmConnectivityTaskParams.isIgnoreResponseCode())
              .build();
      ConnectorValidationResult httpHelmConnectorValidationResult =
          httpHelmValidationHandler.validate(httpHelmValidationParams, getAccountId());
      httpHelmConnectorValidationResult.setDelegateId(getDelegateId());
      return HttpHelmConnectivityTaskResponse.builder()
          .connectorValidationResult(httpHelmConnectorValidationResult)
          .build();
    } catch (HelmClientException e) {
      throw new HelmClientRuntimeException(e);
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(e.getMessage(), "Failed to validate Http Helm Repo", e);
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }

  @Override
  @Deprecated
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("This method is deprecated. Use run(TaskParameters) instead.");
  }
}
