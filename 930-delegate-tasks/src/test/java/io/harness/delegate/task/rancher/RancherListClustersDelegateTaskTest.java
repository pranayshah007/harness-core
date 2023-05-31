/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.rancher;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.task.rancher.RancherBearerTokenAuthPassword;
import io.harness.connector.task.rancher.RancherConfig;
import io.harness.connector.task.rancher.RancherManualConfig;
import io.harness.connector.task.rancher.RancherNgConfigMapper;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.rancher.RancherConnectorDTO;
import io.harness.delegate.beans.connector.rancher.RancherListClustersTaskResponse;
import io.harness.delegate.beans.connector.rancher.RancherTaskParams;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.exception.ngexception.RancherClientRuntimeException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rancher.RancherConnectionHelperService;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jose4j.lang.JoseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class RancherListClustersDelegateTaskTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private static final String ACCOUNT_ID = "accountId";
  private final DelegateTaskPackage delegateTaskPackage =
      DelegateTaskPackage.builder().data(TaskData.builder().build()).accountId(ACCOUNT_ID).build();
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock Consumer<DelegateTaskResponse> consumer;
  @Mock BooleanSupplier preExecute;

  @Mock RancherConfig rancherConfig;

  @Mock RancherConnectionHelperService rancherConnectionHelperService;

  @Mock RancherNgConfigMapper rancherNgConfigMapper;

  @InjectMocks
  RancherListClustersDelegateTask rancherListClustersDelegateTask =
      new RancherListClustersDelegateTask(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void runHappy() throws JoseException, IOException {
    RancherTaskParams taskParams = setupTask();
    doReturn(List.of("c1", "c2")).when(rancherConnectionHelperService).listClusters(any(), any());
    RancherListClustersTaskResponse responseData =
        (RancherListClustersTaskResponse) rancherListClustersDelegateTask.run(taskParams);

    assertThat(responseData.getClusters()).contains("c1", "c2");
    assertThat(responseData.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void runFailure() throws JoseException, IOException {
    RancherTaskParams taskParams = setupTask();
    doThrow(new RancherClientRuntimeException("some message"))
        .when(rancherConnectionHelperService)
        .listClusters(any(), any());

    RancherListClustersTaskResponse responseData =
        (RancherListClustersTaskResponse) rancherListClustersDelegateTask.run(taskParams);
    assertThat(responseData.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(responseData.getErrorMessage()).contains("some message");
  }

  private RancherTaskParams setupTask() {
    List<EncryptedDataDetail> encryptionDetails = Collections.emptyList();
    RancherConnectorDTO rancherConnectorDTO = RancherConnectorDTO.builder().build();

    doReturn(rancherConfig)
        .when(rancherNgConfigMapper)
        .rancherConnectorDTOToConfig(any(RancherConnectorDTO.class), anyList());
    doReturn(RancherManualConfig.builder()
                 .rancherUrl("url")
                 .password(RancherBearerTokenAuthPassword.builder().rancherPassword("token").build())
                 .build())
        .when(rancherConfig)
        .getManualConfig();
    return RancherTaskParams.builder()
        .encryptionDetails(encryptionDetails)
        .rancherConnectorDTO(rancherConnectorDTO)
        .build();
  }
}
