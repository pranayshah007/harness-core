/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.loginSettings.outbox;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAPIL;

import static software.wings.beans.loginSettings.LoginSettingsConstants.AUTHENTICATION_MECHANISM_UPDATED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.HARNESS_USERNAME_PASSWORD_UPDATED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.OAUTH_PROVIDER_CREATED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.OAUTH_PROVIDER_DELETED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.OAUTH_PROVIDER_UPDATED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.SAML_SSO_CREATED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.SAML_SSO_DELETED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.SAML_SSO_UPDATED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.TWO_FACTOR_AUTH_UPDATED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.WHITELISTED_DOMAINS_UPDATED;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.ResourceTypeConstants;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.loginSettings.events.LoginSettingsAuthMechanismUpdateEvent;
import software.wings.beans.loginSettings.events.LoginSettingsHarnessUsernamePasswordUpdateEvent;
import software.wings.beans.loginSettings.events.LoginSettingsOAuthCreateEvent;
import software.wings.beans.loginSettings.events.LoginSettingsOAuthDeleteEvent;
import software.wings.beans.loginSettings.events.LoginSettingsOAuthUpdateEvent;
import software.wings.beans.loginSettings.events.LoginSettingsSAMLCreateEvent;
import software.wings.beans.loginSettings.events.LoginSettingsSAMLDeleteEvent;
import software.wings.beans.loginSettings.events.LoginSettingsSAMLUpdateEvent;
import software.wings.beans.loginSettings.events.LoginSettingsTwoFactorAuthEvent;
import software.wings.beans.loginSettings.events.LoginSettingsWhitelistedDomainsUpdateEvent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import javax.annotation.Nullable;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
public class LoginSettingsOutboxEventHandlerTest extends WingsBaseTest {
  @Inject private LoginSettingsOutboxEventHandler loginSettingsOutboxEventHandler;

  private String accountIdentifier;
  private String loginSettingsId;
  @Nullable ObjectMapper objectMapper;

  @Before
  public void setup() throws IllegalAccessException {
    accountIdentifier = generateUuid();
    loginSettingsId = generateUuid();
    objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testHandle_HarnessUsernamePasswordUpdateEvent() throws JsonProcessingException {
    LoginSettingsHarnessUsernamePasswordUpdateEvent loginSettingsHarnessUsernamePasswordUpdateEvent =
        LoginSettingsHarnessUsernamePasswordUpdateEvent.builder()
            .accountIdentifier(accountIdentifier)
            .loginSettingsId(loginSettingsId)
            .build();
    String createEventString = objectMapper.writeValueAsString(loginSettingsHarnessUsernamePasswordUpdateEvent);
    ResourceScope resourceScope = new AccountScope(accountIdentifier);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(HARNESS_USERNAME_PASSWORD_UPDATED)
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.NG_LOGIN_SETTINGS).build())
                                  .build();

    Boolean returnValue = loginSettingsOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testHandle_WhitelistedDomainsUpdateEvent() throws JsonProcessingException {
    LoginSettingsWhitelistedDomainsUpdateEvent loginSettingsWhitelistedDomainsUpdateEvent =
        LoginSettingsWhitelistedDomainsUpdateEvent.builder().accountIdentifier(accountIdentifier).build();
    String createEventString = objectMapper.writeValueAsString(loginSettingsWhitelistedDomainsUpdateEvent);
    ResourceScope resourceScope = new AccountScope(accountIdentifier);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(WHITELISTED_DOMAINS_UPDATED)
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.NG_LOGIN_SETTINGS).build())
                                  .build();

    Boolean returnValue = loginSettingsOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testHandle_SamlSSOCreatedEvent() throws JsonProcessingException {
    LoginSettingsSAMLCreateEvent loginSettingsSAMLCreateEvent =
        LoginSettingsSAMLCreateEvent.builder().accountIdentifier(accountIdentifier).build();
    String createEventString = objectMapper.writeValueAsString(loginSettingsSAMLCreateEvent);
    ResourceScope resourceScope = new AccountScope(accountIdentifier);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(SAML_SSO_CREATED)
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.NG_LOGIN_SETTINGS).build())
                                  .build();

    Boolean returnValue = loginSettingsOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testHandle_SamlSSOUpdatedEvent() throws JsonProcessingException {
    LoginSettingsSAMLUpdateEvent loginSettingsSAMLUpdateEvent =
        LoginSettingsSAMLUpdateEvent.builder().accountIdentifier(accountIdentifier).build();
    String createEventString = objectMapper.writeValueAsString(loginSettingsSAMLUpdateEvent);
    ResourceScope resourceScope = new AccountScope(accountIdentifier);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(SAML_SSO_UPDATED)
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.NG_LOGIN_SETTINGS).build())
                                  .build();

    Boolean returnValue = loginSettingsOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testHandle_SamlSSODeleteEvent() throws JsonProcessingException {
    LoginSettingsSAMLDeleteEvent loginSettingsSAMLDeleteEvent =
        LoginSettingsSAMLDeleteEvent.builder().accountIdentifier(accountIdentifier).build();
    String createEventString = objectMapper.writeValueAsString(loginSettingsSAMLDeleteEvent);
    ResourceScope resourceScope = new AccountScope(accountIdentifier);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(SAML_SSO_DELETED)
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.NG_LOGIN_SETTINGS).build())
                                  .build();

    Boolean returnValue = loginSettingsOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testHandle_TwoFactorAuthUpdateEvent() throws JsonProcessingException {
    LoginSettingsTwoFactorAuthEvent loginSettingsTwoFactorAuthEvent =
        LoginSettingsTwoFactorAuthEvent.builder().accountIdentifier(accountIdentifier).build();
    String createEventString = objectMapper.writeValueAsString(loginSettingsTwoFactorAuthEvent);
    ResourceScope resourceScope = new AccountScope(accountIdentifier);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(TWO_FACTOR_AUTH_UPDATED)
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.NG_LOGIN_SETTINGS).build())
                                  .build();

    Boolean returnValue = loginSettingsOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testHandle_OAuthProviderCreateEvent() throws JsonProcessingException {
    LoginSettingsOAuthCreateEvent loginSettingsOAuthCreateEvent =
        LoginSettingsOAuthCreateEvent.builder().accountIdentifier(accountIdentifier).build();
    String createEventString = objectMapper.writeValueAsString(loginSettingsOAuthCreateEvent);
    ResourceScope resourceScope = new AccountScope(accountIdentifier);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(OAUTH_PROVIDER_CREATED)
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.NG_LOGIN_SETTINGS).build())
                                  .build();

    Boolean returnValue = loginSettingsOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testHandle_OAuthProviderUpdateEvent() throws JsonProcessingException {
    LoginSettingsOAuthUpdateEvent loginSettingsOAuthUpdateEvent =
        LoginSettingsOAuthUpdateEvent.builder().accountIdentifier(accountIdentifier).build();
    String createEventString = objectMapper.writeValueAsString(loginSettingsOAuthUpdateEvent);
    ResourceScope resourceScope = new AccountScope(accountIdentifier);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(OAUTH_PROVIDER_UPDATED)
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.NG_LOGIN_SETTINGS).build())
                                  .build();

    Boolean returnValue = loginSettingsOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testHandle_OAuthProviderDeleteEvent() throws JsonProcessingException {
    LoginSettingsOAuthDeleteEvent loginSettingsOAuthDeleteEvent =
        LoginSettingsOAuthDeleteEvent.builder().accountIdentifier(accountIdentifier).build();
    String createEventString = objectMapper.writeValueAsString(loginSettingsOAuthDeleteEvent);
    ResourceScope resourceScope = new AccountScope(accountIdentifier);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(OAUTH_PROVIDER_DELETED)
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.NG_LOGIN_SETTINGS).build())
                                  .build();

    Boolean returnValue = loginSettingsOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testHandle_AuthenticationMechanismUpdateEvent() throws JsonProcessingException {
    LoginSettingsAuthMechanismUpdateEvent loginSettingsAuthMechanismUpdateEvent =
        LoginSettingsAuthMechanismUpdateEvent.builder().accountIdentifier(accountIdentifier).build();
    String createEventString = objectMapper.writeValueAsString(loginSettingsAuthMechanismUpdateEvent);
    ResourceScope resourceScope = new AccountScope(accountIdentifier);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(AUTHENTICATION_MECHANISM_UPDATED)
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.NG_LOGIN_SETTINGS).build())
                                  .build();

    Boolean returnValue = loginSettingsOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testHandle_HarnessUsernamePasswordUpdateEvent_FailureScenario() throws JsonProcessingException {
    String createEventString = objectMapper.writeValueAsString(null);
    ResourceScope resourceScope = new AccountScope(accountIdentifier);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(HARNESS_USERNAME_PASSWORD_UPDATED)
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.NG_LOGIN_SETTINGS).build())
                                  .build();

    assertThatThrownBy(() -> loginSettingsOutboxEventHandler.handle(outboxEvent))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testHandle_HarnessUsernamePasswordUpdateEvent_FailureScenarioTwo() {
    ResourceScope resourceScope = new AccountScope(accountIdentifier);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(HARNESS_USERNAME_PASSWORD_UPDATED)
                                  .resourceScope(resourceScope)
                                  .eventData(null)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.NG_LOGIN_SETTINGS).build())
                                  .build();

    assertThatThrownBy(() -> loginSettingsOutboxEventHandler.handle(outboxEvent))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
