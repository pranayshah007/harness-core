package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.pcf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.pcf.PcfEntityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.pcf.PcfDeploymentReleaseData;
import io.harness.delegate.task.pcf.request.CfInstanceSyncRequestNG;
import io.harness.delegate.task.pcf.response.PcfInfraConfig;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.PcfNGDeploymentInfoDTO;
import io.harness.ng.core.BaseNGAccess;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.PcfDeploymentRelease;
import io.harness.perpetualtask.instancesync.PcfNGInstanceSyncPerpetualTaskParams;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.InstanceSyncPerpetualTaskHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDP)
public class PcfInstanceSyncPerpetualTaskHandler extends InstanceSyncPerpetualTaskHandler {
  @Inject private PcfEntityHelper pcfEntityHelper;
  @Override
  public PerpetualTaskExecutionBundle getExecutionBundle(InfrastructureMappingDTO infrastructureMappingDTO,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome) {
    List<PcfDeploymentReleaseData> deploymentReleaseDataList =
        populateDeploymentReleaseList(infrastructureMappingDTO, deploymentInfoDTOList, infrastructureOutcome);

    Any perpetualTaskPack = packPcfInstanceSyncPerpetualTaskParams(
        infrastructureMappingDTO.getAccountIdentifier(), deploymentReleaseDataList);

    List<ExecutionCapability> executionCapabilities = getExecutionCapabilities(deploymentReleaseDataList);

    return createPerpetualTaskExecutionBundle(perpetualTaskPack, executionCapabilities,
        infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier());
  }

  private List<PcfDeploymentReleaseData> populateDeploymentReleaseList(
      InfrastructureMappingDTO infrastructureMappingDTO, List<DeploymentInfoDTO> deploymentInfoDTOList,
      InfrastructureOutcome infrastructureOutcome) {
    return deploymentInfoDTOList.stream()
        .filter(Objects::nonNull)
        .map(PcfNGDeploymentInfoDTO.class ::cast)
        .map(deploymentInfoDTO
            -> toPcfDeploymentReleaseData(infrastructureMappingDTO, deploymentInfoDTO, infrastructureOutcome))
        .collect(Collectors.toList());
  }

  private PcfDeploymentReleaseData toPcfDeploymentReleaseData(InfrastructureMappingDTO infrastructureMappingDTO,
      PcfNGDeploymentInfoDTO deploymentInfoDTO, InfrastructureOutcome infrastructureOutcome) {
    PcfInfraConfig pcfInfraConfig = getPcfInfraConfig(infrastructureMappingDTO, infrastructureOutcome);
    return PcfDeploymentReleaseData.builder()
        .pcfInfraConfig(pcfInfraConfig)
        .applicationName(deploymentInfoDTO.getApplicationName())
        .build();
  }

  private PcfInfraConfig getPcfInfraConfig(
      InfrastructureMappingDTO infrastructure, InfrastructureOutcome infrastructureOutcome) {
    BaseNGAccess baseNGAccess = getBaseNGAccess(infrastructure);
    return pcfEntityHelper.getPcfInfraConfig(infrastructureOutcome, baseNGAccess);
  }

  private BaseNGAccess getBaseNGAccess(InfrastructureMappingDTO infrastructureMappingDTO) {
    return BaseNGAccess.builder()
        .accountIdentifier(infrastructureMappingDTO.getAccountIdentifier())
        .orgIdentifier(infrastructureMappingDTO.getOrgIdentifier())
        .projectIdentifier(infrastructureMappingDTO.getProjectIdentifier())
        .build();
  }

  private Any packPcfInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<PcfDeploymentReleaseData> deploymentReleaseData) {
    return Any.pack(createPcfInstanceSyncPerpetualTaskParams(accountIdentifier, deploymentReleaseData));
  }

  private PcfNGInstanceSyncPerpetualTaskParams createPcfInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<PcfDeploymentReleaseData> deploymentReleaseData) {
    return PcfNGInstanceSyncPerpetualTaskParams.newBuilder()
        .setAccountId(accountIdentifier)
        .addAllPcfDeploymentReleaseList(toPcfDeploymentReleaseList(deploymentReleaseData))
        .build();
  }

  private List<PcfDeploymentRelease> toPcfDeploymentReleaseList(List<PcfDeploymentReleaseData> deploymentReleaseData) {
    return deploymentReleaseData.stream().map(this::toPcfDeploymentRelease).collect(Collectors.toList());
  }

  private PcfDeploymentRelease toPcfDeploymentRelease(PcfDeploymentReleaseData releaseData) {
    return PcfDeploymentRelease.newBuilder()
        .setApplicationName(releaseData.getApplicationName())
        .setPcfInfraConfig(ByteString.copyFrom(kryoSerializer.asBytes(releaseData.getPcfInfraConfig())))
        .build();
  }

  private List<ExecutionCapability> getExecutionCapabilities(List<PcfDeploymentReleaseData> deploymentReleaseDataList) {
    Optional<PcfDeploymentReleaseData> deploymentReleaseSample = deploymentReleaseDataList.stream().findFirst();
    if (!deploymentReleaseSample.isPresent()) {
      return Collections.emptyList();
    }
    return toPcfInstanceSyncRequest(deploymentReleaseSample.get()).fetchRequiredExecutionCapabilities(null);
  }

  private CfInstanceSyncRequestNG toPcfInstanceSyncRequest(PcfDeploymentReleaseData pcfDeploymentReleaseData) {
    return CfInstanceSyncRequestNG.builder()
        .pcfInfraConfig(pcfDeploymentReleaseData.getPcfInfraConfig())
        .applicationName(pcfDeploymentReleaseData.getApplicationName())
        .build();
  }
}
