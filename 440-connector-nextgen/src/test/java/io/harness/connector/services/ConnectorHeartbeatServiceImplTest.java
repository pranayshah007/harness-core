/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.services;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.CONNECTOR_IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.rule.OwnerRule.DEEPAK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.impl.ConnectorHeartbeatServiceImpl;
import io.harness.delegate.AccountId;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.perpetualtask.PerpetualTaskClientContextDetails;
import io.harness.perpetualtask.TaskClientParams;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ConnectorHeartbeatServiceImplTest extends CategoryTest {
  @InjectMocks ConnectorHeartbeatServiceImpl connectorHeartbeatService;
  @Mock DelegateServiceGrpcClient delegateServiceGrpcClient;
  private static final String accountIdentifier = "accountId";
  private static final String perpetualTaskId = "perpetualTaskId";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testCreatePerpetualTask() {
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String connectorIdentifier = "connectorIdentifier";
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .orgIdentifier(orgIdentifier)
                                            .projectIdentifier(projectIdentifier)
                                            .identifier(connectorIdentifier)
                                            .connectorType(ConnectorType.DOCKER)
                                            .build();
    connectorHeartbeatService.createConnectorHeatbeatTask(accountIdentifier, connectorInfoDTO.getOrgIdentifier(),
        connectorInfoDTO.getProjectIdentifier(), connectorInfoDTO.getIdentifier());
    ArgumentCaptor<AccountId> accountIdArgumentCaptor = ArgumentCaptor.forClass(AccountId.class);
    ArgumentCaptor<PerpetualTaskClientContextDetails> perpetualTaskClientContextDetailsCaptor =
        ArgumentCaptor.forClass(PerpetualTaskClientContextDetails.class);
    verify(delegateServiceGrpcClient, times(1))
        .createPerpetualTask(accountIdArgumentCaptor.capture(), eq("CONNECTOR_TEST_CONNECTION"), any(),
            perpetualTaskClientContextDetailsCaptor.capture(), anyBoolean(), any());
    AccountId accountId = accountIdArgumentCaptor.getValue();
    assertThat(accountId.getId()).isEqualTo(accountIdentifier);
    PerpetualTaskClientContextDetails clientContextDetails = perpetualTaskClientContextDetailsCaptor.getValue();
    TaskClientParams taskClientParams = clientContextDetails.getTaskClientParams();
    Map<String, String> contextMap = taskClientParams.getParamsMap();
    assertThat(contextMap.get(ACCOUNT_KEY)).isEqualTo(accountIdentifier);
    assertThat(contextMap.get(ORG_KEY)).isEqualTo(orgIdentifier);
    assertThat(contextMap.get(PROJECT_KEY)).isEqualTo(projectIdentifier);
    assertThat(contextMap.get(CONNECTOR_IDENTIFIER_KEY)).isEqualTo(connectorIdentifier);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testDeletePerpetualTask() {
    connectorHeartbeatService.deletePerpetualTask(accountIdentifier, perpetualTaskId, "connectorFQN");
    verify(delegateServiceGrpcClient, times(1)).deletePerpetualTask(any(), any());
  }
}
