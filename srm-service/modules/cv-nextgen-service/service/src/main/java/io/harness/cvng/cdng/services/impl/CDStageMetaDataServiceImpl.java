/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import io.harness.cvng.cdng.services.api.CDStageMetaDataService;
import io.harness.cvng.client.NextGenPrivilegedClient;
import io.harness.cvng.client.RequestExecutor;
import io.harness.ng.core.dto.CDStageMetaDataDTO;
import io.harness.ng.core.dto.CdDeployStageMetadataRequestDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.yaml.YamlNode;

import com.google.inject.Inject;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

@Slf4j
public class CDStageMetaDataServiceImpl implements CDStageMetaDataService {
  @Inject private NextGenPrivilegedClient nextGenPrivilegedClient;
  @Inject private RequestExecutor requestExecutor;

  @Override
  public ResponseDTO<CDStageMetaDataDTO> getServiceAndEnvironmentRef(YamlNode stageLevelYamlNode) {
    YamlNode pipelineYamlNode = getPipelineYamlNode(stageLevelYamlNode);
    if (Objects.isNull(pipelineYamlNode)) {
      log.warn("Pipeline not found in given Yaml, By passing validation check");
      return null;
    }
    return getServiceAndEnvironmentRef(stageLevelYamlNode.getIdentifier(), pipelineYamlNode.toString());
  }

  @Override
  public ResponseDTO<CDStageMetaDataDTO> getServiceAndEnvironmentRef(String stageIdentifier, String pipelineYaml) {
    ResponseDTO<CDStageMetaDataDTO> responseDTO = getCdStageMetaDataResponse(stageIdentifier, pipelineYaml);
    if (isInvalidResponse(responseDTO)) {
      log.warn("Invalid Response for Service Ref and Environment Ref in pipeline: " + pipelineYaml);
      return null;
    }
    if (CollectionUtils.isEmpty(responseDTO.getData().getServiceEnvRefList())) {
      setServiceEnvRef(responseDTO);
    }
    return responseDTO;
  }

  private void setServiceEnvRef(ResponseDTO<CDStageMetaDataDTO> responseDTO) {
    responseDTO.setData(CDStageMetaDataDTO.builder()
                            .environmentRef(responseDTO.getData().getEnvironmentRef())
                            .serviceRef(responseDTO.getData().getServiceRef())
                            .serviceEnvRef(CDStageMetaDataDTO.ServiceEnvRef.builder()
                                               .environmentRef(responseDTO.getData().getEnvironmentRef())
                                               .serviceRef(responseDTO.getData().getServiceRef())
                                               .build())
                            .build());
  }

  private ResponseDTO<CDStageMetaDataDTO> getCdStageMetaDataResponse(String stageIdentifier, String pipelineYaml) {
    ResponseDTO<CDStageMetaDataDTO> responseDTO = null;
    try {
      responseDTO =
          requestExecutor.execute(nextGenPrivilegedClient.getCDStageMetaData(CdDeployStageMetadataRequestDTO.builder()
                                                                                 .stageIdentifier(stageIdentifier)
                                                                                 .pipelineYaml(pipelineYaml)
                                                                                 .build()));
    } catch (Exception e) {
      log.warn("Exception occurred while fetching service and environment reference, Exception: " + e.getMessage()
          + "pipeline: " + pipelineYaml);
    }
    return responseDTO;
  }

  private static boolean isInvalidResponse(ResponseDTO<CDStageMetaDataDTO> responseDTO) {
    return CollectionUtils.isEmpty(responseDTO.getData().getServiceEnvRefList())
        && (Objects.isNull(responseDTO.getData().getServiceRef())
            || Objects.isNull(responseDTO.getData().getEnvironmentRef()));
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
