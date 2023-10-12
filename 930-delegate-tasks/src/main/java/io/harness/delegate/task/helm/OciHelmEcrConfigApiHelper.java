/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.aws.AwsClient;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.beans.EcrImageDetailConfig;
import io.harness.beans.DecryptableEntity;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.helm.EcrHelmApiListTagsTaskParams;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.exception.OciHelmDockerApiException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.SecretDecryptionService;

import com.amazonaws.services.ecr.model.ImageDetail;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
@Singleton
@OwnedBy(CDP)
public class OciHelmEcrConfigApiHelper {
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private AwsClient awsClient;
  @Inject private OciHelmApiHelperUtils ociHelmApiHelperUtils;
  @Inject private SecretDecryptionService decryptionService;

  public EcrImageDetailConfig getEcrImageDetailConfig(
      EcrHelmApiListTagsTaskParams ecrHelmApiListTagsTaskParams, int pageSize) {
    AwsConnectorDTO awsConnectorDTO = ecrHelmApiListTagsTaskParams.getAwsConnectorDTO();
    String chartNameNormalized = ociHelmApiHelperUtils.normalizeFieldData(ecrHelmApiListTagsTaskParams.getChartName());

    if (EmptyPredicate.isEmpty(ecrHelmApiListTagsTaskParams.getChartName())) {
      throw new OciHelmDockerApiException("Chart name property is invalid");
    }
    decryptEncryptedDetails(ecrHelmApiListTagsTaskParams);
    log.info(format("Making ECR request to retrieve list of tags for chart: %s, returning max %d results %s",
        chartNameNormalized, pageSize,
        EmptyPredicate.isEmpty(ecrHelmApiListTagsTaskParams.getLastTag())
            ? " starting from beginning"
            : format(" continuing from tag %s", ecrHelmApiListTagsTaskParams.getLastTag())));
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    return awsClient.listEcrImageTags(awsInternalConfig, ecrHelmApiListTagsTaskParams.getRegistryId(),
        ecrHelmApiListTagsTaskParams.getRegion(), ecrHelmApiListTagsTaskParams.getChartName(), pageSize,
        ecrHelmApiListTagsTaskParams.getLastTag());
  }

  private void decryptEncryptedDetails(EcrHelmApiListTagsTaskParams ecrHelmApiListTagsTaskParams) {
    final List<DecryptableEntity> decryptableEntityList =
        ecrHelmApiListTagsTaskParams.getAwsConnectorDTO().getDecryptableEntities();
    if (decryptableEntityList == null) {
      return;
    }
    for (DecryptableEntity entity : decryptableEntityList) {
      decryptionService.decrypt(entity, ecrHelmApiListTagsTaskParams.getEncryptionDetails());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          entity, ecrHelmApiListTagsTaskParams.getEncryptionDetails());
    }
  }

  public List<String> getChartVersionsFromImageDetails(EcrImageDetailConfig ecrImageDetailConfig) {
    return ecrImageDetailConfig.getImageDetails()
        .stream()
        .map(ImageDetail::getImageTags)
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }
}
