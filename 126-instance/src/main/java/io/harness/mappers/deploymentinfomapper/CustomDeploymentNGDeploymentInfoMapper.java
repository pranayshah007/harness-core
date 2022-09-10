package io.harness.mappers.deploymentinfomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.deploymentinfo.CustomDeploymentNGDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.CustomDeploymentNGDeploymentInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class CustomDeploymentNGDeploymentInfoMapper {
  public CustomDeploymentNGDeploymentInfoDTO toDTO(CustomDeploymentNGDeploymentInfo customDeploymentNGDeploymentInfo) {
    return CustomDeploymentNGDeploymentInfoDTO.builder()
        .instanceFetchScript(customDeploymentNGDeploymentInfo.getInstanceFetchScript())
        .instanceFetchScriptHash(customDeploymentNGDeploymentInfo.getInstanceFetchScriptHash())
        .artifactBuildNum(customDeploymentNGDeploymentInfo.getArtifactBuildNum())
        .artifactName(customDeploymentNGDeploymentInfo.getArtifactName())
        .artifactSourceName(customDeploymentNGDeploymentInfo.getArtifactSourceName())
        .artifactStreamId(customDeploymentNGDeploymentInfo.getArtifactStreamId())
        .scriptOutput(customDeploymentNGDeploymentInfo.getScriptOutput())
        .tags(customDeploymentNGDeploymentInfo.getTags())
        .build();
  }

  public CustomDeploymentNGDeploymentInfo toEntity(
      CustomDeploymentNGDeploymentInfoDTO customDeploymentNGDeploymentInfoDTO) {
    return CustomDeploymentNGDeploymentInfo.builder()
        .instanceFetchScript(customDeploymentNGDeploymentInfoDTO.getInstanceFetchScript())
        .artifactBuildNum(customDeploymentNGDeploymentInfoDTO.getArtifactBuildNum())
        .artifactName(customDeploymentNGDeploymentInfoDTO.getArtifactName())
        .artifactSourceName(customDeploymentNGDeploymentInfoDTO.getArtifactSourceName())
        .artifactStreamId(customDeploymentNGDeploymentInfoDTO.getArtifactStreamId())
        .instanceFetchScriptHash(customDeploymentNGDeploymentInfoDTO.getInstanceFetchScriptHash())
        .scriptOutput(customDeploymentNGDeploymentInfoDTO.getScriptOutput())
        .tags(customDeploymentNGDeploymentInfoDTO.getTags())
        .build();
  }
}
