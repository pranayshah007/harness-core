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
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.template.v1.OrgTemplateApi;
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

@OwnedBy(CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
public class OrgTemplateApiImpl implements OrgTemplateApi {
  private final TemplateResourceApiUtils templateResourceApiUtils;
  @Override
  public Response createTemplatesOrg(@OrgIdentifier String org, TemplateCreateRequestBody templateCreateRequestBody,
      @AccountIdentifier String account) {
    GitCreateDetails gitCreateDetails = templateCreateRequestBody.getGitDetails();
    String templateYaml = templateCreateRequestBody.getTemplateYaml();
    Boolean isStable = Boolean.TRUE.equals(templateCreateRequestBody.isIsStable());
    return templateResourceApiUtils.createTemplate(
        account, org, null, gitCreateDetails, templateYaml, isStable, templateCreateRequestBody.getComments());
  }

  @Override
  public Response deleteTemplateOrg(@ResourceIdentifier String templateIdentifier, @OrgIdentifier String org,
      String versionLabel, @AccountIdentifier String account, String comments, Boolean forceDelete) {
    return templateResourceApiUtils.deleteTemplate(
        account, org, null, templateIdentifier, versionLabel, comments, Boolean.TRUE == forceDelete);
  }

  @Override
  public Response getTemplateOrg(@ResourceIdentifier String templateIdentifier, @OrgIdentifier String org,
      String versionLabel, @AccountIdentifier String account, Boolean getInputYaml, String branch,
      String parentConnectorRef, String parentRepoName, String parentAccountId, String parentOrgId,
      String parentProjectId) {
    return templateResourceApiUtils.getTemplate(account, org, null, templateIdentifier, versionLabel, false, branch,
        parentConnectorRef, parentRepoName, parentAccountId, parentOrgId, parentProjectId, getInputYaml);
  }

  @Override
  public Response getTemplateStableOrg(@OrgIdentifier String org, @ResourceIdentifier String templateIdentifier,
      @AccountIdentifier String account, Boolean getInputYaml, String branch, String parentConnectorRef,
      String parentRepoName, String parentAccountId, String parentOrgId, String parentProjectId) {
    return templateResourceApiUtils.getTemplate(account, org, null, templateIdentifier, null, false, branch,
        parentConnectorRef, parentRepoName, parentAccountId, parentOrgId, parentProjectId, getInputYaml);
  }

  @Override
  public Response getTemplatesListOrg(@OrgIdentifier String org, @AccountIdentifier String account, Integer page,
      Integer limit, String sort, String order, String searchTerm, String listType, Boolean recursive,
      List<String> names, List<String> identifiers, String description, List<String> entityTypes,
      List<String> childTypes) {
    return templateResourceApiUtils.getTemplates(account, org, null, page, limit, sort, order, searchTerm, listType,
        recursive, names, identifiers, description, entityTypes, childTypes);
  }

  @Override
  public Response importTemplateOrg(@OrgIdentifier String org, @ResourceIdentifier String template,
      @Valid TemplateImportRequestBody body, @AccountIdentifier String harnessAccount) {
    return templateResourceApiUtils.importTemplate(
        harnessAccount, org, null, template, body.getGitImportDetails(), body.getTemplateImportRequest());
  }

  @Override
  public Response updateTemplateOrg(@ResourceIdentifier String templateIdentifier, @OrgIdentifier String org,
      String versionLabel, TemplateUpdateRequestBody templateUpdateRequestBody, @AccountIdentifier String account) {
    GitUpdateDetails gitUpdateDetails = templateUpdateRequestBody.getGitDetails();
    String templateYaml = templateUpdateRequestBody.getTemplateYaml();
    return templateResourceApiUtils.updateTemplate(account, org, null, templateIdentifier, versionLabel,
        gitUpdateDetails, templateYaml, false, templateUpdateRequestBody.getComments());
  }

  @Override
  public Response updateTemplateStableOrg(@OrgIdentifier String org, @ResourceIdentifier String templateIdentifier,
      String versionLabel, GitFindDetails gitFindDetails, @AccountIdentifier String account) {
    return templateResourceApiUtils.updateStableTemplate(
        account, org, null, templateIdentifier, versionLabel, gitFindDetails, gitFindDetails.getComments());
  }
}
