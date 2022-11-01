/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.servicenow.connection;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectionTaskParams;
import io.harness.delegate.beans.connector.servicenow.connection.ServiceNowTestConnectionTaskNGResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters;
import io.harness.delegate.task.servicenow.ServiceNowTaskNgHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HintException;
import io.harness.servicenow.ServiceNowActionNG;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(CDC)
public class ServiceNowTestConnectionTaskNG extends AbstractDelegateRunnableTask {
  @Inject ServiceNowTaskNgHelper serviceNowTaskNgHelper;
  public ServiceNowTestConnectionTaskNG(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("This method is deprecated");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    ServiceNowConnectionTaskParams serviceNowConnectionTaskParams = (ServiceNowConnectionTaskParams) parameters;

    try {
      serviceNowTaskNgHelper.getServiceNowResponse(
          ServiceNowTaskNGParameters.builder()
              .serviceNowConnectorDTO(serviceNowConnectionTaskParams.getServiceNowConnectorDTO())
              .encryptionDetails(serviceNowConnectionTaskParams.getEncryptionDetails())
              .action(ServiceNowActionNG.VALIDATE_CREDENTIALS)
              .build());
      return ServiceNowTestConnectionTaskNGResponse.builder().canConnect(true).build();
    } catch (HintException ex) {
      throw ex;
    } catch (Exception ex) {
      return ServiceNowTestConnectionTaskNGResponse.builder()
          .canConnect(false)
          .errorMessage(ExceptionUtils.getMessage(ex))
          .build();
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
