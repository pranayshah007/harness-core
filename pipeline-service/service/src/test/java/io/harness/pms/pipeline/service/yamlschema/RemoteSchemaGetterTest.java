/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.pipeline.service.yamlschema.exception.YamlSchemaCacheException;
import io.harness.rule.Owner;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.YamlGroup;
import io.harness.yaml.schema.beans.YamlSchemaDetailsWrapper;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;
import io.harness.yaml.schema.client.YamlSchemaClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class RemoteSchemaGetterTest {
  private YamlSchemaClient schemaClient;
  private RemoteSchemaGetter remoteSchemaGetter;
  private MockedStatic<SafeHttpCall> aStatic;

  @Before
  public void setUp() {
    schemaClient = mock(YamlSchemaClient.class);
    remoteSchemaGetter = spy(new RemoteSchemaGetter(schemaClient, ModuleType.CD, "accountId"));
    aStatic = mockStatic(SafeHttpCall.class);
  }

  @After
  public void cleanup() {
    aStatic.close();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetSchema() throws IOException {
    YamlSchemaMetadata yamlSchemaMetadata = YamlSchemaMetadata.builder()
                                                .yamlGroup(YamlGroup.builder().group("step").build())
                                                .modulesSupported(Collections.singletonList(ModuleType.CD))
                                                .build();
    YamlSchemaWithDetails yamlSchemaWithDetails = YamlSchemaWithDetails.builder()
                                                      .yamlSchemaMetadata(yamlSchemaMetadata)
                                                      .moduleType(ModuleType.CD)
                                                      .schemaClassName("HttpStepNode")
                                                      .build();
    List<YamlSchemaWithDetails> yamlSchemaWithDetailsList = new ArrayList<>();
    yamlSchemaWithDetailsList.add(yamlSchemaWithDetails);

    Call<ResponseDTO<List<PartialSchemaDTO>>> call = mock(Call.class);
    doReturn(call).when(schemaClient).get(any(), any(), any(), any(), any());
    List<PartialSchemaDTO> partialSchemaDTOList = new ArrayList<>();
    partialSchemaDTOList.add(
        PartialSchemaDTO.builder().namespace("cd").moduleType(ModuleType.CD).nodeName("deployment").build());
    ResponseDTO<List<PartialSchemaDTO>> responseDTO = ResponseDTO.newResponse(partialSchemaDTOList);
    Response<ResponseDTO<List<PartialSchemaDTO>>> successResponse = Response.success(responseDTO);
    when(call.execute()).thenReturn(successResponse);

    List<PartialSchemaDTO> response = remoteSchemaGetter.getSchema(yamlSchemaWithDetailsList);

    assertNotNull(response);
    assertEquals(response, partialSchemaDTOList);

    when(call.execute()).thenThrow(new RuntimeException());
    assertNull(remoteSchemaGetter.getSchema(yamlSchemaWithDetailsList));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetSchemaDetails() throws IOException {
    Call<ResponseDTO<YamlSchemaDetailsWrapper>> call = mock(Call.class);
    doReturn(call).when(schemaClient).getSchemaDetails(any(), any(), any(), any());

    YamlSchemaWithDetails yamlSchemaWithDetails = YamlSchemaWithDetails.builder()
                                                      .yamlSchemaMetadata(YamlSchemaMetadata.builder().build())
                                                      .moduleType(ModuleType.CD)
                                                      .schemaClassName("K8sCanaryStep")
                                                      .build();

    YamlSchemaDetailsWrapper yamlSchemaDetailsWrapper =
        YamlSchemaDetailsWrapper.builder()
            .yamlSchemaWithDetailsList(Collections.singletonList(yamlSchemaWithDetails))
            .build();
    ResponseDTO<YamlSchemaDetailsWrapper> responseDTO = ResponseDTO.newResponse(yamlSchemaDetailsWrapper);
    Response<ResponseDTO<YamlSchemaDetailsWrapper>> successResponse = Response.success(responseDTO);
    when(call.execute()).thenReturn(successResponse);

    YamlSchemaDetailsWrapper response = remoteSchemaGetter.getSchemaDetails();
    assertNotNull(response);
    assertEquals(response, yamlSchemaDetailsWrapper);

    when(call.execute()).thenThrow(new RuntimeException());
    assertThatThrownBy(() -> remoteSchemaGetter.getSchemaDetails()).isInstanceOf(YamlSchemaCacheException.class);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFetchStepYamlSchema() throws IOException {
    YamlSchemaWithDetails yamlSchemaWithDetails = YamlSchemaWithDetails.builder()
                                                      .yamlSchemaMetadata(YamlSchemaMetadata.builder().build())
                                                      .moduleType(ModuleType.CD)
                                                      .schemaClassName("HttpStepNode")
                                                      .build();
    List<YamlSchemaWithDetails> yamlSchemaWithDetailsList = new ArrayList<>();
    yamlSchemaWithDetailsList.add(yamlSchemaWithDetails);

    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode cdStageSchema = objectMapper.readTree(getResource("cdStageSchema.json"));

    Call<ResponseDTO<JsonNode>> call = mock(Call.class);
    doReturn(call).when(schemaClient).getStepSchema(any(), any(), any(), any(), any(), any(), any());
    ResponseDTO<JsonNode> responseDTO = ResponseDTO.newResponse(cdStageSchema);
    Response<ResponseDTO<JsonNode>> successResponse = Response.success(responseDTO);
    when(call.execute()).thenReturn(successResponse);

    JsonNode response = remoteSchemaGetter.fetchStepYamlSchema(
        "orgId", "projectId", null, EntityType.DEPLOYMENT_STAGE, "STAGE", yamlSchemaWithDetailsList);
    assertNotNull(response);
    assertEquals(response, cdStageSchema);

    when(call.execute()).thenThrow(new RuntimeException());
    assertThatThrownBy(()
                           -> remoteSchemaGetter.fetchStepYamlSchema("orgId", "projectId", null,
                               EntityType.DEPLOYMENT_STAGE, "STAGE", yamlSchemaWithDetailsList))
        .isInstanceOf(YamlSchemaCacheException.class);
  }

  private String getResource(String path) throws IOException {
    return IOUtils.resourceToString(path, StandardCharsets.UTF_8, this.getClass().getClassLoader());
  }
}
