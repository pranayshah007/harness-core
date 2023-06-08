/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.expressions;

import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.ngtriggers.expressions.functors.EventPayloadFunctor;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.SourceType;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.contracts.triggers.Type;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.Reference;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.product.ci.scm.proto.User;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EventPayloadFunctorTest extends CategoryTest {
  @InjectMocks EventPayloadFunctor eventPayloadFunctor;
  @Mock PlanExecutionMetadataService metadataService;
  @Mock Ambiance ambiance;

  private String bigPayload = "{\n"
      + " \"Type\" : \"Notification\",\n"
      + " \"Timestamp\" : \"2021-03-23T20:42:23.163Z\",\n"
      + " \"SignatureVersion\" : \"1\",\n"
      + " \"UnsubscribeURL\" : \"https://sns.eu-central-1.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-central-1:448640225317:aws_cc_push_trigger:bf6ca40a-eb7c-43a8-b452-ec5869813da4\"\n"
      + "}";
  ParseWebhookResponse prEvent = ParseWebhookResponse.newBuilder()
                                     .setPr(PullRequestHook.newBuilder()
                                                .setPr(PullRequest.newBuilder()
                                                           .setNumber(1)
                                                           .setTitle("This is Title")
                                                           .setTarget("target")
                                                           .setSource("source")
                                                           .setSha("123")
                                                           .setBase(Reference.newBuilder().setSha("234").build())
                                                           .build())
                                                .setRepo(Repository.newBuilder().setLink("https://github.com").build())
                                                .setSender(User.newBuilder().setLogin("user").build())
                                                .build())
                                     .build();
  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }
  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testBind() throws IOException {
    when(ambiance.getPlanExecutionId()).thenReturn("");
    when(metadataService.findByPlanExecutionId(any()))
        .thenReturn(Optional.of(
            PlanExecutionMetadata.builder()
                .triggerJsonPayload(bigPayload)
                .triggerPayload(TriggerPayload.newBuilder()
                                    .setParsedPayload(ParsedPayload.newBuilder().setPr(prEvent.getPr()).build())
                                    .setType(Type.WEBHOOK)
                                    .setSourceType(SourceType.GITHUB_REPO)
                                    .build())
                .build()));

    Object object = eventPayloadFunctor.bind();
    assertThat(((HashMap) object).get("Timestamp")).isEqualTo("2021-03-23T20:42:23.163Z");
    assertThat(((HashMap) object).get("Type")).isEqualTo("Notification");

    // triggerJsonPayload null case
    when(metadataService.findByPlanExecutionId(any())).thenReturn(Optional.of(PlanExecutionMetadata.builder().build()));
    assertThat(eventPayloadFunctor.bind()).isNull();

    // IOException case
    when(metadataService.findByPlanExecutionId(any()))
        .thenReturn(Optional.of(
            PlanExecutionMetadata.builder()
                .triggerJsonPayload(",")
                .triggerPayload(TriggerPayload.newBuilder()
                                    .setParsedPayload(ParsedPayload.newBuilder().setPr(prEvent.getPr()).build())
                                    .setType(Type.WEBHOOK)
                                    .setSourceType(SourceType.GITHUB_REPO)
                                    .build())
                .build()));
    assertThatThrownBy(() -> eventPayloadFunctor.bind())
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Event payload could not be converted to a hashmap");

    when(metadataService.findByPlanExecutionId(any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> eventPayloadFunctor.bind())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("PlanExecution metadata null for planExecutionId ");
  }
}
