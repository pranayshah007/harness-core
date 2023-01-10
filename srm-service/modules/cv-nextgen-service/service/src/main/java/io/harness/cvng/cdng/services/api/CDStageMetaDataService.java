/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.api;

import io.harness.ng.core.dto.CDStageMetaDataDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.yaml.YamlNode;

import jdk.jfr.Description;

@Description("Service to get Service Ref and Environment Ref from Pipeline Yaml")
public interface CDStageMetaDataService {
  @Description("Method to get Service Ref and Environment Ref from Pipeline Yaml")
  ResponseDTO<CDStageMetaDataDTO> getServiceAndEnvironmentRef(YamlNode stageLevelYamlNode);
}
