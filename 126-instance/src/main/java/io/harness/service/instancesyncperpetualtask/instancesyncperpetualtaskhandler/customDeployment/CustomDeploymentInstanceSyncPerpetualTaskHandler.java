package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.customDeployment;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.expressionEvaluator.ShellScriptSecretExpressionEvaluator;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.CustomDeploymentNGDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.CustomDeploymentNGInstanceSyncPerpetualTaskParams;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.InstanceSyncPerpetualTaskHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDP)
public class CustomDeploymentInstanceSyncPerpetualTaskHandler extends InstanceSyncPerpetualTaskHandler {
  public static final String OUTPUT_PATH_KEY = "INSTANCE_OUTPUT_PATH";
  @Inject private SecretManagerClientService secretManagerClientService;

  @Override
  public PerpetualTaskExecutionBundle getExecutionBundle(InfrastructureMappingDTO infrastructureMappingDTO,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome) {
    CustomDeploymentNGDeploymentInfoDTO customDeploymentNGDeploymentInfoDTO =
        (CustomDeploymentNGDeploymentInfoDTO) deploymentInfoDTOList.get(0);
    Any perpetualTaskPack = packCustomDeploymentInstanceSyncPerpetualTaskParams(
        infrastructureMappingDTO, infrastructureOutcome, customDeploymentNGDeploymentInfoDTO);

    List<ExecutionCapability> executionCapabilities = getExecutionCapability(deploymentInfoDTOList);

    return createPerpetualTaskExecutionBundle(perpetualTaskPack, executionCapabilities,
        infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier());
  }
  private Any packCustomDeploymentInstanceSyncPerpetualTaskParams(InfrastructureMappingDTO infrastructureMappingDTO,
      InfrastructureOutcome infrastructureOutcome,
      CustomDeploymentNGDeploymentInfoDTO customDeploymentNGDeploymentInfoDTO) {
    return Any.pack(createCustomDeploymentInstanceSyncPerpetualTaskParams(
        infrastructureMappingDTO, infrastructureOutcome, customDeploymentNGDeploymentInfoDTO));
  }
  private CustomDeploymentNGInstanceSyncPerpetualTaskParams createCustomDeploymentInstanceSyncPerpetualTaskParams(
      InfrastructureMappingDTO infrastructureMappingDTO, InfrastructureOutcome infrastructureOutcome,
      CustomDeploymentNGDeploymentInfoDTO customDeploymentNGDeploymentInfoDTO) {
    ShellScriptSecretExpressionEvaluator shellScriptSecretExpressionEvaluator =
        new ShellScriptSecretExpressionEvaluator(infrastructureMappingDTO.getAccountIdentifier(),
            infrastructureMappingDTO.getProjectIdentifier(), infrastructureMappingDTO.getOrgIdentifier(), null, null,
            secretManagerClientService, null);
    Object resolvedScript = shellScriptSecretExpressionEvaluator.evaluateExpression(
        customDeploymentNGDeploymentInfoDTO.getInstanceFetchScript());

    return CustomDeploymentNGInstanceSyncPerpetualTaskParams.newBuilder()
        .setScript((String) resolvedScript)
        .setAccountId(infrastructureMappingDTO.getAccountIdentifier())
        .setOutputPathKey(OUTPUT_PATH_KEY)
        .build();
  }

  List<ExecutionCapability> getExecutionCapability(List<DeploymentInfoDTO> deploymentInfoDTOList) {
    return deploymentInfoDTOList.stream()
        .filter(Objects::nonNull)
        .map(CustomDeploymentNGDeploymentInfoDTO.class ::cast)
        .map(this::getSelectorCapability)
        .collect(Collectors.toList());
  }

  SelectorCapability getSelectorCapability(CustomDeploymentNGDeploymentInfoDTO deploymentPackageInfo) {
    List<String> tagsInDeploymentInfo = deploymentPackageInfo.getTags();
    Set<String> tags = new HashSet<>(tagsInDeploymentInfo);
    return SelectorCapability.builder().selectors(tags).build();
  }
}
