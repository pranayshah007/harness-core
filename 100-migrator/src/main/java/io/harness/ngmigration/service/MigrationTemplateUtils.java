/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.infrastructure.InfrastructureResourceClient;
import io.harness.ng.core.beans.NGEntityTemplateResponseDTO;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.service.remote.ServiceResourceClient;
import io.harness.template.remote.TemplateResourceClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class MigrationTemplateUtils {
  @Inject private TemplateResourceClient templateResourceClient;
  @Inject PipelineServiceClient pipelineServiceClient;
  @Inject ServiceResourceClient serviceResourceClient;
  @Inject InfrastructureResourceClient infrastructureResourceClient;
  @Inject @Named("ngClientConfig") private ServiceHttpClientConfig ngClientConfig;
  @Inject @Named("pipelineServiceClientConfig") private ServiceHttpClientConfig pipelineServiceClientConfig;
  @Inject @Named("templateServiceClientConfig") private ServiceHttpClientConfig templateServiceClientConfig;

  public JsonNode getTemplateInputs(
      MigrationInputDTO inputDTO, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    return getTemplateInputs(inputDTO, ngEntityDetail, accountIdentifier, "");
  }

  public JsonNode getTemplateInputs(
      MigrationInputDTO inputDTO, NgEntityDetail ngEntityDetail, String accountIdentifier, String versionLabel) {
    TemplateResourceClient client =
        getClient(inputDTO, templateServiceClientConfig, this.templateResourceClient, TemplateResourceClient.class);
    try {
      String response =
          NGRestUtils.getResponse(client.getTemplateInputsYaml(ngEntityDetail.getIdentifier(), accountIdentifier,
              ngEntityDetail.getOrgIdentifier(), ngEntityDetail.getProjectIdentifier(), versionLabel, false));
      if (response == null || StringUtils.isBlank(response)) {
        return null;
      }
      return YamlUtils.read(response, JsonNode.class);
    } catch (Exception ex) {
      log.error("Error when getting workflow templates input - ", ex);
      return null;
    }
  }

  public String getPipelineInput(MigrationInputDTO inputDTO, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    PipelineServiceClient client =
        getClient(inputDTO, pipelineServiceClientConfig, this.pipelineServiceClient, PipelineServiceClient.class);
    try {
      InputSetTemplateResponseDTOPMS response =
          NGRestUtils.getResponse(client.getTemplateFromPipeline(accountIdentifier, ngEntityDetail.getOrgIdentifier(),
              ngEntityDetail.getProjectIdentifier(), ngEntityDetail.getIdentifier(),
              InputSetTemplateRequestDTO.builder().stageIdentifiers(Collections.emptyList()).build()));
      if (response == null) {
        return null;
      }
      return response.getInputSetTemplateYaml();
    } catch (Exception ex) {
      log.error("Error when getting template from pipeline - ", ex);
      return null;
    }
  }

  public JsonNode getServiceInput(MigrationInputDTO inputDTO, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    ServiceResourceClient client =
        getClient(inputDTO, ngClientConfig, this.serviceResourceClient, ServiceResourceClient.class);
    try {
      NGEntityTemplateResponseDTO response =
          NGRestUtils.getResponse(client.getServiceRuntimeInputs(ngEntityDetail.getIdentifier(), accountIdentifier,
              ngEntityDetail.getOrgIdentifier(), ngEntityDetail.getProjectIdentifier()));
      if (response == null || StringUtils.isBlank(response.getInputSetTemplateYaml())) {
        return null;
      }
      return YamlUtils.read(response.getInputSetTemplateYaml(), JsonNode.class);
    } catch (Exception ex) {
      log.error("Error when getting service templates input - ", ex);
      return null;
    }
  }

  public JsonNode getInfraInput(
      MigrationInputDTO inputDTO, String accountIdentifier, String envIdentifier, NgEntityDetail ngEntityDetail) {
    InfrastructureResourceClient client =
        getClient(inputDTO, ngClientConfig, this.infrastructureResourceClient, InfrastructureResourceClient.class);
    try {
      NGEntityTemplateResponseDTO response =
          NGRestUtils.getResponse(client.getInfrastructureInputs(accountIdentifier, ngEntityDetail.getOrgIdentifier(),
              ngEntityDetail.getProjectIdentifier(), envIdentifier, ngEntityDetail.getIdentifier()));
      if (response == null || StringUtils.isBlank(response.getInputSetTemplateYaml())) {
        return null;
      }
      return YamlUtils.read(response.getInputSetTemplateYaml(), JsonNode.class);
    } catch (Exception ex) {
      log.error("Error when getting infra templates inputs - ", ex);
      return null;
    }
  }

  private <T> T getClient(
      MigrationInputDTO inputDTO, ServiceHttpClientConfig httpClientConfig, T defaultClient, Class<T> clazz) {
    if (StringUtils.isNoneBlank(inputDTO.getDestinationGatewayUrl(), inputDTO.getDestinationAuthToken())) {
      return MigratorUtility.getRestClient(inputDTO, httpClientConfig, clazz);
    }
    return defaultClient;
  }
}
