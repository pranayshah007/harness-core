/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.pipeline.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.MANKRIT;
import static io.harness.rule.OwnerRule.NAMAN;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.ngexception.beans.yamlschema.NodeErrorInfo;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitsync.sdk.CacheResponse;
import io.harness.gitsync.sdk.CacheState;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.governance.PolicySetMetadata;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.pipeline.PMSPipelineSummaryResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineFilterPropertiesDto;
import io.harness.pms.pipeline.TemplateValidationResponseDTO;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.rule.Owner;
import io.harness.spec.server.commons.v1.model.GovernanceMetadata;
import io.harness.spec.server.commons.v1.model.GovernanceStatus;
import io.harness.spec.server.commons.v1.model.PolicySet;
import io.harness.spec.server.pipeline.v1.model.CacheResponseMetadataDTO;
import io.harness.spec.server.pipeline.v1.model.GitDetails;
import io.harness.spec.server.pipeline.v1.model.PipelineGetResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineListResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineListResponseBody.StoreTypeEnum;
import io.harness.spec.server.pipeline.v1.model.PipelineValidationResponseBody;
import io.harness.spec.server.pipeline.v1.model.YAMLSchemaErrorWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PipelinesApiUtilsTest extends CategoryTest {
  String identifier = randomAlphabetic(10);
  String name = randomAlphabetic(10);

  Long lastUpdatedAt = 987654L;

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetGitDetails() {
    EntityGitDetails entityGitDetails = EntityGitDetails.builder()
                                            .objectId("objectId")
                                            .branch("branch")
                                            .commitId("commitId")
                                            .filePath("filePath")
                                            .fileUrl("fileUrl")
                                            .repoUrl("repoUrl")
                                            .repoName("repoName")
                                            .build();
    GitDetails gitDetails = PipelinesApiUtils.getGitDetails(entityGitDetails);
    assertEquals("objectId", gitDetails.getObjectId());
    assertEquals("branch", gitDetails.getBranchName());
    assertEquals("commitId", gitDetails.getCommitId());
    assertEquals("filePath", gitDetails.getFilePath());
    assertEquals("fileUrl", gitDetails.getFileUrl());
    assertEquals("repoUrl", gitDetails.getRepoUrl());
    assertEquals("repoName", gitDetails.getRepoName());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetYAMLSchemaWrapper() {
    YamlSchemaErrorWrapperDTO yamlSchemaErrorWrapperDTO =
        YamlSchemaErrorWrapperDTO.builder()
            .schemaErrors(Collections.singletonList(YamlSchemaErrorDTO.builder()
                                                        .message("errorMessage")
                                                        .fqn("$.inputSet")
                                                        .stageInfo(NodeErrorInfo.builder().identifier("stage1").build())
                                                        .stepInfo(NodeErrorInfo.builder().identifier("step1").build())
                                                        .hintMessage("trySomething")
                                                        .build()))
            .build();
    List<YAMLSchemaErrorWrapper> yamlSchemaErrorWrappers =
        PipelinesApiUtils.getListYAMLErrorWrapper(yamlSchemaErrorWrapperDTO);
    assertEquals(1, yamlSchemaErrorWrappers.size());
    YAMLSchemaErrorWrapper yamlSchemaErrorWrapper = yamlSchemaErrorWrappers.get(0);
    assertEquals("errorMessage", yamlSchemaErrorWrapper.getMessage());
    assertEquals("$.inputSet", yamlSchemaErrorWrapper.getFqn());
    assertEquals("stage1", yamlSchemaErrorWrapper.getStageInfo().getIdentifier());
    assertEquals("step1", yamlSchemaErrorWrapper.getStepInfo().getIdentifier());
    assertEquals("trySomething", yamlSchemaErrorWrapper.getHintMessage());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetResponseBody() {
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .yaml("yaml")
                                        .identifier(identifier)
                                        .orgIdentifier("org")
                                        .createdAt(123456L)
                                        .lastUpdatedAt(987654L)
                                        .build();
    PipelineGetResponseBody responseBody = PipelinesApiUtils.getGetResponseBody(pipelineEntity);
    assertEquals("yaml", responseBody.getPipelineYaml());
    assertEquals(identifier, responseBody.getIdentifier());
    assertEquals("org", responseBody.getOrg());
    assertEquals(123456L, responseBody.getCreated().longValue());
    assertEquals(987654L, responseBody.getUpdated().longValue());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetFilterProperties() {
    List<String> tags = new ArrayList<>();
    tags.add("key:value");
    tags.add("key2");
    PipelineFilterPropertiesDto pipelineFilterPropertiesDto =
        PipelinesApiUtils.getFilterProperties(Collections.singletonList("pipelineId"), "name", null, tags,
            Collections.singletonList("service"), Collections.singletonList("envs"), "deploymentType", "repo");
    assertEquals(pipelineFilterPropertiesDto.getPipelineIdentifiers().get(0), "pipelineId");
    assertEquals(pipelineFilterPropertiesDto.getName(), "name");
    assertEquals(
        pipelineFilterPropertiesDto.getPipelineTags().get(0), NGTag.builder().key("key").value("value").build());
    assertEquals(pipelineFilterPropertiesDto.getTags().get("key2"), null);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetPipelines() {
    PMSPipelineSummaryResponseDTO pmsPipelineSummaryResponseDTO = PMSPipelineSummaryResponseDTO.builder()
                                                                      .identifier(identifier)
                                                                      .name(name)
                                                                      .createdAt(123456L)
                                                                      .lastUpdatedAt(987654L)
                                                                      .storeType(StoreType.INLINE)
                                                                      .build();
    PipelineListResponseBody listResponseBody = PipelinesApiUtils.getPipelines(pmsPipelineSummaryResponseDTO);
    assertEquals(listResponseBody.getCreated().longValue(), 123456L);
    assertEquals(listResponseBody.getUpdated().longValue(), 987654L);
    assertEquals(listResponseBody.getIdentifier(), identifier);
    assertEquals(listResponseBody.getName(), name);
    assertEquals(listResponseBody.getStoreType(), StoreTypeEnum.INLINE);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildPipelineValidationUUIDResponseBody() {
    PipelineValidationEvent event = PipelineValidationEvent.builder().uuid("abc1").build();
    assertThat(PipelinesApiUtils.buildPipelineValidationUUIDResponseBody(event).getUuid()).isEqualTo("abc1");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildPipelineValidationResponseBody() {
    PipelineValidationEvent event =
        PipelineValidationEvent.builder()
            .status(ValidationStatus.IN_PROGRESS)
            .result(ValidationResult.builder()
                        .templateValidationResponse(
                            TemplateValidationResponseDTO.builder().validYaml(true).exceptionMessage("message").build())
                        .build())
            .build();
    PipelineValidationResponseBody responseBody = PipelinesApiUtils.buildPipelineValidationResponseBody(event);
    assertThat(responseBody.getStatus()).isEqualTo("IN_PROGRESS");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetCacheResponseMetadataDTO() {
    io.harness.pms.pipeline.CacheResponseMetadataDTO cacheResponseMetadataDTO =
        io.harness.pms.pipeline.CacheResponseMetadataDTO.builder()
            .cacheState(CacheState.VALID_CACHE)
            .ttlLeft(234523)
            .build();
    CacheResponseMetadataDTO cacheMetadataResponse =
        PipelinesApiUtils.getCacheResponseMetadataDTO(cacheResponseMetadataDTO);
    assertEquals(CacheResponseMetadataDTO.CacheStateEnum.VALID_CACHE, cacheMetadataResponse.getCacheState());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetResponseBodyWithCacheResponseMetadata() {
    CacheResponse cacheResponse =
        CacheResponse.builder().cacheState(CacheState.VALID_CACHE).lastUpdatedAt(lastUpdatedAt).build();

    GitAwareContextHelper.updateScmGitMetaData(
        ScmGitMetaData.builder().branchName("brName").repoName("repoName").cacheResponse(cacheResponse).build());

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .yaml("yaml")
                                        .identifier(identifier)
                                        .orgIdentifier("org")

                                        .storeType(StoreType.REMOTE)
                                        .build();
    PipelineGetResponseBody responseBody = PipelinesApiUtils.getGetResponseBody(pipelineEntity);
    assertEquals("yaml", responseBody.getPipelineYaml());
    assertEquals(identifier, responseBody.getIdentifier());
    assertEquals("org", responseBody.getOrg());
    assertEquals(
        CacheResponseMetadataDTO.CacheStateEnum.VALID_CACHE, responseBody.getCacheResponseMetadata().getCacheState());
    assertEquals(lastUpdatedAt, responseBody.getCacheResponseMetadata().getLastUpdatedAt());
  }
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildGovernanceMetadata() {
    io.harness.governance.GovernanceMetadata proto =
        io.harness.governance.GovernanceMetadata.newBuilder()
            .setDeny(false)
            .setStatus("pass")
            .addDetails(
                PolicySetMetadata.newBuilder().setIdentifier("id").setPolicySetName("name").setStatus("pass").build())
            .build();
    GovernanceMetadata governanceMetadata = PipelinesApiUtils.buildGovernanceMetadataFromProto(proto);
    assertThat(governanceMetadata.getStatus()).isEqualTo(GovernanceStatus.PASS);
    assertThat(governanceMetadata.isDeny()).isFalse();
    assertThat(governanceMetadata.getMessage()).isNullOrEmpty();
    List<PolicySet> policySets = governanceMetadata.getPolicySets();
    assertThat(policySets).hasSize(1);
    PolicySet policySet = policySets.get(0);
    assertThat(policySet.getStatus()).isEqualTo(GovernanceStatus.PASS);
  }
}
