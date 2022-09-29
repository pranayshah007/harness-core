/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.ceawsmapper;

import io.harness.connector.entities.embedded.ceawsconnector.CEAwsConfig;
import io.harness.connector.entities.embedded.ceawsconnector.CURAttributes;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ceawsconnector.AwsCurAttributesDTO;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO.CEAwsConnectorDTOBuilder;

import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class CEAwsEntityToDTO implements ConnectorEntityToDTOMapper<CEAwsConnectorDTO, CEAwsConfig> {
  @Override
  public CEAwsConnectorDTO createConnectorDTO(CEAwsConfig ceAwsConfig) {
    CEAwsConnectorDTOBuilder ceAwsConnectorDTOBuilder = CEAwsConnectorDTO.builder();
    ceAwsConnectorDTOBuilder.awsAccountId(ceAwsConfig.getAwsAccountId());
    List<CEFeatures> ceAwsFeaturesList = ceAwsConfig.getFeaturesEnabled();
    if (ceAwsFeaturesList.contains(CEFeatures.BILLING)) {
      final CURAttributes curAttributes = ceAwsConfig.getCurAttributes();
      ceAwsConnectorDTOBuilder.curAttributes(AwsCurAttributesDTO.builder()
                                                 .s3BucketName(curAttributes.getS3BucketDetails().getS3BucketName())
                                                 .reportName(curAttributes.getReportName())
                                                 .region(curAttributes.getS3BucketDetails().getRegion())
                                                 .s3Prefix(curAttributes.getS3BucketDetails().getS3Prefix())
                                                 .build());
    }

    return ceAwsConnectorDTOBuilder.crossAccountAccess(ceAwsConfig.getCrossAccountAccess())
        .featuresEnabled(ceAwsFeaturesList)
        .isAWSGovCloudAccount(ceAwsConfig.getIsAWSGovCloudAccount())
        .build();
  }
}
