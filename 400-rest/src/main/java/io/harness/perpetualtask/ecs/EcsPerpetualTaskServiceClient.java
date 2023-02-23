/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.ecs;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Collections.singletonList;

import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.perpetualtask.PerpetualTaskClientBase;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskServiceClient;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.service.impl.aws.model.AwsEcsRequest;
import software.wings.service.impl.aws.model.AwsEcsRequest.AwsEcsRequestType;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EcsPerpetualTaskServiceClient extends PerpetualTaskClientBase implements PerpetualTaskServiceClient {
  @Inject private SecretManager secretManager;
  @Inject private SettingsService settingsService;

  private static final String REGION = "region";
  private static final String SETTING_ID = "settingId";
  private static final String CLUSTER_NAME = "clusterName";
  private static final String CLUSTER_ID = "clusterId";

  @Override
  public EcsPerpetualTaskParams getTaskParams(PerpetualTaskClientContext clientContext, boolean referenceFalse) {
    Map<String, String> clientParams = clientContext.getClientParams();
    log.info("Inside get task params for ecs perpetual task {} ", clientParams);
    String region = clientParams.get(REGION);
    String settingId = clientParams.get(SETTING_ID);
    String clusterName = clientParams.get(CLUSTER_NAME);
    String clusterId = clientParams.get(CLUSTER_ID);

    AwsConfig awsConfig = getAwsConfig(settingId);
    ByteString awsConfigBytes = ByteString.copyFrom(getKryoSerializer(referenceFalse).asBytes(awsConfig));
    ByteString encryptionDetailBytes =
        ByteString.copyFrom(getKryoSerializer(referenceFalse).asBytes(getEncryptionDetails(awsConfig)));

    EcsPerpetualTaskParams ecsPerpetualTaskParams = EcsPerpetualTaskParams.newBuilder()
                                                        .setClusterName(clusterName)
                                                        .setClusterId(clusterId)
                                                        .setSettingId(settingId)
                                                        .setRegion(region)
                                                        .setEncryptionDetail(encryptionDetailBytes)
                                                        .setAwsConfig(awsConfigBytes)
                                                        .build();
    log.debug("Get Task params {} ", ecsPerpetualTaskParams.toString());
    return ecsPerpetualTaskParams;
  }

  private AwsConfig getAwsConfig(String settingId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    if (settingAttribute == null || !(settingAttribute.getValue() instanceof AwsConfig)) {
      throw new InvalidRequestException("AWS account setting not found " + settingId);
    }
    return (AwsConfig) settingAttribute.getValue();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(AwsConfig awsConfig) {
    return secretManager.getEncryptionDetails(awsConfig, null, null);
  }

  @Override
  public DelegateTask getValidationTask(PerpetualTaskClientContext context, String accountId) {
    Map<String, String> clientParams = context.getClientParams();
    String settingId = clientParams.get(SETTING_ID);
    AwsConfig awsConfig = getAwsConfig(settingId);
    String region = clientParams.get(REGION);

    AwsEcsRequest awsEcsRequest =
        new AwsEcsRequest(awsConfig, getEncryptionDetails(awsConfig), AwsEcsRequestType.LIST_CLUSTERS, region);

    return DelegateTask.builder()
        .accountId(accountId)
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.AWS_ECS_TASK.name())
                  .parameters(new Object[] {awsEcsRequest})
                  .timeout(TimeUnit.MINUTES.toMillis(1))
                  .build())
        .tags(isNotEmpty(awsConfig.getTag()) ? singletonList(awsConfig.getTag()) : null)
        .build();
  }
}
