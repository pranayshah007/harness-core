/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SweepingOutput;
import io.harness.context.ContextElementType;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.PhaseElement;
import software.wings.beans.Application;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.AwsConfig;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.aws.manager.AwsHelperServiceManager;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class AwsAmiServiceStateHelper {
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private SettingsService settingsService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private SecretManager secretManager;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  public AwsAmiTrafficShiftAlbData populateAlbTrafficShiftSetupData(ExecutionContext context) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String serviceId = phaseElement.getServiceElement().getUuid();

    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    notNullCheck("workflowStandardParams", workflowStandardParams, USER);
    notNullCheck("currentUser", workflowStandardParams.getCurrentUser(), USER);

    Artifact artifact = ((DeploymentExecutionContext) context).getDefaultArtifactForService(serviceId);
    notNullCheck(format("Unable to find artifact for service id: %s", serviceId), artifact);

    Application app = workflowStandardParamsExtensionService.getApp(workflowStandardParams);
    notNullCheck("Application cannot be null", app);
    Environment env = workflowStandardParamsExtensionService.getEnv(workflowStandardParams);
    Service service = serviceResourceService.getWithDetails(app.getUuid(), serviceId);

    AwsAmiInfrastructureMapping infrastructureMapping =
        (AwsAmiInfrastructureMapping) infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());

    SettingAttribute cloudProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(
        (EncryptableSetting) cloudProviderSetting.getValue(), context.getAppId(), context.getWorkflowExecutionId());
    String region = infrastructureMapping.getRegion();
    AwsConfig awsConfig = (AwsConfig) cloudProviderSetting.getValue();
    AwsHelperServiceManager.setAmazonClientSDKDefaultBackoffStrategyIfExists(context, awsConfig);

    return AwsAmiTrafficShiftAlbData.builder()
        .artifact(artifact)
        .app(app)
        .service(service)
        .env(env)
        .awsConfig(awsConfig)
        .infrastructureMapping(infrastructureMapping)
        .awsEncryptedDataDetails(encryptionDetails)
        .region(region)
        .serviceId(serviceId)
        .currentUser(workflowStandardParams.getCurrentUser())
        .build();
  }

  SweepingOutput getSetupElementFromSweepingOutput(ExecutionContext context, String prefix) {
    String sweepingOutputName = getSweepingOutputName(context, prefix);
    SweepingOutputInquiry inquiry = context.prepareSweepingOutputInquiryBuilder().name(sweepingOutputName).build();
    return sweepingOutputService.findSweepingOutput(inquiry);
  }

  @NotNull
  String getSweepingOutputName(ExecutionContext context, String prefix) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String suffix = phaseElement.getServiceElement().getUuid().trim();
    return prefix + suffix;
  }
}
