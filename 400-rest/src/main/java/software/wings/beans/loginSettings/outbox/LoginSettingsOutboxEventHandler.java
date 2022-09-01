/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.loginSettings.outbox;

import static io.harness.audit.beans.AuditEntry.AuditEntryBuilder;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

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

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoginSettingsOutboxEventHandler implements OutboxEventHandler {
  private final AuditClientService auditClientService;
  private final ObjectMapper objectMapper;

  @Inject
  LoginSettingsOutboxEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
    this.objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    try {
      switch (outboxEvent.getEventType()) {
        case HARNESS_USERNAME_PASSWORD_UPDATED:
          return handleHarnessUsernamePasswordUpdateEvent(outboxEvent, globalContext);
        case WHITELISTED_DOMAINS_UPDATED:
          return handleWhitelistedDomainsUpdateEvent(outboxEvent, globalContext);
        case SAML_SSO_CREATED:
          return handleSamlSSOCreateEvent(outboxEvent, globalContext);
        case SAML_SSO_UPDATED:
          return handleSamlSSOUpdateEvent(outboxEvent, globalContext);
        case SAML_SSO_DELETED:
          return handleSamlSSODeleteEvent(outboxEvent, globalContext);
        case TWO_FACTOR_AUTH_UPDATED:
          return handleTwoFactorAuthUpdateEvent(outboxEvent, globalContext);
        case OAUTH_PROVIDER_CREATED:
          return handleOAuthProviderCreateEvent(outboxEvent, globalContext);
        case OAUTH_PROVIDER_UPDATED:
          return handleOAuthProviderUpdateEvent(outboxEvent, globalContext);
        case OAUTH_PROVIDER_DELETED:
          return handleOAuthProviderDeleteEvent(outboxEvent, globalContext);
        case AUTHENTICATION_MECHANISM_UPDATED:
          return handleAuthMechanismUpdateEvent(outboxEvent, globalContext);
        default:
          log.error(outboxEvent.getEventType() + " event is unidentified and not handled");
          return false;
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event with exception: ", exception);
      return false;
    }
  }

  private AuditEntryBuilder getAuditEntryBuilder(OutboxEvent outboxEvent) {
    return AuditEntry.builder()
        .module(ModuleType.CORE)
        .timestamp(outboxEvent.getCreatedAt())
        .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
        .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
        .insertId(outboxEvent.getId());
  }

  private boolean handleHarnessUsernamePasswordUpdateEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    LoginSettingsHarnessUsernamePasswordUpdateEvent loginSettingsHarnessUsernamePasswordUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsHarnessUsernamePasswordUpdateEvent.class);
    AuditEntry auditEntry =
        getAuditEntryBuilder(outboxEvent)
            .action(Action.UPDATE)
            .oldYaml(getYamlString(loginSettingsHarnessUsernamePasswordUpdateEvent.getOldLoginSettingsYamlDTO()))
            .newYaml(getYamlString(loginSettingsHarnessUsernamePasswordUpdateEvent.getNewLoginSettingsYamlDTO()))
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleWhitelistedDomainsUpdateEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    LoginSettingsWhitelistedDomainsUpdateEvent loginSettingsWhitelistedDomainsUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsWhitelistedDomainsUpdateEvent.class);
    AuditEntry auditEntry =
        getAuditEntryBuilder(outboxEvent)
            .action(Action.UPDATE)
            .oldYaml(getYamlString(loginSettingsWhitelistedDomainsUpdateEvent.getOldWhitelistedDomainsYamlDTO()))
            .newYaml(getYamlString(loginSettingsWhitelistedDomainsUpdateEvent.getNewWhitelistedDomainsYamlDTO()))
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleSamlSSOCreateEvent(OutboxEvent outboxEvent, GlobalContext globalContext) throws IOException {
    LoginSettingsSAMLCreateEvent loginSettingsSAMLCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsSAMLCreateEvent.class);
    AuditEntry auditEntry = getAuditEntryBuilder(outboxEvent)
                                .action(Action.CREATE)
                                .oldYaml(getYamlString(loginSettingsSAMLCreateEvent.getOldSamlSettingsYamlDTO()))
                                .newYaml(getYamlString(loginSettingsSAMLCreateEvent.getNewSamlSettingsYamlDTO()))
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleSamlSSOUpdateEvent(OutboxEvent outboxEvent, GlobalContext globalContext) throws IOException {
    LoginSettingsSAMLUpdateEvent loginSettingsSAMLUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsSAMLUpdateEvent.class);
    AuditEntry auditEntry = getAuditEntryBuilder(outboxEvent)
                                .action(Action.UPDATE)
                                .oldYaml(getYamlString(loginSettingsSAMLUpdateEvent.getOldSamlSettingsYamlDTO()))
                                .newYaml(getYamlString(loginSettingsSAMLUpdateEvent.getNewSamlSettingsYamlDTO()))
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleSamlSSODeleteEvent(OutboxEvent outboxEvent, GlobalContext globalContext) throws IOException {
    LoginSettingsSAMLDeleteEvent loginSettingsSAMLDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsSAMLDeleteEvent.class);
    AuditEntry auditEntry = getAuditEntryBuilder(outboxEvent)
                                .action(Action.DELETE)
                                .oldYaml(getYamlString(loginSettingsSAMLDeleteEvent.getOldSamlSettingsYamlDTO()))
                                .newYaml(getYamlString(loginSettingsSAMLDeleteEvent.getNewSamlSettingsYamlDTO()))
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleTwoFactorAuthUpdateEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    LoginSettingsTwoFactorAuthEvent loginSettingsTwoFactorAuthEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsTwoFactorAuthEvent.class);
    AuditEntry auditEntry = getAuditEntryBuilder(outboxEvent)
                                .action(Action.UPDATE)
                                .oldYaml(getYamlString(loginSettingsTwoFactorAuthEvent.getOldTwoFactorAuthYamlDTO()))
                                .newYaml(getYamlString(loginSettingsTwoFactorAuthEvent.getNewTwoFactorAuthYamlDTO()))
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleOAuthProviderCreateEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    LoginSettingsOAuthCreateEvent loginSettingsOAuthCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsOAuthCreateEvent.class);
    AuditEntry auditEntry = getAuditEntryBuilder(outboxEvent)
                                .action(Action.CREATE)
                                .oldYaml(getYamlString(loginSettingsOAuthCreateEvent.getOldOAuthSettingsYamlDTO()))
                                .newYaml(getYamlString(loginSettingsOAuthCreateEvent.getNewOAuthSettingsYamlDTO()))
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleOAuthProviderUpdateEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    LoginSettingsOAuthUpdateEvent loginSettingsOAuthUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsOAuthUpdateEvent.class);
    AuditEntry auditEntry = getAuditEntryBuilder(outboxEvent)
                                .action(Action.UPDATE)
                                .oldYaml(getYamlString(loginSettingsOAuthUpdateEvent.getOldOAuthSettingsYamlDTO()))
                                .newYaml(getYamlString(loginSettingsOAuthUpdateEvent.getNewOAuthSettingsYamlDTO()))
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleOAuthProviderDeleteEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    LoginSettingsOAuthDeleteEvent loginSettingsOAuthDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsOAuthDeleteEvent.class);
    AuditEntry auditEntry = getAuditEntryBuilder(outboxEvent)
                                .action(Action.DELETE)
                                .oldYaml(getYamlString(loginSettingsOAuthDeleteEvent.getOldOAuthSettingsYamlDTO()))
                                .newYaml(getYamlString(loginSettingsOAuthDeleteEvent.getNewOAuthSettingsYamlDTO()))
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleAuthMechanismUpdateEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    LoginSettingsAuthMechanismUpdateEvent loginSettingsAuthMechanismUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsAuthMechanismUpdateEvent.class);
    AuditEntry auditEntry =
        getAuditEntryBuilder(outboxEvent)
            .action(Action.UPDATE)
            .oldYaml(getYamlString(loginSettingsAuthMechanismUpdateEvent.getOldAuthMechanismYamlDTO()))
            .newYaml(getYamlString(loginSettingsAuthMechanismUpdateEvent.getNewAuthMechanismYamlDTO()))
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
