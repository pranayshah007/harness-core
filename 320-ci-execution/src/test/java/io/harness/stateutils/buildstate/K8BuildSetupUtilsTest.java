/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.stateutils.buildstate;

import static io.harness.common.BuildEnvironmentConstants.DRONE_AWS_REGION;
import static io.harness.common.BuildEnvironmentConstants.DRONE_REMOTE_URL;
import static io.harness.common.CIExecutionConstants.ACCESS_KEY_MINIO_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_ACCOUNT_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_BUILD_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_CI_INDIRECT_LOG_UPLOAD_FF;
import static io.harness.common.CIExecutionConstants.HARNESS_EXECUTION_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_ORG_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_PROJECT_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_STAGE_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.LOG_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.common.CIExecutionConstants.LOG_SERVICE_TOKEN_VARIABLE;
import static io.harness.common.CIExecutionConstants.SECRET_KEY_MINIO_VARIABLE;
import static io.harness.common.CIExecutionConstants.STEP_MOUNT_PATH;
import static io.harness.common.CIExecutionConstants.STEP_VOLUME;
import static io.harness.common.CIExecutionConstants.STEP_WORK_DIR;
import static io.harness.common.CIExecutionConstants.TI_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.common.CIExecutionConstants.TI_SERVICE_TOKEN_VARIABLE;
import static io.harness.common.STOExecutionConstants.STO_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.common.STOExecutionConstants.STO_SERVICE_TOKEN_VARIABLE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.VISTAAR;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.sweepingoutputs.StepTaskDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.ci.beans.entities.LogServiceConfig;
import io.harness.ci.beans.entities.TIServiceConfig;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.CIK8PodParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.SecretVariableDTO;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.GeneralException;
import io.harness.executionplan.CIExecutionPlanTestHelper;
import io.harness.executionplan.CIExecutionTestBase;
import io.harness.ff.CIFeatureFlagService;
import io.harness.logserviceclient.CILogServiceUtils;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.sto.beans.entities.STOServiceConfig;
import io.harness.stoserviceclient.STOServiceUtils;
import io.harness.tiserviceclient.TIServiceUtils;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

@Slf4j
public class K8BuildSetupUtilsTest extends CIExecutionTestBase {
  @Inject private CIExecutionPlanTestHelper ciExecutionPlanTestHelper;
  @Inject private K8BuildSetupUtils k8BuildSetupUtils;
  @Inject private SecretUtils secretUtils;

  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private SecretNGManagerClient secretNGManagerClient;
  @Mock private CIFeatureFlagService featureFlagService;
  @Mock private ConnectorUtils connectorUtils;
  @Mock CILogServiceUtils logServiceUtils;
  @Mock TIServiceUtils tiServiceUtils;
  @Mock STOServiceUtils stoServiceUtils;
  @Mock PipelineRbacHelper pipelineRbacHelper;
  @Mock CodebaseUtils codebaseUtils;

  @Before
  public void setUp() {
    on(k8BuildSetupUtils).set("connectorUtils", connectorUtils);
    on(secretUtils).set("secretNGManagerClient", secretNGManagerClient);
    on(secretUtils).set("secretManagerClientService", secretManagerClientService);
    on(k8BuildSetupUtils).set("secretUtils", secretUtils);
    on(k8BuildSetupUtils).set("executionSweepingOutputResolver", executionSweepingOutputResolver);
    on(k8BuildSetupUtils).set("logServiceUtils", logServiceUtils);
    on(k8BuildSetupUtils).set("featureFlagService", featureFlagService);
    on(k8BuildSetupUtils).set("tiServiceUtils", tiServiceUtils);
    on(k8BuildSetupUtils).set("stoServiceUtils", stoServiceUtils);
    on(k8BuildSetupUtils).set("pipelineRbacHelper", pipelineRbacHelper);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  @Ignore("Recreate test object after pms integration")
  public void shouldCreatePodParameters() throws Exception {
    String accountID = "account";
    String orgID = "org";
    String projectID = "project";
    int buildID = 1;
    String stageID = "stage";
    String executionID = "execution";
    String namespace = "default";

    String logEndpoint = "http://localhost:8080";
    String logToken = "token";
    LogServiceConfig logServiceConfig = LogServiceConfig.builder().baseUrl(logEndpoint).globalToken(logToken).build();
    when(logServiceUtils.getLogServiceConfig()).thenReturn(logServiceConfig);
    when(logServiceUtils.getLogServiceToken(eq(accountID))).thenReturn(logToken);

    String tiEndpoint = "http://localhost:8078";
    String tiToken = "token";

    String stoEndpoint = "http://localhost:4000";
    String stoToken = "stoToken";
    TIServiceConfig tiServiceConfig = TIServiceConfig.builder().baseUrl(tiEndpoint).globalToken(tiToken).build();
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(tiServiceConfig);
    when(tiServiceUtils.getTIServiceToken(eq(accountID))).thenReturn(tiToken);

    STOServiceConfig stoServiceConfig = STOServiceConfig.builder().baseUrl(stoEndpoint).globalToken(stoToken).build();
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(stoServiceConfig);
    when(stoServiceUtils.getSTOServiceToken(eq(accountID))).thenReturn(stoToken);
    doNothing().when(pipelineRbacHelper).checkRuntimePermissions(any(), any(), any());

    when(featureFlagService.isEnabled(FeatureName.CI_INDIRECT_LOG_UPLOAD, eq(accountID))).thenReturn(true);

    Call<ResponseDTO<SecretResponseWrapper>> getSecretCall = mock(Call.class);
    ResponseDTO<SecretResponseWrapper> responseDTO = ResponseDTO.newResponse(
        SecretResponseWrapper.builder().secret(SecretDTOV2.builder().type(SecretType.SecretText).build()).build());
    when(getSecretCall.execute()).thenReturn(Response.success(responseDTO));
    when(secretNGManagerClient.getSecret(any(), any(), any(), any())).thenReturn(getSecretCall);
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));
    when(executionSweepingOutputResolver.resolve(any(), any())).thenReturn(StepTaskDetails.builder().build());
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(ConnectorDetails.builder().build());
    when(connectorUtils.getConnectorDetailsWithConversionInfo(any(), any()))
        .thenReturn(ConnectorDetails.builder().identifier("connectorId").build());

    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "account");
    setupAbstractions.put("projectIdentifier", "project");
    setupAbstractions.put("orgIdentifier", "org");
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setExecutionUuid(generateUuid())
                                              .setRunSequence(buildID)
                                              .setPipelineIdentifier("pipeline")
                                              .build();
    Ambiance ambiance =
        Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).setMetadata(executionMetadata).build();

    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);

    NGAccess ngAccess =
        BaseNGAccess.builder().accountIdentifier(accountID).orgIdentifier(orgID).projectIdentifier(projectID).build();
    K8PodDetails k8PodDetails = K8PodDetails.builder().stageID(stageID).build();

    Infrastructure infrastructure =
        ciExecutionPlanTestHelper.getExpectedLiteEngineTaskInfoOnFirstPodWithSetCallbackId().getInfrastructure();

    String infraNamepsace = ((K8sDirectInfraYaml) infrastructure).getSpec().getNamespace().getValue();

    CIK8PodParams<CIK8ContainerParams> podParams = k8BuildSetupUtils.getPodParams(ngAccess, k8PodDetails,
        ciExecutionPlanTestHelper.getExpectedLiteEngineTaskInfoOnFirstPodWithSetCallbackId(), true, "workspace",
        ambiance, null, null, null, null, null, null, null, null, infraNamepsace, null, null, null, OSType.Linux);

    List<SecretVariableDetails> secretVariableDetails =
        new ArrayList<>(ciExecutionPlanTestHelper.getSecretVariableDetails());
    secretVariableDetails.add(
        SecretVariableDetails.builder()
            .secretVariableDTO(
                SecretVariableDTO.builder()
                    .type(SecretVariableDTO.Type.TEXT)
                    .name(ACCESS_KEY_MINIO_VARIABLE)
                    .secret(SecretRefData.builder().identifier(ACCESS_KEY_MINIO_VARIABLE).scope(Scope.ACCOUNT).build())
                    .build())
            .encryptedDataDetailList(singletonList(EncryptedDataDetail.builder().build()))
            .build());
    secretVariableDetails.add(
        SecretVariableDetails.builder()
            .secretVariableDTO(
                SecretVariableDTO.builder()
                    .type(SecretVariableDTO.Type.TEXT)
                    .name(SECRET_KEY_MINIO_VARIABLE)
                    .secret(SecretRefData.builder().identifier(SECRET_KEY_MINIO_VARIABLE).scope(Scope.ACCOUNT).build())
                    .build())
            .encryptedDataDetailList(singletonList(EncryptedDataDetail.builder().build()))
            .build());

    Map<String, String> stepEnvVars = new HashMap<>();
    stepEnvVars.put(LOG_SERVICE_ENDPOINT_VARIABLE, logEndpoint);
    stepEnvVars.put(LOG_SERVICE_TOKEN_VARIABLE, logToken);
    stepEnvVars.put(STO_SERVICE_ENDPOINT_VARIABLE, stoEndpoint);
    stepEnvVars.put(STO_SERVICE_TOKEN_VARIABLE, stoToken);
    stepEnvVars.put(TI_SERVICE_ENDPOINT_VARIABLE, tiEndpoint);
    stepEnvVars.put(TI_SERVICE_TOKEN_VARIABLE, tiToken);
    stepEnvVars.put(HARNESS_ACCOUNT_ID_VARIABLE, accountID);
    stepEnvVars.put(HARNESS_ORG_ID_VARIABLE, orgID);
    stepEnvVars.put(HARNESS_PROJECT_ID_VARIABLE, projectID);
    stepEnvVars.put(HARNESS_BUILD_ID_VARIABLE, String.valueOf(buildID));
    stepEnvVars.put(HARNESS_STAGE_ID_VARIABLE, stageID);
    stepEnvVars.put(HARNESS_EXECUTION_ID_VARIABLE, executionID);
    stepEnvVars.put(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF, "true");
    stepEnvVars.putAll(ciExecutionPlanTestHelper.getEnvVariables(true));

    Map<String, String> map = new HashMap<>();
    map.put(STEP_VOLUME, STEP_MOUNT_PATH);
    assertThat(podParams.getContainerParamsList().get(3))
        .isEqualToIgnoringGivenFields(ciExecutionPlanTestHelper.getRunStepCIK8Container()
                                          .volumeToMountPath(map)
                                          .workingDir(STEP_WORK_DIR)
                                          .build(),
            "envVars", "containerSecrets");
    assertThat(podParams.getContainerParamsList().get(3).getContainerSecrets().getSecretVariableDetails())
        .containsAnyElementsOf(secretVariableDetails);
    assertThat(podParams.getContainerParamsList().get(3).getEnvVars()).containsAllEntriesOf(stepEnvVars);

    assertThat(podParams.getContainerParamsList().get(4))
        .isEqualToIgnoringGivenFields(
            ciExecutionPlanTestHelper.getPluginStepCIK8Container().build(), "envVars", "containerSecrets");

    assertThat(podParams.getContainerParamsList().get(2))
        .isEqualToIgnoringGivenFields(
            ciExecutionPlanTestHelper.getGitCloneStepCIK8Container().build(), "envVars", "containerSecrets");

    assertThat(podParams.getContainerParamsList().get(1))
        .isEqualToIgnoringGivenFields(
            ciExecutionPlanTestHelper.getServiceCIK8Container(), "envVars", "containerSecrets");
  }

  @Test
  @Owner(developers = VISTAAR)
  @Category(UnitTests.class)
  @Ignore("Ignored to be fixed later")
  public void shouldNotCreatePodParameters() throws Exception {
    String accountID = "account";
    String orgID = "org";
    String projectID = "project";
    String stageID = "stage";
    String namespace = "default";

    String logEndpoint = "http://localhost:8080";
    String logToken = "token";
    LogServiceConfig logServiceConfig = LogServiceConfig.builder().baseUrl(logEndpoint).globalToken(logToken).build();
    when(logServiceUtils.getLogServiceConfig()).thenReturn(logServiceConfig);
    when(logServiceUtils.getLogServiceToken(eq(accountID))).thenThrow(new GeneralException("Could not retrieve token"));

    NGAccess ngAccess =
        BaseNGAccess.builder().accountIdentifier(accountID).orgIdentifier(orgID).projectIdentifier(projectID).build();
    K8PodDetails k8PodDetails = K8PodDetails.builder().stageID(stageID).build();
    //
    //    assertThatThrownBy(()
    //                           -> k8BuildSetupUtils.getPodParams(ngAccess, k8PodDetails,
    //                               ciExecutionPlanTestHelper.getExpectedLiteEngineTaskInfoOnFirstPod(), true, null,
    //                               true, "workspace", null))
    //        .isInstanceOf(Exception.class);
    //
    //    verify(logServiceUtils, times(1)).getLogServiceConfig();
    //    verify(logServiceUtils, times(1)).getLogServiceToken(accountID);
  }

  @Test
  @Owner(developers = VISTAAR)
  @Category(UnitTests.class)
  @Ignore("Ignored to be fixed later")
  public void shouldGetAwsCodeCommitGitEnvVariables() {
    ConnectorDetails gitConnector =
        ConnectorDetails.builder()
            .connectorConfig(
                ciExecutionPlanTestHelper.getAwsCodeCommitConnectorDTO().getConnectorInfo().getConnectorConfig())
            .connectorType(
                ciExecutionPlanTestHelper.getAwsCodeCommitConnectorDTO().getConnectorInfo().getConnectorType())
            .build();
    doNothing().when(pipelineRbacHelper).checkRuntimePermissions(any(), any(), any());
    CodeBase codeBase = CodeBase.builder().repoName(ParameterField.createValueField("test")).build();
    Map<String, String> gitEnvVariables = codebaseUtils.getGitEnvVariables(gitConnector, codeBase);
    assertThat(gitEnvVariables).containsKeys(DRONE_REMOTE_URL, DRONE_AWS_REGION);
    assertThat(gitEnvVariables.get(DRONE_REMOTE_URL))
        .isEqualTo("https://git-codecommit.eu-central-1.amazonaws.com/v1/repos/test.git");
    assertThat(gitEnvVariables.get(DRONE_AWS_REGION)).isEqualTo("eu-central-1");
  }
}
