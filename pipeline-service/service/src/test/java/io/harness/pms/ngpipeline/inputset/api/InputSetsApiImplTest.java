/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.ngpipeline.inputset.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.MANKRIT;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.service.InputSetValidationHelper;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.GitMoveDetails;
import io.harness.spec.server.pipeline.v1.model.InputSetCreateRequestBody;
import io.harness.spec.server.pipeline.v1.model.InputSetMoveConfigRequestBody;
import io.harness.spec.server.pipeline.v1.model.InputSetMoveConfigResponseBody;
import io.harness.spec.server.pipeline.v1.model.InputSetResponseBody;
import io.harness.spec.server.pipeline.v1.model.InputSetUpdateRequestBody;
import io.harness.spec.server.pipeline.v1.model.MoveConfigOperationType;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.repository.support.PageableExecutionUtils;

@OwnedBy(PIPELINE)
@PrepareForTest({InputSetValidationHelper.class})
public class InputSetsApiImplTest extends PipelineServiceTestBase {
  InputSetsApiImpl inputSetsApiImpl;
  @Mock PMSInputSetService pmsInputSetService;
  @Mock InputSetsApiUtils inputSetsApiUtils;
  private static final String account = randomAlphabetic(10);
  private static final String org = randomAlphabetic(10);
  private static final String project = randomAlphabetic(10);
  private static final String pipeline = randomAlphabetic(10);
  private static String branch = randomAlphabetic(10);
  private static String repo = randomAlphabetic(10);
  private static String connectorRef = randomAlphabetic(10);
  private static final String inputSet = "input1";
  private static final String inputSetName = "this name";
  private String inputSetYaml;
  private String pipelineYaml;
  InputSetEntity inputSetEntity;
  PipelineEntity pipelineEntity;
  InputSetResponseBody inputSetResponseBody;
  private String readFile(String filename) {
    ClassLoader classLoader = this.getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read file " + filename, e);
    }
  }

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    inputSetsApiImpl = new InputSetsApiImpl(pmsInputSetService, inputSetsApiUtils);

    String inputSetFilename = "inputSet1.yml";
    inputSetYaml = readFile(inputSetFilename);
    String pipelineYamlFileName = "pipeline.yml";
    pipelineYaml = readFile(pipelineYamlFileName);

    inputSetEntity = InputSetEntity.builder()
                         .accountId(account)
                         .orgIdentifier(org)
                         .projectIdentifier(project)
                         .pipelineIdentifier(pipeline)
                         .identifier(inputSet)
                         .name(inputSet)
                         .yaml(inputSetYaml)
                         .inputSetEntityType(InputSetEntityType.INPUT_SET)
                         .build();

    inputSetResponseBody = new InputSetResponseBody();
    inputSetResponseBody.setIdentifier(inputSet);
    inputSetResponseBody.setName(inputSetName);
    inputSetResponseBody.setInputSetYaml(inputSetYaml);
    inputSetResponseBody.setOrg(org);
    inputSetResponseBody.setProject(project);

    pipelineEntity = PipelineEntity.builder()
                         .accountId(account)
                         .orgIdentifier(org)
                         .projectIdentifier(project)
                         .identifier(pipeline)
                         .yaml(pipelineYaml)
                         .version(1L)
                         .build();
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testCreateInputSet() {
    doReturn(inputSetEntity).when(pmsInputSetService).create(any(), anyBoolean());
    doReturn(inputSetResponseBody).when(inputSetsApiUtils).getInputSetResponse(any());
    InputSetCreateRequestBody inputSetCreateRequestBody = new InputSetCreateRequestBody();
    inputSetCreateRequestBody.setIdentifier(inputSet);
    inputSetCreateRequestBody.setName(inputSetName);
    inputSetCreateRequestBody.setInputSetYaml(inputSetYaml);
    doReturn(PipelineVersion.V0).when(inputSetsApiUtils).inputSetVersion(any(), any());
    Response response = inputSetsApiImpl.createInputSet(inputSetCreateRequestBody, pipeline, org, project, account);
    InputSetResponseBody responseBody = (InputSetResponseBody) response.getEntity();
    assertEquals(responseBody.getInputSetYaml(), inputSetYaml);
    assertEquals(responseBody.getName(), inputSetName);
    assertEquals(responseBody.getIdentifier(), inputSet);
    assertEquals(responseBody.getOrg(), org);
    assertEquals(responseBody.getProject(), project);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testDeleteInputSet() {
    doReturn(true).when(pmsInputSetService).delete(account, org, project, pipeline, inputSet, null);
    Response deleteResponse = inputSetsApiImpl.deleteInputSet(org, project, inputSet, pipeline, account);
    assertThat(deleteResponse.getStatus()).isEqualTo(204);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetInputSet() {
    doReturn(Optional.of(inputSetEntity))
        .when(pmsInputSetService)
        .get(account, org, project, pipeline, inputSet, false, null, null, true, false, false);
    doReturn(inputSetResponseBody).when(inputSetsApiUtils).getInputSetResponse(any());
    InputSetCreateRequestBody inputSetCreateRequestBody = new InputSetCreateRequestBody();
    inputSetCreateRequestBody.setIdentifier(inputSet);
    inputSetCreateRequestBody.setInputSetYaml(inputSetYaml);

    Response response =
        inputSetsApiImpl.getInputSet(org, project, inputSet, pipeline, account, null, null, null, false, "false");
    InputSetResponseBody responseBody = (InputSetResponseBody) response.getEntity();
    assertEquals(responseBody.getInputSetYaml(), inputSetYaml);
    assertEquals(responseBody.getName(), inputSetName);
    assertEquals(responseBody.getIdentifier(), inputSet);
    assertEquals(responseBody.getOrg(), org);
    assertEquals(responseBody.getProject(), project);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetInputSetWithCaching() {
    doReturn(Optional.of(inputSetEntity))
        .when(pmsInputSetService)
        .get(account, org, project, pipeline, inputSet, false, null, null, true, false, true);
    doReturn(inputSetResponseBody).when(inputSetsApiUtils).getInputSetResponse(any());
    InputSetCreateRequestBody inputSetCreateRequestBody = new InputSetCreateRequestBody();
    inputSetCreateRequestBody.setIdentifier(inputSet);
    inputSetCreateRequestBody.setInputSetYaml(inputSetYaml);

    Response response =
        inputSetsApiImpl.getInputSet(org, project, inputSet, pipeline, account, null, null, null, false, "true");
    InputSetResponseBody responseBody = (InputSetResponseBody) response.getEntity();
    assertEquals(responseBody.getInputSetYaml(), inputSetYaml);
    assertEquals(responseBody.getName(), inputSetName);
    assertEquals(responseBody.getIdentifier(), inputSet);
    assertEquals(responseBody.getOrg(), org);
    assertEquals(responseBody.getProject(), project);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testUpdateInputSet() {
    doReturn(inputSetEntity).when(pmsInputSetService).update(any(), any(), anyBoolean());
    doReturn(PipelineVersion.V0).when(inputSetsApiUtils).inputSetVersion(any(), any());
    doReturn(inputSetResponseBody).when(inputSetsApiUtils).getInputSetResponse(any());
    InputSetUpdateRequestBody inputSetUpdateRequestBody = new InputSetUpdateRequestBody();
    inputSetUpdateRequestBody.setIdentifier(inputSet);
    inputSetUpdateRequestBody.setName(inputSetName);
    inputSetUpdateRequestBody.setInputSetYaml(inputSetYaml);

    Response response =
        inputSetsApiImpl.updateInputSet(inputSetUpdateRequestBody, pipeline, org, project, inputSet, account);
    InputSetResponseBody responseBody = (InputSetResponseBody) response.getEntity();
    assertEquals(responseBody.getInputSetYaml(), inputSetYaml);
    assertEquals(responseBody.getName(), inputSetName);
    assertEquals(responseBody.getOrg(), org);
    assertEquals(responseBody.getProject(), project);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testListInputSets() {
    doReturn(PageableExecutionUtils.getPage(Collections.singletonList(inputSetEntity),
                 PageRequest.of(0, 10, Sort.by(Direction.DESC, InputSetEntityKeys.createdAt)), () -> 1L))
        .when(pmsInputSetService)
        .list(any(), any(), eq(account), eq(org), eq(project));
    doReturn(inputSetResponseBody).when(inputSetsApiUtils).getInputSetResponse(any());
    Mockito.mockStatic(InputSetValidationHelper.class);

    Response response = inputSetsApiImpl.listInputSets(org, project, pipeline, account, 0, 10, null, null, null);
    List<InputSetResponseBody> content = (List<InputSetResponseBody>) response.getEntity();

    assertThat(content).isNotEmpty();
    assertThat(content.size()).isEqualTo(1);
    InputSetResponseBody responseBody = content.get(0);
    assertThat(responseBody.getIdentifier()).isEqualTo(inputSet);
    assertThat(responseBody.getName()).isEqualTo(inputSetName);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testMoveConfig() {
    GitMoveDetails gitMoveDetails = new GitMoveDetails();
    gitMoveDetails.setBranchName(branch);
    gitMoveDetails.setRepoName(repo);
    gitMoveDetails.setConnectorRef(connectorRef);

    InputSetMoveConfigRequestBody inputSetMoveConfigRequestBody = new InputSetMoveConfigRequestBody();
    inputSetMoveConfigRequestBody.setGitDetails(gitMoveDetails);
    inputSetMoveConfigRequestBody.setMoveConfigOperationType(MoveConfigOperationType.REMOTE_TO_INLINE);
    inputSetMoveConfigRequestBody.setPipelineIdentifier(pipeline);
    inputSetMoveConfigRequestBody.setInputSetIdentifier(inputSet);
    doReturn(InputSetEntity.builder().identifier(inputSet).build())
        .when(pmsInputSetService)
        .moveConfig(any(), any(), any(), any(), any());
    Response response =
        inputSetsApiImpl.inputSetMoveConfig(org, project, inputSet, inputSetMoveConfigRequestBody, account);
    InputSetMoveConfigResponseBody responseBody = (InputSetMoveConfigResponseBody) response.getEntity();
    assertEquals(inputSet, responseBody.getInputSetIdentifier());
  }
}
