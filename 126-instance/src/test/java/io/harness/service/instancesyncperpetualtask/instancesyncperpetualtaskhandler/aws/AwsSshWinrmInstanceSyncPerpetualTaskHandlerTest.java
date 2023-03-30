/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.aws;

import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.SshWinRmAwsInfrastructureOutcome;
import io.harness.cdng.serverless.ServerlessEntityHelper;
import io.harness.cdng.ssh.SshEntityHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.Capability;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.AwsSshWinrmDeploymentInfoDTO;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.api.NGSecretServiceV2;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.ng.core.models.Secret;
import io.harness.ng.core.models.SecretSpec;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.AwsSshInstanceSyncPerpetualTaskParamsNg;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.infra.HostConnectionTypeKind;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.apache.groovy.util.Maps;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.CDP)
public class AwsSshWinrmInstanceSyncPerpetualTaskHandlerTest extends InstancesTestBase {
  private static final String PROJECT_IDENTIFIER = "project";
  private static final String ACCOUNT_IDENTIFIER = "account";
  private static final String ORG_IDENTIFIER = "org";
  private static final String CRED_REF = "CRED_REF";
  private static final String INFRASTRUCTURE_KEY = "INFRA_KEY";
  private static final String HOST1 = "HOST1";
  private static final String HOST2 = "HOST2";
  private static final String SSH_SERVICE = ServiceSpecType.SSH;
  private static final int PORT = 1234;

  @Mock KryoSerializer kryoSerializer;
  @Mock NGSecretServiceV2 ngSecretServiceV2;
  @Mock SshEntityHelper sshEntityHelper;
  @Mock ServerlessEntityHelper serverlessEntityHelper;
  @Mock DelegateServiceGrpcClient delegateServiceGrpcClient;
  @InjectMocks AwsSshWinrmInstanceSyncPerpetualTaskHandler awsSshWinrmInstanceSyncPerpetualTaskHandler;

  AwsConnectorDTO awsConnectorDTO =
      AwsConnectorDTO.builder()
          .credential(AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS).build())
          .build();

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
                                                            .serviceIdentifier(SSH_SERVICE)
                                                            .build();
    SshWinRmAwsInfrastructureOutcome outcome = SshWinRmAwsInfrastructureOutcome.builder()
                                                   .infrastructureKey(INFRASTRUCTURE_KEY)
                                                   .credentialsRef(CRED_REF)
                                                   .hostConnectionType(HostConnectionTypeKind.PRIVATE_IP)
                                                   .tags(new HashMap<>())
                                                   .build();
    SecretSpec secretSpec = Mockito.mock(SecretSpec.class);
    doReturn(SSHKeySpecDTO.builder().port(PORT).build()).when(secretSpec).toDTO();
    doReturn(Optional.of(Secret.builder().secretSpec(secretSpec).build()))
        .when(ngSecretServiceV2)
        .get(eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), anyString());

    doReturn(ConnectorInfoDTO.builder().connectorConfig(awsConnectorDTO).build())
        .when(sshEntityHelper)
        .getConnectorInfoDTO(eq(outcome), any(BaseNGAccess.class));

    byte[] bytes = {70};

    AwsSshInstanceSyncPerpetualTaskParamsNg params = AwsSshInstanceSyncPerpetualTaskParamsNg.newBuilder()
                                                         .addHosts(HOST1)
                                                         .addHosts(HOST2)
                                                         .setAccountId(ACCOUNT_IDENTIFIER)
                                                         .setServiceType(SSH_SERVICE)
                                                         .setInfrastructureKey(INFRASTRUCTURE_KEY)
                                                         .setInfraDelegateConfig(ByteString.copyFrom(bytes))
                                                         .setHostConnectionType(HostConnectionTypeKind.PRIVATE_IP)
                                                         .build();

    List<ExecutionCapability> expectedExecutionCapabilityList =
        AwsCapabilityHelper.fetchRequiredExecutionCapabilities(awsConnectorDTO, null);
    when(kryoSerializer.asBytes(any())).thenReturn(bytes);
    when(kryoSerializer.asDeflatedBytes(any())).thenReturn(bytes);
    when(delegateServiceGrpcClient.isTaskTypeSupported(any(), any())).thenReturn(false);
    Any perpetualTaskPack = Any.pack(params);

    PerpetualTaskExecutionBundle.Builder builder = PerpetualTaskExecutionBundle.newBuilder();
    expectedExecutionCapabilityList.forEach(executionCapability
        -> builder.addCapabilities(Capability.newBuilder().setKryoCapability(ByteString.copyFrom(bytes)).build())
               .build());
    PerpetualTaskExecutionBundle expectedPerpetualTaskExecutionBundle =
        builder.setTaskParams(perpetualTaskPack)
            .putAllSetupAbstractions(Maps.of(NG, "true", OWNER, ORG_IDENTIFIER + "/" + PROJECT_IDENTIFIER))
            .build();
    PerpetualTaskExecutionBundle executionBundle = awsSshWinrmInstanceSyncPerpetualTaskHandler.getExecutionBundle(
        infrastructureMappingDTO, Arrays.asList(getDeploymentInfoDto(HOST1), getDeploymentInfoDto(HOST2)), outcome);
    assertThat(executionBundle).isEqualTo(expectedPerpetualTaskExecutionBundle);
  }

  private AwsSshWinrmDeploymentInfoDTO getDeploymentInfoDto(String host) {
    return AwsSshWinrmDeploymentInfoDTO.builder()
        .infrastructureKey(INFRASTRUCTURE_KEY)
        .serviceType(SSH_SERVICE)
        .host(host)
        .build();
  }
}
