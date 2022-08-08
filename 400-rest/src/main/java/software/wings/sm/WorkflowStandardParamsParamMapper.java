/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.WorkflowType;
import io.harness.ff.FeatureFlagService;

import software.wings.api.ContextElementParamMapper;
import software.wings.api.InfraMappingElement;
import software.wings.api.ServiceElement;
import software.wings.beans.Application;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.artifact.Artifact;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.inject.Inject;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.client.utils.URIBuilder;

@OwnedBy(CDC)
public class WorkflowStandardParamsParamMapper implements ContextElementParamMapper {
  public static final String DEPLOYMENT_TRIGGERED_BY = "deploymentTriggeredBy";

  private final SubdomainUrlHelperIntfc subdomainUrlHelper;
  private final WorkflowExecutionService workflowExecutionService;
  private final ArtifactService artifactService;
  private final ArtifactStreamService artifactStreamService;
  private final ApplicationManifestService applicationManifestService;
  private final FeatureFlagService featureFlagService;
  private final BuildSourceService buildSourceService;
  private final WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  private final WorkflowStandardParams element;

  @Inject
  public WorkflowStandardParamsParamMapper(SubdomainUrlHelperIntfc subdomainUrlHelper,
      WorkflowExecutionService workflowExecutionService, ArtifactService artifactService,
      ArtifactStreamService artifactStreamService, ApplicationManifestService applicationManifestService,
      FeatureFlagService featureFlagService, BuildSourceService buildSourceService,
      WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService, WorkflowStandardParams element) {
    this.subdomainUrlHelper = subdomainUrlHelper;
    this.workflowExecutionService = workflowExecutionService;
    this.artifactService = artifactService;
    this.artifactStreamService = artifactStreamService;
    this.applicationManifestService = applicationManifestService;
    this.featureFlagService = featureFlagService;
    this.buildSourceService = buildSourceService;
    this.workflowStandardParamsExtensionService = workflowStandardParamsExtensionService;
    this.element = element;
  }

  @Override
  public Map<String, Object> paramMap(ExecutionContext context) {
    Map<String, Object> map = new HashMap<>();
    Application app = this.workflowStandardParamsExtensionService.getApp(this.element);
    if (this.element.getWorkflowElement() != null) {
      if (WorkflowType.ORCHESTRATION == context.getWorkflowType()
          && this.element.getWorkflowElement().getStartTs() == null) {
        this.element.getWorkflowElement().setStartTs(this.workflowExecutionService.fetchWorkflowExecutionStartTs(
            app.getAppId(), context.getWorkflowExecutionId()));
      }
      map.put(ContextElement.WORKFLOW, this.element.getWorkflowElement());
    }
    map.put(ContextElement.APP, app);
    map.put(ContextElement.ACCOUNT, this.workflowStandardParamsExtensionService.getAccount(this.element));
    map.put(ContextElement.ENV, this.workflowStandardParamsExtensionService.getEnv(this.element));
    map.put(ContextElement.TIMESTAMP_ID, this.element.getTimestampId());

    String envUrlId = null;
    if (this.workflowStandardParamsExtensionService.getEnv(this.element) != null) {
      envUrlId = this.workflowStandardParamsExtensionService.getEnv(this.element).getUuid();
    } else {
      envUrlId = BUILD == context.getOrchestrationWorkflowType() ? "build" : "noEnv";
    }

    String url;
    if (this.element.getWorkflowElement() != null
        && isNotBlank(this.element.getWorkflowElement().getPipelineDeploymentUuid())) {
      url = format("/account/%s/app/%s/pipeline-execution/%s/workflow-execution/%s/details", app.getAccountId(),
          app.getUuid(), this.element.getWorkflowElement().getPipelineDeploymentUuid(),
          context.getWorkflowExecutionId());
    } else {
      url = format("/account/%s/app/%s/env/%s/executions/%s/details", app.getAccountId(), app.getUuid(), envUrlId,
          context.getWorkflowExecutionId());
    }
    map.put(ContextElement.DEPLOYMENT_URL, buildAbsoluteUrl(url, context.getAccountId()));

    if (this.element.getCurrentUser() != null) {
      map.put(DEPLOYMENT_TRIGGERED_BY, this.element.getCurrentUser().getName());
    }

    InfraMappingElement infraMappingElement = context.fetchInfraMappingElement();
    if (infraMappingElement != null) {
      map.put(ContextElement.INFRA, infraMappingElement);
    }

    ServiceElement serviceElement = context.fetchServiceElement();
    if (serviceElement == null) {
      if (isNotEmpty(this.element.getArtifactIds())) {
        Artifact artifact = this.artifactService.get(this.element.getArtifactIds().get(0));
        ExecutionContextImpl.addArtifactToContext(
            this.artifactStreamService, app.getAccountId(), map, artifact, this.buildSourceService, false);
      }
      if (isNotEmpty(this.element.getRollbackArtifactIds())) {
        Artifact rollbackArtifact = this.artifactService.get(this.element.getRollbackArtifactIds().get(0));
        ExecutionContextImpl.addArtifactToContext(
            this.artifactStreamService, app.getAccountId(), map, rollbackArtifact, this.buildSourceService, true);
      }
    } else {
      String accountId = app.getAccountId();
      String serviceId = serviceElement.getUuid();
      if (!this.featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, accountId)) {
        Artifact artifact = this.workflowStandardParamsExtensionService.getArtifactForService(this.element, serviceId);
        ExecutionContextImpl.addArtifactToContext(
            this.artifactStreamService, accountId, map, artifact, this.buildSourceService, false);

        Artifact rollbackArtifact =
            this.workflowStandardParamsExtensionService.getRollbackArtifactForService(this.element, serviceId);
        ExecutionContextImpl.addArtifactToContext(
            this.artifactStreamService, app.getAccountId(), map, rollbackArtifact, this.buildSourceService, true);
      }
      if (this.featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, accountId)) {
        HelmChart helmChart =
            this.workflowStandardParamsExtensionService.getHelmChartForService(this.element, serviceId);
        ExecutionContextImpl.addHelmChartToContext(
            this.element.getAppId(), map, helmChart, this.applicationManifestService);
      }
    }

    return map;
  }

  private String buildAbsoluteUrl(String fragment, String accountId) {
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
    try {
      URIBuilder uriBuilder = new URIBuilder(baseUrl);
      uriBuilder.setFragment(fragment);
      return uriBuilder.toString();
    } catch (URISyntaxException e) {
      return baseUrl;
    }
  }
}