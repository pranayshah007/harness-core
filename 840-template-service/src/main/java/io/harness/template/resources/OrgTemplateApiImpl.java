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
import io.harness.spec.server.template.OrgTemplateApi;
import io.harness.spec.server.template.model.GitCreateDetails;
import io.harness.spec.server.template.model.GitFindDetails;
import io.harness.spec.server.template.model.GitUpdateDetails;
import io.harness.spec.server.template.model.TemplateCreateRequestBody;
import io.harness.spec.server.template.model.TemplateFilterProperties;
import io.harness.spec.server.template.model.TemplateUpdateRequestBody;

import com.google.inject.Inject;
import io.swagger.v3.oas.annotations.tags.Tag;
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
      @AccountIdentifier String account, Boolean isStable, String comments) {
    GitCreateDetails gitCreateDetails = templateCreateRequestBody.getGitDetails();
    String templateYaml = templateCreateRequestBody.getTemplateYaml();
    return templateResourceApiUtils.createTemplate(
        account, org, null, gitCreateDetails, templateYaml, isStable, comments);
  }

  @Override
  public Response deleteTemplateOrg(@ResourceIdentifier String templateIdentifier, @OrgIdentifier String org,
      String versionLabel, @AccountIdentifier String account, String comments) {
    return templateResourceApiUtils.deleteTemplate(account, org, null, templateIdentifier, versionLabel, comments);
  }

  @Override
  public Response getTemplateOrg(@ResourceIdentifier String templateIdentifier, @OrgIdentifier String org,
      String versionLabel, GitFindDetails gitFindDetails, @AccountIdentifier String account, Boolean getInputYaml) {
    return templateResourceApiUtils.getTemplate(
        account, org, null, templateIdentifier, versionLabel, false, gitFindDetails, getInputYaml);
  }

  @Override
  public Response getTemplateStableOrg(@OrgIdentifier String org, @ResourceIdentifier String templateIdentifier,
      GitFindDetails gitFindDetails, @AccountIdentifier String account, Boolean getInputYaml) {
    return templateResourceApiUtils.getTemplate(
        account, org, null, templateIdentifier, null, false, gitFindDetails, getInputYaml);
  }

  @Override
  public Response getTemplatesListOrg(@OrgIdentifier String org, TemplateFilterProperties templateFilterProperties,
      @AccountIdentifier String account, Integer page, Integer limit, String sort, String order, String searchTerm,
      String listType, Boolean recursive) {
    return templateResourceApiUtils.getTemplates(
        account, org, null, page, limit, sort, order, searchTerm, listType, recursive, templateFilterProperties);
  }

  @Override
  public Response updateTemplateOrg(@ResourceIdentifier String templateIdentifier, @OrgIdentifier String org,
      String versionLabel, TemplateUpdateRequestBody templateUpdateRequestBody, @AccountIdentifier String account,
      Boolean isStable, String comments) {
    GitUpdateDetails gitUpdateDetails = templateUpdateRequestBody.getGitDetails();
    String templateYaml = templateUpdateRequestBody.getTemplateYaml();
    return templateResourceApiUtils.updateTemplate(
        account, org, null, templateIdentifier, versionLabel, gitUpdateDetails, templateYaml, isStable, comments);
  }

  @Override
  public Response updateTemplateStableOrg(@OrgIdentifier String org, @ResourceIdentifier String templateIdentifier,
      String versionLabel, GitFindDetails gitFindDetails, @AccountIdentifier String account, String comments) {
    return templateResourceApiUtils.updateStableTemplate(
        account, org, null, templateIdentifier, versionLabel, gitFindDetails, comments);
  }
}
