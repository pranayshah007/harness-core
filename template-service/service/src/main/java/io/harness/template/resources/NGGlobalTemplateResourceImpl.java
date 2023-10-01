/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.customDeployment.remote.CustomDeploymentResourceClient;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.template.resources.beans.TemplateWrapperResponseDTO;
import io.harness.template.services.NGGlobalTemplateService;
import io.harness.template.services.TemplateVariableCreatorFactory;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
public class NGGlobalTemplateResourceImpl implements NGGlobalTemplateResource {
  public static final String TEMPLATE = "TEMPLATE";
  private final NGGlobalTemplateService ngGlobalTemplateService;
  @Inject CustomDeploymentResourceClient customDeploymentResourceClient;
  @Inject TemplateVariableCreatorFactory templateVariableCreatorFactory;
  @Override
  public ResponseDTO<List<TemplateWrapperResponseDTO>> createAndUpdate(@NotNull String accountId, String OrgIdentifier,
      String projectIdentifier, String connectorRef, @NotNull Map<String, Object> webhookEvent, String comments) {
    List<TemplateWrapperResponseDTO> templateWrapperResponseDTOS = ngGlobalTemplateService.createUpdateGlobalTemplate(
        accountId, connectorRef, webhookEvent, comments, OrgIdentifier, projectIdentifier);
    if (templateWrapperResponseDTOS.isEmpty()) {
      throw new InvalidRequestException("Unable to fetch the template from Git");
    }
    return ResponseDTO.newResponse(templateWrapperResponseDTOS);
  }
}