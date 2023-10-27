/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.resources;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.template.v1.ProjectTemplateApi;
import io.harness.spec.server.template.v1.model.GitCreateDetails;
import io.harness.spec.server.template.v1.model.GitFindDetails;
import io.harness.spec.server.template.v1.model.GitUpdateDetails;
import io.harness.spec.server.template.v1.model.TemplateCreateRequestBody;
import io.harness.spec.server.template.v1.model.TemplateImportRequestBody;
import io.harness.spec.server.template.v1.model.TemplateUpdateRequestBody;

import com.google.inject.Inject;
import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
public class ProjectTemplateApiImpl implements ProjectTemplateApi {
  private final TemplateResourceApiUtils templateResourceApiUtils;
  @Override
  public Response createTemplatesProject(@OrgIdentifier String org, @ProjectIdentifier String project,
      TemplateCreateRequestBody templateCreateRequestBody, @AccountIdentifier String account) {
    GitCreateDetails gitCreateDetails = templateCreateRequestBody.getGitDetails();
    TemplateRequestInfoDTO requestInfoDTO =
        templateResourceApiUtils.mapCreateToRequestInfoDTO(templateCreateRequestBody);
    Boolean isStable = Boolean.TRUE.equals(templateCreateRequestBody.isIsStable());
    return templateResourceApiUtils.createTemplate(
        account, org, project, gitCreateDetails, requestInfoDTO, isStable, templateCreateRequestBody.getComments());
  }

  @Override
  public Response deleteTemplateProject(@ProjectIdentifier String project,
      @ResourceIdentifier String templateIdentifier, @OrgIdentifier String org, String versionLabel,
      @AccountIdentifier String account, String comments, Boolean forceDelete) {
    return templateResourceApiUtils.deleteTemplate(
        account, org, project, templateIdentifier, versionLabel, comments, Boolean.TRUE == forceDelete);
  }

  @Override
  public Response getTemplateProject(@ProjectIdentifier String project, @ResourceIdentifier String templateIdentifier,
      @OrgIdentifier String org, String versionLabel, @AccountIdentifier String account, Boolean getInputYaml,
      String branch, String parentConnectorRef, String parentRepoName, String parentAccountId, String parentOrgId,
      String parentProjectId) {
    return templateResourceApiUtils.getTemplate(account, org, project, templateIdentifier, versionLabel, false, branch,
        parentConnectorRef, parentRepoName, parentAccountId, parentOrgId, parentProjectId, getInputYaml);
  }

  @Override
  public Response getTemplateStableProject(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String templateIdentifier, @AccountIdentifier String account, Boolean getInputYaml,
      String branch, String parentConnectorRef, String parentRepoName, String parentAccountId, String parentOrgId,
      String parentProjectId) {
    return templateResourceApiUtils.getTemplate(account, org, project, templateIdentifier, null, false, branch,
        parentConnectorRef, parentRepoName, parentAccountId, parentOrgId, parentProjectId, getInputYaml);
  }

  @Override
  public Response getTemplatesListProject(@OrgIdentifier String org, @ProjectIdentifier String project,
      @AccountIdentifier String account, Integer page, Integer limit, String sort, String order, String searchTerm,
      String listType, Boolean recursive, List<String> names, List<String> identifiers, String description,
      List<String> entityTypes, List<String> childTypes) {
    return templateResourceApiUtils.getTemplates(account, org, project, page, limit, sort, order, searchTerm, listType,
        recursive, names, identifiers, description, entityTypes, childTypes);
  }

  @Override
  public Response importTemplateProject(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String template, @Valid TemplateImportRequestBody body,
      @AccountIdentifier String harnessAccount) {
    return templateResourceApiUtils.importTemplate(
        harnessAccount, org, project, template, body.getGitImportDetails(), body.getTemplateImportRequest());
  }

  @Override
  public Response updateTemplateProject(@ProjectIdentifier String project,
      @ResourceIdentifier String templateIdentifier, @OrgIdentifier String org, String versionLabel,
      TemplateUpdateRequestBody templateUpdateRequestBody, @AccountIdentifier String account) {
    GitUpdateDetails gitUpdateDetails = templateUpdateRequestBody.getGitDetails();
    TemplateRequestInfoDTO requestInfoDTO =
        templateResourceApiUtils.mapUpdateToRequestInfoDTO(templateUpdateRequestBody);
    return templateResourceApiUtils.updateTemplate(account, org, project, templateIdentifier, versionLabel,
        gitUpdateDetails, requestInfoDTO, false, templateUpdateRequestBody.getComments());
  }

  @Override
  public Response updateTemplateStableProject(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String templateIdentifier, String versionLabel, GitFindDetails gitFindDetails,
      @AccountIdentifier String account) {
    return templateResourceApiUtils.updateStableTemplate(
        account, org, project, templateIdentifier, versionLabel, gitFindDetails, gitFindDetails.getComments());
  }
}
