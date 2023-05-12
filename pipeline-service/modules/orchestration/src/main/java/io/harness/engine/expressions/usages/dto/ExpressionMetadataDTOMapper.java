/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.expressions.usages.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.expressions.usages.beans.ExpressionMetadata;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class ExpressionMetadataDTOMapper {
  public Set<ExpressionMetadataDTO> toMetadataDTO(Set<ExpressionMetadata> metadataList) {
    return metadataList.stream().map(ExpressionMetadataDTOMapper::toMetadataDTO).collect(Collectors.toSet());
  }

  public ExpressionMetadataDTO toMetadataDTO(ExpressionMetadata metadata) {
    return ExpressionMetadataDTO.builder().expression(metadata.getExpression()).fqn(metadata.getFqn()).build();
  }
}
