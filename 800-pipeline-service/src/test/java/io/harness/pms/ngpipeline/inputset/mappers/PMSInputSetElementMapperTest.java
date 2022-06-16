/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.mappers;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMAN;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSummaryResponseDTOPMS;
import io.harness.pms.ngpipeline.overlayinputset.beans.resource.OverlayInputSetResponseDTOPMS;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PMSInputSetElementMapperTest extends CategoryTest {
  private final String PIPELINE_IDENTIFIER = "Test_Pipline11";
  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";

  String inputSetYaml;
  String overlayInputSetYaml;

  @Before
  public void setUp() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    String inputSet = "inputSet1.yml";
    inputSetYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(inputSet)), StandardCharsets.UTF_8);

    String overlayInputSet = "overlaySet1.yml";
    overlayInputSetYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(overlayInputSet)), StandardCharsets.UTF_8);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToInputSetEntity() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntity(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, inputSetYaml);

    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("company", "harness");
    tags.put("kind", "normal");
    List<NGTag> tagsList = TagMapper.convertToList(tags);

    assertThat(entity.getIdentifier()).isEqualTo("input1");
    assertThat(entity.getName()).isEqualTo("this name");
    assertThat(entity.getDescription()).isEqualTo("this has a description too");
    assertThat(entity.getTags()).isEqualTo(tagsList);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToInputSetEntityWithEmptyIdentifierAndName() {
    String emptyID = "inputSet:\n"
        + "    name: \"\"\n"
        + "    identifier: \"\"\n"
        + "    orgIdentifier: default\n"
        + "    projectIdentifier: Plain_Old_Project\n";
    assertThatThrownBy(()
                           -> PMSInputSetElementMapper.toInputSetEntity(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, emptyID))
        .hasMessage("Input Set Identifier cannot be empty or a runtime input");

    String emptyName = "inputSet:\n"
        + "    name: \"\"\n"
        + "    identifier: \"id\"\n"
        + "    orgIdentifier: default\n"
        + "    projectIdentifier: Plain_Old_Project\n";
    assertThatThrownBy(()
                           -> PMSInputSetElementMapper.toInputSetEntity(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, emptyName))
        .hasMessage("Input Set Name cannot be empty or a runtime input");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetEntityGitDetails() {
    InputSetEntity oldNonGitSync = InputSetEntity.builder().build();
    EntityGitDetails entityGitDetails0 = PMSInputSetElementMapper.getEntityGitDetails(oldNonGitSync);
    assertThat(entityGitDetails0).isEqualTo(EntityGitDetails.builder().build());

    InputSetEntity oldGitSync = InputSetEntity.builder().yamlGitConfigRef("repo").branch("branch1").build();
    EntityGitDetails entityGitDetails1 = PMSInputSetElementMapper.getEntityGitDetails(oldGitSync);
    assertThat(entityGitDetails1).isNotNull();
    assertThat(entityGitDetails1.getRepoIdentifier()).isEqualTo("repo");
    assertThat(entityGitDetails1.getBranch()).isEqualTo("branch1");

    InputSetEntity inline = InputSetEntity.builder().storeType(StoreType.INLINE).build();
    EntityGitDetails entityGitDetails2 = PMSInputSetElementMapper.getEntityGitDetails(inline);
    assertThat(entityGitDetails2).isNull();

    GitAwareContextHelper.updateScmGitMetaData(
        ScmGitMetaData.builder().branchName("brName").repoName("repoName").build());

    InputSetEntity remote = InputSetEntity.builder().storeType(StoreType.REMOTE).build();
    EntityGitDetails entityGitDetails3 = PMSInputSetElementMapper.getEntityGitDetails(remote);
    assertThat(entityGitDetails3).isNotNull();
    assertThat(entityGitDetails3.getBranch()).isEqualTo("brName");
    assertThat(entityGitDetails3.getRepoName()).isEqualTo("repoName");
    assertThat(entityGitDetails3.getRepoIdentifier()).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToInputSetEntityForOverlay() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityForOverlay(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, overlayInputSetYaml);

    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("isOverlaySet", "yes it is");
    List<NGTag> tagsList = TagMapper.convertToList(tags);
    List<String> references = new ArrayList<>();
    references.add("inputSet2");
    references.add("inputSet22");

    assertThat(entity.getIdentifier()).isEqualTo("overlay1");
    assertThat(entity.getName()).isEqualTo("thisName");
    assertThat(entity.getDescription()).isEqualTo("this is an overlay input set");
    assertThat(entity.getTags()).isEqualTo(tagsList);
    assertThat(entity.getInputSetReferences()).isEqualTo(references);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToInputSetResponseDTOPMS() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntity(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, inputSetYaml);
    InputSetResponseDTOPMS inputSetResponseDTOPMS = PMSInputSetElementMapper.toInputSetResponseDTOPMS(entity);
    assertThat(inputSetResponseDTOPMS.isErrorResponse()).isFalse();
    assertThat(inputSetResponseDTOPMS.getInputSetErrorWrapper()).isNull();

    assertThat(inputSetResponseDTOPMS.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(inputSetResponseDTOPMS.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);

    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("company", "harness");
    tags.put("kind", "normal");
    assertThat(inputSetResponseDTOPMS.getIdentifier()).isEqualTo("input1");
    assertThat(inputSetResponseDTOPMS.getName()).isEqualTo("this name");
    assertThat(inputSetResponseDTOPMS.getDescription()).isEqualTo("this has a description too");
    assertThat(inputSetResponseDTOPMS.getTags()).isEqualTo(tags);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToOverlayInputSetResponseDTOPMS() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityForOverlay(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, overlayInputSetYaml);
    OverlayInputSetResponseDTOPMS inputSetResponseDTOPMS =
        PMSInputSetElementMapper.toOverlayInputSetResponseDTOPMS(entity);
    assertThat(inputSetResponseDTOPMS.isErrorResponse()).isFalse();
    assertThat(inputSetResponseDTOPMS.getInvalidInputSetReferences()).isNull();

    assertThat(inputSetResponseDTOPMS.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(inputSetResponseDTOPMS.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);

    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("isOverlaySet", "yes it is");
    List<String> references = new ArrayList<>();
    references.add("inputSet2");
    references.add("inputSet22");
    assertThat(inputSetResponseDTOPMS.getIdentifier()).isEqualTo("overlay1");
    assertThat(inputSetResponseDTOPMS.getName()).isEqualTo("thisName");
    assertThat(inputSetResponseDTOPMS.getDescription()).isEqualTo("this is an overlay input set");
    assertThat(inputSetResponseDTOPMS.getTags()).isEqualTo(tags);
    assertThat(inputSetResponseDTOPMS.getInputSetReferences()).isEqualTo(references);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToInputSetSummaryResponseDTOPMS() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntity(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, inputSetYaml);

    InputSetErrorWrapperDTOPMS inputSetErrorWrapperDTOPMS = null;
    Map<String, String> overlaySetErrorDetails = null;

    InputSetSummaryResponseDTOPMS inputSetResponseDTOPMS = PMSInputSetElementMapper.toInputSetSummaryResponseDTOPMS(
        entity, inputSetErrorWrapperDTOPMS, overlaySetErrorDetails);

    assertThat(inputSetResponseDTOPMS.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);

    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("company", "harness");
    tags.put("kind", "normal");
    assertThat(inputSetResponseDTOPMS.getIdentifier()).isEqualTo("input1");
    assertThat(inputSetResponseDTOPMS.getName()).isEqualTo("this name");
    assertThat(inputSetResponseDTOPMS.getDescription()).isEqualTo("this has a description too");
    assertThat(inputSetResponseDTOPMS.getTags()).isEqualTo(tags);

    InputSetEntity entity1 = PMSInputSetElementMapper.toInputSetEntityForOverlay(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, overlayInputSetYaml);
    OverlayInputSetResponseDTOPMS inputSetResponseDTOPMS1 =
        PMSInputSetElementMapper.toOverlayInputSetResponseDTOPMS(entity1);

    assertThat(inputSetResponseDTOPMS1.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);

    tags = new LinkedHashMap<>();
    tags.put("isOverlaySet", "yes it is");
    assertThat(inputSetResponseDTOPMS1.getIdentifier()).isEqualTo("overlay1");
    assertThat(inputSetResponseDTOPMS1.getName()).isEqualTo("thisName");
    assertThat(inputSetResponseDTOPMS1.getDescription()).isEqualTo("this is an overlay input set");
    assertThat(inputSetResponseDTOPMS1.getTags()).isEqualTo(tags);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToInputSetResponseDTOPMSWithErrors() {
    GitAwareContextHelper.updateScmGitMetaData(
        ScmGitMetaData.builder().branchName("brName").repoName("repoName").build());
    InputSetEntity entity =
        PMSInputSetElementMapper
            .toInputSetEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, inputSetYaml)
            .withStoreType(StoreType.REMOTE);
    InputSetErrorWrapperDTOPMS dummyErrorResponse =
        InputSetErrorWrapperDTOPMS.builder().uuidToErrorResponseMap(Collections.singletonMap("fqn", null)).build();
    InputSetResponseDTOPMS inputSetResponseDTO =
        PMSInputSetElementMapper.toInputSetResponseDTOPMSWithErrors(entity, dummyErrorResponse);
    assertThat(inputSetResponseDTO.isErrorResponse()).isTrue();
    assertThat(inputSetResponseDTO.getInputSetErrorWrapper()).isEqualTo(dummyErrorResponse);

    assertThat(inputSetResponseDTO.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(inputSetResponseDTO.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(inputSetResponseDTO.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(inputSetResponseDTO.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);

    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("company", "harness");
    tags.put("kind", "normal");
    assertThat(inputSetResponseDTO.getIdentifier()).isEqualTo("input1");
    assertThat(inputSetResponseDTO.getName()).isEqualTo("this name");
    assertThat(inputSetResponseDTO.getDescription()).isEqualTo("this has a description too");
    assertThat(inputSetResponseDTO.getTags()).isEqualTo(tags);

    EntityValidityDetails entityValidityDetails = inputSetResponseDTO.getEntityValidityDetails();
    assertThat(entityValidityDetails.isValid()).isFalse();
    assertThat(entityValidityDetails.getInvalidYaml()).isEqualTo(inputSetYaml);

    EntityGitDetails gitDetails = inputSetResponseDTO.getGitDetails();
    assertThat(gitDetails.getRepoName()).isEqualTo("repoName");
    assertThat(gitDetails.getRepoIdentifier()).isNull();
    assertThat(gitDetails.getBranch()).isEqualTo("brName");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testToInputSetEntityByYaml() {
    InputSetEntity inputSetEntity = PMSInputSetElementMapper.toInputSetEntity("accountId", inputSetYaml);
    assertEquals(inputSetEntity.getAccountId(), "accountId");
    assertEquals(inputSetEntity.getPipelineIdentifier(), "Test_Pipline11");
    assertEquals(inputSetEntity.getIdentifier(), "input1");
    inputSetEntity = PMSInputSetElementMapper.toInputSetEntity("accountId", overlayInputSetYaml);
    assertEquals(inputSetEntity.getAccountId(), "accountId");
    assertEquals(inputSetEntity.getIdentifier(), "overlay1");
  }
}
