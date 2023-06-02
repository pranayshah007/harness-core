/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.authorization;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PL)
public enum AuthorizationServiceHeader {
  ANALYZER_SERVICE("AnalyzerService"),
  BEARER("Bearer"),
  MANAGER("Manager"),
  NG_MANAGER("NextGenManager"),
  BATCH_PROCESSING("BatchProcessing"),
  AUDIT_EVENT_STREAMING("AuditEventStreaming"),
  CI_MANAGER("CIManager"),

  STO_MANAGER("STOManager"),
  CV_NEXT_GEN("CVNextGen"),
  CE_NEXT_GEN("CENextGen"),
  CUSTOM_DASHBOARDS("CustomDashboards"),
  DELEGATE_SERVICE("DelegateService"),
  IDENTITY_SERVICE("IdentityService"),
  ADMIN_PORTAL("AdminPortal"),
  NOTIFICATION_SERVICE("NotificationService"),
  AUDIT_SERVICE("AuditService"),
  PIPELINE_SERVICE("PipelineService"),
  TEMPLATE_SERVICE("TemplateService"),
  ACCESS_CONTROL_SERVICE("accessControlService"),
  RESOUCE_GROUP_SERVICE("ResourceGroupService"),
  PLATFORM_SERVICE("PlatformService"),
  GIT_SYNC_SERVICE("GitSyncService"),
  DEFAULT("Default"),
  DASHBOAD_AGGREGATION_SERVICE("DashboardAggregationService"),
  DMS("DelegateManagementService"),
  DEBEZIUM_SERVICE("DebeziumService"),
  SUBSCRIPTION_SERVICE("SubscriptionService"),
  CHAOS_SERVICE("ChaosService"),
  SERVICE_DISCOVERY_SERVICE("ServiceDiscoveryService"),
  CODE("Code"),
  IACM_MANAGER("IACMManager"),
  MIGRATOR("MigratorService"),
  IDP_SERVICE("IDPService"),
  SSCA_SERVICE("SSCAService"),
  IDP_UI("IDPUi");

  private final String serviceId;

  AuthorizationServiceHeader(String serviceId) {
    this.serviceId = serviceId;
  }

  public String getServiceId() {
    return serviceId;
  }
}
