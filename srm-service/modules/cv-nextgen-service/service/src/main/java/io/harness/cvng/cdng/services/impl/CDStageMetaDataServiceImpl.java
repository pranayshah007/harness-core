/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cvng.cdng.services.api.CDStageMetaDataService;
import io.harness.cvng.client.NextGenClient;
import io.harness.cvng.client.RequestExecutor;
import io.harness.ng.core.dto.CDStageMetaDataDTO;
import io.harness.ng.core.dto.CDStageMetaDataDTO.ServiceEnvRef;
import io.harness.ng.core.dto.CdDeployStageMetadataRequestDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.yaml.YamlNode;

import com.esotericsoftware.minlog.Log;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.Objects;

public class CDStageMetaDataServiceImpl implements CDStageMetaDataService {
  @Inject private NextGenClient nextGenClient;
  @Inject private RequestExecutor requestExecutor;

  @Override
  public ResponseDTO<CDStageMetaDataDTO> getServiceAndEnvironmentRef(YamlNode stageLevelYamlNode) {
    YamlNode pipelineYamlNode = getPipelineYamlNode(stageLevelYamlNode);
    if (Objects.isNull(pipelineYamlNode)) {
      Log.error("Pipeline not found in given Yaml, By passing validation check");
      return null;
    }
    return getServiceAndEnvironmentRef(stageLevelYamlNode.getIdentifier(), pipelineYamlNode.toString());
  }

  @Override
  public ResponseDTO<CDStageMetaDataDTO> getServiceAndEnvironmentRef(String stageIdentifier, String pipelineYaml) {
    ResponseDTO<CDStageMetaDataDTO> responseDTO;
    try {
      responseDTO = requestExecutor.execute(nextGenClient.getCDStageMetaData(CdDeployStageMetadataRequestDTO.builder()
                                                                                 .stageIdentifier(stageIdentifier)
                                                                                 .pipelineYaml(pipelineYaml)
                                                                                 .build()));
    } catch (Exception e) {
      Log.error("Exception occurred while fetching service and environment reference, Exception: " + e.getMessage());
      return null;
    }
    if (Objects.isNull(responseDTO) || Objects.isNull(responseDTO.getData().getServiceRef())
        || Objects.isNull(responseDTO.getData().getEnvironmentRef())) {
      Log.error("Invalid Response for Service Ref and Environment Ref in pipeline: " + pipelineYaml);
    } else {
      if (isEmpty(responseDTO.getData().getServiceEnvRefList())) {
        responseDTO.setData(CDStageMetaDataDTO.builder()
                                .environmentRef(responseDTO.getData().getEnvironmentRef())
                                .serviceRef(responseDTO.getData().getServiceRef())
                                .serviceEnvRefList(Collections.singletonList(
                                    ServiceEnvRef.builder()
                                        .environmentRef(responseDTO.getData().getEnvironmentRef())
                                        .serviceRef(responseDTO.getData().getServiceRef())
                                        .build()))
                                .build());
      }
    }
    return responseDTO;
  }

  private YamlNode getPipelineYamlNode(YamlNode yamlNode) {
    if (Objects.isNull(yamlNode)) {
      return null;
    }
    if (yamlNode.getField(CVNGStepUtils.PIPELINE) != null) {
      return yamlNode;
    }
    return getPipelineYamlNode(yamlNode.getParentNode());
  }
}
