/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.accountdetails.outbox;

import static io.harness.annotations.dev.HarnessTeam.GTM;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAPIL;

import static software.wings.beans.accountdetails.AccountDetailsConstants.CROSS_GENERATION_ACCESS_UPDATED;
import static software.wings.beans.accountdetails.AccountDetailsConstants.DEFAULT_EXPERIENCE_UPDATED;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.ResourceTypeConstants;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.accountdetails.events.AccountDetailsCrossGenerationAccessUpdateEvent;
import software.wings.beans.accountdetails.events.AccountDetailsDefaultExperienceUpdateEvent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import javax.annotation.Nullable;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(GTM)
public class AccountDetailsOutboxEventHandlerTest extends WingsBaseTest {
  @Inject private AccountDetailsOutboxEventHandler accountDetailsOutboxEventHandler;

  private String accountIdentifier;
  @Nullable ObjectMapper objectMapper;

  @Before
  public void setup() throws IllegalAccessException {
    accountIdentifier = generateUuid();
    objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testHandle_CrossGenerationAccessUpdateEvent() throws JsonProcessingException {
    AccountDetailsCrossGenerationAccessUpdateEvent accountDetailsCrossGenerationAccessUpdateEvent =
        AccountDetailsCrossGenerationAccessUpdateEvent.builder().accountIdentifier(accountIdentifier).build();
    String createEventString = objectMapper.writeValueAsString(accountDetailsCrossGenerationAccessUpdateEvent);
    ResourceScope resourceScope = new AccountScope(accountIdentifier);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(CROSS_GENERATION_ACCESS_UPDATED)
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.NG_ACCOUNT_DETAILS).build())
                                  .build();
    Boolean returnValue = accountDetailsOutboxEventHandler.handle(outboxEvent);

    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testHandle_DefaultExperienceUpdateEvent() throws JsonProcessingException {
    AccountDetailsDefaultExperienceUpdateEvent accountDetailsDefaultExperienceUpdateEvent =
        AccountDetailsDefaultExperienceUpdateEvent.builder().accountIdentifier(accountIdentifier).build();
    String createEventString = objectMapper.writeValueAsString(accountDetailsDefaultExperienceUpdateEvent);
    ResourceScope resourceScope = new AccountScope(accountIdentifier);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(DEFAULT_EXPERIENCE_UPDATED)
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.NG_ACCOUNT_DETAILS).build())
                                  .build();
    Boolean returnValue = accountDetailsOutboxEventHandler.handle(outboxEvent);

    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testHandle_CrossGenerationAccessUpdateEvent_FailureScenario() throws JsonProcessingException {
    String createEventString = objectMapper.writeValueAsString(null);
    ResourceScope resourceScope = new AccountScope(accountIdentifier);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(CROSS_GENERATION_ACCESS_UPDATED)
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.NG_ACCOUNT_DETAILS).build())
                                  .build();

    assertThatThrownBy(() -> accountDetailsOutboxEventHandler.handle(outboxEvent))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testHandle_CrossGenerationAccessUpdateEvent_FailureScenarioTwo() {
    ResourceScope resourceScope = new AccountScope(accountIdentifier);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(CROSS_GENERATION_ACCESS_UPDATED)
                                  .resourceScope(resourceScope)
                                  .eventData(null)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.NG_ACCOUNT_DETAILS).build())
                                  .build();

    assertThatThrownBy(() -> accountDetailsOutboxEventHandler.handle(outboxEvent))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
