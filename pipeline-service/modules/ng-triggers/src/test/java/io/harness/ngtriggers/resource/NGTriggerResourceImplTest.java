/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.MATT;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.RUTVIJ_MEHTA;
import static io.harness.rule.OwnerRule.SRIDHAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.LastTriggerExecutionDetails;
import io.harness.ngtriggers.beans.dto.NGTriggerCatalogDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerDetailsResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerResponseDTO;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.WebhookDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogItem;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogType;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.scheduled.CronTriggerSpec;
import io.harness.ngtriggers.beans.source.scheduled.ScheduledTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.ngtriggers.exceptions.InvalidTriggerYamlException;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.mapper.TriggerFilterHelper;
import io.harness.ngtriggers.service.NGTriggerEventsService;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.rule.Owner;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
public class NGTriggerResourceImplTest extends CategoryTest {
  @Mock NGTriggerService ngTriggerService;
  @Mock NGTriggerEventsService ngTriggerEventsService;
  @InjectMocks NGTriggerResourceImpl ngTriggerResource;
  @Mock NGTriggerElementMapper ngTriggerElementMapper;

  @Mock AccessControlClient accessControlClient;

  private final String IDENTIFIER = "first_trigger";
  private final String NAME = "first trigger";
  private final String PIPELINE_IDENTIFIER = "myPipeline";
  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private String ngTriggerYaml;
  private String ngTriggerYamlWithGitSync;
  private String ngTriggerYamlGitlabMRComment;
  private String ngTriggerYamlBitbucketPRComment;

  private NGTriggerDetailsResponseDTO ngTriggerDetailsResponseDTO;
  private NGTriggerResponseDTO ngTriggerResponseDTO;

  private NGTriggerResponseDTO ngTriggerErrorDTO;
  private NGTriggerResponseDTO ngTriggerResponseDTOGitSync;
  private NGTriggerResponseDTO ngTriggerResponseDTOGitlabMRComment;
  private NGTriggerResponseDTO ngTriggerResponseDTOBitbucketPRComment;
  private NGTriggerEntity ngTriggerEntity;
  private NGTriggerEntity ngTriggerEntityGitSync;
  private NGTriggerEntity ngTriggerEntityGitlabMRComment;
  private NGTriggerEntity ngTriggerEntityBitbucketPRComment;
  private NGTriggerConfigV2 ngTriggerConfig;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);

    ClassLoader classLoader = getClass().getClassLoader();
    String filename = "ng-trigger-github-pr-v2.yaml";
    String filenameGitSync = "ng-trigger-github-pr-gitsync.yaml";
    ngTriggerYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    ngTriggerYamlWithGitSync =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filenameGitSync)), StandardCharsets.UTF_8);
    ngTriggerYamlGitlabMRComment =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-gitlab-mr-comment-v2.yaml")),
            StandardCharsets.UTF_8);
    ngTriggerConfig = YamlPipelineUtils.read(ngTriggerYaml, NGTriggerConfigV2.class);
    WebhookTriggerConfigV2 webhookTriggerConfig = (WebhookTriggerConfigV2) ngTriggerConfig.getSource().getSpec();
    WebhookMetadata metadata = WebhookMetadata.builder().type(webhookTriggerConfig.getType().getValue()).build();
    NGTriggerMetadata ngTriggerMetadata = NGTriggerMetadata.builder().webhook(metadata).build();

    ngTriggerResponseDTO = NGTriggerResponseDTO.builder()
                               .accountIdentifier(ACCOUNT_ID)
                               .orgIdentifier(ORG_IDENTIFIER)
                               .projectIdentifier(PROJ_IDENTIFIER)
                               .targetIdentifier(PIPELINE_IDENTIFIER)
                               .identifier(IDENTIFIER)
                               .name(NAME)
                               .yaml(ngTriggerYaml)
                               .type(NGTriggerType.WEBHOOK)
                               .version(0L)
                               .build();

    ngTriggerResponseDTOGitSync = NGTriggerResponseDTO.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(PROJ_IDENTIFIER)
                                      .targetIdentifier(PIPELINE_IDENTIFIER)
                                      .identifier(IDENTIFIER)
                                      .name(NAME)
                                      .yaml(ngTriggerYamlWithGitSync)
                                      .type(NGTriggerType.WEBHOOK)
                                      .version(0L)
                                      .build();

    ngTriggerResponseDTOGitlabMRComment = NGTriggerResponseDTO.builder()
                                              .accountIdentifier(ACCOUNT_ID)
                                              .orgIdentifier(ORG_IDENTIFIER)
                                              .projectIdentifier(PROJ_IDENTIFIER)
                                              .targetIdentifier(PIPELINE_IDENTIFIER)
                                              .identifier(IDENTIFIER)
                                              .name(NAME)
                                              .yaml(ngTriggerYamlGitlabMRComment)
                                              .type(NGTriggerType.WEBHOOK)
                                              .version(0L)
                                              .build();

    ngTriggerResponseDTOBitbucketPRComment = NGTriggerResponseDTO.builder()
                                                 .accountIdentifier(ACCOUNT_ID)
                                                 .orgIdentifier(ORG_IDENTIFIER)
                                                 .projectIdentifier(PROJ_IDENTIFIER)
                                                 .targetIdentifier(PIPELINE_IDENTIFIER)
                                                 .identifier(IDENTIFIER)
                                                 .name(NAME)
                                                 .yaml(ngTriggerYamlBitbucketPRComment)
                                                 .type(NGTriggerType.WEBHOOK)
                                                 .version(0L)
                                                 .build();

    ngTriggerDetailsResponseDTO =
        NGTriggerDetailsResponseDTO.builder()
            .name(NAME)
            .identifier(IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .lastTriggerExecutionDetails(LastTriggerExecutionDetails.builder()
                                             .lastExecutionTime(1607306091861L)
                                             .lastExecutionStatus("SUCCESS")
                                             .lastExecutionSuccessful(false)
                                             .planExecutionId("PYV86FtaSfes7uPrGYJhBg")
                                             .message("Pipeline execution was requested successfully")
                                             .build())
            .webhookDetails(WebhookDetails.builder().webhookSourceRepo("Github").build())
            .enabled(true)
            .isPipelineInputOutdated(false)
            .build();

    ngTriggerEntity = NGTriggerEntity.builder()
                          .accountId(ACCOUNT_ID)
                          .orgIdentifier(ORG_IDENTIFIER)
                          .projectIdentifier(PROJ_IDENTIFIER)
                          .targetIdentifier(PIPELINE_IDENTIFIER)
                          .identifier(IDENTIFIER)
                          .name(NAME)
                          .targetType(TargetType.PIPELINE)
                          .type(NGTriggerType.WEBHOOK)
                          .metadata(ngTriggerMetadata)
                          .yaml(ngTriggerYaml)
                          .version(0L)
                          .build();
    ngTriggerEntityGitSync = NGTriggerEntity.builder()
                                 .accountId(ACCOUNT_ID)
                                 .orgIdentifier(ORG_IDENTIFIER)
                                 .projectIdentifier(PROJ_IDENTIFIER)
                                 .targetIdentifier(PIPELINE_IDENTIFIER)
                                 .identifier(IDENTIFIER)
                                 .name(NAME)
                                 .targetType(TargetType.PIPELINE)
                                 .type(NGTriggerType.WEBHOOK)
                                 .metadata(ngTriggerMetadata)
                                 .yaml(ngTriggerYamlWithGitSync)
                                 .version(0L)
                                 .build();

    ngTriggerEntityGitlabMRComment = NGTriggerEntity.builder()
                                         .accountId(ACCOUNT_ID)
                                         .orgIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier(PROJ_IDENTIFIER)
                                         .targetIdentifier(PIPELINE_IDENTIFIER)
                                         .identifier(IDENTIFIER)
                                         .name(NAME)
                                         .targetType(TargetType.PIPELINE)
                                         .type(NGTriggerType.WEBHOOK)
                                         .metadata(ngTriggerMetadata)
                                         .yaml(ngTriggerYamlGitlabMRComment)
                                         .version(0L)
                                         .build();

    ngTriggerEntityBitbucketPRComment = NGTriggerEntity.builder()
                                            .accountId(ACCOUNT_ID)
                                            .orgIdentifier(ORG_IDENTIFIER)
                                            .projectIdentifier(PROJ_IDENTIFIER)
                                            .targetIdentifier(PIPELINE_IDENTIFIER)
                                            .identifier(IDENTIFIER)
                                            .name(NAME)
                                            .targetType(TargetType.PIPELINE)
                                            .type(NGTriggerType.WEBHOOK)
                                            .metadata(ngTriggerMetadata)
                                            .yaml(ngTriggerYamlBitbucketPRComment)
                                            .version(0L)
                                            .build();

    ngTriggerErrorDTO = NGTriggerResponseDTO.builder()
                            .accountIdentifier(ACCOUNT_ID)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJ_IDENTIFIER)
                            .targetIdentifier(PIPELINE_IDENTIFIER)
                            .identifier(IDENTIFIER)
                            .name(NAME)
                            .yaml(ngTriggerYaml)
                            .type(NGTriggerType.WEBHOOK)
                            .errorResponse(true)
                            .version(0L)
                            .build();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreate() throws Exception {
    doReturn(ngTriggerEntity).when(ngTriggerService).create(any());

    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .toTriggerDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ngTriggerYaml, false);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerResponseDTO);

    NGTriggerResponseDTO responseDTO =
        ngTriggerResource
            .create(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, ngTriggerYaml, true, false)
            .getData();
    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTO);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testCreateCheckAccess() {
    doReturn(ngTriggerEntity).when(ngTriggerService).create(any());

    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .toTriggerDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ngTriggerYaml, false);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerResponseDTO);

    NGTriggerResponseDTO responseDTO =
        ngTriggerResource
            .create(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, ngTriggerYaml, true, false)
            .getData();

    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(any(), any(), eq(PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT));
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(any(), any(), eq(PipelineRbacPermissions.PIPELINE_EXECUTE));
    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTO);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testCreateInvalidYamlError() throws Exception {
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .toTriggerDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ngTriggerYaml, false);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerErrorDTO);
    doThrow(InvalidTriggerYamlException.class).when(ngTriggerService).validatePipelineRef(any());

    NGTriggerResponseDTO responseDTO =
        ngTriggerResource
            .create(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, ngTriggerYaml, true, false)
            .getData();
    assertThat(responseDTO.isErrorResponse()).isEqualTo(true);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testCreateException() throws Exception {
    doThrow(new InvalidRequestException("exception")).when(ngTriggerService).create(any());

    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .toTriggerDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ngTriggerYaml, false);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerResponseDTO);

    ngTriggerResource
        .create(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, ngTriggerYaml, true, false)
        .getData();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testCreateWithGitSync() throws Exception {
    doReturn(ngTriggerEntityGitSync).when(ngTriggerService).create(any());
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerResponseDTOGitSync);
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .toTriggerDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ngTriggerYaml, false);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntityGitSync)).thenReturn(ngTriggerResponseDTOGitSync);

    NGTriggerResponseDTO responseDTO =
        ngTriggerResource
            .create(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, ngTriggerYaml, false, false)
            .getData();
    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTOGitSync);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGet() {
    doReturn(Optional.of(ngTriggerEntity))
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, false);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerResponseDTO);
    NGTriggerResponseDTO responseDTO =
        ngTriggerResource.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER).getData();
    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTO);
  }
  @Test(expected = EntityNotFoundException.class)
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetException() {
    doReturn(Optional.empty())
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, false);
    NGTriggerResponseDTO responseDTO =
        ngTriggerResource.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER).getData();
    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTO);
  }
  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetTriggerDetailsNotPresent() {
    doReturn(Optional.empty())
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, false);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(null);
    NGTriggerDetailsResponseDTO responseDTO =
        ngTriggerResource
            .getTriggerDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER)
            .getData();
    assertThat(responseDTO).isEqualTo(null);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetTriggerDetails() throws IOException {
    NGTriggerConfigV2 ngTriggerConfigV2 = YamlPipelineUtils.read(ngTriggerYaml, NGTriggerConfigV2.class);
    doReturn(Optional.of(ngTriggerEntity))
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, false);

    doReturn(Optional.of(ngTriggerEntity))
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, false);
    TriggerDetails triggerDetails =
        TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).ngTriggerConfigV2(ngTriggerConfigV2).build();
    when(ngTriggerElementMapper.toNGTriggerDetailsResponseDTO(ngTriggerEntity, true, true, false))
        .thenReturn(ngTriggerDetailsResponseDTO);

    doReturn(triggerDetails)
        .when(ngTriggerService)
        .fetchTriggerEntity(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, ngTriggerYaml, false);

    NGTriggerDetailsResponseDTO responseDTO =
        ngTriggerResource
            .getTriggerDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, IDENTIFIER, PIPELINE_IDENTIFIER)
            .getData();
    assertThat(responseDTO).isEqualTo(ngTriggerDetailsResponseDTO);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdate() throws Exception {
    doReturn(ngTriggerEntity).when(ngTriggerService).update(any());
    doReturn(Optional.of(ngTriggerEntity))
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, false);
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .toTriggerDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ngTriggerYaml, false);
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .mergeTriggerEntity(triggerDetails.getNgTriggerEntity(), ngTriggerYaml);
    doReturn(triggerDetails)
        .when(ngTriggerService)
        .fetchTriggerEntity(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, ngTriggerYaml, false);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerResponseDTO);

    NGTriggerResponseDTO responseDTO = ngTriggerResource
                                           .update("0", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                                               PIPELINE_IDENTIFIER, IDENTIFIER, ngTriggerYaml, true)
                                           .getData();

    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTO);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testUpdateAccess() throws Exception {
    doReturn(ngTriggerEntity).when(ngTriggerService).update(any());
    doReturn(Optional.of(ngTriggerEntity))
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, false);
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .toTriggerDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ngTriggerYaml, false);
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .mergeTriggerEntity(triggerDetails.getNgTriggerEntity(), ngTriggerYaml);
    doReturn(triggerDetails)
        .when(ngTriggerService)
        .fetchTriggerEntity(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, ngTriggerYaml, false);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerResponseDTO);

    NGTriggerResponseDTO responseDTO = ngTriggerResource
                                           .update("0", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                                               PIPELINE_IDENTIFIER, IDENTIFIER, ngTriggerYaml, true)
                                           .getData();

    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(any(), any(), eq(PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT));
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(any(), any(), eq(PipelineRbacPermissions.PIPELINE_EXECUTE));

    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTO);
  }

  @Test(expected = EntityNotFoundException.class)
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testUpdateNotPresent() throws Exception {
    doReturn(ngTriggerEntity).when(ngTriggerService).update(any());
    doReturn(Optional.empty())
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, false);
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .toTriggerDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ngTriggerYaml, false);
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .mergeTriggerEntity(triggerDetails.getNgTriggerEntity(), ngTriggerYaml);
    doReturn(triggerDetails)
        .when(ngTriggerService)
        .fetchTriggerEntity(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, ngTriggerYaml, false);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerResponseDTO);

    ngTriggerResource
        .update("0", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, ngTriggerYaml, true)
        .getData();
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testUpdateInvalidYamlError() throws Exception {
    doReturn(ngTriggerEntity).when(ngTriggerService).update(any());
    doReturn(Optional.of(ngTriggerEntity))
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, false);
    doThrow(InvalidTriggerYamlException.class).when(ngTriggerService).validatePipelineRef(any());
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .toTriggerDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ngTriggerYaml, false);
    doReturn(triggerDetails)
        .when(ngTriggerService)
        .fetchTriggerEntity(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, ngTriggerYaml, false);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerErrorDTO);

    NGTriggerResponseDTO responseDTO = ngTriggerResource
                                           .update("0", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                                               PIPELINE_IDENTIFIER, IDENTIFIER, ngTriggerYaml, true)
                                           .getData();

    assertThat(responseDTO.isErrorResponse()).isEqualTo(true);
  }

  @Test(expected = EntityNotFoundException.class)
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testUpdateException() throws Exception {
    doThrow(new EntityNotFoundException("exception")).when(ngTriggerService).update(any());
    doReturn(Optional.empty())
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, false);
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .toTriggerDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ngTriggerYaml, false);
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .mergeTriggerEntity(triggerDetails.getNgTriggerEntity(), ngTriggerYaml);
    doReturn(triggerDetails)
        .when(ngTriggerService)
        .fetchTriggerEntity(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, ngTriggerYaml, false);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerResponseDTO);

    ngTriggerResource
        .update("0", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, ngTriggerYaml, true)
        .getData();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateWithGitSync() throws Exception {
    doReturn(ngTriggerEntityGitSync).when(ngTriggerService).update(any());
    doReturn(Optional.of(ngTriggerEntityGitSync))
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, false);
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntityGitSync).build();
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .toTriggerDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ngTriggerYaml, false);
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .mergeTriggerEntity(triggerDetails.getNgTriggerEntity(), ngTriggerYamlWithGitSync);
    doReturn(triggerDetails)
        .when(ngTriggerService)
        .fetchTriggerEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER,
            ngTriggerYamlWithGitSync, false);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntityGitSync)).thenReturn(ngTriggerResponseDTOGitSync);
    NGTriggerResponseDTO responseDTO = ngTriggerResource
                                           .update("0", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                                               PIPELINE_IDENTIFIER, IDENTIFIER, ngTriggerYamlWithGitSync, false)
                                           .getData();

    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTOGitSync);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDelete() {
    doReturn(true)
        .when(ngTriggerService)
        .delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerResponseDTO);
    Boolean response =
        ngTriggerResource.delete(null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER)
            .getData();

    assertThat(response).isTrue();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testDeleteTriggerException() {
    doThrow(new InvalidRequestException(String.format("NGTrigger [%s] couldn't be deleted", IDENTIFIER)))
        .when(ngTriggerService)
        .delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerResponseDTO);
    ngTriggerResource.delete(null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER)
        .getData();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testListServicesWithDESCSort() {
    Criteria criteria = TriggerFilterHelper.createCriteriaForGetList("", "", "", "", null, "", false);
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, NGTriggerEntityKeys.createdAt));
    final Page<NGTriggerEntity> serviceList = new PageImpl<>(Collections.singletonList(ngTriggerEntity), pageable, 1);
    doReturn(serviceList).when(ngTriggerService).list(criteria, pageable);

    when(ngTriggerElementMapper.toNGTriggerDetailsResponseDTO(ngTriggerEntity, true, false, false))
        .thenReturn(ngTriggerDetailsResponseDTO);

    List<NGTriggerDetailsResponseDTO> content =
        ngTriggerResource.getListForTarget("", "", "", "", "", 0, 10, null, "").getData().getContent();

    assertThat(content).isNotNull();
    assertThat(content.size()).isEqualTo(1);
    assertThat(content.get(0).getName()).isEqualTo(ngTriggerDetailsResponseDTO.getName());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGitConnectorTrigger() throws IOException {
    ngTriggerConfig = YamlPipelineUtils.read(ngTriggerYaml, NGTriggerConfigV2.class);
    WebhookTriggerConfigV2 webhookTriggerConfig = (WebhookTriggerConfigV2) ngTriggerConfig.getSource().getSpec();
    assertThat(webhookTriggerConfig.getSpec().fetchGitAware().fetchConnectorRef()).isEqualTo("conn");
  }

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testCronTrigger() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    String filename = "ng-trigger-cron-v2.yaml";
    String triggerYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    ngTriggerConfig = YamlPipelineUtils.read(triggerYaml, NGTriggerConfigV2.class);
    ScheduledTriggerConfig scheduledTriggerConfig = (ScheduledTriggerConfig) ngTriggerConfig.getSource().getSpec();
    CronTriggerSpec cronTriggerSpec = (CronTriggerSpec) scheduledTriggerConfig.getSpec();
    assertThat(cronTriggerSpec.getExpression()).isEqualTo("20 4 * * *");
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testCreateGitlabMRComment() throws Exception {
    doReturn(ngTriggerEntityGitlabMRComment).when(ngTriggerService).create(any());
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerResponseDTOGitlabMRComment);
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntityGitlabMRComment).build();
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .toTriggerDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ngTriggerYaml, false);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntityGitlabMRComment))
        .thenReturn(ngTriggerResponseDTOGitlabMRComment);

    NGTriggerResponseDTO responseDTO =
        ngTriggerResource
            .create(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, ngTriggerYaml, false, false)
            .getData();
    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTOGitlabMRComment);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testCreateBitbucketPRComment() throws Exception {
    doReturn(ngTriggerEntityBitbucketPRComment).when(ngTriggerService).create(any());
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerResponseDTOBitbucketPRComment);
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntityBitbucketPRComment).build();
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .toTriggerDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ngTriggerYaml, false);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntityBitbucketPRComment))
        .thenReturn(ngTriggerResponseDTOBitbucketPRComment);

    NGTriggerResponseDTO responseDTO =
        ngTriggerResource
            .create(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, ngTriggerYaml, false, false)
            .getData();
    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTOBitbucketPRComment);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetTriggerCatalog() {
    List<TriggerCatalogType> catalogTypes = new ArrayList<>();
    catalogTypes.add(TriggerCatalogType.ECR);
    catalogTypes.add(TriggerCatalogType.ACR);
    List<TriggerCatalogItem> triggerCatalogItems = Arrays.asList(
        TriggerCatalogItem.builder().category(NGTriggerType.ARTIFACT).triggerCatalogType(catalogTypes).build());
    when(ngTriggerService.getTriggerCatalog(ACCOUNT_ID)).thenReturn(triggerCatalogItems);
    when(ngTriggerElementMapper.toCatalogDTO(triggerCatalogItems))
        .thenReturn(NGTriggerCatalogDTO.builder().catalog(triggerCatalogItems).build());

    NGTriggerCatalogDTO responseDTO = ngTriggerResource.getTriggerCatalog(ACCOUNT_ID).getData();

    assertThat(responseDTO.getCatalog().size()).isEqualTo(1);
    assertThat(responseDTO.getCatalog().get(0).getCategory()).isEqualTo(NGTriggerType.ARTIFACT);
    assertThat(responseDTO.getCatalog().get(0).getTriggerCatalogType().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetTriggerCatalogScheduled() {
    List<TriggerCatalogType> catalogTypes = new ArrayList<>();
    catalogTypes.add(TriggerCatalogType.CRON);
    List<TriggerCatalogItem> triggerCatalogItems = Arrays.asList(
        TriggerCatalogItem.builder().category(NGTriggerType.SCHEDULED).triggerCatalogType(catalogTypes).build());
    when(ngTriggerService.getTriggerCatalog(ACCOUNT_ID)).thenReturn(triggerCatalogItems);
    when(ngTriggerElementMapper.toCatalogDTO(triggerCatalogItems))
        .thenReturn(NGTriggerCatalogDTO.builder().catalog(triggerCatalogItems).build());

    NGTriggerCatalogDTO responseDTO = ngTriggerResource.getTriggerCatalog(ACCOUNT_ID).getData();

    assertThat(responseDTO.getCatalog().size()).isEqualTo(1);
    assertThat(responseDTO.getCatalog().get(0).getCategory()).isEqualTo(NGTriggerType.SCHEDULED);
    assertThat(responseDTO.getCatalog().get(0).getTriggerCatalogType().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetTriggerCatalogWebhook() {
    List<TriggerCatalogType> catalogTypes = new ArrayList<>();
    catalogTypes.add(TriggerCatalogType.GITHUB);
    catalogTypes.add(TriggerCatalogType.GITLAB);
    List<TriggerCatalogItem> triggerCatalogItems = Arrays.asList(
        TriggerCatalogItem.builder().category(NGTriggerType.WEBHOOK).triggerCatalogType(catalogTypes).build());
    when(ngTriggerService.getTriggerCatalog(ACCOUNT_ID)).thenReturn(triggerCatalogItems);
    when(ngTriggerElementMapper.toCatalogDTO(triggerCatalogItems))
        .thenReturn(NGTriggerCatalogDTO.builder().catalog(triggerCatalogItems).build());

    NGTriggerCatalogDTO responseDTO = ngTriggerResource.getTriggerCatalog(ACCOUNT_ID).getData();

    assertThat(responseDTO.getCatalog().size()).isEqualTo(1);
    assertThat(responseDTO.getCatalog().get(0).getCategory()).isEqualTo(NGTriggerType.WEBHOOK);
    assertThat(responseDTO.getCatalog().get(0).getTriggerCatalogType().size()).isEqualTo(2);
  }
}
