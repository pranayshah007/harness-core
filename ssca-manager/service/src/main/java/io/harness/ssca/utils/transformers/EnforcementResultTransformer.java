/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.utils.transformers;

import io.harness.spec.server.ssca.v1.model.EnforcementResultDTO;
import io.harness.ssca.entities.EnforcementResultEntity;

public class EnforcementResultTransformer implements Transformer<EnforcementResultEntity, EnforcementResultDTO> {
  @Override
  public EnforcementResultEntity toEntity(EnforcementResultDTO dto) {
    // modelMapper.createTypeMap(dto, EnforcementResultEntity.class).addMappings(dto.g);
    return modelMapper.map(dto, EnforcementResultEntity.class);
  }

  @Override
  public EnforcementResultDTO toDTO(EnforcementResultEntity entity) {
    return modelMapper.map(entity, EnforcementResultDTO.class);
  }

  /*public static EnforcementResultEntity toEntity(EnforcementResultDTO dto) {
    return EnforcementResultEntity.builder()
        .accountId(dto.getAccountId())
        .purl(dto.getPurl())
        .enforcementId(dto.getEnforcementId())
        .artifactId(dto.getArtifactId())
        .imageName(dto.getImageName())
        .license(dto.getLicense())
        .name(dto.getName())
        .orchestrationId(dto.getOrchestrationId())
        .orgIdentifier(dto.getOrgIdentifier())
        .packageManager(dto.getPackageManager())
        .projectIdentifier(dto.getProjectIdentifier())
        .supplier(dto.getSupplier())
        .supplierType(dto.getSupplierType())
        .tag(dto.getTag())
        .version(dto.getVersion())
        .violationDetails(dto.getViolationDetails())
        .violationType(dto.getViolationType())
        .build();
  }*/
}
