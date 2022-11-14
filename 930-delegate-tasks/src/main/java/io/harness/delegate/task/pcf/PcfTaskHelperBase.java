package io.harness.delegate.task.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.task.pcf.PcfNgConfigMapper;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.pcf.mappers.PcfInstanceIndexToServerInstanceInfoMapper;
import io.harness.delegate.task.pcf.response.PcfInfraConfig;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CloudFoundryConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.InstanceDetail;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class PcfTaskHelperBase {
  @Inject private PcfNgConfigMapper ngConfigMapper;
  @Inject protected CfDeploymentManager pcfDeploymentManager;
  public List<ServerInstanceInfo> getPcfServerInstanceInfos(PcfDeploymentReleaseData deploymentReleaseData) {
    PcfInfraConfig pcfInfraConfig = deploymentReleaseData.getPcfInfraConfig();
    CloudFoundryConfig pcfConfig = ngConfigMapper.mapPcfConfigWithDecryption(
        pcfInfraConfig.getPcfConnectorDTO(), pcfInfraConfig.getEncryptionDataDetails());
    try {
      CfRequestConfig cfRequestConfig = CfRequestConfig.builder()
                                            .timeOutIntervalInMins(5)
                                            .applicationName(deploymentReleaseData.getApplicationName())
                                            .userName(String.valueOf(pcfConfig.getUserName()))
                                            .password(String.valueOf(pcfConfig.getPassword()))
                                            .endpointUrl(pcfConfig.getEndpointUrl())
                                            .orgName(pcfInfraConfig.getOrganization())
                                            .spaceName(pcfInfraConfig.getSpace())
                                            .build();

      ApplicationDetail applicationDetail = pcfDeploymentManager.getApplicationByName(cfRequestConfig);
      List<String> instanceIndices =
          applicationDetail.getInstanceDetails().stream().map(InstanceDetail::getIndex).collect(toList());

      return PcfInstanceIndexToServerInstanceInfoMapper.toServerInstanceInfoList(
          instanceIndices, pcfInfraConfig, applicationDetail);

    } catch (Exception e) {
      log.warn("Failed while collecting PCF Application Details For Application: {}, with Error: {}",
          (deploymentReleaseData.getApplicationName()), e);
      return Collections.emptyList();
    }
  }
}
