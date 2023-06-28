/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverridev2.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideAuditEventDTO;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import java.io.IOException;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class ServiceOverrideEventDTOMapper {
  public ServiceOverrideAuditEventDTO toOverrideAuditEventDTO(NGServiceOverridesEntity entity) throws IOException {
    String yaml = StringUtils.EMPTY;
    if (entity.getSpec() != null) {
      String specYaml = YamlUtils.writeYamlString(entity.getSpec());
      YamlField yamlField = YamlUtils.readTree(specYaml);
      YamlConfig yamlConfig = new YamlConfig(yamlField.getNode().getCurrJsonNode());
      yaml = yamlConfig.getYaml();
    } else {
      yaml = entity.getYaml();
    }

    return ServiceOverrideAuditEventDTO.builder()
        .accountId(entity.getAccountId())
        .orgIdentifier(entity.getOrgIdentifier())
        .projectIdentifier(entity.getProjectIdentifier())
        .entityV2(Boolean.TRUE.equals(entity.getIsV2()))
        .serviceRef(entity.getServiceRef())
        .environmentRef(entity.getEnvironmentRef())
        .identifier(entity.getIdentifier())
        .yaml(yaml)
        .build();
  }
}
