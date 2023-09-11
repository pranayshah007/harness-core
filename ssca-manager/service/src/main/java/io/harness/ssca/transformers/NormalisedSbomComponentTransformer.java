/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.transformers;

import io.harness.spec.server.ssca.v1.model.NormalizedSbomComponentDTO;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;

import java.math.BigDecimal;
import java.time.Instant;

public class NormalisedSbomComponentTransformer {
  public static NormalizedSBOMComponentEntity toEntity(NormalizedSbomComponentDTO dto) {
    return NormalizedSBOMComponentEntity.builder()
        .packageManager(dto.getPackageManager())
        .packageNamespace(dto.getPackageNamespace())
        .purl(dto.getPurl())
        .patchVersion(dto.getPatchVersion().intValue())
        .minorVersion(dto.getMinorVersion().intValue())
        .majorVersion(dto.getMajorVersion().intValue())
        .artifactUrl(dto.getArtifactUrl())
        .accountId(dto.getAccountId())
        .orgIdentifier(dto.getOrgIdentifier())
        .projectIdentifier(dto.getProjectIdentifier())
        .artifactId(dto.getArtifactId())
        .artifactName(dto.getArtifactName())
        .createdOn(Instant.ofEpochMilli(dto.getCreated().longValue()))
        .orchestrationId(dto.getOrchestrationId())
        .originatorType(dto.getOriginatorType())
        .packageCpe(dto.getPackageCpe())
        .packageDescription(dto.getPackageDescription())
        .packageId(dto.getPackageId())
        .packageLicense(dto.getPackageLicense())
        .packageName(dto.getPackageName())
        .packageOriginatorName(dto.getPackageOriginatorName())
        .packageProperties(dto.getPackageProperties())
        .packageSourceInfo(dto.getPackageSourceInfo())
        .packageSupplierName(dto.getPackageSupplierName())
        .packageType(dto.getPackageType())
        .packageVersion(dto.getPackageVersion())
        .sbomVersion(dto.getSbomVersion())
        .pipelineIdentifier(dto.getPipelineIdentifier())
        .sequenceId(dto.getSequenceId())
        .tags(dto.getTags())
        .toolName(dto.getToolName())
        .toolVendor(dto.getToolVendor())
        .toolVersion(dto.getToolVersion())
        .build();
  }

  public static NormalizedSbomComponentDTO toDTO(NormalizedSBOMComponentEntity entity) {
    return new NormalizedSbomComponentDTO()
        .packageManager(entity.getPackageManager())
        .packageNamespace(entity.getPackageNamespace())
        .purl(entity.getPurl())
        .patchVersion(new BigDecimal(entity.getPatchVersion()))
        .minorVersion(new BigDecimal(entity.getMinorVersion()))
        .majorVersion(new BigDecimal(entity.getMajorVersion()))
        .artifactUrl(entity.getArtifactUrl())
        .accountId(entity.getAccountId())
        .orgIdentifier(entity.getOrgIdentifier())
        .projectIdentifier(entity.getProjectIdentifier())
        .artifactId(entity.getArtifactId())
        .artifactName(entity.getArtifactName())
        .created(new BigDecimal(entity.getCreatedOn().toEpochMilli()))
        .orchestrationId(entity.getOrchestrationId())
        .originatorType(entity.getOriginatorType())
        .packageCpe(entity.getPackageCpe())
        .packageDescription(entity.getPackageDescription())
        .packageId(entity.getPackageId())
        .packageLicense(entity.getPackageLicense())
        .packageName(entity.getPackageName())
        .packageOriginatorName(entity.getPackageOriginatorName())
        .packageProperties(entity.getPackageProperties())
        .packageSourceInfo(entity.getPackageSourceInfo())
        .packageSupplierName(entity.getPackageSupplierName())
        .packageType(entity.getPackageType())
        .packageVersion(entity.getPackageVersion())
        .sbomVersion(entity.getSbomVersion())
        .pipelineIdentifier(entity.getPipelineIdentifier())
        .sequenceId(entity.getSequenceId())
        .tags(entity.getTags())
        .toolName(entity.getToolName())
        .toolVendor(entity.getToolVendor())
        .toolVersion(entity.getToolVersion());
  }
}
