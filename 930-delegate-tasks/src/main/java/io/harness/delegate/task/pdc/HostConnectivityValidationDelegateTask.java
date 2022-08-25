/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.pdc;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.pdcconnector.HostConnectivityTaskParams;
import io.harness.delegate.beans.connector.pdcconnector.HostConnectivityTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.lang.JoseException;

@OwnedBy(CDP)
@Slf4j
public class HostConnectivityValidationDelegateTask extends AbstractDelegateRunnableTask {
  public HostConnectivityValidationDelegateTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    HostConnectivityTaskParams hostConnectivityTaskParams = (HostConnectivityTaskParams) parameters;
    String hostName = hostConnectivityTaskParams.getHostName();
    int port = hostConnectivityTaskParams.getPort();

    try {
      boolean connectableHost = connectableHost(hostName, port, hostConnectivityTaskParams.getSocketTimeout());
      return HostConnectivityTaskResponse.builder().connectionSuccessful(connectableHost).build();
    } catch (Exception ex) {
      log.error("Socket Connection failed for hostName: {}, post: {} ", hostName, port, ex);
      return HostConnectivityTaskResponse.builder()
          .connectionSuccessful(false)
          .errorCode(ErrorCode.SOCKET_CONNECTION_ERROR)
          .errorMessage(ExceptionUtils.getMessage(ex))
          .build();
    }
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    return null;
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }

  private boolean connectableHost(final String hostName, int port, int socketTimeout) throws Exception {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(hostName, port), socketTimeout);
      log.info(
          "Socket Connection succeeded for hostName {} on port {}, socketTimeout: {}", hostName, port, socketTimeout);
      return true;
    }
  }
}
