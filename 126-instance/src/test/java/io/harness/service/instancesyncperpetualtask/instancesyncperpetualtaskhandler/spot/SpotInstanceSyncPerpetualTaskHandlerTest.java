/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.spot;

import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.ElastigroupInfrastructureOutcome;
import io.harness.cdng.ssh.SshEntityHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialType;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.SpotDeploymentInfoDTO;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
public class SpotInstanceSyncPerpetualTaskHandlerTest extends InstancesTestBase {
  private static final String PROJECT_IDENTIFIER = "project";
  private static final String ACCOUNT_IDENTIFIER = "account";
  private static final String ORG_IDENTIFIER = "org";
  private static final String INFRASTRUCTURE_KEY = "INFRA_KEY";
  private static final String ELASTIGROUP_SERVICE = ServiceSpecType.ELASTIGROUP;
  private static final String ELASTIGROUP_ID = "elastigroupId";

  @Mock KryoSerializer kryoSerializer;
  @Mock SshEntityHelper sshEntityHelper;
  @Mock EncryptionHelper encryptionHelper;
  @Mock DelegateServiceGrpcClient delegateServiceGrpcClient;

  @InjectMocks SpotInstanceSyncPerpetualTaskHandler spotInstanceSyncPerpetualTaskHandler;

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetExecutionBundle() {
    InfrastructureMappingDTO infrastructureMappingDTO = InfrastructureMappingDTO.builder()
                                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                                            .infrastructureKind("random")
                                                            .infrastructureKey(INFRASTRUCTURE_KEY)
                                                            .connectorRef("random")
                                                            .envIdentifier("random")
                                                            .serviceIdentifier(ELASTIGROUP_SERVICE)
                                                            .build();
    ElastigroupInfrastructureOutcome outcome =
        ElastigroupInfrastructureOutcome.builder().infrastructureKey(INFRASTRUCTURE_KEY).connectorRef("random").build();

    SpotConnectorDTO spotConnectorDTO = SpotConnectorDTO.builder()
                                            .credential(SpotCredentialDTO.builder()
                                                            .spotCredentialType(SpotCredentialType.PERMANENT_TOKEN)
                                                            .config(SpotPermanentTokenConfigSpecDTO.builder().build())
                                                            .build())
                                            .build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(spotConnectorDTO).build();
    doReturn(connectorInfoDTO).when(sshEntityHelper).getConnectorInfoDTO(any(), any());
    doReturn(Collections.emptyList())
        .when(encryptionHelper)
        .getEncryptionDetail(any(), anyString(), anyString(), anyString());

    byte[] bytes = {70};
    when(kryoSerializer.asBytes(any())).thenReturn(bytes);
    when(kryoSerializer.asDeflatedBytes(any())).thenReturn(bytes);
    when(delegateServiceGrpcClient.isTaskTypeSupported(any(), any())).thenReturn(false);

    PerpetualTaskExecutionBundle executionBundle = spotInstanceSyncPerpetualTaskHandler.getExecutionBundle(
        infrastructureMappingDTO, Arrays.asList(getDeploymentInfoDto()), outcome);

    assertThat(executionBundle.getCapabilitiesList().size()).isEqualTo(1);
    assertThat(executionBundle.getTaskParams().getValue()).isNotNull();
  }

  private SpotDeploymentInfoDTO getDeploymentInfoDto() {
    return SpotDeploymentInfoDTO.builder()
        .infrastructureKey(INFRASTRUCTURE_KEY)
        .ec2Instances(Collections.singletonList("ec2Id"))
        .elastigroupId(ELASTIGROUP_ID)
        .build();
  }
}
