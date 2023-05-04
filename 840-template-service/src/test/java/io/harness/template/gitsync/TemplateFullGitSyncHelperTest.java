/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.gitsync;

import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.filter.service.FilterService;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.FileChange;
import io.harness.gitsync.ScopeDetails;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.ng.core.entitydetail.EntityDetailRestToProtoMapper;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.repositories.NGTemplateRepository;
import io.harness.rule.Owner;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.helpers.TemplateEntityDetailUtils;
import io.harness.template.resources.beans.yaml.NGTemplateConfig;
import io.harness.template.services.NGTemplateService;
import io.harness.template.services.NGTemplateServiceHelper;
import io.harness.template.services.TemplateGitXService;
import io.harness.template.utils.NGTemplateFeatureFlagHelperService;

import com.google.common.io.Resources;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.CDC)
public class TemplateFullGitSyncHelperTest extends CategoryTest {
  @InjectMocks TemplateFullGitSyncHelper templateFullGitSyncHelper;
  @Mock EntityDetailRestToProtoMapper entityDetailRestToProtoMapper;
  @Mock NGTemplateService templateService;

  @Mock FilterService filterService;
  NGTemplateServiceHelper templateServiceHelper;
  @Mock NGTemplateRepository templateRepository;
  @Mock GitSyncSdkService gitSyncSdkService;

  @Mock TemplateGitXService templateGitXService;

  @Mock GitAwareEntityHelper gitAwareEntityHelper;

  @Mock NGTemplateFeatureFlagHelperService ngTemplateFeatureFlagHelperService;
  private final String ACCOUNT_ID = "accountId";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String TEMPLATE_IDENTIFIER = "template1";
  private final String TEMPLATE_VERSION_LABEL = "version1";
  private final String TEMPLATE_CHILD_TYPE = "ShellScript";

  private String yaml;
  TemplateEntity templateEntity;
  EntityDetailProtoDTO entityDetailProtoDTO;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    String filename = "template.yaml";
    yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);

    templateServiceHelper = new NGTemplateServiceHelper(
        filterService, templateRepository, gitSyncSdkService, templateGitXService, gitAwareEntityHelper);
    Reflect.on(templateFullGitSyncHelper).set("templateServiceHelper", templateServiceHelper);

    entityDetailProtoDTO = EntityDetailProtoDTO.newBuilder().setType(EntityTypeProtoEnum.TEMPLATE).build();

    templateEntity = TemplateEntity.builder()
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
                         .version(0L)
                         .build();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetAllEntitiesForFullSync() {
    Criteria criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, null, false);
    criteria = criteria.and(TemplateEntityKeys.yamlGitConfigRef).is(null);
    PageRequest pageRequest = PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));

    Page<TemplateEntity> templateSetPages = new PageImpl<>(Collections.singletonList(templateEntity), pageRequest, 1);
    doReturn(templateSetPages)
        .when(templateService)
        .list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);

    doReturn(entityDetailProtoDTO)
        .when(entityDetailRestToProtoMapper)
        .createEntityDetailDTO(TemplateEntityDetailUtils.getEntityDetail(templateEntity));
    EntityScopeInfo scope = EntityScopeInfo.newBuilder()
                                .setAccountId(ACCOUNT_ID)
                                .setOrgId(StringValue.of(ORG_IDENTIFIER))
                                .setProjectId(StringValue.of(PROJ_IDENTIFIER))
                                .build();
    ScopeDetails scopeDetails = ScopeDetails.newBuilder().setEntityScope(scope).build();
    List<FileChange> allEntitiesForFullSync = templateFullGitSyncHelper.getAllEntitiesForFullSync(scopeDetails);
    assertThat(allEntitiesForFullSync).hasSize(1);
    FileChange fileChange = allEntitiesForFullSync.get(0);
    assertThat(fileChange.getFilePath()).isEqualTo("templates/template1_version1.yaml");
    assertThat(fileChange.getEntityDetail()).isEqualTo(entityDetailProtoDTO);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDoFullGitSync() {
    doReturn(templateEntity).when(templateService).fullSyncTemplate(entityDetailProtoDTO);
    NGTemplateConfig templateConfig = templateFullGitSyncHelper.doFullGitSync(entityDetailProtoDTO);
    assertThat(templateConfig).isNotNull();
  }
}