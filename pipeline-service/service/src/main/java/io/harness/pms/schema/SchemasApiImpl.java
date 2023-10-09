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
import io.harness.pms.pipeline.api.PipelinesApiUtils;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.serializer.JsonUtils;
import io.harness.spec.server.pipeline.v1.SchemasApi;
import io.harness.spec.server.pipeline.v1.model.IndividualSchemaResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineInputSchemaDetailsResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineInputsSchemaRequestBody;
import io.harness.yaml.schema.inputs.beans.YamlInputDetails;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class SchemasApiImpl implements SchemasApi {
  private final PMSYamlSchemaService pmsYamlSchemaService;

  @SneakyThrows
  @Override
  public Response getIndividualStaticSchema(
      String harnessAccount, String nodeGroup, String nodeType, String nodeGroupDifferentiator, String version) {
    ObjectNode schema = (ObjectNode) fetchFile("static-schema/v1/pipeline.json");
    IndividualSchemaResponseBody responseBody = new IndividualSchemaResponseBody();
    responseBody.setData(schema);
    return Response.ok().entity(responseBody).build();
  }

  @Override
  public Response getInputsSchema(@Valid PipelineInputsSchemaRequestBody body, String harnessAccount) {
    List<YamlInputDetails> yamlInputDetails = pmsYamlSchemaService.getInputSchemaDetails(body.getPipelineYaml());
    PipelineInputSchemaDetailsResponseBody responseBody =
        PipelinesApiUtils.getPipelineInputSchemaDetailsResponseBody(yamlInputDetails);
    return Response.ok().entity(responseBody).build();
  }

  JsonNode fetchFile(String filePath) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String staticJson =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filePath)), StandardCharsets.UTF_8);
    return JsonUtils.asObject(staticJson, JsonNode.class);
  }
}
