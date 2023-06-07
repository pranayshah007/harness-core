/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.InputSetReferenceProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.custom.CustomTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.GithubSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubPushSpec;
import io.harness.rule.Owner;

import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class TriggerReferenceHelperTest extends CategoryTest {
  @InjectMocks TriggerReferenceHelper triggerReferenceHelper;
  private String accountId = "accountId";
  private String orgId = "orgId";
  private String projectId = "projectId";
  private String pipelineId = "pipelineId";
  private String triggerId = "triggerId";
  private String name = "name";
  private String identifier = "identifier";
  private String secretId = "secretId";
  private String connectorId = "connectorId";
  private String inputSetId = "inputSetId";

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetReferences() {
    NGTriggerConfigV2 ngTriggerConfigV2 =
        NGTriggerConfigV2.builder()
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .identifier(identifier)
            .pipelineIdentifier(pipelineId)
            .encryptedWebhookSecretIdentifier(secretId)
            .inputSetRefs(Collections.singletonList(inputSetId))
            .source(NGTriggerSourceV2.builder()
                        .type(NGTriggerType.WEBHOOK)
                        .spec(WebhookTriggerConfigV2.builder()
                                  .spec(GithubSpec.builder()
                                            .spec(GithubPushSpec.builder().connectorRef(connectorId).build())
                                            .build())
                                  .build())
                        .build())
            .build();
    List<EntityDetailProtoDTO> entityDetailProtoDTOList = new ArrayList<>();
    EntityDetailProtoDTO secretDetail = EntityDetailProtoDTO.newBuilder()
                                            .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                                                  .setIdentifier(StringValue.of(secretId))
                                                                  .setProjectIdentifier(StringValue.of(projectId))
                                                                  .setOrgIdentifier(StringValue.of(orgId))
                                                                  .setAccountIdentifier(StringValue.of(accountId))
                                                                  .setScope(ScopeProtoEnum.PROJECT)
                                                                  .build())
                                            .setType(EntityTypeProtoEnum.SECRETS)
                                            .build();

    EntityDetailProtoDTO inputSetDetails = EntityDetailProtoDTO.newBuilder()
                                               .setInputSetRef(InputSetReferenceProtoDTO.newBuilder()
                                                                   .setAccountIdentifier(StringValue.of(accountId))
                                                                   .setOrgIdentifier(StringValue.of(orgId))
                                                                   .setProjectIdentifier(StringValue.of(projectId))
                                                                   .setPipelineIdentifier(StringValue.of(pipelineId))
                                                                   .setIdentifier(StringValue.of(inputSetId))
                                                                   .build())
                                               .setType(EntityTypeProtoEnum.INPUT_SETS)
                                               .build();

    EntityDetailProtoDTO connectorDetails = EntityDetailProtoDTO.newBuilder()
                                                .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                                                      .setAccountIdentifier(StringValue.of(accountId))
                                                                      .setOrgIdentifier(StringValue.of(orgId))
                                                                      .setProjectIdentifier(StringValue.of(projectId))
                                                                      .setIdentifier(StringValue.of(connectorId))
                                                                      .setScope(ScopeProtoEnum.PROJECT)
                                                                      .build())
                                                .setType(EntityTypeProtoEnum.CONNECTORS)
                                                .build();
    entityDetailProtoDTOList.add(secretDetail);
    entityDetailProtoDTOList.add(inputSetDetails);
    entityDetailProtoDTOList.add(connectorDetails);
    assertThat(triggerReferenceHelper.getReferences(accountId, ngTriggerConfigV2)).isEqualTo(entityDetailProtoDTOList);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetReferencesForCustomTrigger() {
    NGTriggerConfigV2 ngTriggerConfigV2 =
        NGTriggerConfigV2.builder()
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .identifier(identifier)
            .pipelineIdentifier(pipelineId)
            .encryptedWebhookSecretIdentifier(secretId)
            .inputSetRefs(Collections.singletonList(inputSetId))
            .source(NGTriggerSourceV2.builder()
                        .type(NGTriggerType.WEBHOOK)
                        .spec(WebhookTriggerConfigV2.builder().spec(CustomTriggerSpec.builder().build()).build())
                        .build())
            .build();
    List<EntityDetailProtoDTO> entityDetailProtoDTOList = new ArrayList<>();
    EntityDetailProtoDTO secretDetail = EntityDetailProtoDTO.newBuilder()
                                            .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                                                  .setIdentifier(StringValue.of(secretId))
                                                                  .setProjectIdentifier(StringValue.of(projectId))
                                                                  .setOrgIdentifier(StringValue.of(orgId))
                                                                  .setAccountIdentifier(StringValue.of(accountId))
                                                                  .setScope(ScopeProtoEnum.PROJECT)
                                                                  .build())
                                            .setType(EntityTypeProtoEnum.SECRETS)
                                            .build();

    EntityDetailProtoDTO inputSetDetails = EntityDetailProtoDTO.newBuilder()
                                               .setInputSetRef(InputSetReferenceProtoDTO.newBuilder()
                                                                   .setAccountIdentifier(StringValue.of(accountId))
                                                                   .setOrgIdentifier(StringValue.of(orgId))
                                                                   .setProjectIdentifier(StringValue.of(projectId))
                                                                   .setPipelineIdentifier(StringValue.of(pipelineId))
                                                                   .setIdentifier(StringValue.of(inputSetId))
                                                                   .build())
                                               .setType(EntityTypeProtoEnum.INPUT_SETS)
                                               .build();

    EntityDetailProtoDTO connectorDetails = EntityDetailProtoDTO.newBuilder()
                                                .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                                                      .setAccountIdentifier(StringValue.of(accountId))
                                                                      .setOrgIdentifier(StringValue.of(orgId))
                                                                      .setProjectIdentifier(StringValue.of(projectId))
                                                                      .setIdentifier(StringValue.of(connectorId))
                                                                      .setScope(ScopeProtoEnum.PROJECT)
                                                                      .build())
                                                .setType(EntityTypeProtoEnum.CONNECTORS)
                                                .build();
    entityDetailProtoDTOList.add(secretDetail);
    entityDetailProtoDTOList.add(inputSetDetails);
    entityDetailProtoDTOList.add(connectorDetails);
    List<EntityDetailProtoDTO> resultRntityDetailProtoDTOList =
        triggerReferenceHelper.getReferences(accountId, ngTriggerConfigV2);
    assertThat(resultRntityDetailProtoDTOList.size()).isEqualTo(2);
    assertThat(resultRntityDetailProtoDTOList.get(0).getType()).isNotEqualTo(EntityTypeProtoEnum.CONNECTORS);
    assertThat(resultRntityDetailProtoDTOList.get(1).getType()).isNotEqualTo(EntityTypeProtoEnum.CONNECTORS);
  }
}
