package io.harness.service.instancesynchandler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.PcfNGServerInstanceInfo;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.PcfNGDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.PcfNGInstanceInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.models.infrastructuredetails.PcfInfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.perpetualtask.PerpetualTaskType;

import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class PcfInstanceSyncHandler extends AbstractInstanceSyncHandler {
  @Override
  public String getPerpetualTaskType() {
    return PerpetualTaskType.PCF_INSTANCE_SYNC_NG;
  }

  @Override
  public InstanceType getInstanceType() {
    return InstanceType.PCF_INSTANCE;
  }

  @Override
  public String getInfrastructureKind() {
    return InfrastructureKind.TAS;
  }

  @Override
  public InfrastructureDetails getInfrastructureDetails(InstanceInfoDTO instanceInfoDTO) {
    if (!(instanceInfoDTO instanceof PcfNGInstanceInfoDTO)) {
      throw new InvalidArgumentsException(
          Pair.of("instanceInfoDTO", "Must be instance of CustomDeploymentInstanceInfoDTO"));
    }
    PcfNGInstanceInfoDTO pcfNGInstanceInfoDTO = (PcfNGInstanceInfoDTO) instanceInfoDTO;
    return PcfInfrastructureDetails.builder()
        .organization(pcfNGInstanceInfoDTO.getOrganization())
        .pcfApplicationName(pcfNGInstanceInfoDTO.getPcfApplicationName())
        .space(pcfNGInstanceInfoDTO.getSpace())
        .build();
  }
  @Override
  protected InstanceInfoDTO getInstanceInfoForServerInstance(ServerInstanceInfo serverInstanceInfo) {
    if (!(serverInstanceInfo instanceof PcfNGServerInstanceInfo)) {
      throw new InvalidArgumentsException(Pair.of("serverInstanceInfo", "Must be instance of K8sServerInstanceInfo"));
    }

    PcfNGServerInstanceInfo pcfNGServerInstanceInfo = (PcfNGServerInstanceInfo) serverInstanceInfo;

    return PcfNGInstanceInfoDTO.builder()
        .instanceIndex(pcfNGServerInstanceInfo.getInstanceIndex())
        .pcfApplicationGuid(pcfNGServerInstanceInfo.getPcfApplicationGuid())
        .pcfApplicationName(pcfNGServerInstanceInfo.getPcfApplicationName())
        .organization(pcfNGServerInstanceInfo.getOrganization())
        .space(pcfNGServerInstanceInfo.getSpace())
        .id(pcfNGServerInstanceInfo.getId())
        .build();
  }

  @Override
  public DeploymentInfoDTO getDeploymentInfo(
      InfrastructureOutcome infrastructureOutcome, List<ServerInstanceInfo> serverInstanceInfoList) {
    if (isEmpty(serverInstanceInfoList)) {
      throw new InvalidArgumentsException("Parameter serverInstanceInfoList cannot be null or empty");
    }
    if (!(infrastructureOutcome instanceof TanzuApplicationServiceInfrastructureOutcome)) {
      throw new InvalidArgumentsException(
          Pair.of("infrastructureOutcome", "Must be instance of CustomDeploymentInfrastructureOutcome"));
    }
    if (!(serverInstanceInfoList.get(0) instanceof PcfNGServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of CustomDeploymentServerInstanceInfo"));
    }
    return PcfNGDeploymentInfoDTO.builder()
        .applicationName(((PcfNGServerInstanceInfo) serverInstanceInfoList.get(0)).getPcfApplicationName())
        .applicationGuid(((PcfNGServerInstanceInfo) serverInstanceInfoList.get(0)).getPcfApplicationGuid())
        .build();
  }
}
