/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.template.resources.beans.NGTemplateConstants.FILE_ADDED;
import static io.harness.template.resources.beans.NGTemplateConstants.FILE_MODIFIED;
import static io.harness.template.resources.beans.NGTemplateConstants.FILE_REMOVED;

import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.customDeployment.remote.CustomDeploymentResourceClient;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.template.resources.beans.TemplateWrapperResponseDTO;
import io.harness.template.services.NGTemplateService;
import io.harness.template.services.TemplateVariableCreatorFactory;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
  private final NGTemplateService templateService;
  @Inject CustomDeploymentResourceClient customDeploymentResourceClient;
  @Inject TemplateVariableCreatorFactory templateVariableCreatorFactory;
  @Override
  public ResponseDTO<List<TemplateWrapperResponseDTO>> create(@NotNull String accountId, @OrgIdentifier String orgId,
      @ProjectIdentifier String projectId, GitEntityCreateInfoDTO gitEntityCreateInfo, @NotNull String webhookEvent,
      boolean setDefaultTemplate, String comments, boolean isNewTemplate) throws IOException {
    HashMap<String, Object> triggerPayloadMap = JsonPipelineUtils.read(webhookEvent, HashMap.class);
    HashMap<String, Object> repository = (HashMap<String, Object>) triggerPayloadMap.get("repository");
    String repoName = repository.get("name").toString();
    String branch = repository.get("default_branch").toString();
    ArrayList<Object> tests = (ArrayList) triggerPayloadMap.get("commits");
    ArrayList<String> added = new ArrayList<>();
    ArrayList<String> modified = new ArrayList<>();
    ArrayList<String> removed = new ArrayList<>();
    for (Object test : tests) {
      HashMap<String, Object> map = (HashMap<String, Object>) test;
      added = (ArrayList) map.get(FILE_ADDED);
      modified = (ArrayList) map.get(FILE_MODIFIED);
      removed = (ArrayList) map.get(FILE_REMOVED);
    }
    List<TemplateWrapperResponseDTO> templateWrapperResponseDTOS = Collections.emptyList();
    if (EmptyPredicate.isNotEmpty(added)) {
      templateWrapperResponseDTOS = templateService.createGlobalTemplate(
          accountId, orgId, projectId, repoName, branch, added, setDefaultTemplate, comments, isNewTemplate);
    }

    if (EmptyPredicate.isNotEmpty(modified)) {
      templateWrapperResponseDTOS = templateService.updateGlobalTemplate(
          accountId, orgId, projectId, repoName, branch, modified, setDefaultTemplate, comments);
    }
    return ResponseDTO.newResponse(templateWrapperResponseDTOS);
  }
}
