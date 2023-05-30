/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.rancher;

import io.harness.connector.task.rancher.RancherConfig;
import io.harness.connector.task.rancher.RancherNgConfigMapper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.rancher.RancherConnectorDTO;
import io.harness.delegate.beans.connector.rancher.RancherListClustersTaskParams;
import io.harness.delegate.beans.connector.rancher.RancherListClustersTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.ExceptionUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rancher.RancherConnectionHelperService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;
import org.jose4j.lang.JoseException;

public class RancherListClustersDelegateTask extends AbstractDelegateRunnableTask {
  @Inject RancherConnectionHelperService rancherConnectionHelperService;

  @Inject RancherNgConfigMapper rancherNgConfigMapper;

  public RancherListClustersDelegateTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    try {
      RancherListClustersTaskParams listClustersTaskParams = (RancherListClustersTaskParams) parameters;
      RancherConnectorDTO connector = listClustersTaskParams.getRancherConnectorDTO();
      RancherConfig rancherConfig = rancherNgConfigMapper.rancherConnectorDTOToConfig(
          connector, listClustersTaskParams.getEncryptedDataDetails());
      String rancherUrl = rancherConfig.getManualConfig().getRancherUrl();
      String bearerToken = rancherConfig.getManualConfig().getPassword().getRancherPassword();
      List<String> rancherClusters = rancherConnectionHelperService.listClusters(rancherUrl, bearerToken);

      return RancherListClustersTaskResponse.builder()
          .clusters(rancherClusters)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();
    } catch (Exception e) {
      return RancherListClustersTaskResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(ExceptionUtils.getMessage(e))
          .build();
    }
  }
}
