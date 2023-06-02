/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.inputset.gitsync;

import static io.harness.EntityType.INPUT_SETS;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.beans.InputSetReference;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.EntityDetail;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.api.client.util.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class InputSetEntityGitSyncHelperTest extends CategoryTest {
  @Mock private PMSInputSetService pmsInputSetService;
  @InjectMocks InputSetEntityGitSyncHelper inputSetEntityGitSyncHelper;
  static String accountId = "accountId";
  static String orgId = "orgId";
  static String projectId = "projectId";
  static String pipelineId = "pipelineId";
  static String name = "name";
  static String identifier = "identifier";
  static String inputSetYaml = "inputSet:\n"
      + "  identifier: input1\n"
      + "  name: this name\n"
      + "  description: this has a description too\n"
      + "  tags:\n"
      + "    company: harness\n"
      + "    kind : normal\n"
      + "  pipeline:\n"
      + "    identifier: \"Test_Pipline11\"\n";
  static String overLayYaml;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetEntityDetail() {
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .identifier(identifier)
                                        .name(name)
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .build();

    EntityDetail entityDetail = inputSetEntityGitSyncHelper.getEntityDetail(inputSetEntity);
    assertEquals(entityDetail.getName(), name);
    assertEquals(entityDetail.getType(), INPUT_SETS);
    assertEquals(entityDetail.getEntityRef().getIdentifier(), identifier);
    assertEquals(entityDetail.getEntityRef().getOrgIdentifier(), orgId);
    assertEquals(entityDetail.getEntityRef().getAccountIdentifier(), accountId);
    assertEquals(entityDetail.getEntityRef().getProjectIdentifier(), projectId);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetLastObjectIdIfExists() throws IOException {
    overLayYaml = Resources.toString(this.getClass().getClassLoader().getResource("overlay1.yml"), Charsets.UTF_8);
    String objectId = "objectId";
    doReturn(Optional.of(InputSetEntity.builder().objectIdOfYaml(objectId).build()))
        .when(pmsInputSetService)
        .getWithoutValidations(anyString(), any(), any(), any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean());
    EntityGitDetails returnedEntity =
        inputSetEntityGitSyncHelper.getEntityDetailsIfExists(accountId, inputSetYaml).get();
    verify(pmsInputSetService, times(1))
        .getWithoutValidations(anyString(), any(), any(), any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean());
    assertEquals(returnedEntity.getObjectId(), objectId);
    returnedEntity = inputSetEntityGitSyncHelper.getEntityDetailsIfExists(accountId, overLayYaml).get();
    verify(pmsInputSetService, times(2))
        .getWithoutValidations(anyString(), any(), any(), any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean());
    assertEquals(returnedEntity.getObjectId(), objectId);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSave() throws IOException {
    setupGitContext();
    overLayYaml = Resources.toString(this.getClass().getClassLoader().getResource("overlay1.yml"), Charsets.UTF_8);
    doReturn(InputSetEntity.builder().yaml(inputSetYaml).build()).when(pmsInputSetService).create(any(), anyBoolean());
    InputSetYamlDTO inputSetYamlDTO = inputSetEntityGitSyncHelper.save(accountId, inputSetYaml);
    verify(pmsInputSetService, times(1)).create(any(), anyBoolean());
    assertEquals(inputSetYamlDTO, YamlUtils.read(inputSetYaml, InputSetYamlDTO.class));
    inputSetEntityGitSyncHelper.save(accountId, overLayYaml);
    verify(pmsInputSetService, times(2)).create(any(), anyBoolean());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdate() throws IOException {
    setupGitContext();
    doReturn(InputSetEntity.builder().yaml(inputSetYaml).build())
        .when(pmsInputSetService)
        .update(any(), any(), anyBoolean());
    InputSetYamlDTO inputSetYamlDTO = inputSetEntityGitSyncHelper.update(accountId, inputSetYaml, ChangeType.NONE);
    verify(pmsInputSetService, times(1)).update(any(), any(), anyBoolean());
    assertEquals(inputSetYamlDTO, YamlUtils.read(inputSetYaml, InputSetYamlDTO.class));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testDelete() {
    EntityReference entityReference = InputSetReference.builder()
                                          .identifier(identifier)
                                          .accountIdentifier(accountId)
                                          .orgIdentifier(orgId)
                                          .projectIdentifier(projectId)
                                          .pipelineIdentifier(pipelineId)
                                          .build();
    doReturn(true).when(pmsInputSetService).delete(accountId, orgId, projectId, pipelineId, identifier, null);
    assertTrue(inputSetEntityGitSyncHelper.delete(entityReference));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testKeyGetters() {
    assertEquals(inputSetEntityGitSyncHelper.getEntityType(), INPUT_SETS);
    assertEquals(inputSetEntityGitSyncHelper.getObjectIdOfYamlKey(), InputSetEntityKeys.objectIdOfYaml);
    assertEquals(inputSetEntityGitSyncHelper.getIsFromDefaultBranchKey(), InputSetEntityKeys.isFromDefaultBranch);
    assertEquals(inputSetEntityGitSyncHelper.getYamlGitConfigRefKey(), InputSetEntityKeys.yamlGitConfigRef);
    assertEquals(inputSetEntityGitSyncHelper.getUuidKey(), InputSetEntityKeys.uuid);
    assertEquals(inputSetEntityGitSyncHelper.getBranchKey(), InputSetEntityKeys.branch);
  }

  private void setupGitContext() {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().branch("someBranch").yamlGitConfigId("someRepoID").build();
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(gitEntityInfo).build());
  }
}
