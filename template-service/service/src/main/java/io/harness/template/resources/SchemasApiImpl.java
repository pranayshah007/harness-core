/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.yaml.NGYamlHelper;
import io.harness.pms.yaml.YamlUtils;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.template.v1.SchemasApi;
import io.harness.spec.server.template.v1.model.TemplateInputSchemaDetailsResponseBody;
import io.harness.spec.server.template.v1.model.TemplateInputsSchemaRequestBody;
import io.harness.spec.server.template.v1.model.TemplateSchemaResponse;
import io.harness.template.services.NGTemplateSchemaService;
import io.harness.template.services.TemplateMergeService;
import io.harness.yaml.schema.inputs.beans.YamlInputDetails;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.util.List;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@OwnedBy(CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
public class SchemasApiImpl implements SchemasApi {
  private final NGTemplateSchemaService ngTemplateSchemaService;
  private final TemplateMergeService templateMergeService;

  @Override
  public Response getInputsSchema(
      TemplateInputsSchemaRequestBody body, String harnessAccount, String orgId, String projectId) {
    TemplateMergeResponseDTO templateMergeResponseDTO = templateMergeService.applyTemplatesToYamlV2(harnessAccount,
        orgId, projectId, YamlUtils.readAsJsonNode(body.getTemplateYaml()), false, false, false,
        NGYamlHelper.getVersion(body.getTemplateYaml()));
    List<YamlInputDetails> yamlInputDetails =
        ngTemplateSchemaService.getInputSchemaDetails(templateMergeResponseDTO.getMergedPipelineYaml());
    TemplateInputSchemaDetailsResponseBody responseBody =
        TemplateResourceApiHelper.getTemplateInputSchemaDetailsResponseBody(yamlInputDetails);
    return Response.ok().entity(responseBody).build();
  }

  @Override
  public Response getTemplateSchema(String nodeGroup, String nodeType, String harnessAccount, String version) {
    ObjectNode schema = ngTemplateSchemaService.getIndividualStaticSchema(nodeGroup, nodeType, version);
    TemplateSchemaResponse response = new TemplateSchemaResponse();
    response.setData(schema);
    return Response.ok().entity(response).build();
  }
}
