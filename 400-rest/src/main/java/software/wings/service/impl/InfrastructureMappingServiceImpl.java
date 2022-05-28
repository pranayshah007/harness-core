/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.DEPLOY_TO_INLINE_HOSTS;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.exception.WingsException.USER;
import static io.harness.k8s.KubernetesConvention.DASH;
import static io.harness.persistence.HQuery.allChecks;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.api.DeploymentType.AMI;
import static software.wings.api.DeploymentType.AWS_CODEDEPLOY;
import static software.wings.api.DeploymentType.AWS_LAMBDA;
import static software.wings.api.DeploymentType.AZURE_VMSS;
import static software.wings.api.DeploymentType.AZURE_WEBAPP;
import static software.wings.api.DeploymentType.ECS;
import static software.wings.api.DeploymentType.HELM;
import static software.wings.api.DeploymentType.KUBERNETES;
import static software.wings.api.DeploymentType.PCF;
import static software.wings.api.DeploymentType.SSH;
import static software.wings.api.DeploymentType.WINRM;
import static software.wings.beans.VMSSAuthType.PASSWORD;
import static software.wings.beans.VMSSAuthType.SSH_PUBLIC_KEY;
import static software.wings.beans.infrastructure.Host.Builder.aHost;
import static software.wings.common.InfrastructureConstants.INFRA_KUBERNETES_INFRAID_EXPRESSION;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_AMI_ASG_DESIRED_INSTANCES;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_AMI_ASG_MAX_INSTANCES;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_AMI_ASG_MIN_INSTANCES;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_AMI_ASG_NAME;
import static software.wings.settings.SettingVariableTypes.AWS;
import static software.wings.settings.SettingVariableTypes.AZURE;
import static software.wings.settings.SettingVariableTypes.GCP;
import static software.wings.settings.SettingVariableTypes.PHYSICAL_DATA_CENTER;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.atteo.evo.inflector.English.plural;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SweepingOutputInstance;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.validator.EntityNameValidator;
import io.harness.delegate.task.aws.AwsElbListener;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.KubernetesConvention;
import io.harness.k8s.KubernetesHelperService;
import io.harness.logging.Misc;
import io.harness.observer.RemoteObserverInformer;
import io.harness.observer.Subject;
import io.harness.persistence.HQuery.QueryChecks;
import io.harness.queue.QueuePublisher;
import io.harness.reflection.ReflectionUtils;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.validation.Create;
import io.harness.validation.SuppressValidation;

import software.wings.annotation.BlueprintProcessor;
import software.wings.annotation.EncryptableSetting;
import software.wings.api.DeploymentType;
import software.wings.api.PhaseElement;
import software.wings.beans.AccountEvent;
import software.wings.beans.AccountEventType;
import software.wings.beans.AmiDeploymentType;
import software.wings.beans.Application;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.AwsAmiInfrastructureMapping.AwsAmiInfrastructureMappingKeys;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.AwsInfrastructureMapping.AwsInfrastructureMappingKeys;
import software.wings.beans.AwsLambdaInfraStructureMapping;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureInfrastructureMapping;
import software.wings.beans.AzureKubernetesInfrastructureMapping;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.beans.AzureVMSSInfrastructureMapping.AzureVMSSInfrastructureMappingKeys;
import software.wings.beans.AzureWebAppInfrastructureMapping;
import software.wings.beans.AzureWebAppInfrastructureMapping.AzureWebAppsInfrastructureMappingKeys;
import software.wings.beans.CodeDeployInfrastructureMapping;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.GcpKubernetesInfrastructureMapping;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.HostConnectionType;
import software.wings.beans.HostValidationRequest;
import software.wings.beans.HostValidationResponse;
import software.wings.beans.InfraMappingSweepingOutput;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMapping.InfrastructureMappingKeys;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.InfrastructureType;
import software.wings.beans.PcfConfig;
import software.wings.beans.PcfInfrastructureMapping;
import software.wings.beans.PhysicalInfrastructureMapping;
import software.wings.beans.PhysicalInfrastructureMappingBase;
import software.wings.beans.PhysicalInfrastructureMappingWinRm;
import software.wings.beans.RancherConfig;
import software.wings.beans.RancherKubernetesInfrastructureMapping;
import software.wings.beans.SSHVaultConfig;
import software.wings.beans.Service;
import software.wings.beans.ServiceInstance;
import software.wings.beans.ServiceInstanceSelectionParams;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.container.ContainerTask;
import software.wings.beans.container.KubernetesContainerTask;
import software.wings.beans.container.KubernetesContainerTaskUtils;
import software.wings.beans.infrastructure.Host;
import software.wings.common.InfrastructureConstants;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.dl.WingsPersistence;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.helpers.ext.azure.AzureDelegateHelperService;
import software.wings.helpers.ext.azure.AzureHelperService;
import software.wings.helpers.ext.container.ContainerMasterUrlHelper;
import software.wings.prune.PruneEntityListener;
import software.wings.prune.PruneEvent;
import software.wings.service.impl.aws.model.AwsAsgGetRunningCountData;
import software.wings.service.impl.aws.model.AwsRoute53HostedZoneData;
import software.wings.service.impl.aws.model.AwsSecurityGroup;
import software.wings.service.impl.aws.model.AwsSubnet;
import software.wings.service.impl.aws.model.AwsVPC;
import software.wings.service.impl.instance.InstanceSyncPerpetualTaskService;
import software.wings.service.impl.servicetemplates.ServiceTemplateHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ContainerService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.InfrastructureMappingServiceObserver;
import software.wings.service.intfc.InfrastructureProvider;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceInstanceService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.aws.manager.AwsAsgHelperServiceManager;
import software.wings.service.intfc.aws.manager.AwsCodeDeployHelperServiceManager;
import software.wings.service.intfc.aws.manager.AwsEc2HelperServiceManager;
import software.wings.service.intfc.aws.manager.AwsEcsHelperServiceManager;
import software.wings.service.intfc.aws.manager.AwsIamHelperServiceManager;
import software.wings.service.intfc.aws.manager.AwsRoute53HelperServiceManager;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.ownership.OwnedByInfrastructureMapping;
import software.wings.service.intfc.security.SSHVaultService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.settings.SettingVariableTypes;
import software.wings.stencils.Stencil;
import software.wings.stencils.StencilPostProcessor;
import software.wings.utils.ArtifactType;
import software.wings.utils.EcsConvention;
import software.wings.utils.HostValidationService;
import software.wings.utils.Utils;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ecs.model.LaunchType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.azure.management.compute.VirtualMachine;
import com.microsoft.azure.management.resources.fluentcore.arm.models.HasName;
import com.mongodb.DuplicateKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.executable.ValidateOnExecution;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.FindOptions;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

/**
 * Created by anubhaw on 1/10/17.
 */
@Singleton
@ValidateOnExecution
@Slf4j
@TargetModule(_870_CG_ORCHESTRATION)
@OwnedBy(CDC)
public class InfrastructureMappingServiceImpl implements InfrastructureMappingService {
  private static final String COMPUTE_PROVIDER_SETTING_ID_KEY = "computeProviderSettingId";
  private static final Integer REFERENCED_ENTITIES_TO_SHOW = 10;
  private static final String DEFAULT = "default";
  @Inject
  @Getter(onMethod = @__(@SuppressValidation))
  private Subject<InfrastructureMappingServiceObserver> subject = new Subject<>();

  @Inject private WingsPersistence wingsPersistence;
  @Inject private Map<String, InfrastructureProvider> infrastructureProviders;
  @Inject private AppService appService;
  @Inject private EnvironmentService envService;
  // DO NOT DELETE THIS, PRUNE logic needs it
  @Inject private InstanceSyncPerpetualTaskService instanceSyncPerpetualTaskService;
  @Inject private InstanceService instanceService;
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private ServiceInstanceService serviceInstanceService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private ServiceTemplateService serviceTemplateService;
  @Inject private SettingsService settingsService;
  @Inject private StencilPostProcessor stencilPostProcessor;
  @Inject private WorkflowService workflowService;
  @Inject private SecretManager secretManager;
  @Inject private ManagerExpressionEvaluator evaluator;
  @Inject private PcfHelperService pcfHelperService;
  @Inject private AwsEcsHelperServiceManager awsEcsHelperServiceManager;
  @Inject private AwsIamHelperServiceManager awsIamHelperServiceManager;
  @Inject private AwsEc2HelperServiceManager awsEc2HelperServiceManager;
  @Inject private AwsCodeDeployHelperServiceManager awsCodeDeployHelperServiceManager;
  @Inject private AwsRoute53HelperServiceManager awsRoute53HelperServiceManager;
  @Inject private AwsAsgHelperServiceManager awsAsgHelperServiceManager;
  @Inject private YamlPushService yamlPushService;
  @Inject private AzureHelperService azureHelperService;
  @Inject private AzureDelegateHelperService azureDelegateHelperService;
  @Inject private TriggerService triggerService;
  @Inject private PipelineService pipelineService;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private InfrastructureProvisionerService infrastructureProvisionerService;
  @Inject private ContainerMasterUrlHelper containerMasterUrlHelper;
  @Inject private ServiceTemplateHelper serviceTemplateHelper;
  @Inject private EventPublishHelper eventPublishHelper;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private SSHVaultService sshVaultService;
  @Inject private QueuePublisher<PruneEvent> pruneQueue;
  @Inject private RemoteObserverInformer remoteObserverInformer;
  @Inject private RancherHelperService rancherHelperService;

  @Override
  public PageResponse<InfrastructureMapping> list(PageRequest<InfrastructureMapping> pageRequest) {
    return list(pageRequest, allChecks);
  }

  @Override
  public PageResponse<InfrastructureMapping> list(
      PageRequest<InfrastructureMapping> pageRequest, Set<QueryChecks> queryChecks) {
    PageResponse<InfrastructureMapping> pageResponse =
        wingsPersistence.query(InfrastructureMapping.class, pageRequest, queryChecks);
    if (pageResponse != null && pageResponse.getResponse() != null) {
      for (InfrastructureMapping infrastructureMapping : pageResponse.getResponse()) {
        try {
          setLoadBalancerName(infrastructureMapping);
        } catch (Exception e) {
          log.error(
              format("Failed to set load balancer for InfrastructureMapping %s", infrastructureMapping.toString()), e);
        }
      }
    }
    return pageResponse;
  }

  private void setLoadBalancerName(InfrastructureMapping infrastructureMapping) {
    if (infrastructureMapping instanceof AwsInfrastructureMapping) {
      ((AwsInfrastructureMapping) infrastructureMapping)
          .setLoadBalancerName(((AwsInfrastructureMapping) infrastructureMapping).getLoadBalancerId());
    }
  }

  @VisibleForTesting
  void setDefaults(InfrastructureMapping infraMapping) {
    switch (infraMapping.getInfraMappingType()) {
      case InfrastructureType.GCP_KUBERNETES_ENGINE:
        GcpKubernetesInfrastructureMapping gcpKubernetesInfrastructureMapping =
            (GcpKubernetesInfrastructureMapping) infraMapping;
        if (containerMasterUrlHelper.masterUrlRequiredWithProvisioner(gcpKubernetesInfrastructureMapping)) {
          gcpKubernetesInfrastructureMapping.setMasterUrl(
              containerMasterUrlHelper.fetchMasterUrl(getGcpContainerServiceParams(gcpKubernetesInfrastructureMapping),
                  getSyncTaskContext(gcpKubernetesInfrastructureMapping)));
        }
        if (isBlank(gcpKubernetesInfrastructureMapping.getNamespace())) {
          gcpKubernetesInfrastructureMapping.setNamespace(DEFAULT);
        }
        break;
      case InfrastructureType.AZURE_KUBERNETES:
        AzureKubernetesInfrastructureMapping azureKubernetesInfrastructureMapping =
            (AzureKubernetesInfrastructureMapping) infraMapping;
        if (containerMasterUrlHelper.masterUrlRequiredWithProvisioner(azureKubernetesInfrastructureMapping)) {
          azureKubernetesInfrastructureMapping.setMasterUrl(containerMasterUrlHelper.fetchMasterUrl(
              getAzureContainerServiceParams(azureKubernetesInfrastructureMapping),
              getSyncTaskContext(azureKubernetesInfrastructureMapping)));
        }
        break;
      case InfrastructureType.DIRECT_KUBERNETES:
        DirectKubernetesInfrastructureMapping directKubernetesInfraMapping =
            (DirectKubernetesInfrastructureMapping) infraMapping;
        if (isBlank(directKubernetesInfraMapping.getNamespace())) {
          directKubernetesInfraMapping.setNamespace(DEFAULT);
        }
        break;
      case InfrastructureType.PHYSICAL_INFRA:
        PhysicalInfrastructureMapping pyInfraMapping = (PhysicalInfrastructureMapping) infraMapping;
        pyInfraMapping.setHostNames(getUniqueHostNames(pyInfraMapping));
        break;
      case InfrastructureType.PHYSICAL_INFRA_WINRM:
        PhysicalInfrastructureMappingWinRm physicalInfraMappingWinRm =
            (PhysicalInfrastructureMappingWinRm) infraMapping;
        physicalInfraMappingWinRm.setHostNames(getUniqueHostNames(physicalInfraMappingWinRm));
        break;
      case InfrastructureType.AWS_INSTANCE:
        AwsInfrastructureMapping awsInfrastructureMapping = (AwsInfrastructureMapping) infraMapping;
        if (isEmpty(awsInfrastructureMapping.getHostConnectionType())) {
          awsInfrastructureMapping.setHostConnectionType(awsInfrastructureMapping.isUsePublicDns()
                  ? HostConnectionType.PUBLIC_DNS.name()
                  : HostConnectionType.PRIVATE_DNS.name());
        }
        break;
      case InfrastructureType.RANCHER_KUBERNETES:
        RancherKubernetesInfrastructureMapping rancherKubernetesInfrastructureMapping =
            (RancherKubernetesInfrastructureMapping) infraMapping;
        if (isBlank(rancherKubernetesInfrastructureMapping.getNamespace())) {
          rancherKubernetesInfrastructureMapping.setNamespace(DEFAULT);
        }
        break;
      default:
    }
  }

  @Override
  public void validateInfraMapping(
      @Valid InfrastructureMapping infraMapping, boolean skipValidation, String workflowExecutionId) {
    if (skipValidation) {
      log.info(
          "Ignore validation for InfraMapping as skipValidation is marked true. Infra mapping coming from yaml or Infra def");
      return;
    }
    if (isNotEmpty(infraMapping.getProvisionerId())) {
      return;
    }

    if (infraMapping instanceof ContainerInfrastructureMapping) {
      ContainerInfrastructureMapping containerInfraMapping = (ContainerInfrastructureMapping) infraMapping;
      // skipValidation if the namespace is an expression
      if (isNamespaceExpression(containerInfraMapping)) {
        log.info(
            "Ignore validation for InfraMapping as mapping is of type ContainerInfrastructureMapping and namespace is an expression. infraMapping = {}",
            infraMapping);
        return;
      }
    }

    if (infraMapping instanceof AwsInfrastructureMapping) {
      validateAwsInfraMapping((AwsInfrastructureMapping) infraMapping);
    }

    if (infraMapping instanceof EcsInfrastructureMapping) {
      validateEcsInfraMapping((EcsInfrastructureMapping) infraMapping);
    }

    if (infraMapping instanceof GcpKubernetesInfrastructureMapping) {
      GcpKubernetesInfrastructureMapping gcpKubernetesInfrastructureMapping =
          (GcpKubernetesInfrastructureMapping) infraMapping;
      validateGcpInfraMapping(gcpKubernetesInfrastructureMapping);
    }

    if (infraMapping instanceof AzureKubernetesInfrastructureMapping) {
      AzureKubernetesInfrastructureMapping azureKubernetesInfrastructureMapping =
          (AzureKubernetesInfrastructureMapping) infraMapping;
      validateAzureKubernetesInfraMapping(azureKubernetesInfrastructureMapping);
    }

    if (infraMapping instanceof AzureInfrastructureMapping) {
      AzureInfrastructureMapping azureInfrastructureMapping = (AzureInfrastructureMapping) infraMapping;
      validateAzureInfraMapping(azureInfrastructureMapping);
    }

    if (infraMapping instanceof DirectKubernetesInfrastructureMapping) {
      DirectKubernetesInfrastructureMapping directKubernetesInfrastructureMapping =
          (DirectKubernetesInfrastructureMapping) infraMapping;
      validateDirectKubernetesInfraMapping(directKubernetesInfrastructureMapping, null);
    }

    if (infraMapping instanceof RancherKubernetesInfrastructureMapping) {
      RancherKubernetesInfrastructureMapping rancherKubernetesInfrastructureMapping =
          (RancherKubernetesInfrastructureMapping) infraMapping;
      validateRancherKubernetesInfraMapping(rancherKubernetesInfrastructureMapping, null);
    }

    if (infraMapping instanceof PhysicalInfrastructureMapping) {
      PhysicalInfrastructureMapping physicalInfrastructureMapping = (PhysicalInfrastructureMapping) infraMapping;
      validatePhysicalInfrastructureMapping(physicalInfrastructureMapping);
    }

    if (infraMapping instanceof PhysicalInfrastructureMappingWinRm) {
      validatePhysicalInfrastructureMappingWinRm((PhysicalInfrastructureMappingWinRm) infraMapping);
    }

    if (infraMapping instanceof PcfInfrastructureMapping) {
      validatePcfInfrastructureMapping((PcfInfrastructureMapping) infraMapping);
    }

    if (infraMapping instanceof AwsLambdaInfraStructureMapping) {
      validateAwsLambdaInfrastructureMapping((AwsLambdaInfraStructureMapping) infraMapping);
    }

    if (infraMapping instanceof AwsAmiInfrastructureMapping) {
      validateAwsAmiInfrastructureMapping((AwsAmiInfrastructureMapping) infraMapping);
    }

    if (infraMapping instanceof AzureVMSSInfrastructureMapping) {
      validateAzureVMSSInfraMapping((AzureVMSSInfrastructureMapping) infraMapping);
    }

    if (infraMapping instanceof AzureWebAppInfrastructureMapping) {
      validateAzureWebAppInfraMapping((AzureWebAppInfrastructureMapping) infraMapping);
    }
  }

  @VisibleForTesting
  boolean isNamespaceExpression(ContainerInfrastructureMapping containerInfraMapping) {
    return ExpressionEvaluator.containsVariablePattern(containerInfraMapping.getNamespace());
  }

  @VisibleForTesting
  public void validateProvisionerConfig(InfrastructureMapping infraMapping) {
    if (isEmpty(infraMapping.getProvisionerId())) {
      return;
    }
    if (isEmpty(infraMapping.getBlueprints())) {
      throw new InvalidRequestException("Blueprints can't be empty with Provisioner", USER);
    }
    BlueprintProcessor.validateKeys(infraMapping, infraMapping.getBlueprints());
  }

  private String fetchReleaseName(InfrastructureMapping infraMapping) {
    Service service = serviceResourceService.get(infraMapping.getAppId(), infraMapping.getServiceId(), false);
    if (service == null) {
      return null;
    }

    String releaseName = null;

    if (infraMapping instanceof AzureKubernetesInfrastructureMapping
        || infraMapping instanceof DirectKubernetesInfrastructureMapping
        || infraMapping instanceof GcpKubernetesInfrastructureMapping
        || infraMapping instanceof RancherKubernetesInfrastructureMapping) {
      releaseName = ((ContainerInfrastructureMapping) infraMapping).getReleaseName();

      if (isBlank(releaseName)) {
        releaseName = INFRA_KUBERNETES_INFRAID_EXPRESSION;
      }
    }

    return releaseName;
  }

  @Override
  @ValidationGroups(Create.class)
  public InfrastructureMapping save(@Valid InfrastructureMapping infraMapping, String workflowExecutionId) {
    return save(infraMapping, false, workflowExecutionId);
  }

  @Override
  @ValidationGroups(Create.class)
  public InfrastructureMapping save(
      InfrastructureMapping infraMapping, boolean skipValidation, String workflowExecutionId) {
    // The default name uses a bunch of user inputs, which is why we generate it at the time of save.
    if (infraMapping.isAutoPopulate()) {
      setAutoPopulatedName(infraMapping);
    }

    SettingAttribute computeProviderSetting = settingsService.get(infraMapping.getComputeProviderSettingId());

    if (computeProviderSetting != null) {
      infraMapping.setComputeProviderName(computeProviderSetting.getName());
    }

    if (infraMapping instanceof ContainerInfrastructureMapping) {
      ((ContainerInfrastructureMapping) infraMapping).setReleaseName(fetchReleaseName(infraMapping));
    }

    setDefaults(infraMapping);
    validateInfraMapping(infraMapping, skipValidation, workflowExecutionId);
    InfrastructureMapping savedInfraMapping;
    try {
      savedInfraMapping = wingsPersistence.saveAndGet(InfrastructureMapping.class, infraMapping);
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(ex.getMessage());
    } catch (Exception e) {
      if (e.getCause() != null && e.getCause() instanceof DuplicateKeyException) {
        throw new DuplicateFieldException(e.getCause().getMessage());
      } else {
        throw new UnexpectedException(ExceptionUtils.getMessage(e));
      }
    }

    updateMissingFields(infraMapping, savedInfraMapping);

    String accountId = appService.getAccountIdByAppId(infraMapping.getAppId());
    if (!savedInfraMapping.isSample()) {
      eventPublishHelper.publishAccountEvent(
          accountId, AccountEvent.builder().accountEventType(AccountEventType.INFRA_MAPPING_ADDED).build(), true, true);
    }

    try {
      subject.fireInform(InfrastructureMappingServiceObserver::onSaved, infraMapping);
      remoteObserverInformer.sendEvent(
          ReflectionUtils.getMethod(InfrastructureMappingServiceObserver.class, "onSaved", InfrastructureMapping.class),
          InfrastructureMappingServiceImpl.class, infraMapping);
    } catch (Exception e) {
      log.error("Encountered exception while informing the observers of Infrastructure Mappings.", e);
    }

    return savedInfraMapping;
  }

  private void updateMissingFields(InfrastructureMapping infraMapping, InfrastructureMapping savedInfraMapping) {
    if (savedInfraMapping instanceof AwsInfrastructureMapping && infraMapping instanceof AwsInfrastructureMapping
        && ((AwsInfrastructureMapping) savedInfraMapping).getAwsInstanceFilter() == null
        && ((AwsInfrastructureMapping) infraMapping).getAwsInstanceFilter() != null) {
      try {
        wingsPersistence.updateField(AwsInfrastructureMapping.class, savedInfraMapping.getUuid(),
            AwsInfrastructureMappingKeys.awsInstanceFilter,
            ((AwsInfrastructureMapping) infraMapping).getAwsInstanceFilter());
      } catch (Exception e) {
        log.error("Encountered exception while updating AwsInfrastructureMapping {}.", savedInfraMapping.getUuid(), e);
      }
    }
  }

  @Override
  public InfrastructureMapping update(
      @Valid InfrastructureMapping infrastructureMapping, boolean skipValidation, String workflowExecutionId) {
    InfrastructureMapping savedInfraMapping = get(infrastructureMapping.getAppId(), infrastructureMapping.getUuid());
    SettingAttribute computeProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());

    Map<String, Object> keyValuePairs = new HashMap<>();
    Set<String> fieldsToRemove = new HashSet<>();
    keyValuePairs.put("computeProviderSettingId", infrastructureMapping.getComputeProviderSettingId());

    if (savedInfraMapping.getHostConnectionAttrs() != null
        && !savedInfraMapping.getHostConnectionAttrs().equals(infrastructureMapping.getHostConnectionAttrs())) {
      getInfrastructureProviderByComputeProviderType(infrastructureMapping.getComputeProviderType())
          .updateHostConnAttrs(infrastructureMapping, infrastructureMapping.getHostConnectionAttrs());
      keyValuePairs.put("hostConnectionAttrs", infrastructureMapping.getHostConnectionAttrs());
    }

    if (isNotEmpty(infrastructureMapping.getName())) {
      keyValuePairs.put("name", infrastructureMapping.getName());
    } else {
      fieldsToRemove.add("name");
    }

    if (isNotEmpty(infrastructureMapping.getDisplayName())) {
      keyValuePairs.put(InfrastructureMappingKeys.displayName, infrastructureMapping.getDisplayName());
    } else {
      fieldsToRemove.add(InfrastructureMappingKeys.displayName);
    }

    if (isNotEmpty(infrastructureMapping.getInfrastructureDefinitionId())) {
      keyValuePairs.put(
          InfrastructureMappingKeys.infrastructureDefinitionId, infrastructureMapping.getInfrastructureDefinitionId());
    } else {
      fieldsToRemove.add(InfrastructureMappingKeys.infrastructureDefinitionId);
    }

    if (isNotEmpty(infrastructureMapping.getProvisionerId())) {
      keyValuePairs.put(InfrastructureMapping.PROVISIONER_ID_KEY, infrastructureMapping.getProvisionerId());
    } else {
      fieldsToRemove.add(InfrastructureMapping.PROVISIONER_ID_KEY);
    }

    if (isNotEmpty(infrastructureMapping.getBlueprints())) {
      keyValuePairs.put(InfrastructureMappingKeys.blueprints, infrastructureMapping.getBlueprints());
    } else {
      fieldsToRemove.add(InfrastructureMappingKeys.blueprints);
    }

    setDefaults(infrastructureMapping);

    if (infrastructureMapping instanceof EcsInfrastructureMapping) {
      EcsInfrastructureMapping ecsInfrastructureMapping = (EcsInfrastructureMapping) infrastructureMapping;
      validateInfraMapping(ecsInfrastructureMapping, skipValidation, null);
      handleEcsInfraMapping(keyValuePairs, fieldsToRemove, ecsInfrastructureMapping);

    } else if (infrastructureMapping instanceof DirectKubernetesInfrastructureMapping) {
      DirectKubernetesInfrastructureMapping directKubernetesInfrastructureMapping =
          (DirectKubernetesInfrastructureMapping) infrastructureMapping;
      validateInfraMapping(directKubernetesInfrastructureMapping, skipValidation, null);
      if (isNotBlank(directKubernetesInfrastructureMapping.getNamespace())) {
        keyValuePairs.put("namespace", directKubernetesInfrastructureMapping.getNamespace());
      } else {
        directKubernetesInfrastructureMapping.setNamespace(DEFAULT);
        keyValuePairs.put("namespace", DEFAULT);
      }
      if (directKubernetesInfrastructureMapping.getClusterName() != null) {
        keyValuePairs.put("clusterName", directKubernetesInfrastructureMapping.getClusterName());
      } else {
        fieldsToRemove.add("clusterName");
      }
    } else if (infrastructureMapping instanceof RancherKubernetesInfrastructureMapping) {
      RancherKubernetesInfrastructureMapping rancherKubernetesInfrastructureMapping =
          (RancherKubernetesInfrastructureMapping) infrastructureMapping;
      validateInfraMapping(rancherKubernetesInfrastructureMapping, skipValidation, null);
      if (isNotBlank(rancherKubernetesInfrastructureMapping.getNamespace())) {
        keyValuePairs.put("namespace", rancherKubernetesInfrastructureMapping.getNamespace());
      } else {
        rancherKubernetesInfrastructureMapping.setNamespace(DEFAULT);
        keyValuePairs.put("namespace", DEFAULT);
      }
      if (rancherKubernetesInfrastructureMapping.getClusterName() != null) {
        keyValuePairs.put("clusterName", rancherKubernetesInfrastructureMapping.getClusterName());
      } else {
        fieldsToRemove.add("clusterName");
      }
    } else if (infrastructureMapping instanceof GcpKubernetesInfrastructureMapping) {
      GcpKubernetesInfrastructureMapping gcpKubernetesInfrastructureMapping =
          (GcpKubernetesInfrastructureMapping) infrastructureMapping;
      validateInfraMapping(gcpKubernetesInfrastructureMapping, skipValidation, null);
      if (isNotEmpty(gcpKubernetesInfrastructureMapping.getClusterName())) {
        keyValuePairs.put("clusterName", gcpKubernetesInfrastructureMapping.getClusterName());
      } else {
        fieldsToRemove.add("clusterName");
      }
      if (isNotEmpty(gcpKubernetesInfrastructureMapping.getNamespace())) {
        keyValuePairs.put("namespace", gcpKubernetesInfrastructureMapping.getNamespace());
      } else {
        fieldsToRemove.add("namespace");
      }
    } else if (infrastructureMapping instanceof AzureKubernetesInfrastructureMapping) {
      AzureKubernetesInfrastructureMapping azureKubernetesInfrastructureMapping =
          (AzureKubernetesInfrastructureMapping) infrastructureMapping;
      validateInfraMapping(azureKubernetesInfrastructureMapping, skipValidation, null);
      keyValuePairs.put("clusterName", azureKubernetesInfrastructureMapping.getClusterName());
      keyValuePairs.put("subscriptionId", azureKubernetesInfrastructureMapping.getSubscriptionId());
      keyValuePairs.put("resourceGroup", azureKubernetesInfrastructureMapping.getResourceGroup());
      keyValuePairs.put("namespace",
          isNotBlank(azureKubernetesInfrastructureMapping.getNamespace())
              ? azureKubernetesInfrastructureMapping.getNamespace()
              : DEFAULT);
    } else if (infrastructureMapping instanceof AzureInfrastructureMapping) {
      AzureInfrastructureMapping azureInfrastructureMapping = (AzureInfrastructureMapping) infrastructureMapping;
      validateInfraMapping(azureInfrastructureMapping, skipValidation, null);

      if (isNotEmpty(azureInfrastructureMapping.getSubscriptionId())) {
        keyValuePairs.put("subscriptionId", azureInfrastructureMapping.getSubscriptionId());
      }

      if (isNotEmpty(azureInfrastructureMapping.getResourceGroup())) {
        keyValuePairs.put("resourceGroup", azureInfrastructureMapping.getResourceGroup());
      }

      if (isNotEmpty(azureInfrastructureMapping.getTags())) {
        keyValuePairs.put("tags", azureInfrastructureMapping.getTags());
      }

      keyValuePairs.put("usePublicDns", azureInfrastructureMapping.isUsePublicDns());

      if (DeploymentType.SSH.name().equals(infrastructureMapping.getDeploymentType())) {
        keyValuePairs.put("hostConnectionAttrs", azureInfrastructureMapping.getHostConnectionAttrs());
      }
      if (DeploymentType.WINRM.name().equals(infrastructureMapping.getDeploymentType())) {
        keyValuePairs.put("winRmConnectionAttributes", azureInfrastructureMapping.getWinRmConnectionAttributes());
      }

      if (!StringUtils.equals(((AzureInfrastructureMapping) savedInfraMapping).getWinRmConnectionAttributes(),
              azureInfrastructureMapping.getWinRmConnectionAttributes())) {
        getInfrastructureProviderByComputeProviderType(infrastructureMapping.getComputeProviderType())
            .updateHostConnAttrs(infrastructureMapping, azureInfrastructureMapping.getWinRmConnectionAttributes());
      }

    } else if (infrastructureMapping instanceof AwsInfrastructureMapping) {
      AwsInfrastructureMapping awsInfrastructureMapping = (AwsInfrastructureMapping) infrastructureMapping;
      validateInfraMapping(awsInfrastructureMapping, skipValidation, null);
      if (awsInfrastructureMapping.getRegion() != null) {
        keyValuePairs.put("region", awsInfrastructureMapping.getRegion());
      } else {
        fieldsToRemove.add("region");
      }
      if (isNotEmpty(awsInfrastructureMapping.getLoadBalancerId())) {
        keyValuePairs.put("loadBalancerId", awsInfrastructureMapping.getLoadBalancerId());
      } else {
        fieldsToRemove.add("loadBalancerId");
      }
      keyValuePairs.put("usePublicDns", awsInfrastructureMapping.isUsePublicDns());
      if (awsInfrastructureMapping.getHostConnectionType() != null) {
        keyValuePairs.put("hostConnectionType", awsInfrastructureMapping.getHostConnectionType());
      } else {
        fieldsToRemove.add("hostConnectionType");
      }
      keyValuePairs.put("setDesiredCapacity", awsInfrastructureMapping.isSetDesiredCapacity());
      if (awsInfrastructureMapping.getHostNameConvention() != null) {
        keyValuePairs.put("hostNameConvention", awsInfrastructureMapping.getHostNameConvention());
      } else {
        fieldsToRemove.add("hostNameConvention");
      }
      keyValuePairs.put("desiredCapacity", awsInfrastructureMapping.getDesiredCapacity());
      keyValuePairs.put("provisionInstances", awsInfrastructureMapping.isProvisionInstances());
      if (awsInfrastructureMapping.getAwsInstanceFilter() != null) {
        keyValuePairs.put("awsInstanceFilter", awsInfrastructureMapping.getAwsInstanceFilter());
      } else {
        fieldsToRemove.add("awsInstanceFilter");
      }
      if (isNotEmpty(awsInfrastructureMapping.getAutoScalingGroupName())) {
        keyValuePairs.put("autoScalingGroupName", awsInfrastructureMapping.getAutoScalingGroupName());
      } else {
        fieldsToRemove.add("autoScalingGroupName");
      }
    } else if (infrastructureMapping instanceof AwsLambdaInfraStructureMapping) {
      AwsLambdaInfraStructureMapping lambdaInfraStructureMapping =
          (AwsLambdaInfraStructureMapping) infrastructureMapping;
      validateInfraMapping(lambdaInfraStructureMapping, skipValidation, null);
      if (lambdaInfraStructureMapping.getRegion() != null) {
        keyValuePairs.put("region", lambdaInfraStructureMapping.getRegion());
      } else {
        fieldsToRemove.add("region");
      }
      if (lambdaInfraStructureMapping.getVpcId() == null) {
        if (isNotEmpty(lambdaInfraStructureMapping.getSubnetIds())
            || isNotEmpty(lambdaInfraStructureMapping.getSecurityGroupIds())) {
          throw new InvalidRequestException("Subnets or Security Groups can't be added without any VPC.");
        }
        fieldsToRemove.addAll(Arrays.asList("vpcId", "subnetIds", "securityGroupIds"));
      } else {
        keyValuePairs.put("vpcId", lambdaInfraStructureMapping.getVpcId());
        keyValuePairs.put("subnetIds", lambdaInfraStructureMapping.getSubnetIds());
        keyValuePairs.put("securityGroupIds", lambdaInfraStructureMapping.getSecurityGroupIds());
      }
      if (lambdaInfraStructureMapping.getRole() != null) {
        keyValuePairs.put("role", lambdaInfraStructureMapping.getRole());
      } else {
        fieldsToRemove.add("role");
      }

      // BLUEPRINT
      Map<String, Object> blueprints = infrastructureMapping.getBlueprints();
      if (blueprints == null) {
        fieldsToRemove.add(InfrastructureMappingKeys.blueprints);
      } else {
        keyValuePairs.put(InfrastructureMappingKeys.blueprints, blueprints);
      }

    } else if (infrastructureMapping instanceof PhysicalInfrastructureMapping) {
      PhysicalInfrastructureMapping physicalInfrastructureMapping =
          (PhysicalInfrastructureMapping) infrastructureMapping;
      validateInfraMapping(physicalInfrastructureMapping, skipValidation, null);
      if (isNotEmpty(physicalInfrastructureMapping.hosts())) {
        keyValuePairs.put("hosts", physicalInfrastructureMapping.hosts());
      } else {
        fieldsToRemove.add("hosts");
      }
      if (isNotEmpty(physicalInfrastructureMapping.getHostNames())) {
        keyValuePairs.put("hostNames", physicalInfrastructureMapping.getHostNames());
      } else {
        fieldsToRemove.add("hostNames");
      }
      if (isNotEmpty(physicalInfrastructureMapping.getLoadBalancerId())) {
        keyValuePairs.put("loadBalancerId", physicalInfrastructureMapping.getLoadBalancerId());
      } else {
        fieldsToRemove.add("loadBalancerId");
      }
    } else if (infrastructureMapping instanceof PhysicalInfrastructureMappingWinRm) {
      PhysicalInfrastructureMappingWinRm physicalInfrastructureMappingWinRm =
          (PhysicalInfrastructureMappingWinRm) infrastructureMapping;
      validateInfraMapping(infrastructureMapping, skipValidation, null);

      if (isNotEmpty(physicalInfrastructureMappingWinRm.getLoadBalancerId())) {
        keyValuePairs.put("loadBalancerId", physicalInfrastructureMappingWinRm.getLoadBalancerId());
      }

      if (isNotEmpty(physicalInfrastructureMappingWinRm.getHostNames())) {
        keyValuePairs.put("hostNames", physicalInfrastructureMappingWinRm.getHostNames());
      }

      if (!StringUtils.equals(((PhysicalInfrastructureMappingWinRm) savedInfraMapping).getWinRmConnectionAttributes(),
              physicalInfrastructureMappingWinRm.getWinRmConnectionAttributes())) {
        getInfrastructureProviderByComputeProviderType(physicalInfrastructureMappingWinRm.getComputeProviderType())
            .updateHostConnAttrs(
                infrastructureMapping, physicalInfrastructureMappingWinRm.getWinRmConnectionAttributes());

        keyValuePairs.put(
            "winRmConnectionAttributes", physicalInfrastructureMappingWinRm.getWinRmConnectionAttributes());
      }
    } else if (infrastructureMapping instanceof CodeDeployInfrastructureMapping) {
      CodeDeployInfrastructureMapping codeDeployInfrastructureMapping =
          (CodeDeployInfrastructureMapping) infrastructureMapping;
      if (codeDeployInfrastructureMapping.getRegion() != null) {
        keyValuePairs.put("region", codeDeployInfrastructureMapping.getRegion());
      } else {
        fieldsToRemove.add("region");
      }
      keyValuePairs.put("applicationName", codeDeployInfrastructureMapping.getApplicationName());
      keyValuePairs.put("deploymentGroup", codeDeployInfrastructureMapping.getDeploymentGroup());
      keyValuePairs.put("deploymentConfig", codeDeployInfrastructureMapping.getDeploymentConfig());
      if (codeDeployInfrastructureMapping.getHostNameConvention() != null) {
        keyValuePairs.put("hostNameConvention", codeDeployInfrastructureMapping.getHostNameConvention());
      } else {
        fieldsToRemove.add("hostNameConvention");
      }
    } else if (infrastructureMapping instanceof AwsAmiInfrastructureMapping) {
      AwsAmiInfrastructureMapping awsAmiInfrastructureMapping = (AwsAmiInfrastructureMapping) infrastructureMapping;
      AwsAmiInfrastructureMapping savedAwsAmiInfrastructureMapping = (AwsAmiInfrastructureMapping) savedInfraMapping;
      validateInfraMapping(awsAmiInfrastructureMapping, skipValidation, null);
      if (awsAmiInfrastructureMapping.getRegion() != null) {
        keyValuePairs.put("region", awsAmiInfrastructureMapping.getRegion());
      } else {
        fieldsToRemove.add("region");
      }
      if (awsAmiInfrastructureMapping.getHostNameConvention() != null) {
        keyValuePairs.put("hostNameConvention", awsAmiInfrastructureMapping.getHostNameConvention());
      } else {
        fieldsToRemove.add("hostNameConvention");
      }
      if (AmiDeploymentType.AWS_ASG == savedAwsAmiInfrastructureMapping.getAmiDeploymentType()) {
        if (awsAmiInfrastructureMapping.getAutoScalingGroupName() != null) {
          keyValuePairs.put("autoScalingGroupName", awsAmiInfrastructureMapping.getAutoScalingGroupName());
        } else {
          fieldsToRemove.add("autoScalingGroupName");
        }
        if (awsAmiInfrastructureMapping.getClassicLoadBalancers() != null) {
          keyValuePairs.put("classicLoadBalancers", awsAmiInfrastructureMapping.getClassicLoadBalancers());
        } else {
          fieldsToRemove.add("classicLoadBalancers");
        }
        if (awsAmiInfrastructureMapping.getTargetGroupArns() != null) {
          keyValuePairs.put("targetGroupArns", awsAmiInfrastructureMapping.getTargetGroupArns());
        } else {
          fieldsToRemove.add("targetGroupArns");
        }
        if (awsAmiInfrastructureMapping.getStageClassicLoadBalancers() != null) {
          keyValuePairs.put("stageClassicLoadBalancers", awsAmiInfrastructureMapping.getStageClassicLoadBalancers());
        } else {
          fieldsToRemove.add("stageClassicLoadBalancers");
        }
        if (awsAmiInfrastructureMapping.getStageTargetGroupArns() != null) {
          keyValuePairs.put("stageTargetGroupArns", awsAmiInfrastructureMapping.getStageTargetGroupArns());
        } else {
          fieldsToRemove.add("stageTargetGroupArns");
        }
      } else if (AmiDeploymentType.SPOTINST == savedAwsAmiInfrastructureMapping.getAmiDeploymentType()) {
        if (isNotEmpty(awsAmiInfrastructureMapping.getSpotinstCloudProvider())) {
          keyValuePairs.put(AwsAmiInfrastructureMappingKeys.spotinstCloudProvider,
              awsAmiInfrastructureMapping.getSpotinstCloudProvider());
        } else {
          fieldsToRemove.add(AwsAmiInfrastructureMappingKeys.spotinstCloudProvider);
        }
        if (isNotEmpty(awsAmiInfrastructureMapping.getSpotinstElastiGroupJson())) {
          keyValuePairs.put(AwsAmiInfrastructureMappingKeys.spotinstElastiGroupJson,
              awsAmiInfrastructureMapping.getSpotinstElastiGroupJson());
        } else {
          fieldsToRemove.add(AwsAmiInfrastructureMappingKeys.spotinstElastiGroupJson);
        }
      } else {
        throw new InvalidRequestException(format(
            "Unrecognized Ami deployment type: [%s]", savedAwsAmiInfrastructureMapping.getAmiDeploymentType().name()));
      }
    } else if (infrastructureMapping instanceof PcfInfrastructureMapping) {
      PcfInfrastructureMapping pcfInfrastructureMapping = (PcfInfrastructureMapping) infrastructureMapping;
      validateInfraMapping(pcfInfrastructureMapping, skipValidation, null);
      handlePcfInfraMapping(keyValuePairs, pcfInfrastructureMapping);

    } else if (infrastructureMapping instanceof AzureVMSSInfrastructureMapping) {
      AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping =
          (AzureVMSSInfrastructureMapping) infrastructureMapping;
      validateAzureVMSSInfraMapping(azureVMSSInfrastructureMapping);
      updateVMSSInfraFields(keyValuePairs, azureVMSSInfrastructureMapping);

    } else if (infrastructureMapping instanceof AzureWebAppInfrastructureMapping) {
      AzureWebAppInfrastructureMapping azureWebAppInfrastructureMapping =
          (AzureWebAppInfrastructureMapping) infrastructureMapping;
      validateAzureWebAppInfraMapping(azureWebAppInfrastructureMapping);
      updateWebAppInfraFields(keyValuePairs, azureWebAppInfrastructureMapping);
    }
    if (computeProviderSetting != null) {
      keyValuePairs.put("computeProviderName", computeProviderSetting.getName());
    }

    if (infrastructureMapping instanceof ContainerInfrastructureMapping) {
      String releaseName = fetchReleaseName(infrastructureMapping);
      if (isNotBlank(releaseName)) {
        keyValuePairs.put("releaseName", releaseName);
      }
    }

    Map<String, Object> finalKeyValuePairs = new HashMap<>();
    for (Entry<String, Object> e : keyValuePairs.entrySet()) {
      if (e.getValue() == null) {
        fieldsToRemove.add(e.getKey());
      } else {
        finalKeyValuePairs.put(e.getKey(), e.getValue());
      }
    }

    wingsPersistence.updateFields(
        infrastructureMapping.getClass(), infrastructureMapping.getUuid(), finalKeyValuePairs, fieldsToRemove);
    InfrastructureMapping updatedInfraMapping = get(infrastructureMapping.getAppId(), infrastructureMapping.getUuid());

    try {
      subject.fireInform(InfrastructureMappingServiceObserver::onUpdated, updatedInfraMapping);
      remoteObserverInformer.sendEvent(ReflectionUtils.getMethod(InfrastructureMappingServiceObserver.class,
                                           "onUpdated", InfrastructureMapping.class),
          InfrastructureMappingServiceImpl.class, updatedInfraMapping);
    } catch (Exception e) {
      log.error("Encountered exception while informing the observers of Infrastructure Mappings.", e);
    }

    return updatedInfraMapping;
  }

  private void updateVMSSInfraFields(
      Map<String, Object> keyValuePairs, AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping) {
    keyValuePairs.put(
        AzureVMSSInfrastructureMappingKeys.subscriptionId, azureVMSSInfrastructureMapping.getSubscriptionId());
    keyValuePairs.put(
        AzureVMSSInfrastructureMappingKeys.resourceGroupName, azureVMSSInfrastructureMapping.getResourceGroupName());
    keyValuePairs.put(
        AzureVMSSInfrastructureMappingKeys.baseVMSSName, azureVMSSInfrastructureMapping.getBaseVMSSName());
    keyValuePairs.put(AzureVMSSInfrastructureMappingKeys.userName, azureVMSSInfrastructureMapping.getUserName());
    keyValuePairs.put(
        AzureVMSSInfrastructureMappingKeys.vmssAuthType, azureVMSSInfrastructureMapping.getVmssAuthType());
    keyValuePairs.put(AzureVMSSInfrastructureMappingKeys.passwordSecretTextName,
        azureVMSSInfrastructureMapping.getPasswordSecretTextName());
  }

  private void updateWebAppInfraFields(
      Map<String, Object> keyValuePairs, AzureWebAppInfrastructureMapping azureWebAppInfrastructureMapping) {
    keyValuePairs.put(
        AzureWebAppsInfrastructureMappingKeys.subscriptionId, azureWebAppInfrastructureMapping.getSubscriptionId());
    keyValuePairs.put(
        AzureWebAppsInfrastructureMappingKeys.resourceGroup, azureWebAppInfrastructureMapping.getResourceGroup());
  }

  @Override
  public InfrastructureMapping update(InfrastructureMapping infrastructureMapping, String workflowExecutionId) {
    return update(infrastructureMapping, false, workflowExecutionId);
  }

  private List<String> getUniqueHostNames(PhysicalInfrastructureMappingBase physicalInfrastructureMapping) {
    return CollectionUtils.emptyIfNull(physicalInfrastructureMapping.getHostNames())
        .stream()
        .map(String::trim)
        .filter(StringUtils::isNotEmpty)
        .distinct()
        .collect(toList());
  }

  /**
   * This method gets the default name, checks if another entry exists with the same name, if exists, it parses and
   * extracts the chartVersion and creates a name with the next chartVersion.
   *
   * @param infraMapping
   */
  private void setAutoPopulatedName(InfrastructureMapping infraMapping) {
    String name = EntityNameValidator.getMappedString(infraMapping.getDefaultName());

    // We need to check if the name exists in case of auto generate, if it exists, we need to add a suffix to the name.
    PageRequest<InfrastructureMapping> pageRequest =
        aPageRequest()
            .addFilter(InfrastructureMappingKeys.appId, Operator.EQ, infraMapping.getAppId())
            .addFilter(InfrastructureMappingKeys.envId, Operator.EQ, infraMapping.getEnvId())
            .addFilter(InfrastructureMappingKeys.name, Operator.STARTS_WITH, name)
            .build();
    PageResponse<InfrastructureMapping> response = wingsPersistence.query(InfrastructureMapping.class, pageRequest);

    // If an entry exists with the given default name get the next revision number
    if (isNotEmpty(response)) {
      name = Utils.getNameWithNextRevision(
          response.getResponse().stream().map(InfrastructureMapping::getName).collect(toList()), name);
    }

    infraMapping.setName(name);
  }

  public void validateEcsInfraMapping(EcsInfrastructureMapping infraMapping) {
    SettingAttribute settingAttribute = settingsService.get(infraMapping.getComputeProviderSettingId());
    notNullCheck("SettingAttribute", settingAttribute, USER);

    if (isEmpty(infraMapping.getRegion())) {
      throw new InvalidRequestException("Region is required.");
    }
    if (isEmpty(infraMapping.getClusterName())) {
      throw new InvalidRequestException("Cluster Name is required.");
    }
    if (isEmpty(infraMapping.getLaunchType())) {
      throw new InvalidRequestException("Launch Type can't be empty");
    }

    Set<String> launchTypeAllowedValues = Arrays.stream(LaunchType.values()).map(Enum::name).collect(toSet());
    if (!launchTypeAllowedValues.contains(infraMapping.getLaunchType())) {
      throw new InvalidRequestException("Invalid LaunchType. Allowed values are FARGATE or EC2");
    }

    if (LaunchType.FARGATE.toString().equals(infraMapping.getLaunchType())) {
      validateFargateSpecificRequiredFields(infraMapping);
    }
  }

  private void validateFargateSpecificRequiredFields(EcsInfrastructureMapping infraMapping) {
    if (isEmpty(infraMapping.getExecutionRole())) {
      throw new InvalidRequestException("execution role is required with Fargate Launch Type.");
    }
    if (isEmpty(infraMapping.getVpcId())) {
      throw new InvalidRequestException("vpc-id is required with Fargate Launch Type.");
    }
    if (isEmpty(infraMapping.getSecurityGroupIds())) {
      throw new InvalidRequestException("security-groupIds are required with Fargate Launch Type.");
    }
    if (isEmpty(infraMapping.getSubnetIds())) {
      throw new InvalidRequestException("subnet-ids are required with Fargate Launch Type.");
    }
  }

  @VisibleForTesting
  public void validateAwsInfraMapping(AwsInfrastructureMapping infraMapping) {
    if (infraMapping.isProvisionInstances()) {
      if (isEmpty(infraMapping.getAutoScalingGroupName())) {
        throw new InvalidRequestException(
            "Auto Scaling group must not be empty when provision instances is true.", USER);
      }
      if (infraMapping.isSetDesiredCapacity() && infraMapping.getDesiredCapacity() <= 0) {
        throw new InvalidRequestException("Desired count must be greater than zero.", USER);
      }
    } else {
      if (infraMapping.getAwsInstanceFilter() == null) {
        throw new InvalidRequestException("Instance filter must not be null when provision instances is false.", USER);
      }
    }

    if (isEmpty(infraMapping.getHostConnectionType())) {
      throw new InvalidRequestException("Host Connection Type can't be empty", USER);
    }
  }

  @VisibleForTesting
  public void validateGcpInfraMapping(GcpKubernetesInfrastructureMapping infraMapping) {
    String clusterName = infraMapping.getClusterName();
    String namespace = infraMapping.getNamespace();
    if (isEmpty(clusterName)) {
      throw new InvalidRequestException("Cluster name can't be empty");
    }
    if (isEmpty(namespace)) {
      throw new InvalidRequestException("Namespace can't be empty");
    }
    if (DeploymentType.KUBERNETES.name().equals(infraMapping.getDeploymentType())) {
      if (isEmpty(infraMapping.getReleaseName())) {
        throw new InvalidRequestException("Release name can't be empty");
      }
    }
    KubernetesHelperService.validateNamespace(namespace);

    ContainerServiceParams containerServiceParams = getGcpContainerServiceParams(infraMapping);

    SyncTaskContext syncTaskContext = getSyncTaskContext(infraMapping);
    boolean useNewKubectlVersion =
        featureFlagService.isEnabled(FeatureName.NEW_KUBECTL_VERSION, infraMapping.getAccountId());
    try {
      delegateProxyFactory.get(ContainerService.class, syncTaskContext)
          .validate(containerServiceParams, useNewKubectlVersion);
    } catch (InvalidRequestException ex) {
      throw ex;
    } catch (Exception e) {
      log.warn(ExceptionUtils.getMessage(e), e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), USER);
    }
  }

  ContainerServiceParams getGcpContainerServiceParams(GcpKubernetesInfrastructureMapping infraMapping) {
    SettingAttribute settingAttribute = settingsService.get(infraMapping.getComputeProviderSettingId());
    notNullCheck(format("No cloud provider found with given id : [%s]", infraMapping.getComputeProviderSettingId()),
        settingAttribute, USER);
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue());
    return ContainerServiceParams.builder()
        .settingAttribute(settingAttribute)
        .encryptionDetails(encryptionDetails)
        .clusterName(infraMapping.getClusterName())
        .namespace(infraMapping.getNamespace())
        .masterUrl(infraMapping.getMasterUrl())
        .build();
  }

  private void validateAzureInfraMapping(AzureInfrastructureMapping infraMapping) {
    if (isEmpty(infraMapping.getComputeProviderType()) || !infraMapping.getComputeProviderType().equals(AZURE.name())) {
      throw new InvalidRequestException("Compute Provider type is empty or not correct for Azure Infra mapping.", USER);
    }

    if (isEmpty(infraMapping.getSubscriptionId())) {
      throw new InvalidRequestException("Subscription Id must not be empty for Azure Infra mapping.", USER);
    }

    DeploymentType deploymentType =
        serviceResourceService.getDeploymentType(infraMapping, null, infraMapping.getServiceId());
    if (!(SSH == deploymentType || WINRM == deploymentType)) {
      throw new InvalidRequestException(
          "Deployment type must not be empty and must be one of SSH/WINRM for Azure Infra mapping.", USER);
    }

    if (isEmpty(infraMapping.getInfraMappingType())) {
      throw new InvalidRequestException("Infra mapping type must not be empty for Azure Infra mapping.", USER);
    }
  }

  private void validateAzureKubernetesInfraMapping(AzureKubernetesInfrastructureMapping infraMapping) {
    if (isNotEmpty(infraMapping.getProvisionerId())) {
      return;
    }
    KubernetesHelperService.validateNamespace(infraMapping.getNamespace());

    ContainerServiceParams containerServiceParams = getAzureContainerServiceParams(infraMapping);
    SyncTaskContext syncTaskContext = getSyncTaskContext(infraMapping);
    boolean useNewKubectlVersion =
        featureFlagService.isEnabled(FeatureName.NEW_KUBECTL_VERSION, infraMapping.getAccountId());
    try {
      delegateProxyFactory.get(ContainerService.class, syncTaskContext)
          .validate(containerServiceParams, useNewKubectlVersion);
    } catch (Exception e) {
      log.warn(ExceptionUtils.getMessage(e), e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), USER);
    }
  }

  ContainerServiceParams getAzureContainerServiceParams(AzureKubernetesInfrastructureMapping infraMapping) {
    SettingAttribute settingAttribute = settingsService.get(infraMapping.getComputeProviderSettingId());
    notNullCheck("SettingAttribute", settingAttribute, USER);
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue());

    return ContainerServiceParams.builder()
        .settingAttribute(settingAttribute)
        .encryptionDetails(encryptionDetails)
        .clusterName(infraMapping.getClusterName())
        .subscriptionId(infraMapping.getSubscriptionId())
        .resourceGroup(infraMapping.getResourceGroup())
        .namespace(infraMapping.getNamespace())
        .masterUrl(infraMapping.getMasterUrl())
        .build();
  }

  SyncTaskContext getSyncTaskContext(InfrastructureMapping infraMapping) {
    Application app = appService.get(infraMapping.getAppId());
    return SyncTaskContext.builder()
        .accountId(app.getAccountId())
        .appId(app.getUuid())
        .envId(infraMapping.getEnvId())
        .infrastructureMappingId(infraMapping.getUuid())
        .infraStructureDefinitionId(infraMapping.getInfrastructureDefinitionId())
        .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
        .build();
  }

  private void validateDirectKubernetesInfraMapping(
      DirectKubernetesInfrastructureMapping infraMapping, String workflowExecutionId) {
    SettingAttribute settingAttribute = settingsService.get(infraMapping.getComputeProviderSettingId());
    String namespace = infraMapping.getNamespace();

    if (isNotEmpty(infraMapping.getProvisionerId())) {
      return;
    }

    KubernetesHelperService.validateNamespace(namespace);

    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, workflowExecutionId);

    SyncTaskContext syncTaskContext = getSyncTaskContext(infraMapping);
    ContainerServiceParams containerServiceParams = ContainerServiceParams.builder()
                                                        .settingAttribute(settingAttribute)
                                                        .encryptionDetails(encryptionDetails)
                                                        .namespace(namespace)
                                                        .build();
    boolean useNewKubectlVersion =
        featureFlagService.isEnabled(FeatureName.NEW_KUBECTL_VERSION, infraMapping.getAccountId());
    try {
      delegateProxyFactory.get(ContainerService.class, syncTaskContext)
          .validate(containerServiceParams, useNewKubectlVersion);
    } catch (Exception e) {
      log.warn(ExceptionUtils.getMessage(e), e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), USER);
    }
  }

  private void validateRancherKubernetesInfraMapping(
      RancherKubernetesInfrastructureMapping infraMapping, String workflowExecutionId) {
    SettingAttribute settingAttribute = settingsService.get(infraMapping.getComputeProviderSettingId());
    String namespace = infraMapping.getNamespace();

    if (isNotEmpty(infraMapping.getProvisionerId())) {
      return;
    }

    KubernetesHelperService.validateNamespace(namespace);

    try {
      RancherConfig rancherConfig = (RancherConfig) settingAttribute.getValue();
      rancherHelperService.validateRancherConfig(rancherConfig);
    } catch (Exception e) {
      log.warn(ExceptionUtils.getMessage(e), e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), USER);
    }
  }

  @Override
  public InfrastructureMapping get(String appId, String infraMappingId) {
    return wingsPersistence.getWithAppId(InfrastructureMapping.class, appId, infraMappingId);
  }

  @Override
  public List<InfrastructureMapping> get(String appId) {
    PageRequest<InfrastructureMapping> pageRequest = new PageRequest<>();
    pageRequest.addFilter(InfrastructureMappingKeys.appId, SearchFilter.Operator.EQ, appId);
    PageResponse<InfrastructureMapping> result = list(pageRequest);
    return result.getResponse();
  }

  void handlePcfInfraMapping(Map<String, Object> keyValuePairs, PcfInfrastructureMapping pcfInfrastructureMapping) {
    keyValuePairs.put("organization", pcfInfrastructureMapping.getOrganization());
    keyValuePairs.put("space", pcfInfrastructureMapping.getSpace());
    keyValuePairs.put("tempRouteMap",
        pcfInfrastructureMapping.getTempRouteMap() == null ? Collections.EMPTY_LIST
                                                           : pcfInfrastructureMapping.getTempRouteMap());
    keyValuePairs.put("routeMaps",
        pcfInfrastructureMapping.getRouteMaps() == null ? Collections.EMPTY_LIST
                                                        : pcfInfrastructureMapping.getRouteMaps());
  }

  void handleEcsInfraMapping(Map<String, Object> keyValuePairs, Set<String> fieldsToRemove,
      EcsInfrastructureMapping ecsInfrastructureMapping) {
    if (isNotEmpty(ecsInfrastructureMapping.getClusterName())) {
      keyValuePairs.put("clusterName", ecsInfrastructureMapping.getClusterName());
    } else {
      fieldsToRemove.add("clusterName");
    }

    if (isNotEmpty(ecsInfrastructureMapping.getRegion())) {
      keyValuePairs.put("region", ecsInfrastructureMapping.getRegion());
    } else {
      fieldsToRemove.add("region");
    }

    if (isNotEmpty(ecsInfrastructureMapping.getLaunchType())) {
      keyValuePairs.put("launchType", ecsInfrastructureMapping.getLaunchType());
    } else {
      fieldsToRemove.add("launchType");
    }
    keyValuePairs.put("assignPublicIp", ecsInfrastructureMapping.isAssignPublicIp());
    keyValuePairs.put(
        "vpcId", ecsInfrastructureMapping.getVpcId() == null ? EMPTY : ecsInfrastructureMapping.getVpcId());
    keyValuePairs.put("subnetIds",
        ecsInfrastructureMapping.getSubnetIds() == null ? Collections.EMPTY_LIST
                                                        : ecsInfrastructureMapping.getSubnetIds());
    keyValuePairs.put("securityGroupIds",
        ecsInfrastructureMapping.getSecurityGroupIds() == null ? Collections.EMPTY_LIST
                                                               : ecsInfrastructureMapping.getSecurityGroupIds());
    keyValuePairs.put("executionRole",
        ecsInfrastructureMapping.getExecutionRole() == null ? EMPTY : ecsInfrastructureMapping.getExecutionRole());
  }

  private void validatePhysicalInfrastructureMapping(PhysicalInfrastructureMapping infraMapping) {
    if (isEmpty(infraMapping.getHostNames())) {
      throw new InvalidRequestException("Host names must not be empty", USER);
    }
  }

  private void validatePhysicalInfrastructureMappingWinRm(PhysicalInfrastructureMappingWinRm infraMapping) {
    if (isEmpty(infraMapping.getHostNames())) {
      throw new InvalidRequestException("Host names must not be empty", USER);
    }

    SettingAttribute settingAttribute = settingsService.get(infraMapping.getComputeProviderSettingId());
    notNullCheck("ComputeProviderSettingAttribute", settingAttribute);

    settingAttribute = settingsService.get(infraMapping.getWinRmConnectionAttributes());
    notNullCheck("WinRmConnectionAttributes", settingAttribute);
  }

  private void validatePcfInfrastructureMapping(PcfInfrastructureMapping infraMapping) {
    if (StringUtils.isBlank(infraMapping.getOrganization()) || StringUtils.isBlank(infraMapping.getSpace())) {
      log.error("For PCFInfraMapping, Org and Space value cant be null");
      throw new InvalidRequestException("Host names must be unique", USER);
    }

    SettingAttribute settingAttribute = settingsService.get(infraMapping.getComputeProviderSettingId());
    notNullCheck("ComputeProviderSettingAttribute", settingAttribute, USER);
  }

  @VisibleForTesting
  public void validateAwsLambdaInfrastructureMapping(AwsLambdaInfraStructureMapping infraMapping) {
    if (StringUtils.isEmpty(infraMapping.getRegion())) {
      throw new InvalidRequestException("Region is mandatory");
    }
    if (StringUtils.isEmpty(infraMapping.getRole())) {
      throw new InvalidRequestException("IAM Role is mandatory");
    }
  }

  @VisibleForTesting
  public void validateAwsAmiInfrastructureMapping(AwsAmiInfrastructureMapping infrastructureMapping) {
    if (isEmpty(infrastructureMapping.getRegion())) {
      throw new InvalidRequestException("Region is mandatory");
    }
    if (AmiDeploymentType.AWS_ASG == infrastructureMapping.getAmiDeploymentType()
        && isEmpty(infrastructureMapping.getAutoScalingGroupName())) {
      throw new InvalidRequestException("Auto Scaling Group is mandatory");
    }
  }

  @VisibleForTesting
  void validateAzureVMSSInfraMapping(AzureVMSSInfrastructureMapping infrastructureMapping) {
    SettingAttribute settingAttribute = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    notNullCheck("SettingAttribute", settingAttribute, USER);

    if (isEmpty(infrastructureMapping.getBaseVMSSName())) {
      throw new InvalidRequestException("Base VMSS name should not be empty");
    }

    if (isEmpty(infrastructureMapping.getSubscriptionId())) {
      throw new InvalidRequestException("Subscription Id should not be empty");
    }

    if (isEmpty(infrastructureMapping.getResourceGroupName())) {
      throw new InvalidRequestException("Resource group name should not be empty");
    }

    if (isEmpty(infrastructureMapping.getUserName())) {
      throw new InvalidRequestException("User name should not be empty");
    }

    if (infrastructureMapping.getVmssAuthType() == null) {
      throw new InvalidRequestException("Auth Type should not be empty");
    }

    if (PASSWORD == infrastructureMapping.getVmssAuthType()) {
      if (isEmpty(infrastructureMapping.getPasswordSecretTextName())) {
        throw new InvalidRequestException("Password should not be empty");
      }
    }

    if (SSH_PUBLIC_KEY == infrastructureMapping.getVmssAuthType()) {
      if (isEmpty(infrastructureMapping.getHostConnectionAttrs())) {
        throw new InvalidRequestException("SSH Public Key should not be empty");
      }
    }
  }

  @VisibleForTesting
  void validateAzureWebAppInfraMapping(AzureWebAppInfrastructureMapping infrastructureMapping) {
    SettingAttribute settingAttribute = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    notNullCheck("SettingAttribute", settingAttribute, USER);

    if (isEmpty(infrastructureMapping.getSubscriptionId())) {
      throw new InvalidRequestException("Subscription Id should not be empty");
    }

    if (isEmpty(infrastructureMapping.getResourceGroup())) {
      throw new InvalidRequestException("Subscription Id should not be empty");
    }
  }

  @Override
  public void delete(String appId, String infraMappingId) {
    delete(appId, infraMappingId, false, false);
  }

  @Override
  public void deleteByYamlGit(String appId, String infraMappingId, boolean syncFromGit) {
    delete(appId, infraMappingId, false, syncFromGit);
  }

  private void delete(String appId, String infraMappingId, boolean forceDelete, boolean syncFromGit) {
    InfrastructureMapping infrastructureMapping = get(appId, infraMappingId);
    if (infrastructureMapping == null) {
      return;
    }

    if (!forceDelete) {
      ensureSafeToDelete(appId, infraMappingId);
    }

    prune(appId, infraMappingId);
  }

  private void prune(String appId, String infraMappingId) {
    pruneQueue.send(new PruneEvent(InfrastructureMapping.class, appId, infraMappingId));
    wingsPersistence.delete(InfrastructureMapping.class, appId, infraMappingId);
  }

  @Override
  public void pruneByEnvironment(String appId, String envId) {
    List<InfrastructureMapping> infrastructureMappings = wingsPersistence.createQuery(InfrastructureMapping.class)
                                                             .filter(InfrastructureMappingKeys.appId, appId)
                                                             .filter(InfrastructureMappingKeys.envId, envId)
                                                             .project(InfrastructureMappingKeys.appId, true)
                                                             .project(InfrastructureMappingKeys.envId, true)
                                                             .project(InfrastructureMappingKeys.name, true)
                                                             .project(InfrastructureMapping.ID, true)
                                                             .asList();
    for (InfrastructureMapping infrastructureMapping : infrastructureMappings) {
      prune(appId, infrastructureMapping.getUuid());
      auditServiceHelper.reportDeleteForAuditing(appId, infrastructureMapping);
    }
  }

  @Override
  public void pruneByInfrastructureProvisioner(String appId, String infrastructureProvisionerId) {
    List<Key<InfrastructureMapping>> keys =
        wingsPersistence.createQuery(InfrastructureMapping.class)
            .filter(InfrastructureMapping.APP_ID_KEY, appId)
            .filter(InfrastructureMapping.PROVISIONER_ID_KEY, infrastructureProvisionerId)
            .asKeyList();
    for (Key<InfrastructureMapping> key : keys) {
      prune(appId, (String) key.getId());
    }
  }

  @Override
  public void pruneDescendingEntities(@NotEmpty String appId, @NotEmpty String infraMappingId) {
    List<OwnedByInfrastructureMapping> services = ServiceClassLocator.descendingServices(
        this, InfrastructureMappingServiceImpl.class, OwnedByInfrastructureMapping.class);
    PruneEntityListener.pruneDescendingEntities(
        services, descending -> descending.pruneByInfrastructureMapping(appId, infraMappingId));
  }

  @Override
  public void ensureSafeToDelete(@NotEmpty String appId, @NotEmpty String infraMappingId) {
    List<String> referencingWorkflowNames =
        workflowService.obtainWorkflowNamesReferencedByServiceInfrastructure(appId, infraMappingId);

    if (!referencingWorkflowNames.isEmpty()) {
      throw new InvalidRequestException(
          format("Service Infrastructure %s is referenced by %s %s [%s].", infraMappingId,
              referencingWorkflowNames.size(), plural("workflow", referencingWorkflowNames.size()),
              Joiner.on(", ").join(referencingWorkflowNames)),
          USER);
    }

    List<String> refPipelines = pipelineService.obtainPipelineNamesReferencedByTemplatedEntity(appId, infraMappingId);
    if (isNotEmpty(refPipelines)) {
      throw new InvalidRequestException(
          format("Service Infrastructure is referenced by %d %s [%s] as a workflow variable.", refPipelines.size(),
              plural("pipeline", refPipelines.size()), Joiner.on(", ").join(refPipelines)),
          USER);
    }

    List<String> refTriggers = triggerService.obtainTriggerNamesReferencedByTemplatedEntityId(appId, infraMappingId);
    if (isNotEmpty(refTriggers)) {
      throw new InvalidRequestException(
          format("Service Infrastructure is referenced by %d %s [%s] as a workflow variable.", refTriggers.size(),
              plural("trigger", refTriggers.size()), Joiner.on(", ").join(refTriggers)),
          USER);
    }
  }

  @Override
  public void deleteByServiceTemplate(String appId, String serviceTemplateId) {
    List<Key<InfrastructureMapping>> keys = wingsPersistence.createQuery(InfrastructureMapping.class)
                                                .filter(InfrastructureMappingKeys.appId, appId)
                                                .filter(InfrastructureMappingKeys.serviceTemplateId, serviceTemplateId)
                                                .asKeyList();
    keys.forEach(key -> delete(appId, (String) key.getId(), true, false));
  }

  @Override
  public Map<String, Object> getInfraMappingStencils(String appId) {
    return stencilPostProcessor
        .postProcess(Lists.newArrayList(InfrastructureMappingType.values()), appId, new HashMap<>())
        .stream()
        .collect(toMap(Stencil::getName, identity()));
  }

  @Override
  public List<ServiceInstance> selectServiceInstances(
      String appId, String infraMappingId, String workflowExecutionId, ServiceInstanceSelectionParams selectionParams) {
    InfrastructureMapping infrastructureMapping = get(appId, infraMappingId);
    notNullCheck("Infra Mapping", infrastructureMapping);

    List<Host> hosts;
    if (infrastructureMapping instanceof AwsInfrastructureMapping
        && ((AwsInfrastructureMapping) infrastructureMapping).isProvisionInstances()) {
      hosts = getAutoScaleGroupNodes(appId, infraMappingId, workflowExecutionId);
    } else {
      hosts = listHosts(infrastructureMapping, workflowExecutionId, selectionParams)
                  .stream()
                  .filter(host
                      -> !selectionParams.isSelectSpecificHosts()
                          || selectionParams.getHostNames().contains(host.getPublicDns()))
                  .collect(toList());
    }

    int count =
        selectionParams.isSelectSpecificHosts() ? selectionParams.getHostNames().size() : selectionParams.getCount();
    List<String> excludedServiceInstanceIds = selectionParams.getExcludedServiceInstanceIds();
    return syncHostsAndUpdateInstances(infrastructureMapping, hosts)
        .stream()
        .filter(serviceInstance -> !excludedServiceInstanceIds.contains(serviceInstance.getUuid()))
        .limit(count)
        .collect(toList());
  }

  @Override
  public List<Host> listHosts(String appId, String infrastructureMappingId) {
    InfrastructureMapping infrastructureMapping = get(appId, infrastructureMappingId);
    notNullCheck("Infra Mapping", infrastructureMapping);
    return listHosts(infrastructureMapping, null, null);
  }

  private List<Host> listHosts(InfrastructureMapping infrastructureMapping, String workflowExecutionId,
      ServiceInstanceSelectionParams selectionParams) {
    if (infrastructureMapping instanceof PhysicalInfrastructureMapping) {
      PhysicalInfrastructureMapping pyInfraMapping = (PhysicalInfrastructureMapping) infrastructureMapping;
      if (isNotEmpty(pyInfraMapping.getProvisionerId())) {
        if (isNotEmpty(pyInfraMapping.hosts())) {
          if (featureFlagService.isEnabled(DEPLOY_TO_INLINE_HOSTS, infrastructureMapping.getAccountId())
              && isNotEmpty(selectionParams.getHostNames())) {
            return selectionParams.getHostNames()
                .stream()
                .map(hostName -> buildHost(pyInfraMapping, hostName))
                .collect(toList());
          }
          return pyInfraMapping.hosts();
        } else {
          throw new InvalidRequestException(
              "Hosts are not present in infra. Make sure to add provisioner step in the Workflow/Pipeline.");
        }
      }
      List<String> hostNames = pyInfraMapping.getHostNames()
                                   .stream()
                                   .map(String::trim)
                                   .filter(StringUtils::isNotEmpty)
                                   .distinct()
                                   .collect(toList());
      if (featureFlagService.isEnabled(DEPLOY_TO_INLINE_HOSTS, infrastructureMapping.getAccountId())) {
        addSpecificHosts(selectionParams, hostNames);
      }
      return hostNames.stream().map(hostName -> buildHost(pyInfraMapping, hostName)).collect(toList());
    } else if (infrastructureMapping instanceof PhysicalInfrastructureMappingWinRm) {
      PhysicalInfrastructureMappingWinRm pyInfraMappingWinRm =
          (PhysicalInfrastructureMappingWinRm) infrastructureMapping;
      List<String> hostNames = pyInfraMappingWinRm.getHostNames()
                                   .stream()
                                   .map(String::trim)
                                   .filter(StringUtils::isNotEmpty)
                                   .distinct()
                                   .collect(toList());
      if (featureFlagService.isEnabled(DEPLOY_TO_INLINE_HOSTS, infrastructureMapping.getAccountId())) {
        addSpecificHosts(selectionParams, hostNames);
      }
      return hostNames.stream()
          .map(hostName
              -> aHost()
                     .withHostName(hostName)
                     .withPublicDns(hostName)
                     .withAppId(pyInfraMappingWinRm.getAppId())
                     .withEnvId(pyInfraMappingWinRm.getEnvId())
                     .withInfraMappingId(pyInfraMappingWinRm.getUuid())
                     .withInfraDefinitionId(pyInfraMappingWinRm.getInfrastructureDefinitionId())
                     .withWinrmConnAttr(pyInfraMappingWinRm.getWinRmConnectionAttributes())
                     .withServiceTemplateId(serviceTemplateHelper.fetchServiceTemplateId(pyInfraMappingWinRm))
                     .build())
          .collect(toList());
    } else if (infrastructureMapping instanceof AwsInfrastructureMapping) {
      AwsInfrastructureMapping awsInfraMapping = (AwsInfrastructureMapping) infrastructureMapping;
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      SettingAttribute computeProviderSetting = settingsService.get(awsInfraMapping.getComputeProviderSettingId());
      notNullCheck("Compute Provider", computeProviderSetting);
      return infrastructureProvider
          .listHosts(awsInfraMapping, computeProviderSetting,
              secretManager.getEncryptionDetails(
                  (EncryptableSetting) computeProviderSetting.getValue(), null, workflowExecutionId),
              new PageRequest<>())
          .getResponse();
    } else if (infrastructureMapping instanceof AzureInfrastructureMapping) {
      AzureInfrastructureMapping azureInfraMapping = (AzureInfrastructureMapping) infrastructureMapping;
      SettingAttribute computeProviderSetting = settingsService.get(azureInfraMapping.getComputeProviderSettingId());
      notNullCheck("Compute Provider", computeProviderSetting);
      DeploymentType deploymentType =
          serviceResourceService.getDeploymentType(azureInfraMapping, null, azureInfraMapping.getServiceId());

      return azureHelperService.listHosts(azureInfraMapping, computeProviderSetting,
          secretManager.getEncryptionDetails(
              (EncryptableSetting) computeProviderSetting.getValue(), null, workflowExecutionId),
          deploymentType);
    } else {
      throw new InvalidRequestException(
          "Unsupported infrastructure mapping: " + infrastructureMapping.getClass().getName());
    }
  }

  private Host buildHost(PhysicalInfrastructureMapping pyInfraMapping, String hostName) {
    return aHost()
        .withHostName(hostName)
        .withPublicDns(hostName)
        .withAppId(pyInfraMapping.getAppId())
        .withEnvId(pyInfraMapping.getEnvId())
        .withInfraMappingId(pyInfraMapping.getUuid())
        .withInfraDefinitionId(pyInfraMapping.getInfrastructureDefinitionId())
        .withHostConnAttr(pyInfraMapping.getHostConnectionAttrs())
        .withServiceTemplateId(serviceTemplateHelper.fetchServiceTemplateId(pyInfraMapping))
        .build();
  }

  private void addSpecificHosts(ServiceInstanceSelectionParams selectionParams, List<String> hostNames) {
    List<String> specificHosts =
        selectionParams.getHostNames().stream().filter(s -> !hostNames.contains(s)).collect(toList());
    hostNames.addAll(specificHosts);
  }

  private List<ServiceInstance> syncHostsAndUpdateInstances(
      InfrastructureMapping infrastructureMapping, List<Host> hosts) {
    InfrastructureProvider infrastructureProvider =
        getInfrastructureProviderByComputeProviderType(infrastructureMapping.getComputeProviderType());
    ServiceTemplate serviceTemplate = serviceTemplateHelper.fetchServiceTemplate(infrastructureMapping);

    List<Host> savedHosts = hosts.stream().map(infrastructureProvider::saveHost).collect(toList());
    return serviceInstanceService.updateInstanceMappings(serviceTemplate, infrastructureMapping, savedHosts);
  }

  private AwsConfig validateAndGetAwsConfig(SettingAttribute computeProviderSetting) {
    if (computeProviderSetting == null || !(computeProviderSetting.getValue() instanceof AwsConfig)) {
      throw new InvalidRequestException("InvalidConfiguration", USER);
    }
    return (AwsConfig) computeProviderSetting.getValue();
  }

  private AzureConfig validateAndGetAzureConfig(SettingAttribute computeProviderSetting) {
    if (computeProviderSetting == null || !(computeProviderSetting.getValue() instanceof AzureConfig)) {
      throw new InvalidRequestException("No cloud provider exist or not of type Azure", USER);
    }
    return (AzureConfig) computeProviderSetting.getValue();
  }

  @Override
  public List<String> listClusters(String appId, String deploymentType, String computeProviderId, String region) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    String type = computeProviderSetting.getValue().getType();
    if (AWS.name().equals(type)) {
      AwsConfig awsConfig = validateAndGetAwsConfig(computeProviderSetting);
      return awsEcsHelperServiceManager.listClusters(
          awsConfig, secretManager.getEncryptionDetails(awsConfig, appId, null), region, appId);
    } else if (GCP.name().equals(type)) {
      GcpInfrastructureProvider infrastructureProvider =
          (GcpInfrastructureProvider) getInfrastructureProviderByComputeProviderType(GCP.name());
      return infrastructureProvider.listClusterNames(computeProviderSetting,
          secretManager.getEncryptionDetails((EncryptableSetting) computeProviderSetting.getValue(), null, null));
    }
    return emptyList();
  }

  @Override
  public List<String> listRegions(String appId, String deploymentType, String computeProviderId) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      try {
        AwsConfig awsConfig = validateAndGetAwsConfig(computeProviderSetting);
        return awsEc2HelperServiceManager.listRegions(
            awsConfig, secretManager.getEncryptionDetails(awsConfig, appId, null), appId);
      } catch (Exception e) {
        log.warn(ExceptionUtils.getMessage(e), e);
        throw new InvalidRequestException(ExceptionUtils.getMessage(e), USER);
      }
    }
    return emptyList();
  }

  @Override
  public List<String> listInstanceTypes(String appId, String deploymentType, String computeProviderId) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      return infrastructureProvider.listInstanceTypes(computeProviderSetting);
    }
    return emptyList();
  }

  @Override
  public List<String> listInstanceRoles(String appId, String deploymentType, String computeProviderId) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      return infrastructureProvider.listIAMInstanceRoles(computeProviderSetting, appId);
    }
    return emptyList();
  }

  @Override
  public Map<String, String> listAwsIamRoles(String appId, String infraMappingId) {
    InfrastructureMapping infrastructureMapping = get(appId, infraMappingId);
    notNullCheck("Service Infrastructure", infrastructureMapping);
    return listAllRoles(appId, infrastructureMapping.getComputeProviderSettingId());
  }

  @Override
  public Set<String> listTags(String appId, String computeProviderId, String region) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      try {
        AwsConfig awsConfig = validateAndGetAwsConfig(computeProviderSetting);
        return awsEc2HelperServiceManager.listTags(
            awsConfig, secretManager.getEncryptionDetails(awsConfig, appId, null), region, appId);
      } catch (Exception e) {
        log.warn(ExceptionUtils.getMessage(e), e);
        throw new InvalidRequestException(ExceptionUtils.getMessage(e), USER);
      }
    }
    return Collections.emptySet();
  }

  @Override
  public Set<String> listAzureTags(String appId, String computeProviderId, String subscriptionId) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (AZURE.name().equals(computeProviderSetting.getValue().getType())) {
      try {
        AzureConfig azureConfig = validateAndGetAzureConfig(computeProviderSetting);
        return azureDelegateHelperService.listTagsBySubscription(
            subscriptionId, azureConfig, secretManager.getEncryptionDetails(azureConfig, null, null));
      } catch (Exception e) {
        log.warn(ExceptionUtils.getMessage(e), e);
        throw new InvalidRequestException(ExceptionUtils.getMessage(e), USER);
      }
    }
    return Collections.emptySet();
  }

  @Override
  public Set<String> listAzureResourceGroups(String appId, String computeProviderId, String subscriptionId) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (AZURE.name().equals(computeProviderSetting.getValue().getType())) {
      try {
        AzureConfig azureConfig = validateAndGetAzureConfig(computeProviderSetting);
        return azureDelegateHelperService.listResourceGroups(
            azureConfig, secretManager.getEncryptionDetails(azureConfig, null, null), subscriptionId);
      } catch (Exception e) {
        log.warn(ExceptionUtils.getMessage(e), e);
        throw new InvalidRequestException(ExceptionUtils.getMessage(e), USER);
      }
    }
    return Collections.emptySet();
  }

  @Override
  public List<String> listAutoScalingGroups(String appId, String computeProviderId, String region) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      return infrastructureProvider.listAutoScalingGroups(computeProviderSetting, region, appId);
    }
    return emptyList();
  }

  @Override
  public Map<String, String> listAlbTargetGroups(String appId, String computeProviderId, String region) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      return infrastructureProvider.listTargetGroups(computeProviderSetting, region, null, appId);
    }
    return emptyMap();
  }

  @Override
  public Map<String, String> listAllRoles(String appId, String computeProviderId) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsConfig awsConfig = validateAndGetAwsConfig(computeProviderSetting);
      return awsIamHelperServiceManager.listIamRoles(
          awsConfig, secretManager.getEncryptionDetails(awsConfig, appId, null), appId);
    }
    return Collections.emptyMap();
  }

  @Override
  @VisibleForTesting
  public List<String> getVPCIds(String appId, String computeProviderId, String region) {
    return CollectionUtils.emptyIfNull(listVPC(appId, computeProviderId, region))
        .stream()
        .map(vpc -> vpc.getId())
        .collect(Collectors.toList());
  }

  @Override
  public List<AwsVPC> listVPC(String appId, String computeProviderId, String region) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      try {
        AwsConfig awsConfig = validateAndGetAwsConfig(computeProviderSetting);
        return awsEc2HelperServiceManager.listVPCs(
            awsConfig, secretManager.getEncryptionDetails(awsConfig, appId, null), region, appId);
      } catch (Exception e) {
        log.warn(ExceptionUtils.getMessage(e), e);
        throw new InvalidRequestException(ExceptionUtils.getMessage(e), USER);
      }
    }
    return emptyList();
  }

  @Override
  public List<String> listOrganizationsForPcf(String appId, String computeProviderId) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (computeProviderSetting == null || !(computeProviderSetting.getValue() instanceof PcfConfig)) {
      throw new InvalidRequestException("InvalidConfiguration", USER);
    }
    return pcfHelperService.listOrganizations((PcfConfig) computeProviderSetting.getValue());
  }

  @Override
  public List<String> listSpacesForPcf(String appId, String computeProviderId, String organization) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (computeProviderSetting == null || !(computeProviderSetting.getValue() instanceof PcfConfig)) {
      throw new InvalidRequestException("InvalidConfiguration", USER);
    }
    return pcfHelperService.listSpaces((PcfConfig) computeProviderSetting.getValue(), organization);
  }

  @Override
  public List<String> lisRouteMapsForPcf(String appId, String computeProviderId, String organization, String spaces) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (computeProviderSetting == null || !(computeProviderSetting.getValue() instanceof PcfConfig)) {
      throw new InvalidRequestException("InvalidConfiguration", USER);
    }

    return pcfHelperService.listRoutes((PcfConfig) computeProviderSetting.getValue(), organization, spaces);
  }

  @Override
  public String createRoute(String appId, String computeProviderId, String organization, String spaces, String host,
      String domain, String path, boolean tcpRoute, boolean useRandomPort, String port) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (computeProviderSetting == null || !(computeProviderSetting.getValue() instanceof PcfConfig)) {
      throw new InvalidRequestException("InvalidConfiguration", USER);
    }

    Integer portNum = StringUtils.isBlank(port) ? null : Integer.parseInt(port);
    return pcfHelperService.createRoute((PcfConfig) computeProviderSetting.getValue(), organization, spaces, host,
        domain, path, tcpRoute, useRandomPort, portNum);
  }

  @Override
  public List<String> getSGIds(String appId, String computeProviderId, String region, List<String> vpcIds) {
    return CollectionUtils.emptyIfNull(listSecurityGroups(appId, computeProviderId, region, vpcIds))
        .stream()
        .map(sg -> sg.getId())
        .collect(Collectors.toList());
  }

  @Override
  public List<AwsSecurityGroup> listSecurityGroups(
      String appId, String computeProviderId, String region, List<String> vpcIds) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      try {
        AwsConfig awsConfig = validateAndGetAwsConfig(computeProviderSetting);
        return awsEc2HelperServiceManager.listSGs(
            awsConfig, secretManager.getEncryptionDetails(awsConfig, appId, null), region, vpcIds, appId);
      } catch (Exception e) {
        log.warn(ExceptionUtils.getMessage(e), e);
        throw new InvalidRequestException(ExceptionUtils.getMessage(e), USER);
      }
    }
    return emptyList();
  }

  @Override
  @VisibleForTesting
  public List<String> getSubnetIds(String appId, String computeProviderId, String region, List<String> vpcIds) {
    return CollectionUtils.emptyIfNull(listSubnets(appId, computeProviderId, region, vpcIds))
        .stream()
        .map(subnet -> subnet.getId())
        .collect(Collectors.toList());
  }

  @Override
  public List<AwsSubnet> listSubnets(String appId, String computeProviderId, String region, List<String> vpcIds) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      try {
        AwsConfig awsConfig = validateAndGetAwsConfig(computeProviderSetting);
        return awsEc2HelperServiceManager.listSubnets(
            awsConfig, secretManager.getEncryptionDetails(awsConfig, appId, null), region, vpcIds, appId);
      } catch (Exception e) {
        log.warn(ExceptionUtils.getMessage(e), e);
        throw new InvalidRequestException(ExceptionUtils.getMessage(e), USER);
      }
    }
    return emptyList();
  }

  @Override
  public Map<String, String> listLoadBalancers(String appId, String deploymentType, String computeProviderId) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      return infrastructureProvider.listLoadBalancers(computeProviderSetting, Regions.US_EAST_1.getName(), appId)
          .stream()
          .collect(toMap(identity(), identity()));
    } else if (PHYSICAL_DATA_CENTER.name().equals(computeProviderSetting.getValue().getType())) {
      return settingsService
          .getGlobalSettingAttributesByType(computeProviderSetting.getAccountId(), SettingVariableTypes.ELB.name())
          .stream()
          .collect(toMap(SettingAttribute::getUuid, SettingAttribute::getName));
    }
    return Collections.emptyMap();
  }

  @Override
  public Map<String, String> listLoadBalancers(String appId, String infraMappingId) {
    InfrastructureMapping infrastructureMapping = get(appId, infraMappingId);
    notNullCheck("Service Infrastructure", infrastructureMapping);

    SettingAttribute computeProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    notNullCheck("Compute Provider", computeProviderSetting);

    if (infrastructureMapping instanceof AwsInfrastructureMapping
        || infrastructureMapping instanceof EcsInfrastructureMapping) {
      String region = infrastructureMapping instanceof AwsInfrastructureMapping
          ? ((AwsInfrastructureMapping) infrastructureMapping).getRegion()
          : ((EcsInfrastructureMapping) infrastructureMapping).getRegion();

      return ((AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name()))
          .listLoadBalancers(computeProviderSetting, region, appId)
          .stream()
          .collect(toMap(identity(), identity()));
    } else if (PHYSICAL_DATA_CENTER.name().equals(computeProviderSetting.getValue().getType())) {
      return settingsService
          .getGlobalSettingAttributesByType(computeProviderSetting.getAccountId(), SettingVariableTypes.ELB.name())
          .stream()
          .collect(toMap(SettingAttribute::getUuid, SettingAttribute::getName));
    }
    return Collections.emptyMap();
  }

  String extractRegionFromInfraMapping(InfrastructureMapping infrastructureMapping) {
    String region = EMPTY;
    if (infrastructureMapping instanceof EcsInfrastructureMapping) {
      region = ((EcsInfrastructureMapping) infrastructureMapping).getRegion();
    } else if (infrastructureMapping instanceof AwsInfrastructureMapping) {
      region = ((AwsInfrastructureMapping) infrastructureMapping).getRegion();
    } else if (infrastructureMapping instanceof AwsAmiInfrastructureMapping) {
      region = ((AwsAmiInfrastructureMapping) infrastructureMapping).getRegion();
    }
    return region;
  }

  @Override
  public Map<String, String> listElasticLoadBalancers(String appId, String infraMappingId) {
    InfrastructureMapping infrastructureMapping = get(appId, infraMappingId);
    notNullCheck("Service Infrastructure", infrastructureMapping);

    SettingAttribute computeProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    notNullCheck("Compute Provider", computeProviderSetting);
    String region = extractRegionFromInfraMapping(infrastructureMapping);
    if (isEmpty(region)) {
      return Collections.emptyMap();
    }

    List<String> elasticBalancers =
        ((AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name()))
            .listElasticBalancers(computeProviderSetting, region, appId);

    Map<String, String> lbMap = new HashMap<>();
    for (String lbName : elasticBalancers) {
      lbMap.put(lbName, lbName);
    }

    return lbMap;
  }

  @Override
  public Map<String, String> listNetworkLoadBalancers(String appId, String infraMappingId) {
    InfrastructureMapping infrastructureMapping = get(appId, infraMappingId);
    notNullCheck("Service Infrastructure", infrastructureMapping);

    SettingAttribute computeProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    notNullCheck("Compute Provider", computeProviderSetting);

    if (infrastructureMapping instanceof EcsInfrastructureMapping
        || infrastructureMapping instanceof AwsInfrastructureMapping) {
      String region = infrastructureMapping instanceof EcsInfrastructureMapping
          ? ((EcsInfrastructureMapping) infrastructureMapping).getRegion()
          : ((AwsInfrastructureMapping) infrastructureMapping).getRegion();

      return ((AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name()))
          .listNetworkBalancers(computeProviderSetting, region, appId)
          .stream()
          .collect(toMap(identity(), identity()));
    }
    return Collections.emptyMap();
  }

  @Override
  public List<String> listClassicLoadBalancers(String appId, String computeProviderId, String region) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      return infrastructureProvider.listClassicLoadBalancers(computeProviderSetting, region, appId);
    }
    return emptyList();
  }

  @Override
  public Map<String, String> listTargetGroups(
      String appId, String deploymentType, String computeProviderId, String loadBalancerName) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      return infrastructureProvider.listTargetGroups(
          computeProviderSetting, Regions.US_EAST_1.getName(), loadBalancerName, appId);
    }
    return Collections.emptyMap();
  }

  @Override
  public Map<String, String> listTargetGroups(String appId, String infraMappingId, String loadbalancerName) {
    InfrastructureMapping infrastructureMapping = get(appId, infraMappingId);
    notNullCheck("Service Infrastructure", infrastructureMapping);

    SettingAttribute computeProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    notNullCheck("Compute Provider", computeProviderSetting);

    if (infrastructureMapping instanceof AwsInfrastructureMapping
        || infrastructureMapping instanceof EcsInfrastructureMapping) {
      String region = infrastructureMapping instanceof AwsInfrastructureMapping
          ? ((AwsInfrastructureMapping) infrastructureMapping).getRegion()
          : ((EcsInfrastructureMapping) infrastructureMapping).getRegion();
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      return infrastructureProvider.listTargetGroups(computeProviderSetting, region, loadbalancerName, appId);
    }
    return Collections.emptyMap();
  }

  @Override
  public List<AwsRoute53HostedZoneData> listHostedZones(String appId, String infraMappingId) {
    InfrastructureMapping infrastructureMapping = get(appId, infraMappingId);
    notNullCheck("Service Infrastructure", infrastructureMapping);

    SettingAttribute computeProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    notNullCheck("Compute Provider", computeProviderSetting);

    if (infrastructureMapping instanceof EcsInfrastructureMapping) {
      String region = ((EcsInfrastructureMapping) infrastructureMapping).getRegion();
      AwsConfig awsConfig = validateAndGetAwsConfig(computeProviderSetting);
      List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(awsConfig, appId, null);
      return awsRoute53HelperServiceManager.listHostedZones(awsConfig, encryptionDetails, region, appId);
    }
    return emptyList();
  }

  @Override
  public List<AwsElbListener> listListeners(String appId, String infraMappingId, String loadbalancerName) {
    InfrastructureMapping infrastructureMapping = get(appId, infraMappingId);
    notNullCheck("Service Infrastructure", infrastructureMapping);

    SettingAttribute computeProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    notNullCheck("Compute Provider", computeProviderSetting);

    String region = extractRegionFromInfraMapping(infrastructureMapping);
    if (isEmpty(region)) {
      return Collections.emptyList();
    }

    AwsInfrastructureProvider infrastructureProvider =
        (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
    return infrastructureProvider.listListeners(computeProviderSetting, region, loadbalancerName, appId);
  }

  @Override
  public List<HostValidationResponse> validateHost(@Valid HostValidationRequest validationRequest) {
    SettingAttribute computeProviderSetting = settingsService.get(validationRequest.getComputeProviderSettingId());
    notNullCheck("Compute Provider", computeProviderSetting);

    if (!PHYSICAL_DATA_CENTER.name().equals(computeProviderSetting.getValue().getType())) {
      throw new InvalidRequestException("Invalid infrastructure provider");
    }

    SettingAttribute hostConnectionSetting = settingsService.get(validationRequest.getHostConnectionAttrs());
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) hostConnectionSetting.getValue(), null, null);
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(hostConnectionSetting.getAccountId())
                                          .appId(validationRequest.getAppId())
                                          .envId(validationRequest.getEnvId())
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT * 3)
                                          .build();
    SSHVaultConfig sshVaultConfig = null;
    if (hostConnectionSetting.getValue() instanceof HostConnectionAttributes
        && ((HostConnectionAttributes) hostConnectionSetting.getValue()).isVaultSSH()) {
      sshVaultConfig = sshVaultService.getSSHVaultConfig(hostConnectionSetting.getAccountId(),
          ((HostConnectionAttributes) hostConnectionSetting.getValue()).getSshVaultConfigId());
      ((HostConnectionAttributes) hostConnectionSetting.getValue()).setSshVaultConfig(sshVaultConfig);
    }
    return delegateProxyFactory.get(HostValidationService.class, syncTaskContext)
        .validateHost(validationRequest.getHostNames(), hostConnectionSetting, encryptionDetails,
            validationRequest.getExecutionCredential(), sshVaultConfig);
  }

  @Override
  public List<String> listElasticLoadBalancer(String accessKey, char[] secretKey, String region, String accountId) {
    AwsInfrastructureProvider infrastructureProvider =
        (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
    return infrastructureProvider.listClassicLoadBalancers(accessKey, secretKey, region);
  }

  @Override
  public List<String> listCodeDeployApplicationNames(String computeProviderId, String region, String appId) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsConfig awsConfig = validateAndGetAwsConfig(computeProviderSetting);
      return awsCodeDeployHelperServiceManager.listApplications(
          awsConfig, secretManager.getEncryptionDetails(awsConfig, appId, null), region, appId);
    }
    return ImmutableList.of();
  }

  @Override
  public List<String> listCodeDeployDeploymentGroups(
      String computeProviderId, String region, String applicationName, String appId) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsConfig awsConfig = validateAndGetAwsConfig(computeProviderSetting);
      return awsCodeDeployHelperServiceManager.listDeploymentGroups(
          awsConfig, secretManager.getEncryptionDetails(awsConfig, appId, null), region, applicationName, appId);
    }
    return ImmutableList.of();
  }

  @Override
  public List<String> listCodeDeployDeploymentConfigs(String computeProviderId, String region, String appId) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsConfig awsConfig = validateAndGetAwsConfig(computeProviderSetting);
      return awsCodeDeployHelperServiceManager.listDeploymentConfiguration(
          awsConfig, secretManager.getEncryptionDetails(awsConfig, appId, null), region, appId);
    }
    return ImmutableList.of();
  }

  @Override
  public List<String> listHostDisplayNames(String appId, String infraMappingId, String workflowExecutionId) {
    InfrastructureMapping infrastructureMapping = get(appId, infraMappingId);
    notNullCheck(
        "Infra Mapping not found. Make sure to run provisioner step if Infra Definition is dynamic provisioned.",
        infrastructureMapping, USER);
    return getInfrastructureMappingHostDisplayNames(infrastructureMapping, appId, workflowExecutionId);
  }

  @Override
  public List<String> listComputeProviderHostDisplayNames(
      String appId, String envId, String serviceId, String computeProviderId) {
    Object serviceTemplateId =
        serviceTemplateService.getTemplateRefKeysByService(appId, serviceId, envId).get(0).getId();
    InfrastructureMapping infrastructureMapping =
        wingsPersistence.createQuery(InfrastructureMapping.class)
            .filter(InfrastructureMappingKeys.appId, appId)
            .filter(InfrastructureMappingKeys.envId, envId)
            .filter(InfrastructureMappingKeys.serviceTemplateId, serviceTemplateId)
            .filter(InfrastructureMappingKeys.computeProviderSettingId, computeProviderId)
            .get();
    notNullCheck("Infra Mapping", infrastructureMapping);

    return getInfrastructureMappingHostDisplayNames(infrastructureMapping, appId, null);
  }

  List<String> getInfrastructureMappingHostDisplayNames(
      InfrastructureMapping infrastructureMapping, String appId, String workflowExecutionId) {
    List<String> hostDisplayNames = new ArrayList<>();
    if (infrastructureMapping instanceof PhysicalInfrastructureMappingBase) {
      PhysicalInfrastructureMappingBase physicalInfraMapping =
          (PhysicalInfrastructureMappingBase) infrastructureMapping;
      if (infrastructureMapping.getProvisionerId() != null) {
        if (isEmpty(physicalInfraMapping.hosts())) {
          return emptyList();
        }
        return physicalInfraMapping.hosts().stream().map(Host::getHostName).collect(Collectors.toList());
      }
      return physicalInfraMapping.getHostNames();
    } else if (infrastructureMapping instanceof AwsInfrastructureMapping) {
      AwsInfrastructureMapping awsInfrastructureMapping = (AwsInfrastructureMapping) infrastructureMapping;
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      SettingAttribute computeProviderSetting =
          settingsService.get(awsInfrastructureMapping.getComputeProviderSettingId());
      notNullCheck("Compute Provider", computeProviderSetting);
      List<Host> hosts =
          infrastructureProvider
              .listHosts(awsInfrastructureMapping, computeProviderSetting,
                  secretManager.getEncryptionDetails(
                      (EncryptableSetting) computeProviderSetting.getValue(), appId, workflowExecutionId),
                  new PageRequest<>())
              .getResponse();
      for (Host host : hosts) {
        String displayName = host.getPublicDns();
        if (host.getEc2Instance() != null) {
          Optional<Tag> optNameTag =
              host.getEc2Instance().getTags().stream().filter(tag -> tag.getKey().equals("Name")).findFirst();
          if (optNameTag.isPresent() && isNotBlank(optNameTag.get().getValue())) {
            // UI checks for " [" in the name to get dns name only. If you change here then also update
            // NodeSelectModal.js
            displayName += " [" + optNameTag.get().getValue() + "]";
          }
        }
        hostDisplayNames.add(displayName);
      }
      return hostDisplayNames;
    } else if (infrastructureMapping instanceof AzureInfrastructureMapping) {
      AzureInfrastructureMapping azureInfrastructureMapping = (AzureInfrastructureMapping) infrastructureMapping;
      SettingAttribute computeProviderSetting =
          settingsService.get(azureInfrastructureMapping.getComputeProviderSettingId());
      notNullCheck("Compute Provider", computeProviderSetting);

      // Get VMs
      List<VirtualMachine> vms = azureHelperService.listVms(azureInfrastructureMapping, computeProviderSetting,
          secretManager.getEncryptionDetails((EncryptableSetting) computeProviderSetting.getValue(), null, null));
      hostDisplayNames = vms.stream().map(HasName::name).collect(Collectors.toList());
      return hostDisplayNames;
    }
    return emptyList();
  }

  @Override
  public String getContainerRunningInstances(String appId, String infraMappingId, String serviceNameExpression) {
    InfrastructureMapping infrastructureMapping = get(appId, infraMappingId);
    return getContainerRunningInstances(serviceNameExpression, infrastructureMapping);
  }

  @Override
  @Nonnull
  public String getContainerRunningInstances(
      String serviceNameExpression, InfrastructureMapping infrastructureMapping) {
    if (infrastructureMapping == null) {
      return "0";
    }

    Application app = appService.get(infrastructureMapping.getAppId());
    Environment env = envService.get(infrastructureMapping.getAppId(), infrastructureMapping.getEnvId(), false);
    Service service =
        serviceResourceService.getWithDetails(infrastructureMapping.getAppId(), infrastructureMapping.getServiceId());

    Map<String, Object> context = new HashMap<>();
    context.put("app", app);
    context.put("env", env);
    context.put("service", service);

    SettingAttribute settingAttribute;
    String clusterName = null;
    String namespace = null;
    String containerServiceName = null;
    String region = null;
    String subscriptionId = null;
    String resourceGroup = null;
    String masterUrl = null;
    ContainerInfrastructureMapping containerInfraMapping = (ContainerInfrastructureMapping) infrastructureMapping;
    boolean isStatefulSet = false;

    DeploymentType deploymentType = serviceResourceService.getDeploymentType(infrastructureMapping, service, null);
    ContainerTask containerTask = serviceResourceService.getContainerTaskByDeploymentType(
        app.getUuid(), service.getUuid(), deploymentType.name());
    if (containerTask instanceof KubernetesContainerTask) {
      KubernetesContainerTask kubernetesContainerTask = (KubernetesContainerTask) containerTask;
      isStatefulSet = KubernetesContainerTaskUtils.checkStatefulSet(kubernetesContainerTask.getAdvancedConfig());
    }

    String kubernetesName;
    String controllerNamePrefix;
    if (isNotBlank(serviceNameExpression)) {
      controllerNamePrefix = KubernetesConvention.normalize(evaluator.substitute(serviceNameExpression, context));
    } else {
      controllerNamePrefix =
          KubernetesConvention.getControllerNamePrefix(app.getName(), service.getName(), env.getName());
    }
    kubernetesName = isStatefulSet ? KubernetesConvention.getKubernetesServiceName(controllerNamePrefix)
                                   : (controllerNamePrefix + DASH + "0");

    if (containerInfraMapping instanceof DirectKubernetesInfrastructureMapping) {
      DirectKubernetesInfrastructureMapping directInfraMapping =
          (DirectKubernetesInfrastructureMapping) containerInfraMapping;
      settingAttribute = settingsService.get(directInfraMapping.getComputeProviderSettingId());
      namespace = directInfraMapping.getNamespace();
      containerServiceName = kubernetesName;
    } else {
      settingAttribute = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
      clusterName = containerInfraMapping.getClusterName();
      if (containerInfraMapping instanceof GcpKubernetesInfrastructureMapping) {
        namespace = containerInfraMapping.getNamespace();
        containerServiceName = kubernetesName;
        masterUrl = ((GcpKubernetesInfrastructureMapping) containerInfraMapping).getMasterUrl();
      } else if (containerInfraMapping instanceof AzureKubernetesInfrastructureMapping) {
        namespace = containerInfraMapping.getNamespace();
        subscriptionId = ((AzureKubernetesInfrastructureMapping) containerInfraMapping).getSubscriptionId();
        resourceGroup = ((AzureKubernetesInfrastructureMapping) containerInfraMapping).getResourceGroup();
        containerServiceName = kubernetesName;
        masterUrl = ((AzureKubernetesInfrastructureMapping) containerInfraMapping).getMasterUrl();

      } else if (containerInfraMapping instanceof EcsInfrastructureMapping) {
        region = ((EcsInfrastructureMapping) containerInfraMapping).getRegion();
        containerServiceName = (isNotBlank(serviceNameExpression)
                                       ? Misc.normalizeExpression(evaluator.substitute(serviceNameExpression, context))
                                       : EcsConvention.getTaskFamily(app.getName(), service.getName(), env.getName()))
            + EcsConvention.DELIMITER + "0";
      }
    }
    notNullCheck("SettingAttribute might have been deleted", settingAttribute, USER);

    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);

    SyncTaskContext syncTaskContext =
        SyncTaskContext.builder()
            .accountId(app.getAccountId())
            .appId(app.getUuid())
            .envId(infrastructureMapping.getEnvId())
            .infrastructureMappingId(infrastructureMapping.getUuid())
            .infraStructureDefinitionId(infrastructureMapping.getInfrastructureDefinitionId())
            .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
            .build();
    ContainerServiceParams containerServiceParams = ContainerServiceParams.builder()
                                                        .settingAttribute(settingAttribute)
                                                        .containerServiceName(containerServiceName)
                                                        .encryptionDetails(encryptionDetails)
                                                        .clusterName(clusterName)
                                                        .namespace(namespace)
                                                        .subscriptionId(subscriptionId)
                                                        .resourceGroup(resourceGroup)
                                                        .region(region)
                                                        .masterUrl(masterUrl)
                                                        .build();
    try {
      Map<String, Integer> activeServiceCounts = delegateProxyFactory.get(ContainerService.class, syncTaskContext)
                                                     .getActiveServiceCounts(containerServiceParams);
      return Integer.toString(activeServiceCounts.values().stream().mapToInt(Integer::intValue).sum());
    } catch (Exception e) {
      log.warn(ExceptionUtils.getMessage(e), e);
      return "0";
    }
  }

  @Override
  public AwsAsgGetRunningCountData getAmiCurrentlyRunningInstanceCount(String infraMappingId, String appId) {
    AwsAmiInfrastructureMapping infrastructureMapping = (AwsAmiInfrastructureMapping) get(appId, infraMappingId);
    notNullCheck("Service Infrastructure", infrastructureMapping);
    SettingAttribute computeProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    notNullCheck("Compute Provider", computeProviderSetting);
    String region = infrastructureMapping.getRegion();
    if (isEmpty(region)) {
      // case that could happen since we support dynamic infra for Ami Asg
      return AwsAsgGetRunningCountData.builder()
          .asgName(DEFAULT_AMI_ASG_NAME)
          .asgMin(DEFAULT_AMI_ASG_MIN_INSTANCES)
          .asgMax(DEFAULT_AMI_ASG_MAX_INSTANCES)
          .asgDesired(DEFAULT_AMI_ASG_DESIRED_INSTANCES)
          .build();
    }
    AwsConfig awsConfig = validateAndGetAwsConfig(computeProviderSetting);
    List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(awsConfig, appId, null);
    return awsAsgHelperServiceManager.getCurrentlyRunningInstanceCount(
        awsConfig, encryptionDetails, region, infraMappingId, appId);
  }

  @Override
  public Integer getPcfRunningInstances(String appId, String infraMappingId, String appNameExpression) {
    PcfInfrastructureMapping infrastructureMapping = (PcfInfrastructureMapping) get(appId, infraMappingId);
    notNullCheck("Inframapping Doesnt Exists", infrastructureMapping);

    Application app = appService.get(infrastructureMapping.getAppId());
    Environment env = envService.get(infrastructureMapping.getAppId(), infrastructureMapping.getEnvId(), false);
    Service service =
        serviceResourceService.getWithDetails(infrastructureMapping.getAppId(), infrastructureMapping.getServiceId());

    Map<String, Object> context = new HashMap<>();
    context.put("app", app);
    context.put("env", env);
    context.put("service", service);

    SettingAttribute computeProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    notNullCheck("Compute Provider Doesnt Exist", computeProviderSetting);

    if (!(computeProviderSetting.getValue() instanceof PcfConfig)) {
      throw new InvalidRequestException("InvalidConfiguration, Needs Instance of PcfConfig", USER);
    }

    appNameExpression = StringUtils.isNotBlank(appNameExpression)
        ? Misc.normalizeExpression(evaluator.substitute(appNameExpression, context))
        : EcsConvention.getTaskFamily(app.getName(), service.getName(), env.getName());

    return pcfHelperService.getRunningInstanceCount((PcfConfig) computeProviderSetting.getValue(),
        infrastructureMapping.getOrganization(), infrastructureMapping.getSpace(), appNameExpression);
  }

  private InfrastructureProvider getInfrastructureProviderByComputeProviderType(String computeProviderType) {
    return infrastructureProviders.get(computeProviderType);
  }

  @Override
  public InfrastructureMapping getInfraMappingByName(String appId, String envId, String name) {
    return wingsPersistence.createQuery(InfrastructureMapping.class)
        .filter(InfrastructureMappingKeys.appId, appId)
        .filter(InfrastructureMappingKeys.envId, envId)
        .filter(InfrastructureMappingKeys.name, name)
        .get();
  }

  @Override
  public List<InfrastructureMapping> getInfraMappingLinkedToInfraDefinition(String appId, String infraDefinitionId) {
    return wingsPersistence.createQuery(InfrastructureMapping.class)
        .field(InfrastructureMappingKeys.appId)
        .equal(appId)
        .field(InfrastructureMappingKeys.infrastructureDefinitionId)
        .equal(infraDefinitionId)
        .asList();
  }

  @Override
  public List<Host> getAutoScaleGroupNodes(String appId, String infraMappingId, String workflowExecutionId) {
    InfrastructureMapping infrastructureMapping = get(appId, infraMappingId);
    notNullCheck("Infra Mapping", infrastructureMapping);

    if (infrastructureMapping instanceof AwsInfrastructureMapping) {
      SettingAttribute computeProviderSetting =
          settingsService.get(infrastructureMapping.getComputeProviderSettingId());
      notNullCheck("Compute Provider", computeProviderSetting);

      AwsInfrastructureProvider awsInfrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(
              infrastructureMapping.getComputeProviderType());
      AwsInfrastructureMapping awsInfrastructureMapping = (AwsInfrastructureMapping) infrastructureMapping;

      return awsInfrastructureProvider.maybeSetAutoScaleCapacityAndGetHosts(
          appId, workflowExecutionId, awsInfrastructureMapping, computeProviderSetting);
    } else {
      throw new InvalidRequestException("Auto Scale groups are only supported for AWS infrastructure mapping", USER);
    }
  }

  @Override
  public Map<DeploymentType, List<SettingVariableTypes>> listInfraTypes(String appId, String envId, String serviceId) {
    Service service = serviceResourceService.getWithDetails(appId, serviceId);
    ArtifactType artifactType = service.getArtifactType();
    Map<DeploymentType, List<SettingVariableTypes>> infraTypes = new HashMap<>();

    if (service.getDeploymentType() != null) {
      boolean restrictInfraMappings = true;
      switch (service.getDeploymentType()) {
        case KUBERNETES:
          infraTypes.put(KUBERNETES,
              asList(SettingVariableTypes.GCP, SettingVariableTypes.AZURE, SettingVariableTypes.KUBERNETES_CLUSTER,
                  SettingVariableTypes.RANCHER));
          break;
        case HELM:
          infraTypes.put(HELM,
              asList(SettingVariableTypes.GCP, SettingVariableTypes.AZURE, SettingVariableTypes.KUBERNETES_CLUSTER));
          break;
        case ECS:
          infraTypes.put(ECS, asList(SettingVariableTypes.AWS));
          break;
        case AWS_CODEDEPLOY:
          infraTypes.put(AWS_CODEDEPLOY, asList(SettingVariableTypes.AWS));
          break;
        case AWS_LAMBDA:
          infraTypes.put(AWS_LAMBDA, asList(SettingVariableTypes.AWS));
          break;
        case AZURE_VMSS:
          infraTypes.put(AZURE_VMSS, asList(AZURE));
          break;
        case AZURE_WEBAPP:
          infraTypes.put(AZURE_WEBAPP, asList(AZURE));
          break;
        case AMI:
          infraTypes.put(AMI, asList(SettingVariableTypes.AWS));
          break;
        case WINRM:
          infraTypes.put(WINRM,
              asList(SettingVariableTypes.PHYSICAL_DATA_CENTER, SettingVariableTypes.AWS, SettingVariableTypes.AZURE));
          break;
        case PCF:
          infraTypes.put(PCF, asList(SettingVariableTypes.PCF));
          break;
        case SSH:
          if (ArtifactType.DOCKER == artifactType) {
            infraTypes.put(SSH, asList(SettingVariableTypes.PHYSICAL_DATA_CENTER, SettingVariableTypes.AWS));
          } else {
            infraTypes.put(SSH,
                asList(
                    SettingVariableTypes.PHYSICAL_DATA_CENTER, SettingVariableTypes.AWS, SettingVariableTypes.AZURE));
          }
          break;
        default:
          restrictInfraMappings = false;
      }
      if (restrictInfraMappings) {
        return infraTypes;
      }
    }

    if (artifactType == ArtifactType.DOCKER) {
      infraTypes.put(ECS, asList(SettingVariableTypes.AWS));
      infraTypes.put(KUBERNETES,
          asList(SettingVariableTypes.GCP, SettingVariableTypes.AZURE, SettingVariableTypes.KUBERNETES_CLUSTER,
              SettingVariableTypes.RANCHER));
      infraTypes.put(
          HELM, asList(SettingVariableTypes.GCP, SettingVariableTypes.AZURE, SettingVariableTypes.KUBERNETES_CLUSTER));
      infraTypes.put(SSH, asList(SettingVariableTypes.PHYSICAL_DATA_CENTER, SettingVariableTypes.AWS));
    } else if (artifactType == ArtifactType.AWS_CODEDEPLOY) {
      infraTypes.put(AWS_CODEDEPLOY, asList(SettingVariableTypes.AWS));
    } else if (artifactType == ArtifactType.AWS_LAMBDA) {
      infraTypes.put(AWS_LAMBDA, asList(SettingVariableTypes.AWS));
    } else if (artifactType == ArtifactType.AMI) {
      infraTypes.put(AMI, asList(SettingVariableTypes.AWS));
    } else if (artifactType == ArtifactType.AZURE_MACHINE_IMAGE) {
      infraTypes.put(AZURE_VMSS, asList(AZURE));
    } else if (artifactType == ArtifactType.AZURE_WEBAPP) {
      infraTypes.put(AZURE_WEBAPP, asList(AZURE));
    } else if (artifactType == ArtifactType.IIS || artifactType == ArtifactType.IIS_APP
        || artifactType == ArtifactType.IIS_VirtualDirectory) {
      infraTypes.put(WINRM,
          asList(SettingVariableTypes.PHYSICAL_DATA_CENTER, SettingVariableTypes.AWS, SettingVariableTypes.AZURE));
    } else if (artifactType == ArtifactType.PCF) {
      infraTypes.put(PCF, asList(SettingVariableTypes.PCF));
    } else {
      infraTypes.put(
          SSH, asList(SettingVariableTypes.PHYSICAL_DATA_CENTER, SettingVariableTypes.AWS, SettingVariableTypes.AZURE));
    }

    return infraTypes;
  }

  @Override
  public List<InfrastructureMapping> getInfraStructureMappingsByUuids(String appId, List<String> infraMappingIds) {
    if (isNotEmpty(infraMappingIds)) {
      return wingsPersistence.createQuery(InfrastructureMapping.class)
          .filter(InfrastructureMappingKeys.appId, appId)
          .field("uuid")
          .in(infraMappingIds)
          .asList();
    }
    return new ArrayList<>();
  }

  @Override
  public String getInfraMappingsByServiceAndInfraDefinitionIds(
      String appId, String serviceId, String infraDefinitionId) {
    if (isNotEmpty(serviceId) && isNotEmpty(infraDefinitionId)) {
      InfrastructureMapping infraMapping =
          wingsPersistence.createQuery(InfrastructureMapping.class)
              .filter(InfrastructureMappingKeys.appId, appId)
              .filter(InfrastructureMappingKeys.serviceId, serviceId)
              .filter(InfrastructureMappingKeys.infrastructureDefinitionId, infraDefinitionId)
              .get();

      if (infraMapping != null) {
        return infraMapping.getUuid();
      } else {
        return null;
      }
    }
    return null;
  }

  @Override
  public List<InfrastructureMapping> listByComputeProviderId(String accountId, String computeProviderId) {
    return wingsPersistence.createQuery(InfrastructureMapping.class)
        .filter(InfrastructureMappingKeys.accountId, accountId)
        .filter(COMPUTE_PROVIDER_SETTING_ID_KEY, computeProviderId)
        .asList(new FindOptions().limit(REFERENCED_ENTITIES_TO_SHOW));
  }

  @Override
  public List<String> fetchCloudProviderIds(String appId, List<String> infraMappingIds) {
    if (isNotEmpty(infraMappingIds)) {
      List<InfrastructureMapping> infrastructureMappings =
          wingsPersistence.createQuery(InfrastructureMapping.class)
              .project(InfrastructureMappingKeys.appId, true)
              .project(InfrastructureMappingKeys.computeProviderSettingId, true)
              .filter(InfrastructureMappingKeys.appId, appId)
              .field(InfrastructureMappingKeys.uuid)
              .in(infraMappingIds)
              .asList();
      return infrastructureMappings.stream()
          .map(InfrastructureMapping::getComputeProviderSettingId)
          .distinct()
          .collect(toList());
    }
    return new ArrayList<>();
  }

  @Override
  public List<InfrastructureMapping> listInfraMappings(String appId, String envId) {
    PageRequest pageRequest = PageRequestBuilder.aPageRequest()
                                  .addFilter(InfrastructureMappingKeys.appId, Operator.EQ, appId)
                                  .addFilter(InfrastructureMappingKeys.envId, Operator.EQ, envId)
                                  .build();
    return list(pageRequest);
  }

  @Override
  public void saveInfrastructureMappingToSweepingOutput(
      String appId, String workflowExecutionId, PhaseElement phaseElement, String infrastructureMappingId) {
    String phaseExecutionId = phaseElement.getPhaseExecutionIdForSweepingOutput();
    sweepingOutputService.save(
        SweepingOutputServiceImpl
            .prepareSweepingOutputBuilder(
                appId, null, workflowExecutionId, phaseExecutionId, null, SweepingOutputInstance.Scope.PHASE)
            .name(InfrastructureConstants.PHASE_INFRA_MAPPING_KEY_NAME + phaseElement.getUuid())
            .value(InfraMappingSweepingOutput.builder().infraMappingId(infrastructureMappingId).build())
            .build());
  }

  /**
   * Prune if belongs to infrastructure definition.
   *
   * @param appId             the app id
   * @param infraDefinitionId the infrastructure definition id
   */
  @Override
  public void pruneByInfrastructureDefinition(String appId, String infraDefinitionId) {
    List<Key<InfrastructureMapping>> keys =
        wingsPersistence.createQuery(InfrastructureMapping.class)
            .filter(InfrastructureMapping.APP_ID_KEY, appId)
            .filter(InfrastructureMappingKeys.infrastructureDefinitionId, infraDefinitionId)
            .asKeyList();
    for (Key<InfrastructureMapping> key : keys) {
      prune(appId, (String) key.getId());
    }
  }
}
