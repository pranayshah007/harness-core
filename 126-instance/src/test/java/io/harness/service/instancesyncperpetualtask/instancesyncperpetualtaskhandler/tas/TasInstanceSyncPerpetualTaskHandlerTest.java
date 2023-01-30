/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.tas;

import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.cdng.tas.TasEntityHelper;
import io.harness.delegate.Capability;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.pcf.request.CfInstanceSyncRequestNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.TasDeploymentInfoDTO;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.TasDeploymentRelease;
import io.harness.perpetualtask.instancesync.TasInstanceSyncPerpetualTaskParams;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.List;
import org.apache.groovy.util.Maps;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class TasInstanceSyncPerpetualTaskHandlerTest extends InstancesTestBase {
  private static final String PROJECT_IDENTIFIER = "project";
  private static final String ACCOUNT_IDENTIFIER = "account";
  private static final String ORG_IDENTIFIER = "org";
  private static final String CONNECTOR = "connector";
  private static final String APPLICATION_NAME = "application";
  private static final String APPLICATION_ID = "app_Id";
  private static final String SPACE = "space";

  @Mock TasEntityHelper tasEntityHelper;
  @Mock KryoSerializer kryoSerializer;
  @InjectMocks TasInstanceSyncPerpetualTaskHandler tasInstanceSyncPerpetualTaskHandler;

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetExecutionBundle() {
    InfrastructureMappingDTO infrastructureMappingDTO = InfrastructureMappingDTO.builder()
                                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                            .infrastructureKind("Tas")
                                                            .connectorRef(CONNECTOR)
                                                            .envIdentifier("env")
                                                            .serviceIdentifier("service")
                                                            .infrastructureKey("key")
                                                            .build();

    DeploymentInfoDTO deploymentInfoDTO =
        TasDeploymentInfoDTO.builder().applicationName(APPLICATION_NAME).applicationGuid(APPLICATION_ID).build();
    List<DeploymentInfoDTO> deploymentInfoDTOList = Collections.singletonList(deploymentInfoDTO);
    InfrastructureOutcome infrastructureOutcome = TanzuApplicationServiceInfrastructureOutcome.builder()
                                                      .connectorRef(CONNECTOR)
                                                      .organization(ORG_IDENTIFIER)
                                                      .space(SPACE)
                                                      .build();
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder()
                                        .tasConnectorDTO(TasConnectorDTO.builder().build())
                                        .organization(ORG_IDENTIFIER)
                                        .space(SPACE)
                                        .build();
    byte[] bytes = {70};
    byte[] bytes3 = {72};
    TasDeploymentRelease tasDeploymentRelease = TasDeploymentRelease.newBuilder()
                                                    .setTasInfraConfig(ByteString.copyFrom(bytes))
                                                    .setApplicationName(APPLICATION_NAME)
                                                    .build();
    List<TasDeploymentRelease> tasDeploymentReleaseList = List.of(tasDeploymentRelease);
    TasInstanceSyncPerpetualTaskParams tasInstanceSyncPerpetualTaskParams =
        TasInstanceSyncPerpetualTaskParams.newBuilder()
            .setAccountId(ACCOUNT_IDENTIFIER)
            .addAllTasDeploymentReleaseList(tasDeploymentReleaseList)
            .build();
    CfInstanceSyncRequestNG tasInstanceSyncRequest = CfInstanceSyncRequestNG.builder()
                                                         .applicationName(APPLICATION_NAME)
                                                         .tasInfraConfig(tasInfraConfig)
                                                         .accountId(ACCOUNT_IDENTIFIER)
                                                         .build();
    List<ExecutionCapability> expectedExecutionCapabilityList =
        tasInstanceSyncRequest.fetchRequiredExecutionCapabilities(null);

    when(tasEntityHelper.getTasInfraConfig(any(), any())).thenReturn(tasInfraConfig);
    when(kryoSerializer.asBytes(any())).thenReturn(bytes);
    PerpetualTaskExecutionBundle.Builder builder = PerpetualTaskExecutionBundle.newBuilder();
    expectedExecutionCapabilityList.forEach(executionCapability
        -> builder.addCapabilities(Capability.newBuilder().setKryoCapability(ByteString.copyFrom(bytes3)).build())
               .build());
    PerpetualTaskExecutionBundle expectedPerpetualTaskExecutionBundle =
        builder.setTaskParams(Any.pack(tasInstanceSyncPerpetualTaskParams))
            .putAllSetupAbstractions(Maps.of(NG, "true", OWNER, ORG_IDENTIFIER + "/" + PROJECT_IDENTIFIER))
            .build();
    PerpetualTaskExecutionBundle perpetualTaskExecutionBundle = tasInstanceSyncPerpetualTaskHandler.getExecutionBundle(
        infrastructureMappingDTO, deploymentInfoDTOList, infrastructureOutcome);
    assertThat(perpetualTaskExecutionBundle).isEqualTo(expectedPerpetualTaskExecutionBundle);
  }
}
