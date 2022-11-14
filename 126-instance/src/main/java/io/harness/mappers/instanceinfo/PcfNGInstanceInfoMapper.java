package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.PcfNGInstanceInfoDTO;
import io.harness.entities.instanceinfo.PcfNGInstanceInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class PcfNGInstanceInfoMapper {
  public PcfNGInstanceInfoDTO toDTO(PcfNGInstanceInfo pcfNGInstanceInfo) {
    return PcfNGInstanceInfoDTO.builder()
        .id(pcfNGInstanceInfo.getId())
        .instanceIndex(pcfNGInstanceInfo.getInstanceIndex())
        .space(pcfNGInstanceInfo.getSpace())
        .organization(pcfNGInstanceInfo.getOrganization())
        .pcfApplicationName(pcfNGInstanceInfo.getPcfApplicationName())
        .pcfApplicationGuid(pcfNGInstanceInfo.getPcfApplicationGuid())
        .build();
  }
  public PcfNGInstanceInfo toEntity(PcfNGInstanceInfoDTO pcfNGInstanceInfoDTO) {
    return PcfNGInstanceInfo.builder()
        .id(pcfNGInstanceInfoDTO.getId())
        .instanceIndex(pcfNGInstanceInfoDTO.getInstanceIndex())
        .space(pcfNGInstanceInfoDTO.getSpace())
        .organization(pcfNGInstanceInfoDTO.getOrganization())
        .pcfApplicationName(pcfNGInstanceInfoDTO.getPcfApplicationName())
        .pcfApplicationGuid(pcfNGInstanceInfoDTO.getPcfApplicationGuid())
        .build();
  }
}
