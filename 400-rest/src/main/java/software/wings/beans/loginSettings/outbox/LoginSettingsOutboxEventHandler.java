/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.loginSettings.outbox;

import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static software.wings.beans.loginSettings.LoginSettingsConstants.AUTHENTICATION_MECHANISM_UPDATED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.HARNESS_USERNAME_PASSWORD_UPDATED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.LDAP_SSO_CREATED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.LDAP_SSO_DELETED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.LDAP_SSO_UPDATED;
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
import software.wings.beans.loginSettings.events.LoginSettingsLDAPCreateEvent;
import software.wings.beans.loginSettings.events.LoginSettingsLDAPDeleteEvent;
import software.wings.beans.loginSettings.events.LoginSettingsLDAPUpdateEvent;
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
        case LDAP_SSO_CREATED:
          return handleLdapSSOCreateEvent(outboxEvent, globalContext);
        case LDAP_SSO_UPDATED:
          return handleLdapSSOUpdateEvent(outboxEvent, globalContext);
        case LDAP_SSO_DELETED:
          return handleLdapSSODeleteEvent(outboxEvent, globalContext);
        default:
          log.error(outboxEvent.getEventType() + " event is unidentified and not handled");
          return false;
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event with exception: ", exception);
      return false;
    }
  }

  private AuditEntry getAuditEntry(OutboxEvent outboxEvent, Action action, String oldYaml, String newYaml) {
    return AuditEntry.builder()
        .action(action)
        .module(ModuleType.CORE)
        .oldYaml(oldYaml)
        .newYaml(newYaml)
        .timestamp(outboxEvent.getCreatedAt())
        .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
        .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
        .insertId(outboxEvent.getId())
        .build();
  }

  private boolean handleHarnessUsernamePasswordUpdateEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    LoginSettingsHarnessUsernamePasswordUpdateEvent loginSettingsHarnessUsernamePasswordUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsHarnessUsernamePasswordUpdateEvent.class);
    AuditEntry auditEntry = getAuditEntry(outboxEvent, Action.UPDATE,
        getYamlString(loginSettingsHarnessUsernamePasswordUpdateEvent.getOldLoginSettingsYamlDTO()),
        getYamlString(loginSettingsHarnessUsernamePasswordUpdateEvent.getNewLoginSettingsYamlDTO()));
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleWhitelistedDomainsUpdateEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    LoginSettingsWhitelistedDomainsUpdateEvent loginSettingsWhitelistedDomainsUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsWhitelistedDomainsUpdateEvent.class);
    AuditEntry auditEntry = getAuditEntry(outboxEvent, Action.UPDATE,
        getYamlString(loginSettingsWhitelistedDomainsUpdateEvent.getOldWhitelistedDomainsYamlDTO()),
        getYamlString(loginSettingsWhitelistedDomainsUpdateEvent.getNewWhitelistedDomainsYamlDTO()));
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleSamlSSOCreateEvent(OutboxEvent outboxEvent, GlobalContext globalContext) throws IOException {
    LoginSettingsSAMLCreateEvent loginSettingsSAMLCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsSAMLCreateEvent.class);
    AuditEntry auditEntry = getAuditEntry(outboxEvent, Action.CREATE,
        getYamlString(loginSettingsSAMLCreateEvent.getOldSamlSettingsYamlDTO()),
        getYamlString(loginSettingsSAMLCreateEvent.getNewSamlSettingsYamlDTO()));
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleSamlSSOUpdateEvent(OutboxEvent outboxEvent, GlobalContext globalContext) throws IOException {
    LoginSettingsSAMLUpdateEvent loginSettingsSAMLUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsSAMLUpdateEvent.class);
    AuditEntry auditEntry = getAuditEntry(outboxEvent, Action.UPDATE,
        getYamlString(loginSettingsSAMLUpdateEvent.getOldSamlSettingsYamlDTO()),
        getYamlString(loginSettingsSAMLUpdateEvent.getNewSamlSettingsYamlDTO()));
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleSamlSSODeleteEvent(OutboxEvent outboxEvent, GlobalContext globalContext) throws IOException {
    LoginSettingsSAMLDeleteEvent loginSettingsSAMLDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsSAMLDeleteEvent.class);
    AuditEntry auditEntry = getAuditEntry(outboxEvent, Action.DELETE,
        getYamlString(loginSettingsSAMLDeleteEvent.getOldSamlSettingsYamlDTO()),
        getYamlString(loginSettingsSAMLDeleteEvent.getNewSamlSettingsYamlDTO()));
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleTwoFactorAuthUpdateEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    LoginSettingsTwoFactorAuthEvent loginSettingsTwoFactorAuthEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsTwoFactorAuthEvent.class);
    AuditEntry auditEntry = getAuditEntry(outboxEvent, Action.UPDATE,
        getYamlString(loginSettingsTwoFactorAuthEvent.getOldTwoFactorAuthYamlDTO()),
        getYamlString(loginSettingsTwoFactorAuthEvent.getNewTwoFactorAuthYamlDTO()));
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleOAuthProviderCreateEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    LoginSettingsOAuthCreateEvent loginSettingsOAuthCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsOAuthCreateEvent.class);
    AuditEntry auditEntry = getAuditEntry(outboxEvent, Action.CREATE,
        getYamlString(loginSettingsOAuthCreateEvent.getOldOAuthSettingsYamlDTO()),
        getYamlString(loginSettingsOAuthCreateEvent.getNewOAuthSettingsYamlDTO()));
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleOAuthProviderUpdateEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    LoginSettingsOAuthUpdateEvent loginSettingsOAuthUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsOAuthUpdateEvent.class);
    AuditEntry auditEntry = getAuditEntry(outboxEvent, Action.UPDATE,
        getYamlString(loginSettingsOAuthUpdateEvent.getOldOAuthSettingsYamlDTO()),
        getYamlString(loginSettingsOAuthUpdateEvent.getNewOAuthSettingsYamlDTO()));
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleOAuthProviderDeleteEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    LoginSettingsOAuthDeleteEvent loginSettingsOAuthDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsOAuthDeleteEvent.class);
    AuditEntry auditEntry = getAuditEntry(outboxEvent, Action.DELETE,
        getYamlString(loginSettingsOAuthDeleteEvent.getOldOAuthSettingsYamlDTO()),
        getYamlString(loginSettingsOAuthDeleteEvent.getNewOAuthSettingsYamlDTO()));
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleAuthMechanismUpdateEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    LoginSettingsAuthMechanismUpdateEvent loginSettingsAuthMechanismUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsAuthMechanismUpdateEvent.class);
    AuditEntry auditEntry = getAuditEntry(outboxEvent, Action.UPDATE,
        getYamlString(loginSettingsAuthMechanismUpdateEvent.getOldAuthMechanismYamlDTO()),
        getYamlString(loginSettingsAuthMechanismUpdateEvent.getNewAuthMechanismYamlDTO()));
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleLdapSSOCreateEvent(OutboxEvent outboxEvent, GlobalContext globalContext) throws IOException {
    LoginSettingsLDAPCreateEvent loginSettingsLDAPCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsLDAPCreateEvent.class);
    AuditEntry auditEntry = getAuditEntry(outboxEvent, Action.CREATE,
        getYamlString(loginSettingsLDAPCreateEvent.getOldLdapSettingsYamlDTO()),
        getYamlString(loginSettingsLDAPCreateEvent.getNewLdapSettingsYamlDTO()));
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleLdapSSOUpdateEvent(OutboxEvent outboxEvent, GlobalContext globalContext) throws IOException {
    LoginSettingsLDAPUpdateEvent loginSettingsLDAPUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsLDAPUpdateEvent.class);
    AuditEntry auditEntry = getAuditEntry(outboxEvent, Action.UPDATE,
        getYamlString(loginSettingsLDAPUpdateEvent.getOldLdapSettingsYamlDTO()),
        getYamlString(loginSettingsLDAPUpdateEvent.getNewLdapSettingsYamlDTO()));
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleLdapSSODeleteEvent(OutboxEvent outboxEvent, GlobalContext globalContext) throws IOException {
    LoginSettingsLDAPDeleteEvent loginSettingsLDAPDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), LoginSettingsLDAPDeleteEvent.class);
    AuditEntry auditEntry = getAuditEntry(outboxEvent, Action.DELETE,
        getYamlString(loginSettingsLDAPDeleteEvent.getOldLdapSettingsYamlDTO()),
        getYamlString(loginSettingsLDAPDeleteEvent.getNewLdapSettingsYamlDTO()));
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
