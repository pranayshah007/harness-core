package io.harness.perpetualtask.instancesyncv2.cg;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesyncv2.AwsSshInstanceSyncTaskDetails;
import io.harness.perpetualtask.instancesyncv2.CgDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncData;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.AwsConfig;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;

import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(CDP)
public class CgAwsSshInstancesDetailsFetcher implements InstanceDetailsFetcher {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private AwsEc2HelperServiceDelegate ec2ServiceDelegate;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Override
  public InstanceSyncData fetchRunningInstanceDetails(
      String perpetualTaskId, CgDeploymentReleaseDetails releaseDetails) {
    AwsSshInstanceSyncTaskDetails instanceSyncTaskDetails;
    try {
      instanceSyncTaskDetails =
          AnyUtils.unpack(releaseDetails.getReleaseDetails(), AwsSshInstanceSyncTaskDetails.class);

      final String region = instanceSyncTaskDetails.getRegion();

      final AwsConfig awsConfig =
          (AwsConfig) kryoSerializer.asObject(instanceSyncTaskDetails.getAwsConfig().toByteArray());
      final List<EncryptedDataDetail> encryptedDataDetails =
          cast(kryoSerializer.asObject(instanceSyncTaskDetails.getEncryptedData().toByteArray()));
      final List<Filter> filters = cast(kryoSerializer.asObject(instanceSyncTaskDetails.getFilter().toByteArray()));

      final List<InstanceInfo> instanceInfos = getInstances(region, awsConfig, encryptedDataDetails, filters);
      return InstanceSyncData.newBuilder()
          .setTaskDetailsId(releaseDetails.getTaskDetailsId())
          .setExecutionStatus(
              instanceInfos != null ? CommandExecutionStatus.SUCCESS.name() : CommandExecutionStatus.FAILURE.name())
          .addAllInstanceData(instanceInfos != null ? instanceInfos.parallelStream()
                                                          .map(pod -> ByteString.copyFrom(kryoSerializer.asBytes(pod)))
                                                          .collect(toList())
                                                    : null)
          .build();

    } catch (Exception e) {
      log.error("Unable to unpack Instance Sync task details for Id: [{}]", releaseDetails.getTaskDetailsId(), e);
      return InstanceSyncData.newBuilder().setTaskDetailsId(releaseDetails.getTaskDetailsId()).build();
    }
  }

  @SuppressWarnings("unchecked")
  private static <T extends List<?>> T cast(Object obj) {
    return (T) obj;
  }
  private List<InstanceInfo> getInstances(
      String region, AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails, List<Filter> filters) {
    try {
      final List<Instance> instances =
          ec2ServiceDelegate.listEc2Instances(awsConfig, encryptedDataDetails, region, filters, true);
      /*            return instances.stream().map(instance -> Ec2InstanceInfo.builder()
                          .ec2Instance(instance)
                          .hostName(getPrivateDnsName(instance.getPrivateDnsName()))
                          .hostPublicDns(instance.getPublicDnsName())
                          .build()).collect(toList());*/
      return null;

    } catch (Exception e) {
      throw new InvalidRequestException("Error occured while fetching instances from AWS", e);
    }
  }

  @VisibleForTesting
  String getPrivateDnsName(String privateDnsNameWithSuffix) {
    // e.g. null, "", "   "
    if (StringUtils.isEmpty(privateDnsNameWithSuffix) || StringUtils.isBlank(privateDnsNameWithSuffix)) {
      return StringUtils.EMPTY;
    }

    // "ip-172-31-11-6.ec2.internal", we return ip-172-31-11-6
    if (privateDnsNameWithSuffix.indexOf('.') != -1) {
      return privateDnsNameWithSuffix.substring(0, privateDnsNameWithSuffix.indexOf('.'));
    }

    return privateDnsNameWithSuffix;
  }
}
