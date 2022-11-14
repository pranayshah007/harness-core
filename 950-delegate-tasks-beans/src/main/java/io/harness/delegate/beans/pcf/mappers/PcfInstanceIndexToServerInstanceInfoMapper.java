package io.harness.delegate.beans.pcf.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.PcfNGServerInstanceInfo;
import io.harness.delegate.task.pcf.response.PcfInfraConfig;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.cloudfoundry.operations.applications.ApplicationDetail;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class PcfInstanceIndexToServerInstanceInfoMapper {
  public List<ServerInstanceInfo> toServerInstanceInfoList(
      List<String> instanceIndices, PcfInfraConfig pcfInfraConfig, ApplicationDetail applicationDetail) {
    return instanceIndices.stream()
        .map(index
            -> PcfNGServerInstanceInfo.builder()
                   .instanceIndex(index)
                   .pcfApplicationGuid(applicationDetail.getId())
                   .pcfApplicationName(applicationDetail.getName())
                   .organization(pcfInfraConfig.getOrganization())
                   .space(pcfInfraConfig.getSpace())
                   .id(applicationDetail.getId() + ":" + index)
                   .build())
        .collect(Collectors.toList());
  }
}
