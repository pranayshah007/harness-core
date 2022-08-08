/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.expression;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.govern.Switch.noop;
import static io.harness.k8s.K8sConstants.HARNESS_KUBE_CONFIG_PATH;
import static io.harness.k8s.model.K8sExpressions.canaryDestination;
import static io.harness.k8s.model.K8sExpressions.canaryWorkload;
import static io.harness.k8s.model.K8sExpressions.stableDestination;
import static io.harness.k8s.model.K8sExpressions.virtualServiceName;
import static io.harness.pcf.model.PcfConstants.CONTEXT_ACTIVE_APP_NAME_EXPR;
import static io.harness.pcf.model.PcfConstants.CONTEXT_APP_FINAL_ROUTES_EXPR;
import static io.harness.pcf.model.PcfConstants.CONTEXT_APP_TEMP_ROUTES_EXPR;
import static io.harness.pcf.model.PcfConstants.CONTEXT_INACTIVE_APP_NAME_EXPR;
import static io.harness.pcf.model.PcfConstants.CONTEXT_NEW_APP_GUID_EXPR;
import static io.harness.pcf.model.PcfConstants.CONTEXT_NEW_APP_NAME_EXPR;
import static io.harness.pcf.model.PcfConstants.CONTEXT_NEW_APP_ROUTES_EXPR;
import static io.harness.pcf.model.PcfConstants.CONTEXT_OLD_APP_GUID_EXPR;
import static io.harness.pcf.model.PcfConstants.CONTEXT_OLD_APP_NAME_EXPR;
import static io.harness.pcf.model.PcfConstants.CONTEXT_OLD_APP_ROUTES_EXPR;

import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.EntityType.SERVICE_TEMPLATE;
import static software.wings.beans.config.ArtifactSourceable.ARTIFACT_SOURCE_REGISTRY_URL_KEY;
import static software.wings.beans.config.ArtifactSourceable.ARTIFACT_SOURCE_REPOSITORY_NAME_KEY;
import static software.wings.beans.config.ArtifactSourceable.ARTIFACT_SOURCE_USER_NAME_KEY;
import static software.wings.common.PathConstants.WINGS_BACKUP_PATH;
import static software.wings.common.PathConstants.WINGS_RUNTIME_PATH;
import static software.wings.common.PathConstants.WINGS_STAGING_PATH;
import static software.wings.service.impl.aws.model.AwsConstants.CONTEXT_NEW_ASG_NAME_EXPR;
import static software.wings.service.impl.aws.model.AwsConstants.CONTEXT_OLD_ASG_NAME_EXPR;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.INFRA_ROUTE_PCF;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.INFRA_TEMP_ROUTE_PCF;
import static software.wings.service.intfc.ServiceVariableService.EncryptedFieldMode.MASKED;
import static software.wings.service.intfc.ServiceVariableService.EncryptedFieldMode.OBTAIN_VALUE;
import static software.wings.sm.ContextElement.DEPLOYMENT_URL;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.EntityType;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariable.ServiceVariableKeys;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.SubEntityType;
import software.wings.beans.artifact.Artifact.ArtifactKeys;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;

import com.google.inject.Inject;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Created by sgurubelli on 8/7/17.
 */
public abstract class ExpressionBuilder {
  @Inject private FeatureFlagService featureFlagService;
  @Inject private AppService appService;

  protected static final String APP_NAME = "app.name";
  protected static final String APP_DESCRIPTION = "app.description";

  protected static final String ARTIFACT_PREFIX = "artifact.";
  protected static final String ARTIFACT = "artifact";
  protected static final String ARTIFACT_FILE_NAME = "ARTIFACT_FILE_NAME";
  protected static final String ARTIFACT_PATH = "artifact.artifactPath";
  protected static final String ARTIFACT_SOURCE = "artifact.source.";

  protected static final String ARTIFACT_DISPLAY_NAME_SUFFIX = ".displayName";
  protected static final String ARTIFACT_DESCRIPTION_SUFFIX = ".description";
  protected static final String ARTIFACT_BUILDNO_SUFFIX = ".buildNo";
  protected static final String ARTIFACT_REVISION_SUFFIX = ".revision";
  protected static final String ARTIFACT_FILE_NAME_SUFFIX = ".fileName";
  protected static final String ARTIFACT_BUCKET_NAME_SUFFIX = ".bucketName";
  protected static final String ARTIFACT_BUCKET_KEY_SUFFIX = ".key";
  protected static final String ARTIFACT_LABEL_SUFFIX = ".label";
  protected static final String ARTIFACT_URL_SUFFIX = ".url";
  protected static final String ARTIFACT_BUILD_FULL_DISPLAYNAME_SUFFIX = ".buildFullDisplayName";
  protected static final String ARTIFACT_METADATA_IMAGE_SUFFIX = "." + ArtifactKeys.metadata_image;
  protected static final String ARTIFACT_METADATA_TAG_SUFFIX = "." + ArtifactKeys.metadata_tag;
  protected static final String ARTIFACT_METADATA_SHA256 = ".metadata.getSHA()";
  protected static final String ARTIFACT_PATH_SUFFIX = ".artifactPath";
  protected static final String ARTIFACT_SOURCE_USER_NAME_SUFFIX = ".source." + ARTIFACT_SOURCE_USER_NAME_KEY;
  protected static final String ARTIFACT_SOURCE_REGISTRY_URL_SUFFIX = ".source." + ARTIFACT_SOURCE_REGISTRY_URL_KEY;
  protected static final String ARTIFACT_SOURCE_REPOSITORY_NAME_SUFFIX =
      ".source." + ARTIFACT_SOURCE_REPOSITORY_NAME_KEY;

  protected static final String ROLLBACK_ARTIFACT_PREFIX = "rollbackArtifact";
  protected static final String ROLLBACK_ARTIFACT_FILE_NAME = "ROLLBACK_ARTIFACT_FILE_NAME";

  protected static final String ENV_PREFIX = "env.";
  protected static final String ENV_NAME = "env.name";
  protected static final String ENV_DESCRIPTION = "env.description";

  protected static final String SERVICE_PREFIX = "service.";
  protected static final String SERVICE_VARIABLE_PREFIX = "serviceVariable.";
  protected static final String ENV_VARIABLE_PREFIX = "environmentVariable.";

  protected static final String SERVICE_NAME = "service.name";
  protected static final String SERVICE_DESCRIPTION = "service.description";
  protected static final String INFRA_NAME = "infra.name";

  protected static final String WORKFLOW_NAME = "workflow.name";
  protected static final String WORKFLOW_START_TS = "workflow.startTs";
  protected static final String WORKFLOW_DESCRIPTION = "workflow.description";
  protected static final String WORKFLOW_DISPLAY_NAME = "workflow.displayName";
  protected static final String WORKFLOW_RELEASE_NO = "workflow.releaseNo";
  protected static final String WORKFLOW_LAST_GOOD_RELEASE_NO = "workflow.lastGoodReleaseNo";
  protected static final String WORKFLOW_LAST_GOOD_DEPLOYMENT_DISPLAY_NAME = "workflow.lastGoodDeploymentDisplayName";
  protected static final String WORKFLOW_PIPELINE_DEPLOYMENT_UUID = "workflow.pipelineDeploymentUuid";

  protected static final String PIPELINE_NAME = "pipeline.name";
  protected static final String PIPELINE_DESCRIPTION = "pipeline.description";
  protected static final String PIPELINE_START_TS = "pipeline.startTs";

  protected static final String INSTANCE_NAME = "instance.name";
  protected static final String INSTANCE_HOSTNAME = "instance.hostName";
  protected static final String INSTANCE_HOST_PUBLICDNS = "instance.host.publicDns";

  protected static final String INFRA_PREFIX = "infra.";
  protected static final String INFRA_KUBERNETES_NAMESPACE = "infra.kubernetes.namespace";
  protected static final String INFRA_KUBERNETES_INFRAID = "infra.kubernetes.infraId";
  protected static final String INFRA_HELM_SHORTID = "infra.helm.shortId";
  protected static final String INFRA_HELM_RELEASENAME = "infra.helm.releaseName";
  protected static final String INFRA_CUSTOM_VARS = "infra.custom.vars";

  protected static final String INFRA_PCF_ORG = "infra.pcf.organization";
  protected static final String INFRA_PCF_SPACE = "infra.pcf.space";
  protected static final String INFRA_PCF_CLOUDPROVIDER_NAME = "infra.pcf.cloudProvider.name";
  protected static final String PCF_PLUGIN_SERVICE_MANIFEST = "service.manifest";
  protected static final String PCF_PLUGIN_SERVICE_MANIFEST_REPO_ROOT = "service.manifest.repoRoot";

  protected static final String APPROVEDBY_NAME = "approvedBy.name";
  protected static final String APPROVEDBY_EMAIL = "approvedBy.email";

  protected static final String EMAIl_TO_ADDRESS = "toAddress";
  protected static final String EMAIL_CC_ADDRESS = "ccAddress";
  protected static final String EMAIL_SUBJECT = "subject";
  protected static final String EMAIL_BODY = "body";

  protected static final String ASSERTION_STATEMENT = "assertionStatement";
  protected static final String ASSERTION_STATUS = "assertionStatus";

  protected static final String XPATH = "xpath('//status/text()')";
  protected static final String JSONPATH = "jsonpath('health.status')";

  protected static final String HTTP_URL = "httpUrl";
  protected static final String HTTP_RESPONSE_METHOD = "httpResponseMethod";
  protected static final String HTTP_RESPONSE_CODE = "httpResponseCode";
  protected static final String HTTP_RESPONSE_BODY = "httpResponseBody";

  protected static final String HELM_CHART_NAME = "helmChart.name";
  protected static final String HELM_CHART_DESCRIPTION = "helmChart.description";
  protected static final String HELM_CHART_DISPLAY_NAME = "helmChart.displayName";
  protected static final String HELM_CHART_VERSION = "helmChart.version";
  protected static final String HELM_CHART_URL = "helmChart.metadata.url";
  protected static final String HELM_CHART_BASE_PATH = "helmChart.metadata.basePath";
  protected static final String HELM_CHART_REPO_NAME = "helmChart.metadata.repositoryName";
  protected static final String HELM_CHART_BUCKET_NAME = "helmChart.metadata.bucketName";

  @Inject private ServiceVariableService serviceVariablesService;
  @Inject private ServiceTemplateService serviceTemplateService;

  public Set<String> getExpressions(String appId, String entityId, String serviceId) {
    return getExpressions(appId, entityId);
  }

  public Set<String> getExpressions(String appId, String entityId, String serviceId, StateType stateType) {
    return getExpressions(appId, entityId);
  }

  public Set<String> getExpressions(String appId, String entityId, String serviceId, StateType stateType,
      SubEntityType subEntityType, boolean forTags) {
    return getExpressions(appId, entityId, serviceId, stateType);
  }

  public abstract Set<String> getExpressions(String appId, String entityId);

  public abstract Set<String> getDynamicExpressions(String appId, String entityId);

  public Set<String> getExpressions(String appId, String entityId, StateType stateType) {
    if (stateType == null) {
      return getExpressions(appId, entityId);
    }
    return new HashSet<>();
  }

  public static Set<String> getStaticExpressions(boolean multiArtifact) {
    Set<String> expressions = new TreeSet<>();
    expressions.addAll(asList(APP_NAME, APP_DESCRIPTION));
    if (!multiArtifact) {
      expressions.addAll(getArtifactExpressionSuffixes().stream().map(ARTIFACT::concat).collect(Collectors.toSet()));
      expressions.add(ARTIFACT_FILE_NAME);
      expressions.addAll(
          getArtifactExpressionSuffixes().stream().map(ROLLBACK_ARTIFACT_PREFIX::concat).collect(Collectors.toSet()));
      expressions.add(ROLLBACK_ARTIFACT_FILE_NAME);
    }
    expressions.addAll(asList(ENV_NAME, ENV_DESCRIPTION));
    expressions.addAll(asList(SERVICE_NAME, SERVICE_DESCRIPTION));
    expressions.addAll(asList(INFRA_NAME));
    expressions.addAll(asList(WORKFLOW_NAME, WORKFLOW_DESCRIPTION, WORKFLOW_DISPLAY_NAME, WORKFLOW_RELEASE_NO,
        WORKFLOW_LAST_GOOD_DEPLOYMENT_DISPLAY_NAME, WORKFLOW_LAST_GOOD_RELEASE_NO, WORKFLOW_PIPELINE_DEPLOYMENT_UUID,
        WORKFLOW_START_TS));
    expressions.addAll(asList(PIPELINE_NAME, PIPELINE_DESCRIPTION, PIPELINE_START_TS));

    expressions.addAll(asList(INSTANCE_NAME, INSTANCE_HOSTNAME, INSTANCE_HOST_PUBLICDNS));

    expressions.addAll(asList(INFRA_KUBERNETES_NAMESPACE, INFRA_KUBERNETES_INFRAID));
    expressions.addAll(asList(INFRA_ROUTE_PCF, INFRA_TEMP_ROUTE_PCF));
    expressions.add(WorkflowStandardParams.DEPLOYMENT_TRIGGERED_BY);
    expressions.addAll(asList(INFRA_CUSTOM_VARS));

    expressions.addAll(asList(HELM_CHART_BASE_PATH, HELM_CHART_DESCRIPTION, HELM_CHART_NAME, HELM_CHART_REPO_NAME,
        HELM_CHART_URL, HELM_CHART_VERSION, HELM_CHART_BUCKET_NAME, HELM_CHART_DISPLAY_NAME));

    return expressions;
  }

  public static Set<String> getStateTypeExpressions(StateType stateType) {
    Set<String> expressions = new TreeSet<>(asList(DEPLOYMENT_URL));
    switch (stateType) {
      case SHELL_SCRIPT:
        expressions.addAll(asList(WINGS_RUNTIME_PATH, WINGS_STAGING_PATH, WINGS_BACKUP_PATH, HARNESS_KUBE_CONFIG_PATH));
        break;
      case HTTP:
        expressions.addAll(asList(HTTP_URL, HTTP_RESPONSE_METHOD, HTTP_RESPONSE_CODE, HTTP_RESPONSE_BODY,
            ASSERTION_STATEMENT, ASSERTION_STATUS, XPATH, JSONPATH));
        break;
      case APPROVAL:
        expressions.addAll(asList(APPROVEDBY_NAME, APPROVEDBY_EMAIL));
        break;
      case EMAIL:
        expressions.addAll(asList(EMAIl_TO_ADDRESS, EMAIL_CC_ADDRESS, EMAIL_SUBJECT, EMAIL_BODY));
        break;
      case COMMAND:
        expressions.addAll(asList(WINGS_RUNTIME_PATH, WINGS_STAGING_PATH, WINGS_BACKUP_PATH));
        break;
      case AWS_CODEDEPLOY_STATE:
        expressions.addAll(asList(ARTIFACT + ARTIFACT_BUCKET_NAME_SUFFIX, ARTIFACT + ARTIFACT_BUCKET_KEY_SUFFIX,
            ARTIFACT + ARTIFACT_URL_SUFFIX));
        break;
      case AWS_LAMBDA_STATE:
      case ECS_SERVICE_SETUP:
      case JENKINS:
      case BAMBOO:
      case KUBERNETES_SETUP:
      case NEW_RELIC_DEPLOYMENT_MARKER:
      case KUBERNETES_DEPLOY:
        noop();
        break;
      case PCF_PLUGIN:
        expressions.addAll(getPcfWorkflowExpressions());
        expressions.addAll(getPcfWorkflowExprAfterSetupState());
        expressions.add(PCF_PLUGIN_SERVICE_MANIFEST);
        expressions.add(PCF_PLUGIN_SERVICE_MANIFEST_REPO_ROOT);
        break;
      case PCF_SETUP:
        expressions.addAll(getPcfWorkflowExpressions());
        break;
      case PCF_RESIZE:
      case PCF_ROLLBACK:
      case PCF_MAP_ROUTE:
      case PCF_UNMAP_ROUTE:
        expressions.addAll(getPcfWorkflowExpressions());
        expressions.addAll(getPcfWorkflowExprAfterSetupState());
        break;
      case HELM_DEPLOY:
      case HELM_ROLLBACK:
        expressions.addAll(asList(INFRA_HELM_SHORTID, INFRA_HELM_RELEASENAME));
        break;

      case K8S_TRAFFIC_SPLIT:
        expressions.addAll(asList(canaryDestination, stableDestination, virtualServiceName));
        break;

      case K8S_SCALE:
        expressions.add(canaryWorkload);
        break;

      case K8S_DELETE:
        expressions.add(canaryWorkload);
        break;

      case AWS_AMI_SERVICE_SETUP:
      case AWS_AMI_SERVICE_DEPLOY:
      case AWS_AMI_SWITCH_ROUTES:
      case AWS_AMI_SERVICE_ROLLBACK:
      case AWS_AMI_ROLLBACK_SWITCH_ROUTES:
        expressions.addAll(asList(CONTEXT_NEW_ASG_NAME_EXPR, CONTEXT_OLD_ASG_NAME_EXPR));
        break;

      default:
        break;
    }

    return expressions;
  }

  private static Collection<String> getPcfWorkflowExpressions() {
    return asList(INFRA_PCF_ORG, INFRA_PCF_SPACE, INFRA_PCF_CLOUDPROVIDER_NAME);
  }

  private static Collection<String> getPcfWorkflowExprAfterSetupState() {
    return asList(CONTEXT_NEW_APP_GUID_EXPR, CONTEXT_NEW_APP_NAME_EXPR, CONTEXT_NEW_APP_ROUTES_EXPR,
        CONTEXT_OLD_APP_GUID_EXPR, CONTEXT_OLD_APP_NAME_EXPR, CONTEXT_ACTIVE_APP_NAME_EXPR,
        CONTEXT_INACTIVE_APP_NAME_EXPR, CONTEXT_OLD_APP_ROUTES_EXPR, CONTEXT_APP_FINAL_ROUTES_EXPR,
        CONTEXT_APP_TEMP_ROUTES_EXPR);
  }

  protected Set<String> getServiceVariables(String appId, List<String> entityIds) {
    return getServiceVariables(appId, entityIds, null);
  }

  protected Set<String> getServiceVariables(String appId, List<String> entityIds, EntityType entityType) {
    if (isEmpty(entityIds)) {
      return new TreeSet<>();
    }
    PageRequest<ServiceVariable> serviceVariablePageRequest = aPageRequest()
                                                                  .withLimit(PageRequest.UNLIMITED)
                                                                  .addFilter(ServiceVariableKeys.appId, EQ, appId)
                                                                  .addFilter("entityId", IN, entityIds.toArray())
                                                                  .build();
    if (entityType != null) {
      serviceVariablePageRequest.addFilter("entityType", EQ, entityType);
    }
    List<ServiceVariable> serviceVariables = serviceVariablesService.list(serviceVariablePageRequest, MASKED);

    String accountId = appService.getAccountIdByAppId(appId);
    if (featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, accountId)) {
      Set<String> serviceVariableMentions = new HashSet<>();
      serviceVariables.forEach(serviceVariable -> {
        if (ServiceVariableType.ARTIFACT == serviceVariable.getType()) {
          String artifactMentions = "artifacts." + serviceVariable.getName();
          serviceVariableMentions.add(artifactMentions);
          for (String suffix : getArtifactExpressionSuffixes()) {
            serviceVariableMentions.add(artifactMentions + suffix);
          }
        } else {
          serviceVariableMentions.add("serviceVariable." + serviceVariable.getName());
        }
      });
      return serviceVariableMentions;
    }

    return serviceVariables.stream()
        .map(serviceVariable -> "serviceVariable." + serviceVariable.getName())
        .collect(Collectors.toSet());
  }

  protected Set<String> getServiceVariablesOfTemplates(
      String appId, PageRequest<ServiceTemplate> pageRequest, EntityType entityType) {
    List<ServiceTemplate> serviceTemplates = serviceTemplateService.list(pageRequest, false, OBTAIN_VALUE);
    SortedSet<String> serviceVariables = new TreeSet<>();
    if (SERVICE == entityType) {
      return getServiceVariables(
          appId, serviceTemplates.stream().map(ServiceTemplate::getServiceId).collect(toList()), SERVICE);
    } else if (ENVIRONMENT == entityType) {
      serviceVariables.addAll(getServiceVariables(
          appId, serviceTemplates.stream().map(ServiceTemplate::getEnvId).collect(toList()), ENVIRONMENT));
    }
    serviceVariables.addAll(getServiceVariables(
        appId, serviceTemplates.stream().map(ServiceTemplate::getUuid).collect(toList()), SERVICE_TEMPLATE));
    return serviceVariables;
  }

  public static Set<String> getArtifactExpressionSuffixes() {
    Set<String> expressions = new TreeSet<>();
    expressions.addAll(asList(ARTIFACT_DISPLAY_NAME_SUFFIX, ARTIFACT_BUILDNO_SUFFIX, ARTIFACT_REVISION_SUFFIX,
        ARTIFACT_DESCRIPTION_SUFFIX, ARTIFACT_FILE_NAME_SUFFIX, ARTIFACT_BUILD_FULL_DISPLAYNAME_SUFFIX,
        ARTIFACT_BUCKET_NAME_SUFFIX, ARTIFACT_BUCKET_KEY_SUFFIX, ARTIFACT_PATH_SUFFIX, ARTIFACT_URL_SUFFIX,
        ARTIFACT_SOURCE_USER_NAME_SUFFIX, ARTIFACT_SOURCE_REGISTRY_URL_SUFFIX, ARTIFACT_SOURCE_REPOSITORY_NAME_SUFFIX,
        ARTIFACT_METADATA_IMAGE_SUFFIX, ARTIFACT_METADATA_TAG_SUFFIX, ARTIFACT_LABEL_SUFFIX, ARTIFACT_METADATA_SHA256));
    return expressions;
  }
}
