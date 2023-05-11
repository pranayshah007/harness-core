/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.stateutils.buildstate.providers;

import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_CI_INDIRECT_LOG_UPLOAD_FF;
import static io.harness.ci.commonconstants.CIExecutionConstants.LOG_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.LOG_SERVICE_TOKEN_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.SETUP_ADDON_CONTAINER_NAME;
import static io.harness.ci.commonconstants.CIExecutionConstants.TI_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.TI_SERVICE_TOKEN_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.UNIX_SETUP_ADDON_ARGS;
import static io.harness.common.STOExecutionConstants.STO_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.common.STOExecutionConstants.STO_SERVICE_TOKEN_VARIABLE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.ci.pod.CICommonConstants.LITE_ENGINE_CONTAINER_NAME;
import static io.harness.rule.OwnerRule.ALEKSANDAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.providers.InternalContainerParamsProvider;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class InternalContainerParamsProviderTest extends CIExecutionTestBase {
  @Inject InternalContainerParamsProvider internalContainerParamsProvider;
  @Mock private CIFeatureFlagService featureFlagService;

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void getSetupAddonContainerParams() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();

    CIK8ContainerParams containerParams = internalContainerParamsProvider.getSetupAddonContainerParams(
        connectorDetails, null, "workspace", null, "account", OSType.Linux);

    assertThat(containerParams.getName()).isEqualTo(SETUP_ADDON_CONTAINER_NAME);
    assertThat(containerParams.getContainerType()).isEqualTo(CIContainerType.ADD_ON);
    assertThat(containerParams.getArgs()).isEqualTo(Collections.singletonList(UNIX_SETUP_ADDON_ARGS));
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void getLiteEngineContainerParams() {
    int buildID = 1;
    on(internalContainerParamsProvider).set("featureFlagService", featureFlagService);
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "account");
    setupAbstractions.put("projectIdentifier", "project");
    setupAbstractions.put("orgIdentifier", "org");
    when(featureFlagService.isEnabled(FeatureName.CI_INDIRECT_LOG_UPLOAD, "account")).thenReturn(true);
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setExecutionUuid(generateUuid())
                                              .setRunSequence(buildID)
                                              .setPipelineIdentifier("pipeline")
                                              .build();
    Ambiance ambiance =
        Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).setMetadata(executionMetadata).build();
    K8PodDetails k8PodDetails = K8PodDetails.builder().stageID("stage").build();

    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);

    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    Map<String, ConnectorDetails> publishArtifactConnectorDetailsMap = new HashMap<>();
    String logSecret = "secret";
    String logEndpoint = "http://localhost:8079";
    Map<String, String> logEnvVars = new HashMap<>();
    logEnvVars.put(LOG_SERVICE_ENDPOINT_VARIABLE, logEndpoint);
    logEnvVars.put(LOG_SERVICE_TOKEN_VARIABLE, logSecret);

    String tiToken = "token";
    String tiEndpoint = "http://localhost:8078";
    Map<String, String> tiEnvVars = new HashMap<>();
    tiEnvVars.put(TI_SERVICE_ENDPOINT_VARIABLE, tiEndpoint);
    tiEnvVars.put(TI_SERVICE_TOKEN_VARIABLE, tiToken);

    String stoToken = "token";
    String stoEndpoint = "http://localhost:4000";
    Map<String, String> stoEnvVars = new HashMap<>();
    stoEnvVars.put(STO_SERVICE_ENDPOINT_VARIABLE, stoEndpoint);
    stoEnvVars.put(STO_SERVICE_TOKEN_VARIABLE, stoToken);
    Map<String, String> volumeToMountPath = new HashMap<>();

    Integer stageCpuRequest = 500;
    Integer stageMemoryRequest = 200;

    CIK8ContainerParams containerParams = internalContainerParamsProvider.getLiteEngineContainerParams(connectorDetails,
        publishArtifactConnectorDetailsMap, k8PodDetails, stageCpuRequest, stageMemoryRequest, logEnvVars, tiEnvVars,
        stoEnvVars, volumeToMountPath, "/step-exec/workspace", null, "test", ambiance, null);

    assertThat(containerParams.getName()).isEqualTo(LITE_ENGINE_CONTAINER_NAME);
    assertThat(containerParams.getContainerType()).isEqualTo(CIContainerType.LITE_ENGINE);
    assertThat(containerParams.getEnvVars().containsKey(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF));
    assertThat(containerParams.getEnvVars().get(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF)).isEqualTo("true");
  }
}
