/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.helper;

import io.harness.ng.core.mapper.TagMapper;
import io.harness.secretmanagerclient.NGSecretManagerMetadata;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;

import lombok.experimental.UtilityClass;

@UtilityClass
public class NgSecretManagerHelper {
  public static void updateNGSecretManagerMetadata(
      NGSecretManagerMetadata ngMetadata, SecretManagerConfigDTO secretManagerConfigDTO) {
    if (ngMetadata != null) {
      secretManagerConfigDTO.setAccountIdentifier(ngMetadata.getAccountIdentifier());
      secretManagerConfigDTO.setOrgIdentifier(ngMetadata.getOrgIdentifier());
      secretManagerConfigDTO.setProjectIdentifier(ngMetadata.getProjectIdentifier());
      secretManagerConfigDTO.setIdentifier(ngMetadata.getIdentifier());
      secretManagerConfigDTO.setTags(TagMapper.convertToMap(ngMetadata.getTags()));
      secretManagerConfigDTO.setDescription(ngMetadata.getDescription());
      secretManagerConfigDTO.setHarnessManaged(secretManagerConfigDTO.isHarnessManaged());
    }
  }
}
