/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.writer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Singleton;
import io.harness.batch.processing.BatchProcessingException;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.ccm.S3SyncRecord;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.ng.NGConnectorHelper;
import io.harness.batch.processing.service.impl.AwsS3SyncServiceImpl;
import io.harness.batch.processing.tasklet.GcpSyncTasklet;
import io.harness.ccm.commons.dao.AWSConnectorToBucketMappingDao;
import io.harness.ccm.commons.entities.AWSConnectorToBucketMapping;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.delegate.beans.connector.ceawsconnector.AwsCurAttributesDTO;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;
import io.harness.delegate.beans.connector.ceazure.BillingExportSpecDTO;
import io.harness.delegate.beans.connector.ceazure.CEAzureConnectorDTO;
import io.harness.ff.FeatureFlagService;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.AwsS3BucketDetails;
import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEAwsConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static software.wings.beans.SettingAttribute.SettingCategory.CE_CONNECTOR;
import static software.wings.settings.SettingVariableTypes.CE_AWS;

@Slf4j
@Singleton
public class S3FastSyncEventWriter extends EventWriter implements ItemWriter<SettingAttribute> {
  @Autowired private AwsS3SyncServiceImpl awsS3SyncService;
  @Autowired private ConnectorResourceClient connectorResourceClient;
  @Autowired private FeatureFlagService featureFlagService;
  @Autowired private AWSConnectorToBucketMappingDao awsConnectorToBucketMappingDao;
  @Autowired private NGConnectorHelper ngConnectorHelper;
  private JobParameters parameters;
  private static final String MASTER = "MASTER";


  private final Cache<S3FastSyncEventWriter.CacheKey, Boolean> s3SyncInfo =
          Caffeine.newBuilder().expireAfterWrite(60, TimeUnit.MINUTES).build();

  @Value
  private static class CacheKey {
    private String connectorId;
  }

  @BeforeStep
  public void beforeStep(final StepExecution stepExecution) {
    parameters = stepExecution.getJobExecution().getJobParameters();
  }

  @Override
  public void write(List<? extends SettingAttribute> dummySettingAttributeList) {
    String accountId = parameters.getString(CCMJobConstants.ACCOUNT_ID);
    boolean isSuccessfulSync = syncNextGenContainers(accountId);
    if (!isSuccessfulSync) {
      throw new BatchProcessingException("AWS Fast S3 sync failed", null);
    }
  }

  public boolean syncNextGenContainers(String accountId) {
    // TODO: Get connectors added since the last batch job run. PL api doesnt support this atm.
    //    List<ConnectorResponseDTO> nextGenConnectorResponses = ngConnectorHelper.getNextGenConnectors(
    //        accountId, Arrays.asList(ConnectorType.CE_AWS), Arrays.asList(CEFeatures.BILLING), Collections.emptyList());
    List<ConnectorResponseDTO> nextGenConnectorResponses = new ArrayList<ConnectorResponseDTO>();
    CrossAccountAccessDTO crossAccountAccessDTO = CrossAccountAccessDTO.builder()
            .crossAccountRoleArn("arn:aws:iam::890436954479:role/HarnessCERole-od5vywtpt79o")
            .externalId("harness:108817434118:EfOfrUbHTtupeZjUqxlHBg")
            .build();
    AwsCurAttributesDTO curAttributes = AwsCurAttributesDTO.builder()
            .reportName("report_name_utsav")
            .region("us-east-1")
            .s3BucketName("ce-customer-billing-data-dev")
            .s3Prefix("prefix").build();
    ConnectorConfigDTO connectorConfig = CEAwsConnectorDTO.builder().awsAccountId("890436954479")
            .crossAccountAccess(crossAccountAccessDTO).curAttributes(curAttributes)
            .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
            .connectorConfig(connectorConfig)
            .connectorType(ConnectorType.CE_AWS)
            .identifier("harnessce")
            .name("harness-ce")
            .build();
    ConnectorResponseDTO connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();
    nextGenConnectorResponses.add(connectorResponse);
    log.info("Processing batch size of {} in S3SyncEventWriter (From NG)", nextGenConnectorResponses.size());
    return syncAwsContainers(nextGenConnectorResponses, accountId, true);
  }

  public boolean syncAwsContainers(List<ConnectorResponseDTO> connectorResponses, String accountId, boolean isNextGen) {
    for (ConnectorResponseDTO connector : connectorResponses) {
      try {
        ConnectorInfoDTO connectorInfo = connector.getConnector();
        CEAwsConnectorDTO ceAwsConnectorDTO = (CEAwsConnectorDTO) connectorInfo.getConnectorConfig();
        if (ceAwsConnectorDTO != null && ceAwsConnectorDTO.getCrossAccountAccess() != null) {
          String destinationBucket = null;
          if (isNextGen) {
            AWSConnectorToBucketMapping awsConnectorToBucketMapping =
                awsConnectorToBucketMappingDao.getByAwsConnectorId(accountId, connectorInfo.getIdentifier());
            if (awsConnectorToBucketMapping != null) {
              destinationBucket = awsConnectorToBucketMapping.getDestinationBucket();
            }
          }
          AwsCurAttributesDTO curAttributes = ceAwsConnectorDTO.getCurAttributes();
          CrossAccountAccessDTO crossAccountAccess = ceAwsConnectorDTO.getCrossAccountAccess();
          S3SyncRecord s3SyncRecord =
              S3SyncRecord.builder()
                  .accountId(accountId)
                  .settingId(connectorInfo.getIdentifier())
                  .billingAccountId(ceAwsConnectorDTO.getAwsAccountId())
                  .curReportName(curAttributes.getReportName())
                  .billingBucketPath(String.join("/", "s3://" + curAttributes.getS3BucketName(),
                      curAttributes.getS3Prefix().equals("/") ? "" : curAttributes.getS3Prefix(),
                      curAttributes.getReportName()))
                  .billingBucketRegion(curAttributes.getRegion())
                  .externalId(crossAccountAccess.getExternalId())
                  .roleArn(crossAccountAccess.getCrossAccountRoleArn())
                  .destinationBucket(destinationBucket)
                  .build();

          awsS3SyncService.syncBuckets(s3SyncRecord);
        }
      } catch (Exception ex) {
        log.error("Exception while s3 sync", ex);
      }
    }
    return true;
  }
}
