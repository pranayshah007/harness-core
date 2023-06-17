/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.rule.OwnerRule.ANSHUL;

import static software.wings.beans.GcpKubernetesInfrastructureMapping.Builder.aGcpKubernetesInfrastructureMapping;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.WorkflowStandardParams.Builder.aWorkflowStandardParams;
import static software.wings.sm.states.KubernetesSwapServiceSelectors.KUBERNETES_SWAP_SERVICE_SELECTORS_COMMAND_NAME;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.CLUSTER_NAME;
import static software.wings.utils.WingsTestConstants.COMPUTE_PROVIDER_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.STATE_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.api.KubernetesSwapServiceSelectorsExecutionData;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.k8s.K8sStateHelper;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class KubernetesSwapServiceSelectorsTest extends WingsBaseTest {
  @Mock private EnvironmentService environmentService;
  @Mock private AppService appService;
  @Mock private ActivityService activityService;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private K8sStateHelper k8sStateHelper;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;
  @Inject KryoSerializer kryoSerializer;

  @InjectMocks
  KubernetesSwapServiceSelectors kubernetesSwapServiceSelectors =
      new KubernetesSwapServiceSelectors(KUBERNETES_SWAP_SERVICE_SELECTORS_COMMAND_NAME);

  @InjectMocks
  private WorkflowStandardParams workflowStandardParams =
      aWorkflowStandardParams().withAppId(APP_ID).withEnvId(ENV_ID).build();

  private StateExecutionInstance stateExecutionInstance =
      aStateExecutionInstance()
          .displayName(STATE_NAME)
          .addContextElement(workflowStandardParams)
          .addStateExecutionData(KubernetesSwapServiceSelectorsExecutionData.builder().build())
          .build();

  private InfrastructureMapping infrastructureMapping = aGcpKubernetesInfrastructureMapping()
                                                            .withUuid(INFRA_MAPPING_ID)
                                                            .withClusterName(CLUSTER_NAME)
                                                            .withComputeProviderSettingId(COMPUTE_PROVIDER_ID)
                                                            .withDeploymentType(DeploymentType.KUBERNETES.name())
                                                            .build();

  private ExecutionContextImpl context;

  @Before
  public void setup() {
    context = new ExecutionContextImpl(stateExecutionInstance);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    on(kubernetesSwapServiceSelectors).set("kryoSerializer", kryoSerializer);

    KubernetesSwapServiceSelectorsResponse kubernetesSwapServiceSelectorsResponse =
        KubernetesSwapServiceSelectorsResponse.builder().executionStatus(ExecutionStatus.SUCCESS).build();

    Map<String, ResponseData> response = new HashMap<>();
    response.put("activityId", kubernetesSwapServiceSelectorsResponse);
    ExecutionResponse executionResponse = kubernetesSwapServiceSelectors.handleAsyncResponse(context, response);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    verify(activityService, times(1)).updateStatus("activityId", APP_ID, ExecutionStatus.SUCCESS);
    verify(sweepingOutputService, times(1)).save(any(SweepingOutputInstance.class));
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExecute() {
    on(kubernetesSwapServiceSelectors).set("service1", "service1");
    on(kubernetesSwapServiceSelectors).set("service2", "service2");
    on(kubernetesSwapServiceSelectors).set("rollback", true);

    on(context).set("infrastructureMappingService", infrastructureMappingService);
    on(context).set("stateExecutionInstance", stateExecutionInstance);
    on(context).set("sweepingOutputService", sweepingOutputService);

    when(appService.get(APP_ID)).thenReturn(Application.Builder.anApplication().uuid(APP_ID).build());
    when(environmentService.get(APP_ID, ENV_ID, false))
        .thenReturn(Environment.Builder.anEnvironment().uuid(ENV_ID).build());
    when(infrastructureMappingService.get(any(), any())).thenReturn(infrastructureMapping);
    when(sweepingOutputService.find(any())).thenReturn(null);

    ExecutionResponse executionResponse = kubernetesSwapServiceSelectors.execute(context);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SKIPPED);
  }
}
