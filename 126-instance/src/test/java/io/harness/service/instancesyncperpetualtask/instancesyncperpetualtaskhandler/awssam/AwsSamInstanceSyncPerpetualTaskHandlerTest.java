/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.awssam;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.awssam.AwsSamEntityHelper;
import io.harness.cdng.infra.beans.AwsSamInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.task.awssam.AwsSamInfraConfig;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.AwsSamDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
public class AwsSamInstanceSyncPerpetualTaskHandlerTest extends InstancesTestBase {
  private static final String PROJECT_IDENTIFIER = "project";
  private static final String ACCOUNT_IDENTIFIER = "account";
  private static final String ORG_IDENTIFIER = "org";
  private final String FUNCTION = "fun";
  private final String REGION = "us-east1";
  private final String INFRA_KEY = "198398123";

  @Mock private AwsSamEntityHelper awsSamEntityHelper;
  @Mock private KryoSerializer kryoSerializer;

  @Mock private DelegateServiceGrpcClient delegateServiceGrpcClient;

  @InjectMocks AwsSamInstanceSyncPerpetualTaskHandler awsSamInstanceSyncPerpetualTaskHandler;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getExecutionBundleTest() {
    InfrastructureMappingDTO infrastructureMappingDTO = InfrastructureMappingDTO.builder()
                                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                            .infrastructureKind("KUBERNETES_DIRECT")
                                                            .connectorRef("connector")
                                                            .envIdentifier("env")
                                                            .serviceIdentifier("service")
                                                            .infrastructureKey("key")
                                                            .build();
    DeploymentInfoDTO deploymentInfoDTO =
        AwsSamDeploymentInfoDTO.builder().functions(Arrays.asList(FUNCTION)).region(REGION).build();
    List<DeploymentInfoDTO> deploymentInfoDTOList = Arrays.asList(deploymentInfoDTO);

    InfrastructureOutcome infrastructureOutcome =
        AwsSamInfrastructureOutcome.builder().region(REGION).infrastructureKey(INFRA_KEY).build();
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE).build();
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    AwsSamInfraConfig awsSamInfraConfig = AwsSamInfraConfig.builder()
                                              .awsConnectorDTO(awsConnectorDTO)
                                              .infraStructureKey(INFRA_KEY)
                                              .region(REGION)
                                              .build();
    ConnectorInfoDTO connectorDTO = ConnectorInfoDTO.builder().connectorConfig(awsConnectorDTO).build();
    doReturn(awsSamInfraConfig).when(awsSamEntityHelper).getAwsSamInfraConfig(any(), any());
    doReturn(connectorDTO).when(awsSamEntityHelper).getConnectorInfoDTO(any(), any());
    byte[] bytes = {70};
    when(kryoSerializer.asBytes(any())).thenReturn(bytes);
    when(kryoSerializer.asDeflatedBytes(any())).thenReturn(bytes);
    PerpetualTaskExecutionBundle executionBundle = awsSamInstanceSyncPerpetualTaskHandler.getExecutionBundle(
        infrastructureMappingDTO, deploymentInfoDTOList, infrastructureOutcome);

    assertThat(executionBundle.getCapabilitiesList().size()).isEqualTo(1);
    assertThat(executionBundle.getTaskParams().getValue()).isNotNull();
  }
}
