/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputs.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.inputset.InputSetMoveConfigOperationDTO;
import io.harness.pms.ngpipeline.inputs.beans.entity.InputEntity;
import io.harness.pms.ngpipeline.inputs.beans.entity.RepositoryInput;
import io.harness.pms.ngpipeline.inputs.mappers.PMSInputsElementMapper;
import io.harness.pms.ngpipeline.inputs.service.PMSInputsService;
import io.harness.pms.ngpipeline.inputset.api.InputSetsApiUtils;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.api.PipelinesApiUtils;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.spec.server.pipeline.v1.InputsApi;
import io.harness.spec.server.pipeline.v1.model.InputSetMoveConfigRequestBody;
import io.harness.spec.server.pipeline.v1.model.InputSetMoveConfigResponseBody;
import io.harness.spec.server.pipeline.v1.model.InputsResponseBody;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class InputsApiImpl implements InputsApi {
  private final PMSInputsService pmsInputsService;
  private final PMSPipelineService pmsPipelineService;

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response getPipelineInputs(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, @AccountIdentifier String account, String branch, String repo,
      String connector) {
    log.info(String.format(
        "Retrieving inputs for pipeline %s in project %s, org %s, account %s", pipeline, project, org, account));
    GitAwareContextHelper.populateGitDetails(
        GitEntityInfo.builder().branch(branch).connectorRef(connector).repoName(repo).build());
    Optional<PipelineEntity> optionalPipelineEntity =
        pmsPipelineService.getPipeline(account, org, project, pipeline, false, false);
    if (optionalPipelineEntity.isEmpty()) {
      throw new EntityNotFoundException(
          String.format("Pipeline with the given ID: %s does not exist or has been deleted", pipeline));
    }
    PipelineEntity pipelineEntity = optionalPipelineEntity.get();
    String pipelineYaml = pipelineEntity.getYaml();
    Optional<Map<String, InputEntity>> optionalInputEntityMap = pmsInputsService.get(pipelineYaml);
    if (optionalInputEntityMap.isEmpty()) {
      throw new IllegalStateException(String.format("Error in parsing inputs for pipeline %s", pipeline));
    }
    Map<String, InputEntity> inputEntityMap = optionalInputEntityMap.get();
    Optional<RepositoryInput> optionalRepositoryInput = pmsInputsService.getRepository(pipelineYaml);
    InputsResponseBody inputsResponseBody =
        PMSInputsElementMapper.inputsResponseDTOPMS(inputEntityMap, optionalRepositoryInput.orElse(null));
    return Response.ok().entity(inputsResponseBody).build();
  }
}
