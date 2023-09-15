/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.ceawsmapper;

import io.harness.aws.AwsClient;
import io.harness.connector.entities.embedded.ceawsconnector.CEAwsConfig;
import io.harness.connector.entities.embedded.ceawsconnector.CEAwsConfig.CEAwsConfigBuilder;
import io.harness.connector.entities.embedded.ceawsconnector.CURAttributes;
import io.harness.connector.entities.embedded.ceawsconnector.S3BucketDetails;
import io.harness.connector.entities.embedded.ceawsconnector.S3BucketDetails.S3BucketDetailsBuilder;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.delegate.beans.connector.ceawsconnector.AwsCurAttributesDTO;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.remote.CEAwsSetupConfig;
import io.harness.remote.CEProxyConfig;

import com.amazonaws.arn.Arn;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.costandusagereport.model.ReportDefinition;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class CEAwsDTOToEntity implements ConnectorDTOToEntityMapper<CEAwsConnectorDTO, CEAwsConfig> {
  @Inject AwsClient awsClient;
  @Inject CEAwsSetupConfig ceAwsSetupConfig;
  @Inject CEProxyConfig ceProxyConfig;

  @Override
  public CEAwsConfig toConnectorEntity(CEAwsConnectorDTO connectorDTO) {
    CEAwsConfigBuilder ceAwsConfigBuilder = CEAwsConfig.builder();
    String awsAccountIdFromRoleArn;
    List<CEFeatures> featuresList = connectorDTO.getFeaturesEnabled();
    if (featuresList.contains(CEFeatures.BILLING)) {
      final AwsCurAttributesDTO awsCurAttributes = connectorDTO.getCurAttributes();

      if (awsCurAttributes == null) {
        throw new InvalidRequestException("curAttributes should be provided when the features 'BILLING' is enabled.");
      }

      final S3BucketDetailsBuilder s3BucketDetailsBuilder =
          S3BucketDetails.builder().s3BucketName(awsCurAttributes.getS3BucketName());

      Optional<ReportDefinition> report = getReportDefinition(connectorDTO);
      if (report.isPresent()) {
        s3BucketDetailsBuilder.region(report.get().getS3Region());
        s3BucketDetailsBuilder.s3Prefix(report.get().getS3Prefix());
      }

      final CURAttributes curAttributes = CURAttributes.builder()
                                              .reportName(awsCurAttributes.getReportName())
                                              .s3BucketDetails(s3BucketDetailsBuilder.build())
                                              .build();
      ceAwsConfigBuilder.curAttributes(curAttributes);
    }

    final CrossAccountAccessDTO crossAccountAccessDTO = connectorDTO.getCrossAccountAccess();

    awsAccountIdFromRoleArn = getAccountId(crossAccountAccessDTO);
    if (!awsAccountIdFromRoleArn.isEmpty() && !awsAccountIdFromRoleArn.equals(connectorDTO.getAwsAccountId())) {
      throw new InvalidRequestException("accountId in role arn does not match accountId in overview screen");
    }

    return ceAwsConfigBuilder.crossAccountAccess(crossAccountAccessDTO)
        .awsAccountId(awsAccountIdFromRoleArn)
        .featuresEnabled(featuresList)
        .isAWSGovCloudAccount(connectorDTO.getIsAWSGovCloudAccount())
        .build();
  }

  @VisibleForTesting
  public Optional<ReportDefinition> getReportDefinition(CEAwsConnectorDTO connectorDTO) {
    try {
      final AWSCredentialsProvider credentialsProvider =
          awsClient.getAssumedCredentialsProvider(awsClient.constructStaticBasicAwsCredentials(
                                                      ceAwsSetupConfig.getAccessKey(), ceAwsSetupConfig.getSecretKey()),
              connectorDTO.getCrossAccountAccess().getCrossAccountRoleArn(),
              connectorDTO.getCrossAccountAccess().getExternalId());
      return awsClient.getReportDefinition(
          credentialsProvider, connectorDTO.getCurAttributes().getReportName(), ceProxyConfig);
    } catch (Exception ex) {
      log.error("Error getting report definition", ex);
      return Optional.empty();
    }
  }

  private static String getAccountId(final CrossAccountAccessDTO crossAccountAccessDTO) {
    Arn roleArn = Arn.fromString(crossAccountAccessDTO.getCrossAccountRoleArn());
    return roleArn.getAccountId();
  }
}
