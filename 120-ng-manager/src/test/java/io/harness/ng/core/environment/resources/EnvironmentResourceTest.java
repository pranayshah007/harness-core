/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.NGConstants.HARNESS_BLUE;
import static io.harness.rule.OwnerRule.ARCHIT;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.dto.EnvironmentRequestDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponseDTO;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.services.impl.EnvironmentServiceImpl;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.environment.yaml.NGEnvironmentInfoConfig;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.repositories.UpsertOptions;
import io.harness.rule.Owner;

import software.wings.beans.Environment.EnvironmentKeys;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentResourceTest extends CategoryTest {
  @Mock EnvironmentServiceImpl environmentService;
  EnvironmentResource environmentResource;

  EnvironmentRequestDTO environmentRequestDTO;
  EnvironmentResponseDTO environmentResponseDTO;
  Environment environmentEntity;
  List<NGTag> tags;
  NGEnvironmentConfig ngEnvironmentConfig;

  @Before
  public void setUp() {
    tags = Arrays.asList(NGTag.builder().key("k1").value("v1").build());
    MockitoAnnotations.initMocks(this);
    environmentResource = new EnvironmentResource(environmentService);
    ngEnvironmentConfig = NGEnvironmentConfig.builder()
                              .ngEnvironmentInfoConfig(NGEnvironmentInfoConfig.builder()
                                                           .name("ENV")
                                                           .identifier("IDENTIFIER")
                                                           .orgIdentifier("ORG_ID")
                                                           .projectIdentifier("PROJECT_ID")
                                                           .tags(singletonMap("k1", "v1"))
                                                           .type(EnvironmentType.PreProduction)
                                                           .build())
                              .build();
    environmentRequestDTO = EnvironmentRequestDTO.builder()
                                .identifier("IDENTIFIER")
                                .orgIdentifier("ORG_ID")
                                .projectIdentifier("PROJECT_ID")
                                .name("ENV")
                                .type(EnvironmentType.PreProduction)
                                .tags(singletonMap("k1", "v1"))
                                .build();

    environmentResponseDTO = EnvironmentResponseDTO.builder()
                                 .accountId("ACCOUNT_ID")
                                 .identifier("IDENTIFIER")
                                 .orgIdentifier("ORG_ID")
                                 .projectIdentifier("PROJECT_ID")
                                 .color(HARNESS_BLUE)
                                 .name("ENV")
                                 .type(EnvironmentType.PreProduction)
                                 .tags(singletonMap("k1", "v1"))
                                 .yaml(EnvironmentMapper.toYaml(ngEnvironmentConfig))
                                 .version(0L)
                                 .build();

    environmentEntity = Environment.builder()
                            .accountId("ACCOUNT_ID")
                            .identifier("IDENTIFIER")
                            .orgIdentifier("ORG_ID")
                            .projectIdentifier("PROJECT_ID")
                            .color(HARNESS_BLUE)
                            .name("ENV")
                            .type(EnvironmentType.PreProduction)
                            .tags(tags)
                            .version(0L)
                            .yaml(EnvironmentMapper.toYaml(ngEnvironmentConfig))
                            .build();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGet() {
    doReturn(Optional.of(environmentEntity))
        .when(environmentService)
        .get("ACCOUNT_ID", environmentRequestDTO.getOrgIdentifier(), environmentRequestDTO.getProjectIdentifier(),
            environmentRequestDTO.getIdentifier(), false);

    EnvironmentResponseDTO envResponse =
        environmentResource.get("IDENTIFIER", "ACCOUNT_ID", "ORG_ID", "PROJECT_ID", false).getData();

    assertThat(envResponse).isNotNull();
    assertThat(envResponse).isEqualTo(environmentResponseDTO);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCreate() {
    doReturn(environmentEntity).when(environmentService).create(any());
    EnvironmentResponseDTO envResponse =
        environmentResource.create(environmentEntity.getAccountId(), environmentRequestDTO).getData();
    assertThat(envResponse).isEqualTo(environmentResponseDTO);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDelete() {
    doReturn(true)
        .when(environmentService)
        .delete("ACCOUNT_ID", environmentRequestDTO.getOrgIdentifier(), environmentRequestDTO.getProjectIdentifier(),
            environmentRequestDTO.getIdentifier(), null, false);

    Boolean data = environmentResource.delete(null, "IDENTIFIER", "ACCOUNT_ID", "ORG_ID", "PROJECT_ID").getData();
    assertThat(data).isTrue();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testUpdate() {
    doReturn(environmentEntity).when(environmentService).update(environmentEntity);
    EnvironmentResponseDTO response =
        environmentResource.update("0", environmentEntity.getAccountId(), environmentRequestDTO).getData();
    assertThat(response).isNotNull();
    assertThat(response).isEqualTo(environmentResponseDTO);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testUpsert() {
    doReturn(environmentEntity).when(environmentService).upsert(environmentEntity, UpsertOptions.DEFAULT);
    EnvironmentResponseDTO response =
        environmentResource.upsert("0", environmentEntity.getAccountId(), environmentRequestDTO).getData();
    assertThat(response).isNotNull();
    assertThat(response).isEqualTo(environmentResponseDTO);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testListEnvironmentsWithDESCSort() {
    Criteria criteria = CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", false);
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    final Page<Environment> environments = new PageImpl<>(Collections.singletonList(environmentEntity), pageable, 1);
    doReturn(environments).when(environmentService).list(criteria, pageable);

    List<EnvironmentResponseDTO> content =
        environmentResource.listEnvironmentsForProject(0, 10, "ACCOUNT_ID", "ORG_ID", "PROJECT_ID", null, null)
            .getData()
            .getContent();
    assertThat(content).isNotNull();
    assertThat(content.size()).isEqualTo(1);
    assertThat(content.get(0)).isEqualTo(environmentResponseDTO);
  }
}
