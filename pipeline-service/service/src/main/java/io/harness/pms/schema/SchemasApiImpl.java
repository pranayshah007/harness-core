/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.schema;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.pipeline.service.PMSYamlSchemaServiceImpl;
import io.harness.spec.server.pipeline.v1.SchemasApi;
import io.harness.spec.server.pipeline.v1.model.IndividualSchemaResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineInputsSchemaRequestBody;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class SchemasApiImpl implements SchemasApi {
  private final PMSYamlSchemaServiceImpl pmsYamlSchemaService;

  @Override
  public Response getIndividualStaticSchema(
      String harnessAccount, String nodeGroup, String nodeType, String nodeGroupDifferentiator, String version) {
    ObjectNode schema =
        pmsYamlSchemaService.getStaticSchemaForAllEntities(nodeGroup, nodeType, nodeGroupDifferentiator, version);
    IndividualSchemaResponseBody responseBody = new IndividualSchemaResponseBody();
    responseBody.setData(schema);
    return Response.ok().entity(responseBody).build();
  }

  @Override
  public Response getInputsSchema(@Valid PipelineInputsSchemaRequestBody body, String harnessAccount) {
    return null;
  }
}
