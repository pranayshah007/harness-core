/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.helm.OciHelmConnectivityTaskParams;
import io.harness.delegate.beans.connector.helm.OciHelmConnectivityTaskResponse;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmValidationParams;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.HelmClientException;
import io.harness.exception.HelmClientRuntimeException;
import io.harness.exception.NestedExceptionUtils;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@OwnedBy(CDP)
public class OciHelmConnectivityDelegateTask extends AbstractDelegateRunnableTask {
  @Inject private OciHelmValidationHandler ociHelmValidationHandler;

  public OciHelmConnectivityDelegateTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    try {
      OciHelmConnectivityTaskParams ociHelmConnectivityTaskParams = (OciHelmConnectivityTaskParams) parameters;
      OciHelmConnectorDTO ociHelmConnectorDTO = ociHelmConnectivityTaskParams.getHelmConnector();
      final OciHelmValidationParams ociHelmValidationParams =
          OciHelmValidationParams.builder()
              .encryptionDataDetails(ociHelmConnectivityTaskParams.getEncryptionDetails())
              .ociHelmConnectorDTO(ociHelmConnectorDTO)
              .build();
      ConnectorValidationResult validationResult =
          ociHelmValidationHandler.validate(ociHelmValidationParams, getAccountId());
      validationResult.setDelegateId(getDelegateId());
      return OciHelmConnectivityTaskResponse.builder().connectorValidationResult(validationResult).build();
    } catch (HelmClientException e) {
      throw new HelmClientRuntimeException(e);
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(e.getMessage(), "Failed to validate Oci Helm Repo", e);
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
