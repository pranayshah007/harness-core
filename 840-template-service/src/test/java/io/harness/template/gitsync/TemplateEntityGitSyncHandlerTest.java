/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.gitsync;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ARCHIT;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.NGTemplateReference;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.rule.Owner;
import io.harness.template.beans.yaml.NGTemplateConfig;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.services.NGTemplateService;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
public class TemplateEntityGitSyncHandlerTest extends CategoryTest {
  @Mock private NGTemplateService templateService;
  @InjectMocks TemplateEntityGitSyncHandler templateEntityGitSyncHandler;

  private final String ACCOUNT_ID = "accountId";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String TEMPLATE_IDENTIFIER = "template1";
  private final String TEMPLATE_VERSION_LABEL = "version1";
  private final String TEMPLATE_CHILD_TYPE = "ShellScript";

  private String yaml;
  TemplateEntity entity;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    String filename = "template.yaml";
    yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);

    entity = TemplateEntity.builder()
                 .accountId(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .projectIdentifier(PROJ_IDENTIFIER)
                 .identifier(TEMPLATE_IDENTIFIER)
                 .name(TEMPLATE_IDENTIFIER)
                 .versionLabel(TEMPLATE_VERSION_LABEL)
                 .yaml(yaml)
                 .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                 .childType(TEMPLATE_CHILD_TYPE)
                 .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
                 .templateScope(Scope.PROJECT)
                 .build();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetEntityDetail() {
    EntityDetail entityDetail = templateEntityGitSyncHandler.getEntityDetail(entity);
    assertThat(entityDetail.getName()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(entityDetail.getType()).isEqualTo(EntityType.TEMPLATE);
    assertThat(entityDetail.getEntityRef().getAccountIdentifier()).isEqualTo(entity.getAccountId());
    assertThat(entityDetail.getEntityRef().getOrgIdentifier()).isEqualTo(entity.getOrgIdentifier());
    assertThat(entityDetail.getEntityRef().getProjectIdentifier()).isEqualTo(entity.getProjectIdentifier());
    assertThat(entityDetail.getEntityRef().getIdentifier()).isEqualTo(entity.getIdentifier());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetLastObjectIdIfExists() {
    String objectId = "objectId";
    doReturn(Optional.of(TemplateEntity.builder().objectIdOfYaml(objectId).build()))
        .when(templateService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, false, false);

    EntityGitDetails lastEntityDetails = templateEntityGitSyncHandler.getEntityDetailsIfExists(ACCOUNT_ID, yaml).get();
    assertThat(lastEntityDetails.getObjectId()).isEqualTo(objectId);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testSave() throws IOException {
    doReturn(entity.withVersion(0L)).when(templateService).create(any(), anyBoolean(), anyString(), anyBoolean());
    NGTemplateConfig templateConfig = templateEntityGitSyncHandler.save(ACCOUNT_ID, yaml);
    verify(templateService, times(1)).create(any(), anyBoolean(), anyString(), anyBoolean());
    assertThat(templateConfig).isEqualTo(YamlPipelineUtils.read(yaml, NGTemplateConfig.class));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testUpdate() throws IOException {
    doReturn(entity.withVersion(1L))
        .when(templateService)
        .updateTemplateEntity(any(), any(), anyBoolean(), anyString());
    NGTemplateConfig templateConfig = templateEntityGitSyncHandler.update(ACCOUNT_ID, yaml, ChangeType.NONE);
    verify(templateService, times(1)).updateTemplateEntity(any(), any(), anyBoolean(), anyString());
    assertThat(templateConfig).isEqualTo(YamlPipelineUtils.read(yaml, NGTemplateConfig.class));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDelete() {
    NGTemplateReference reference = NGTemplateReference.builder()
                                        .accountIdentifier(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier(TEMPLATE_IDENTIFIER)
                                        .versionLabel(TEMPLATE_VERSION_LABEL)
                                        .build();
    doReturn(true)
        .when(templateService)
        .delete(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, null, "", false);
    assertThat(templateEntityGitSyncHandler.delete(reference)).isTrue();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetters() {
    assertEquals(templateEntityGitSyncHandler.getEntityType(), EntityType.TEMPLATE);
    assertEquals(templateEntityGitSyncHandler.getObjectIdOfYamlKey(), TemplateEntityKeys.objectIdOfYaml);
    assertEquals(templateEntityGitSyncHandler.getIsFromDefaultBranchKey(), TemplateEntityKeys.isFromDefaultBranch);
    assertEquals(templateEntityGitSyncHandler.getYamlGitConfigRefKey(), TemplateEntityKeys.yamlGitConfigRef);
    assertEquals(templateEntityGitSyncHandler.getUuidKey(), TemplateEntityKeys.uuid);
    assertEquals(templateEntityGitSyncHandler.getBranchKey(), TemplateEntityKeys.branch);
  }
}
