/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.mapper;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.beans.YamlDiffRecordDTO;
import io.harness.audit.entities.YamlDiffRecord;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class YamlDiffRecordMapper {
  public static YamlDiffRecord fromDTO(YamlDiffRecordDTO yamlDiffRecordDTO) {
    return YamlDiffRecord.builder()
        .oldYaml(yamlDiffRecordDTO.getOldYaml())
        .newYaml(yamlDiffRecordDTO.getNewYaml())
        .build();
  }

  public static YamlDiffRecordDTO toDTO(YamlDiffRecord yamlDiffRecord) {
    return YamlDiffRecordDTO.builder()
        .oldYaml(yamlDiffRecord.getOldYaml())
        .newYaml(yamlDiffRecord.getNewYaml())
        .build();
  }
}
