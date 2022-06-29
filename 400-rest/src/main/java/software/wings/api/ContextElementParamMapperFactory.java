/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ff.FeatureFlagService;

import software.wings.api.cloudformation.CloudFormationOutputInfoElement;
import software.wings.api.cloudformation.CloudFormationOutputInfoElementParamMapper;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ContextElement;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.WorkflowStandardParamsParamMapper;
import software.wings.sm.states.azure.AzureVMSSSetupContextElement;
import software.wings.sm.states.azure.AzureVMSSSetupContextElementParamMapper;
import software.wings.sm.states.azure.appservices.AzureAppServiceSlotSetupContextElement;
import software.wings.sm.states.azure.appservices.AzureAppServiceSlotSetupContextElementParamMapper;

import com.google.inject.Inject;

/**
 * A factory that creates a ContextElementParamMapper for a given ContextElement.
 */
@OwnedBy(CDC)
public class ContextElementParamMapperFactory {
  private final SubdomainUrlHelperIntfc subdomainUrlHelper;
  private final WorkflowExecutionService workflowExecutionService;
  private final ArtifactService artifactService;
  private final ArtifactStreamService artifactStreamService;
  private final ApplicationManifestService applicationManifestService;
  private final FeatureFlagService featureFlagService;
  private final BuildSourceService buildSourceService;
  private final WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @Inject
  public ContextElementParamMapperFactory(SubdomainUrlHelperIntfc subdomainUrlHelper,
      WorkflowExecutionService workflowExecutionService, ArtifactService artifactService,
      ArtifactStreamService artifactStreamService, ApplicationManifestService applicationManifestService,
      FeatureFlagService featureFlagService, BuildSourceService buildSourceService,
      WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService) {
    this.subdomainUrlHelper = subdomainUrlHelper;
    this.workflowExecutionService = workflowExecutionService;
    this.artifactService = artifactService;
    this.artifactStreamService = artifactStreamService;
    this.applicationManifestService = applicationManifestService;
    this.featureFlagService = featureFlagService;
    this.buildSourceService = buildSourceService;
    this.workflowStandardParamsExtensionService = workflowStandardParamsExtensionService;
  }

  /**
   * Creates a ContextElementParamMapper for a given ContextElement.
   *
   * @param element the element to create a ContextElementParamMapper with
   * @return the ContextElementParamMapper
   */
  public ContextElementParamMapper getParamMapper(ContextElement element) {
    if (element instanceof AmiServiceSetupElement) {
      return new AmiServiceSetupElementParamMapper((AmiServiceSetupElement) element);
    } else if (element instanceof AmiServiceTrafficShiftAlbSetupElement) {
      return new AmiServiceTrafficShiftAlbSetupElementParamMapper((AmiServiceTrafficShiftAlbSetupElement) element);
    } else if (element instanceof AzureAppServiceSlotSetupContextElement) {
      return new AzureAppServiceSlotSetupContextElementParamMapper((AzureAppServiceSlotSetupContextElement) element);
    } else if (element instanceof AzureVMSSSetupContextElement) {
      return new AzureVMSSSetupContextElementParamMapper((AzureVMSSSetupContextElement) element);
    } else if (element instanceof CloudFormationOutputInfoElement) {
      return new CloudFormationOutputInfoElementParamMapper((CloudFormationOutputInfoElement) element);
    } else if (element instanceof HostElement) {
      return new HostElementParamMapper((HostElement) element);
    } else if (element instanceof InstanceElement) {
      return new InstanceElementParamMapper((InstanceElement) element);
    } else if (element instanceof PcfInstanceElement) {
      return new PcfInstanceElementParamMapper((PcfInstanceElement) element);
    } else if (element instanceof PhaseElement) {
      return new PhaseElementParamMapper(this.artifactService, this.featureFlagService, (PhaseElement) element);
    } else if (element instanceof RancherClusterElement) {
      return new RancherClusterElementParamMapper((RancherClusterElement) element);
    } else if (element instanceof ServiceElement) {
      return new ServiceElementParamMapper((ServiceElement) element);
    } else if (element instanceof ServiceTemplateElement) {
      return new ServiceTemplateElementParamMapper((ServiceTemplateElement) element);
    } else if (element instanceof ShellScriptProvisionerOutputElement) {
      return new ShellScriptProvisionerOutputElementParamMapper((ShellScriptProvisionerOutputElement) element);
    } else if (element instanceof SimpleWorkflowParam) {
      return new SimpleWorkflowParamMapper((SimpleWorkflowParam) element);
    } else if (element instanceof TerraformOutputInfoElement) {
      return new TerraformOutputInfoElementParamMapper((TerraformOutputInfoElement) element);
    } else if (element instanceof WorkflowStandardParams) {
      return new WorkflowStandardParamsParamMapper(this.subdomainUrlHelper, this.workflowExecutionService,
          this.artifactService, this.artifactStreamService, this.applicationManifestService, this.featureFlagService,
          this.buildSourceService, this.workflowStandardParamsExtensionService, (WorkflowStandardParams) element);
    } else {
      return new NoopContextElementParamMapper();
    }
  }
}