/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.CDS_TERRAFORM_CONFIG_INSPECT_V1_2;
import static io.harness.beans.FeatureName.GIT_HOST_CONNECTIVITY;
import static io.harness.beans.FeatureName.TERRAFORM_CONFIG_INSPECT_VERSION_SELECTOR;
import static io.harness.beans.FeatureName.VALIDATE_PROVISIONER_EXPRESSION;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.ARMSourceType.GIT;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.atteo.evo.inflector.English.plural;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.ARMResourceType;
import io.harness.azure.model.ARMScopeType;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.context.ContextElementType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.TaskData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.git.model.GitRepositoryType;
import io.harness.limits.Action;
import io.harness.limits.ActionType;
import io.harness.limits.LimitCheckerFactory;
import io.harness.limits.LimitEnforcementUtils;
import io.harness.limits.checker.StaticLimitCheckerWithDecrement;
import io.harness.provision.model.TfConfigInspectVersion;
import io.harness.queue.QueuePublisher;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.validation.Create;
import io.harness.validation.Update;

import software.wings.api.DeploymentType;
import software.wings.api.PhaseElement;
import software.wings.api.TerraformExecutionData;
import software.wings.beans.ARMInfrastructureProvisioner;
import software.wings.beans.ARMSourceType;
import software.wings.beans.AwsConfig;
import software.wings.beans.BlueprintProperty;
import software.wings.beans.CloudFormationInfrastructureProvisioner;
import software.wings.beans.CloudFormationSourceType;
import software.wings.beans.EntityType;
import software.wings.beans.Event.Type;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingBlueprint.CloudProviderType;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.InfrastructureProvisionerDetails;
import software.wings.beans.InfrastructureProvisionerDetails.InfrastructureProvisionerDetailsBuilder;
import software.wings.beans.NameValuePair;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.TaskType;
import software.wings.beans.TerraGroupProvisioners;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.TerraformInputVariablesTaskResponse;
import software.wings.beans.TerraformSourceType;
import software.wings.beans.TerragruntInfrastructureProvisioner;
import software.wings.beans.delegation.TerraformProvisionParameters;
import software.wings.beans.delegation.TerraformProvisionParameters.TerraformProvisionParametersBuilder;
import software.wings.beans.dto.Log.Builder;
import software.wings.beans.shellscript.provisioner.ShellScriptInfrastructureProvisioner;
import software.wings.dl.WingsPersistence;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.PhysicalInfra;
import software.wings.infra.ProvisionerAware;
import software.wings.prune.PruneEntityListener;
import software.wings.prune.PruneEvent;
import software.wings.service.impl.aws.model.AwsCFTemplateParamsData;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.HarnessTagService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ResourceLookupService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.aws.manager.AwsCFHelperServiceManager;
import software.wings.service.intfc.ownership.OwnedByInfrastructureProvisioner;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.settings.SettingVariableTypes;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.states.ManagerExecutionLogCallback;
import software.wings.utils.GitUtilsManager;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.Key;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.validation.Valid;
import javax.validation.executable.ValidateOnExecution;
import javax.ws.rs.core.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.hibernate.validator.constraints.NotEmpty;
import org.jetbrains.annotations.NotNull;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

@OwnedBy(CDP)
@Singleton
@TargetModule(_870_CG_ORCHESTRATION)
@ValidateOnExecution
@Slf4j
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class InfrastructureProvisionerServiceImpl implements InfrastructureProvisionerService {
  private static final String PROVISIONER_TYPE_KEY_EXPRESSION_FORMAT = "${%s.";
  private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\$\\{.+?}");
  @Inject private ManagerExpressionEvaluator evaluator;

  @Inject InfrastructureMappingService infrastructureMappingService;
  @Inject ServiceResourceService serviceResourceService;
  @Inject SettingsService settingService;
  @Inject AwsCFHelperServiceManager awsCFHelperServiceManager;
  @Inject private AppService appService;
  @Inject YamlPushService yamlPushService;
  @Inject private DelegateService delegateService;
  @Inject private SecretManager secretManager;
  @Inject private GitConfigHelperService gitConfigHelperService;
  @Inject FileService fileService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private LogService logService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private QueuePublisher<PruneEvent> pruneQueue;
  @Inject private LimitCheckerFactory limitCheckerFactory;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private HarnessTagService harnessTagService;
  @Inject private ResourceLookupService resourceLookupService;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private GitUtilsManager gitUtilsManager;
  @Inject private GitFileConfigHelperService gitFileConfigHelperService;
  @Inject private UserGroupService userGroupService;

  @Inject private transient SettingsService settingsService;

  static final String DUPLICATE_VAR_MSG_PREFIX = "variable names should be unique, duplicate variable(s) found: ";

  @Override
  @ValidationGroups(Create.class)
  public InfrastructureProvisioner save(@Valid InfrastructureProvisioner infrastructureProvisioner) {
    String accountId = appService.getAccountIdByAppId(infrastructureProvisioner.getAppId());
    infrastructureProvisioner.setAccountId(accountId);
    trimInfrastructureProvisionerVariables(infrastructureProvisioner);
    StaticLimitCheckerWithDecrement checker = (StaticLimitCheckerWithDecrement) limitCheckerFactory.getInstance(
        new Action(accountId, ActionType.CREATE_INFRA_PROVISIONER));

    return LimitEnforcementUtils.withLimitCheck(checker, () -> {
      checkForDuplicate(infrastructureProvisioner);
      populateDerivedFields(infrastructureProvisioner);

      validateProvisioner(infrastructureProvisioner);

      InfrastructureProvisioner finalInfraProvisioner =
          wingsPersistence.saveAndGet(InfrastructureProvisioner.class, infrastructureProvisioner);

      yamlPushService.pushYamlChangeSet(
          accountId, null, infrastructureProvisioner, Type.CREATE, infrastructureProvisioner.isSyncFromGit(), false);

      return finalInfraProvisioner;
    });
  }

  @VisibleForTesting
  void checkForDuplicate(InfrastructureProvisioner provisioner) {
    if (differentProvisionerWithSameNameExists(provisioner.getAppId(), provisioner.getUuid(), provisioner.getName())) {
      throw new InvalidRequestException(format("Provisioner with name [%s] already exists", provisioner.getName()));
    }
  }

  @VisibleForTesting
  boolean differentProvisionerWithSameNameExists(String appId, String uuid, String name) {
    Query<InfrastructureProvisioner> query = wingsPersistence.createQuery(InfrastructureProvisioner.class)
                                                 .field(InfrastructureProvisioner.APP_ID)
                                                 .equal(appId)
                                                 .field(InfrastructureProvisioner.NAME_KEY)
                                                 .equal(name);
    if (isNotEmpty(uuid)) {
      query.field(InfrastructureProvisioner.ID).notEqual(uuid);
    }
    return query.count() > 0;
  }

  void restrictDuplicateVariables(InfrastructureProvisioner infrastructureProvisioner) {
    List<NameValuePair> variables = infrastructureProvisioner.getVariables();
    ensureNoDuplicateVars(variables);
  }

  private void ensureNoDuplicateVars(List<NameValuePair> variables) {
    if (isEmpty(variables)) {
      return;
    }

    HashSet<String> distinctVariableNames = new HashSet<>();
    Set<String> duplicateVariableNames = new HashSet<>();
    for (NameValuePair variable : variables) {
      if (!distinctVariableNames.contains(variable.getName().trim())) {
        distinctVariableNames.add(variable.getName());
      } else {
        duplicateVariableNames.add(variable.getName());
      }
    }

    if (!duplicateVariableNames.isEmpty()) {
      throw new InvalidRequestException(
          DUPLICATE_VAR_MSG_PREFIX + duplicateVariableNames.toString(), WingsException.USER);
    }
  }

  @VisibleForTesting
  void validateProvisioner(InfrastructureProvisioner provisioner) {
    restrictDuplicateVariables(provisioner);

    if (provisioner instanceof TerraformInfrastructureProvisioner) {
      validateTerraformProvisioner((TerraformInfrastructureProvisioner) provisioner);
    } else if (provisioner instanceof CloudFormationInfrastructureProvisioner) {
      validateCloudFormationProvisioner((CloudFormationInfrastructureProvisioner) provisioner);
    } else if (provisioner instanceof ShellScriptInfrastructureProvisioner) {
      validateShellScriptProvisioner((ShellScriptInfrastructureProvisioner) provisioner);
    } else if (provisioner instanceof ARMInfrastructureProvisioner) {
      validateARMProvisioner((ARMInfrastructureProvisioner) provisioner);
    } else if (provisioner instanceof TerragruntInfrastructureProvisioner) {
      validateTerragruntProvisioner((TerragruntInfrastructureProvisioner) provisioner);
    }
  }

  private void validateARMProvisioner(ARMInfrastructureProvisioner provisioner) {
    validateARMProvisionerMandatoryFields(provisioner);

    if (ARMResourceType.BLUEPRINT == provisioner.getResourceType()) {
      validateBlueprintProvisioner(provisioner);
    }

    if (ARMSourceType.GIT == provisioner.getSourceType()) {
      validateARMGitSourceType(provisioner);
    }

    if (ARMSourceType.TEMPLATE_BODY == provisioner.getSourceType()) {
      validateARMTemplateBodySourceType(provisioner);
      repairARMProvisionerInlineParams(provisioner);
    }
  }

  private void validateARMProvisionerMandatoryFields(ARMInfrastructureProvisioner provisioner) {
    if (provisioner.getScopeType() == null) {
      throw new InvalidRequestException("ScopeType for ARM provisioner cannot be null", USER);
    }

    if (provisioner.getResourceType() == null) {
      throw new InvalidRequestException("ResourceType for ARM provisioner cannot be null", USER);
    }

    if (provisioner.getSourceType() == null) {
      throw new InvalidRequestException("SourceType for ARM provisioner cannot be null", USER);
    }
  }

  private void validateBlueprintProvisioner(ARMInfrastructureProvisioner provisioner) {
    if (ARMSourceType.TEMPLATE_BODY == provisioner.getSourceType()) {
      throw new InvalidRequestException("Template Body is not supported for Blueprint", USER);
    }

    if (ARMScopeType.TENANT == provisioner.getScopeType()) {
      throw new InvalidRequestException("Tenant scope is not supported for Blueprint", USER);
    }

    if (ARMScopeType.RESOURCE_GROUP == provisioner.getScopeType()) {
      throw new InvalidRequestException("ResourceGroup scope is not supported for Blueprint", USER);
    }
  }

  private void validateARMGitSourceType(ARMInfrastructureProvisioner provisioner) {
    gitFileConfigHelperService.validate(provisioner.getGitFileConfig());
    if (isNotEmpty(provisioner.getTemplateBody())) {
      throw new InvalidRequestException(
          format("Template Body cannot be set for sourceType: %s", provisioner.getSourceType()), USER);
    }
  }

  private void validateARMTemplateBodySourceType(ARMInfrastructureProvisioner provisioner) {
    if (isEmpty(provisioner.getTemplateBody())) {
      throw new InvalidRequestException(
          format("Template Body cannot be empty or null for sourceType: %s", provisioner.getSourceType()), USER);
    }
  }

  private void repairARMProvisionerInlineParams(ARMInfrastructureProvisioner provisioner) {
    provisioner.setTemplateBody(trim(provisioner.getTemplateBody()));
    provisioner.setGitFileConfig(null);
  }

  private void populateDerivedFields(InfrastructureProvisioner infrastructureProvisioner) {
    if (infrastructureProvisioner instanceof TerraGroupProvisioners) {
      TerraGroupProvisioners terraGroupProvisioners = (TerraGroupProvisioners) infrastructureProvisioner;

      terraGroupProvisioners.setTemplatized(isTemplatizedProvisioner(terraGroupProvisioners));
      terraGroupProvisioners.setNormalizedPath(FilenameUtils.normalize(terraGroupProvisioners.getPath()));
    }
  }

  @Override
  @ValidationGroups(Update.class)
  public InfrastructureProvisioner update(@Valid InfrastructureProvisioner infrastructureProvisioner) {
    checkForDuplicate(infrastructureProvisioner);
    populateDerivedFields(infrastructureProvisioner);

    infrastructureProvisioner.setAccountId(appService.getAccountIdByAppId(infrastructureProvisioner.getAppId()));
    trimInfrastructureProvisionerVariables(infrastructureProvisioner);

    validateProvisioner(infrastructureProvisioner);

    InfrastructureProvisioner savedInfraProvisioner =
        get(infrastructureProvisioner.getAppId(), infrastructureProvisioner.getUuid());

    InfrastructureProvisioner updatedInfraProvisioner =
        wingsPersistence.saveAndGet(InfrastructureProvisioner.class, infrastructureProvisioner);

    String accountId = appService.getAccountIdByAppId(infrastructureProvisioner.getAppId());
    boolean isRename = !infrastructureProvisioner.getName().equals(savedInfraProvisioner.getName());
    yamlPushService.pushYamlChangeSet(accountId, savedInfraProvisioner, updatedInfraProvisioner, Type.UPDATE,
        infrastructureProvisioner.isSyncFromGit(), isRename);

    return updatedInfraProvisioner;
  }

  public void trimInfrastructureProvisionerVariables(InfrastructureProvisioner infrastructureProvisioner) {
    if (isEmpty(infrastructureProvisioner.getVariables())) {
      return;
    }
    infrastructureProvisioner.getVariables().forEach(var -> var.setName(var.getName().trim()));
  }

  @Override
  public PageResponse<InfrastructureProvisioner> list(PageRequest<InfrastructureProvisioner> pageRequest) {
    return wingsPersistence.query(InfrastructureProvisioner.class, pageRequest);
  }

  @Override
  public PageResponse<InfrastructureProvisioner> listByBlueprintDetails(@NotEmpty String appId,
      String infrastructureProvisionerType, String serviceId, DeploymentType deploymentType,
      CloudProviderType cloudProviderType) {
    PageRequestBuilder requestBuilder = aPageRequest().addFilter(InfrastructureProvisioner.APP_ID, Operator.EQ, appId);

    if (infrastructureProvisionerType != null) {
      requestBuilder.addFilter(
          InfrastructureProvisioner.INFRASTRUCTURE_PROVISIONER_TYPE_KEY, Operator.EQ, infrastructureProvisionerType);
    }

    return wingsPersistence.query(InfrastructureProvisioner.class, requestBuilder.build());
  }

  InfrastructureProvisionerDetails details(
      InfrastructureProvisioner provisioner, Map<String, SettingAttribute> idToSettingAttributeMapping) {
    final InfrastructureProvisionerDetailsBuilder detailsBuilder =
        InfrastructureProvisionerDetails.builder()
            .uuid(provisioner.getUuid())
            .name(provisioner.getName())
            .description(provisioner.getDescription())
            .infrastructureProvisionerType(provisioner.getInfrastructureProvisionerType())
            .tagLinks(provisioner.getTagLinks());

    if (provisioner instanceof TerraGroupProvisioners) {
      final TerraGroupProvisioners terraGroupInfrastructureProvisioner = (TerraGroupProvisioners) provisioner;

      final SettingAttribute settingAttribute =
          idToSettingAttributeMapping.get(terraGroupInfrastructureProvisioner.getSourceRepoSettingId());

      if (settingAttribute != null && settingAttribute.getValue() instanceof GitConfig) {
        GitConfig gitConfig = (GitConfig) settingAttribute.getValue();
        String repositoryUrl =
            gitConfigHelperService.getRepositoryUrl(gitConfig, terraGroupInfrastructureProvisioner.getRepoName());
        detailsBuilder.repository(repositoryUrl);
      }
    } else if (provisioner instanceof CloudFormationInfrastructureProvisioner) {
      CloudFormationInfrastructureProvisioner cloudFormationInfrastructureProvisioner =
          (CloudFormationInfrastructureProvisioner) provisioner;
      String sourceType =
          CloudFormationSourceType.getSourceType(cloudFormationInfrastructureProvisioner.getSourceType());
      detailsBuilder.cloudFormationSourceType(sourceType);
    } else if (provisioner instanceof ARMInfrastructureProvisioner) {
      ARMInfrastructureProvisioner armInfrastructureProvisioner = (ARMInfrastructureProvisioner) provisioner;
      if (GIT == armInfrastructureProvisioner.getSourceType()) {
        GitFileConfig gitFileConfig = armInfrastructureProvisioner.getGitFileConfig();
        if (gitFileConfig != null) {
          SettingAttribute settingAttribute = idToSettingAttributeMapping.get(gitFileConfig.getConnectorId());
          if (settingAttribute != null && settingAttribute.getValue() instanceof GitConfig) {
            GitConfig gitConfig = (GitConfig) settingAttribute.getValue();
            detailsBuilder.repository(gitConfigHelperService.getRepositoryUrl(gitConfig, gitFileConfig.getRepoName()));
          }
        }
      }
      detailsBuilder.azureARMResourceType(armInfrastructureProvisioner.getResourceType());
    }
    return detailsBuilder.build();
  }

  Map<String, Service> getIdToServiceMapping(String appId, Set<String> servicesIds) {
    if (isEmpty(servicesIds)) {
      return Collections.emptyMap();
    }
    PageRequest<Service> servicePageRequest = new PageRequest<>();
    servicePageRequest.addFilter(Service.APP_ID, Operator.EQ, appId);
    servicePageRequest.addFilter(ServiceKeys.uuid, Operator.IN, servicesIds.toArray());

    PageResponse<Service> services = serviceResourceService.list(servicePageRequest, false, false, false, null);

    Map<String, Service> idToServiceMapping = new HashMap<>();
    for (Service service : services.getResponse()) {
      idToServiceMapping.put(service.getUuid(), service);
    }
    return idToServiceMapping;
  }

  Map<String, SettingAttribute> getIdToSettingAttributeMapping(String accountId, Set<String> settingAttributeIds) {
    if (isEmpty(settingAttributeIds)) {
      return Collections.emptyMap();
    }
    PageRequest<SettingAttribute> settingAttributePageRequest = new PageRequest<>();
    settingAttributePageRequest.addFilter(SettingAttribute.ACCOUNT_ID_KEY, Operator.EQ, accountId);
    settingAttributePageRequest.addFilter(
        SettingAttributeKeys.value_type, Operator.EQ, SettingVariableTypes.GIT.name());
    settingAttributePageRequest.addFilter(SettingAttributeKeys.uuid, Operator.IN, settingAttributeIds.toArray());

    PageResponse<SettingAttribute> settingAttributes =
        settingService.list(settingAttributePageRequest, null, null, false);

    Map<String, SettingAttribute> idToSettingAttributeMapping = new HashMap<>();
    for (SettingAttribute settingAttribute : settingAttributes.getResponse()) {
      idToSettingAttributeMapping.put(settingAttribute.getUuid(), settingAttribute);
    }
    return idToSettingAttributeMapping;
  }

  @Override
  public PageResponse<InfrastructureProvisionerDetails> listDetails(
      PageRequest<InfrastructureProvisioner> pageRequest, boolean withTags, String tagFilter, @NotEmpty String appId) {
    final long apiStartTime = System.currentTimeMillis();
    PageResponse<InfrastructureProvisioner> pageResponse =
        resourceLookupService.listWithTagFilters(pageRequest, tagFilter, EntityType.PROVISIONER, withTags, false);

    log.info(format("Time taken in fetching listWithTagFilters : [%s] ms", System.currentTimeMillis() - apiStartTime));
    long startTime = System.currentTimeMillis();

    Set<String> settingAttributeIds = new HashSet<>();
    for (InfrastructureProvisioner infrastructureProvisioner : pageResponse.getResponse()) {
      if (infrastructureProvisioner instanceof TerraGroupProvisioners) {
        settingAttributeIds.add(((TerraGroupProvisioners) infrastructureProvisioner).getSourceRepoSettingId());
      } else if (infrastructureProvisioner instanceof ARMInfrastructureProvisioner
          && GIT == ((ARMInfrastructureProvisioner) infrastructureProvisioner).getSourceType()) {
        settingAttributeIds.add(
            ((ARMInfrastructureProvisioner) infrastructureProvisioner).getGitFileConfig().getConnectorId());
      }
    }

    Map<String, SettingAttribute> idToSettingAttributeMapping =
        getIdToSettingAttributeMapping(appService.getAccountIdByAppId(appId), settingAttributeIds);

    log.info(format("Time taken in getIdToSettingAttributeMapping : [%s] ms", System.currentTimeMillis() - startTime));
    startTime = System.currentTimeMillis();

    log.info("Time taken in idToServiceMapping : [{}] ms", System.currentTimeMillis() - startTime);
    startTime = System.currentTimeMillis();

    PageResponse<InfrastructureProvisionerDetails> infrastructureProvisionerDetails = new PageResponse<>();
    infrastructureProvisionerDetails.setResponse(
        pageResponse.getResponse().stream().map(item -> details(item, idToSettingAttributeMapping)).collect(toList()));
    log.info("Time taken in setting details : [{}] ms", System.currentTimeMillis() - startTime);
    log.info("Time taken by details api : [{}] ms", System.currentTimeMillis() - apiStartTime);
    return infrastructureProvisionerDetails;
  }

  @Override
  public InfrastructureProvisioner get(String appId, String infrastructureProvisionerId) {
    InfrastructureProvisioner infrastructureProvisioner =
        wingsPersistence.getWithAppId(InfrastructureProvisioner.class, appId, infrastructureProvisionerId);
    if (infrastructureProvisioner == null) {
      throw new InvalidRequestException("Provisioner Not Found");
    }
    return infrastructureProvisioner;
  }

  @Override
  public InfrastructureProvisioner getByName(String appId, String provisionerName) {
    return wingsPersistence.createQuery(InfrastructureProvisioner.class)
        .filter(InfrastructureProvisioner.APP_ID, appId)
        .filter(InfrastructureProvisioner.NAME_KEY, provisionerName)
        .get();
  }

  private void ensureSafeToDelete(String appId, InfrastructureProvisioner infrastructureProvisioner) {
    final String infrastructureProvisionerId = infrastructureProvisioner.getUuid();

    List<String> infraDefinitionNames =
        infrastructureDefinitionService.listNamesByProvisionerId(appId, infrastructureProvisionerId);
    if (isNotEmpty(infraDefinitionNames)) {
      throw new InvalidRequestException(format("Infrastructure provisioner [%s] is not safe to "
              + "delete."
              + "Referenced "
              + "by "
              + "%d %s [%s].",
          infrastructureProvisioner.getName(), infraDefinitionNames.size(),
          plural("Infrastructure "
                  + "Definition",
              infraDefinitionNames.size()),
          HarnessStringUtils.join(", ", infraDefinitionNames)));
    }
  }

  @Override
  public void delete(String appId, String infrastructureProvisionerId) {
    delete(appId, infrastructureProvisionerId, false);
  }

  @Override
  public void delete(String appId, String infrastructureProvisionerId, boolean syncFromGit) {
    InfrastructureProvisioner infrastructureProvisioner = get(appId, infrastructureProvisionerId);

    ensureSafeToDelete(appId, infrastructureProvisioner);

    String accountId = appService.getAccountIdByAppId(infrastructureProvisioner.getAppId());
    StaticLimitCheckerWithDecrement checker = (StaticLimitCheckerWithDecrement) limitCheckerFactory.getInstance(
        new Action(accountId, ActionType.CREATE_INFRA_PROVISIONER));

    LimitEnforcementUtils.withCounterDecrement(checker, () -> {
      ensureSafeToDelete(appId, infrastructureProvisioner);
      pruneQueue.send(new PruneEvent(InfrastructureProvisioner.class, appId, infrastructureProvisionerId));
      yamlPushService.pushYamlChangeSet(accountId, infrastructureProvisioner, null, Type.DELETE, syncFromGit, false);
      wingsPersistence.delete(InfrastructureProvisioner.class, appId, infrastructureProvisionerId);
    });
  }

  @Override
  public void pruneDescendingEntities(String appId, String infrastructureProvisionerId) {
    List<OwnedByInfrastructureProvisioner> services = ServiceClassLocator.descendingServices(
        this, InfrastructureProvisionerServiceImpl.class, OwnedByInfrastructureProvisioner.class);
    PruneEntityListener.pruneDescendingEntities(
        services, descending -> descending.pruneByInfrastructureProvisioner(appId, infrastructureProvisionerId));
  }

  @Override
  public void pruneByApplication(String appId) {
    List<Key<InfrastructureProvisioner>> keys = wingsPersistence.createQuery(InfrastructureProvisioner.class)
                                                    .filter(InfrastructureProvisioner.APP_ID, appId)
                                                    .asKeyList();
    for (Key<InfrastructureProvisioner> key : keys) {
      delete(appId, (String) key.getId());
      harnessTagService.pruneTagLinks(appService.getAccountIdByAppId(appId), (String) key.getId());
    }
  }

  @Override
  public Map<String, Object> resolveProperties(Map<String, Object> contextMap, List<BlueprintProperty> properties,
      Optional<ManagerExecutionLogCallback> executionLogCallbackOptional, Optional<String> region,
      String infraProvisionerTypeKey) {
    Map<String, Object> propertyNameEvaluatedMap = getPropertyNameEvaluatedMap(
        properties.stream()
            .map(property -> new NameValuePair(property.getName(), property.getValue(), property.getValueType()))
            .collect(toList()),
        contextMap, infraProvisionerTypeKey);

    for (BlueprintProperty property : properties) {
      if (isNotEmpty(property.getFields())) {
        String propertyName = property.getName();
        Object evaluatedValue = propertyNameEvaluatedMap.get(propertyName);
        if (evaluatedValue == null) {
          throw new InvalidRequestException(format("Property %s not found for resolving fields", propertyName));
        }
        List<NameValuePair> fields = property.getFields();
        if (evaluatedValue instanceof List) {
          List<Map<String, Object>> fieldsEvaluatedList = new ArrayList<>();
          for (Object evaluatedEntry : (List) evaluatedValue) {
            fieldsEvaluatedList.add(
                getPropertyNameEvaluatedMap(fields, (Map<String, Object>) evaluatedEntry, infraProvisionerTypeKey));
          }
          propertyNameEvaluatedMap.put(propertyName, fieldsEvaluatedList);

        } else {
          getPropertyNameEvaluatedMap(fields, (Map<String, Object>) evaluatedValue, infraProvisionerTypeKey);
        }
      }
    }

    region.ifPresent(s -> propertyNameEvaluatedMap.put("region", s));
    return propertyNameEvaluatedMap;
  }

  @NotNull
  @VisibleForTesting
  Map<String, Object> getPropertyNameEvaluatedMap(
      List<NameValuePair> properties, Map<String, Object> contextMap, String infrastructureProvisionerTypeKey) {
    Map<String, Object> propertyNameEvaluatedMap = new HashMap<>();
    for (NameValuePair property : properties) {
      if (isEmpty(property.getValue())) {
        continue;
      }
      if (!property.getValue().contains("$")) {
        propertyNameEvaluatedMap.put(property.getName(), property.getValue());
        continue;
      }
      Object evaluated = null;
      try {
        evaluated = evaluator.evaluate(property.getValue(), contextMap);
      } catch (Exception ignore) {
        // ignore this exception, it is based on user input
      }
      if (evaluated == null) {
        evaluated = evaluator.substitute(property.getValue(), contextMap);
        if (isNullString(evaluated)
            && property.getValue().contains(
                format(PROVISIONER_TYPE_KEY_EXPRESSION_FORMAT, infrastructureProvisionerTypeKey))) {
          log.info("Unresolved expression \"{}\" ", property.getValue());
          throw new InvalidRequestException(format("Unable to resolve \"%s\" ", property.getValue()), USER);
        }
      }
      propertyNameEvaluatedMap.put(property.getName(), evaluated);
    }
    return propertyNameEvaluatedMap;
  }

  private boolean isNullString(Object evaluated) {
    return evaluated == null || "null".equals(evaluated);
  }

  private void addToExecutionLog(Optional<ManagerExecutionLogCallback> executionLogCallbackOptional, String msg) {
    if (executionLogCallbackOptional.isPresent()) {
      executionLogCallbackOptional.get().saveExecutionLog("# " + msg);
    }
  }

  @Override
  public void regenerateInfrastructureMappings(
      String provisionerId, ExecutionContext context, Map<String, Object> outputs) {
    regenerateInfrastructureMappings(provisionerId, context, outputs, Optional.empty(), Optional.empty());
  }

  @Override
  public void regenerateInfrastructureMappings(String provisionerId, ExecutionContext context,
      Map<String, Object> outputs, Optional<ManagerExecutionLogCallback> executionLogCallbackOptional,
      Optional<String> region) {
    String appId = context.getAppId();
    final InfrastructureProvisioner infrastructureProvisioner = get(appId, provisionerId);

    final Map<String, Object> contextMap = context.asMap();
    contextMap.put(infrastructureProvisioner.variableKey(), outputs);
    contextMap.putAll(outputs);

    try {
      String infraMappingId = getInfraMappingId(context);
      String infraDefinitionId = getInfraDefinitionId(context);
      if (isEmpty(infraMappingId) && isNotEmpty(infraDefinitionId)) {
        // Inside deployment phase, but mapping not yet generated
        InfrastructureDefinition infrastructureDefinition =
            infrastructureDefinitionService.get(appId, infraDefinitionId);
        addToExecutionLog(executionLogCallbackOptional,
            format("Mapping provisioner outputs to Infra Definition: [%s]", infrastructureDefinition.getName()));
        Map<String, Object> resolvedExpressions =
            resolveExpressions(infrastructureDefinition, contextMap, infrastructureProvisioner);
        ((ProvisionerAware) infrastructureDefinition.getInfrastructure())
            .applyExpressions(resolvedExpressions, appId, infrastructureDefinition.getEnvId(), infraDefinitionId);
        InfrastructureMapping infrastructureMapping = infrastructureDefinitionService.saveInfrastructureMapping(
            getServiceId(context), infrastructureDefinition, context.getWorkflowExecutionId());
        PhaseElement phaseElement =
            context.getContextElement(ContextElementType.PARAM, ExecutionContextImpl.PHASE_PARAM);
        infrastructureMappingService.saveInfrastructureMappingToSweepingOutput(
            appId, context.getWorkflowExecutionId(), phaseElement, infrastructureMapping.getUuid());
        workflowExecutionService.appendInfraMappingId(
            appId, context.getWorkflowExecutionId(), infrastructureMapping.getUuid());
        addToExecutionLog(executionLogCallbackOptional, "Mapping completed successfully");
      }
    } catch (WingsException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new UnexpectedException("Failed while mapping provisioner outputs to Infra Definition", ex);
    }
  }

  private String getServiceId(ExecutionContext context) {
    return ((PhaseElement) context.getContextElement(ContextElementType.PARAM, ExecutionContextImpl.PHASE_PARAM))
        .getServiceElement()
        .getUuid();
  }

  private String getInfraDefinitionId(ExecutionContext context) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, ExecutionContextImpl.PHASE_PARAM);
    return phaseElement == null ? null : phaseElement.getInfraDefinitionId();
  }

  private String getInfraMappingId(ExecutionContext context) {
    return context.fetchInfraMappingId();
  }

  @Override
  public List<AwsCFTemplateParamsData> getCFTemplateParamKeys(String type, String region, String awsConfigId,
      String data, String appId, String sourceRepoSettingId, String sourceRepoBranch, String templatePath,
      String commitId, Boolean useBranch, String repoName) {
    if (type.equalsIgnoreCase(CloudFormationSourceType.GIT.name())) {
      if (isEmpty(sourceRepoSettingId) || (isEmpty(sourceRepoBranch) && isEmpty(commitId))) {
        throw new InvalidRequestException("Empty Fields Connector Id or both Branch and commitID");
      }
    } else if (isEmpty(data)) {
      throw new InvalidRequestException("Empty Data Field, Template body or Template url");
    }
    return awsCFHelperServiceManager.getParamsData(type, data, awsConfigId, region, appId, sourceRepoSettingId,
        sourceRepoBranch, templatePath, commitId, useBranch, repoName);
  }

  @Override
  public List<NameValuePair> getTerraformVariables(String appId, String scmSettingId, String terraformDirectory,
      String accountId, String sourceRepoBranch, String commitId, String repoName,
      TerraformSourceType terraformSourceType, String s3URI, String awsConfigId) {
    terraformDirectory = normalizeScriptPath(terraformDirectory);
    TerraformProvisionParametersBuilder terraformProvisionParameters =
        TerraformProvisionParameters.builder()
            .scriptPath(terraformDirectory)
            .useTfConfigInspectLatestVersion(
                featureFlagService.isEnabled(TERRAFORM_CONFIG_INSPECT_VERSION_SELECTOR, accountId));
    if (featureFlagService.isEnabled(CDS_TERRAFORM_CONFIG_INSPECT_V1_2, accountId)) {
      terraformProvisionParameters.terraformConfigInspectVersion(TfConfigInspectVersion.V1_2);
    }

    if (terraformSourceType.equals(TerraformSourceType.S3)) {
      validateS3Config(awsConfigId, s3URI, scmSettingId);
      SettingAttribute awsS3SettingAttribute = settingService.get(awsConfigId);
      notNullCheck("AWS setting attribute provided is not Valid", awsS3SettingAttribute);
      if (!(awsS3SettingAttribute.getValue() instanceof AwsConfig)) {
        throw new InvalidRequestException("AWS setting attribute provided is not Valid");
      }

      AwsConfig awsS3SourceBucketConfig = (AwsConfig) awsS3SettingAttribute.getValue();

      List<EncryptedDataDetail> awsS3EncryptionDetails = getAwsS3EncryptionDetails(awsS3SourceBucketConfig, appId);
      terraformProvisionParameters.configFilesS3URI(s3URI)
          .configFilesAwsSourceConfig(awsS3SourceBucketConfig)
          .configFileAWSEncryptionDetails(awsS3EncryptionDetails)
          .sourceType(terraformSourceType);
    } else {
      SettingAttribute gitSettingAttribute = settingService.get(scmSettingId);
      notNullCheck("Source repo provided is not Valid", gitSettingAttribute);
      validateBranchCommitId(sourceRepoBranch, commitId);
      if (!(gitSettingAttribute.getValue() instanceof GitConfig)) {
        throw new InvalidRequestException("Source repo provided is not Valid");
      }

      GitConfig gitConfig = (GitConfig) gitSettingAttribute.getValue();
      gitConfigHelperService.setSshKeySettingAttributeIfNeeded(gitConfig);
      gitConfig.setGitRepoType(GitRepositoryType.TERRAFORM);
      gitConfigHelperService.convertToRepoGitConfig(gitConfig, repoName);

      terraformProvisionParameters.sourceRepoSettingId(gitSettingAttribute.getUuid())
          .sourceRepo(gitConfig)
          .sourceRepoEncryptionDetails(secretManager.getEncryptionDetails(gitConfig, appId, null))
          .sourceRepoBranch(sourceRepoBranch)
          .commitId(commitId)
          .isGitHostConnectivityCheck(featureFlagService.isEnabled(GIT_HOST_CONNECTIVITY, accountId));
    }
    String delegateTaskType = getVariablesTaskType(terraformSourceType);

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, appId)
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(delegateTaskType)
                                              .parameters(new Object[] {terraformProvisionParameters.build()})
                                              .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT)
                                              .build())
                                    .build();

    DelegateResponseData notifyResponseData;
    try {
      notifyResponseData = delegateService.executeTaskV2(delegateTask);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InvalidRequestException("Thread was interrupted. Please try again.");
    }
    if (notifyResponseData instanceof ErrorNotifyResponseData) {
      throw new WingsException(((ErrorNotifyResponseData) notifyResponseData).getErrorMessage());
    } else if ((notifyResponseData instanceof RemoteMethodReturnValueData)
        && (((RemoteMethodReturnValueData) notifyResponseData).getException() instanceof InvalidRequestException)) {
      throw(InvalidRequestException)((RemoteMethodReturnValueData) notifyResponseData).getException();
    } else if (!(notifyResponseData instanceof TerraformInputVariablesTaskResponse)) {
      throw new WingsException(ErrorCode.GENERAL_ERROR)
          .addParam("message", "Unknown Response from delegate")
          .addContext(DelegateResponseData.class, notifyResponseData);
    }

    TerraformInputVariablesTaskResponse taskResponse = (TerraformInputVariablesTaskResponse) notifyResponseData;
    if (taskResponse.getTerraformExecutionData().getExecutionStatus() == ExecutionStatus.SUCCESS) {
      return taskResponse.getVariablesList();
    } else {
      throw new GeneralException(taskResponse.getTerraformExecutionData().getErrorMessage());
    }
  }

  private String getVariablesTaskType(TerraformSourceType terraformSourceType) {
    if (terraformSourceType != null && terraformSourceType.equals(TerraformSourceType.S3)) {
      return TaskType.TERRAFORM_INPUT_VARIABLES_OBTAIN_TASK_V2.name();
    }
    return TaskType.TERRAFORM_INPUT_VARIABLES_OBTAIN_TASK.name();
  }

  private String getTargetsTaskType(TerraformSourceType terraformSourceType) {
    if (terraformSourceType != null && terraformSourceType.equals(TerraformSourceType.S3)) {
      return TaskType.TERRAFORM_FETCH_TARGETS_TASK_V2.name();
    }
    return TaskType.TERRAFORM_FETCH_TARGETS_TASK.name();
  }

  private void validateBranchCommitId(String sourceRepoBranch, String commitId) {
    if (isEmpty(sourceRepoBranch) && isEmpty(commitId)) {
      throw new InvalidRequestException("Either sourceRepoBranch or commitId should be specified", USER);
    }
    if (isNotEmpty(sourceRepoBranch) && isNotEmpty(commitId)) {
      throw new InvalidRequestException("Cannot specify both sourceRepoBranch and commitId", USER);
    }
  }

  private void validateS3Config(String awsConfigId, String s3URI, String scmSettingId) {
    if (isNotEmpty(awsConfigId) && isNotEmpty(s3URI) && (isEmpty(scmSettingId) || scmSettingId == null)) {
      return;
    }
    if (isEmpty(awsConfigId) || isEmpty(s3URI)) {
      throw new InvalidRequestException("Both AWS Cloud Provider and S3 URI must be specified", USER);
    }
    if (isNotEmpty(scmSettingId) && isNotEmpty(awsConfigId)) {
      throw new InvalidRequestException("Cannot specify both AWS Cloud Provider and GIT repo", USER);
    }
  }

  @Override
  public List<String> getTerraformTargets(String appId, String accountId, String provisionerId) {
    InfrastructureProvisioner infrastructureProvisioner = get(appId, provisionerId);
    if (infrastructureProvisioner == null) {
      throw new InvalidRequestException("Infra Provisioner not found");
    }
    if (!(infrastructureProvisioner instanceof TerraformInfrastructureProvisioner)) {
      throw new InvalidRequestException("Targets only valid for Terraform Infrastructure Provisioner");
    }
    TerraformInfrastructureProvisioner terraformInfrastructureProvisioner =
        (TerraformInfrastructureProvisioner) infrastructureProvisioner;
    validateTerraformProvisioner(terraformInfrastructureProvisioner);
    if (isTemplatizedProvisioner(terraformInfrastructureProvisioner)) {
      throw new InvalidRequestException("Cannot Fetch Targets Since Terraform Provisioner is templatized",
          ErrorCode.INVALID_TERRAFORM_TARGETS_REQUEST, USER);
    }

    GitConfig gitConfig = null;
    SettingAttribute settingAttribute = null;

    AwsConfig awsS3SourceBucketConfig = null;
    List<EncryptedDataDetail> awsS3EncryptionDetails = null;

    if (terraformInfrastructureProvisioner.getSourceType().equals(TerraformSourceType.S3)) {
      SettingAttribute awsS3SettingAttribute = settingService.get(terraformInfrastructureProvisioner.getAwsConfigId());
      notNullCheck("AWS setting attribute provided is not Valid", awsS3SettingAttribute);
      if (!(awsS3SettingAttribute.getValue() instanceof AwsConfig)) {
        throw new InvalidRequestException("AWS setting attribute provided is not Valid");
      }

      awsS3SourceBucketConfig = (AwsConfig) awsS3SettingAttribute.getValue();
      awsS3EncryptionDetails = getAwsS3EncryptionDetails(awsS3SourceBucketConfig, appId);
    } else {
      settingAttribute = settingService.get(terraformInfrastructureProvisioner.getSourceRepoSettingId());
      if (settingAttribute == null || !(settingAttribute.getValue() instanceof GitConfig)) {
        throw new InvalidRequestException("Invalid Git Repo");
      }
      gitConfig = (GitConfig) settingAttribute.getValue();
      gitConfig.setGitRepoType(GitRepositoryType.TERRAFORM);
      gitConfigHelperService.convertToRepoGitConfig(gitConfig, terraformInfrastructureProvisioner.getRepoName());
    }
    String delegateTaskType =
        getTargetsTaskType(((TerraformInfrastructureProvisioner) infrastructureProvisioner).getSourceType());

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, appId)
            .data(
                TaskData.builder()
                    .async(false)
                    .taskType(delegateTaskType)
                    .parameters(new Object[] {
                        TerraformProvisionParameters.builder()
                            .sourceRepoSettingId(settingAttribute != null ? settingAttribute.getUuid() : null)
                            .useTfConfigInspectLatestVersion(
                                featureFlagService.isEnabled(TERRAFORM_CONFIG_INSPECT_VERSION_SELECTOR, accountId))
                            .sourceRepo(gitConfig)
                            .sourceRepoBranch(terraformInfrastructureProvisioner.getSourceRepoBranch())
                            .commitId(terraformInfrastructureProvisioner.getCommitId())
                            .scriptPath(terraformInfrastructureProvisioner.getPath() != null
                                    ? normalizeScriptPath(terraformInfrastructureProvisioner.getPath())
                                    : null)
                            .sourceRepoEncryptionDetails(
                                gitConfig != null ? secretManager.getEncryptionDetails(gitConfig, appId, null) : null)
                            .isGitHostConnectivityCheck(featureFlagService.isEnabled(GIT_HOST_CONNECTIVITY, accountId))
                            .sourceType(terraformInfrastructureProvisioner.getSourceType())
                            .configFilesS3URI(terraformInfrastructureProvisioner.getS3URI())
                            .configFilesAwsSourceConfig(awsS3SourceBucketConfig)
                            .configFileAWSEncryptionDetails(awsS3EncryptionDetails)
                            .terraformConfigInspectVersion(
                                featureFlagService.isEnabled(CDS_TERRAFORM_CONFIG_INSPECT_V1_2, accountId)
                                    ? TfConfigInspectVersion.V1_2
                                    : null)
                            .build()})
                    .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT)
                    .build())
            .build();
    DelegateResponseData responseData;
    try {
      responseData = delegateService.executeTaskV2(delegateTask);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new WingsException("Thread was interrupted. Please try again.");
    }
    if (responseData instanceof ErrorNotifyResponseData) {
      throw new WingsException(((ErrorNotifyResponseData) responseData).getErrorMessage());
    } else if (responseData instanceof RemoteMethodReturnValueData
        && ((RemoteMethodReturnValueData) responseData).getException() instanceof InvalidRequestException) {
      throw(InvalidRequestException)((RemoteMethodReturnValueData) responseData).getException();
    } else if (!(responseData instanceof TerraformExecutionData)) {
      throw new WingsException("Unknown response from delegate.").addContext(DelegateResponseData.class, responseData);
    }
    return ((TerraformExecutionData) responseData).getTargets();
  }

  private List<EncryptedDataDetail> getAwsS3EncryptionDetails(AwsConfig awsS3SourceBucketConfig, String appId) {
    return secretManager.getEncryptionDetails(awsS3SourceBucketConfig, appId, null);
  }

  private AwsConfig getAWSS3SourceConfig(String awsConfigId) {
    return (AwsConfig) getAwsConfigSettingAttribute(awsConfigId).getValue();
  }

  private SettingAttribute getAwsConfigSettingAttribute(String awsConfigId) {
    SettingAttribute awsSettingAttribute = settingsService.get(awsConfigId);
    if (awsSettingAttribute == null) {
      throw new InvalidRequestException("Could not find AwsSettingAttribute for Id: " + awsConfigId);
    }
    if (!(awsSettingAttribute.getValue() instanceof AwsConfig)) {
      throw new InvalidRequestException("Setting attribute is not of type AwsConfig");
    }
    return awsSettingAttribute;
  }

  @Override
  public boolean isTemplatizedProvisioner(TerraGroupProvisioners infrastructureProvisioner) {
    return (isNotEmpty(infrastructureProvisioner.getSourceRepoBranch())
               && infrastructureProvisioner.getSourceRepoBranch().charAt(0) == '$')
        || (isNotEmpty(infrastructureProvisioner.getPath()) && infrastructureProvisioner.getPath().charAt(0) == '$');
  }

  private void validateTerraformProvisioner(TerraformInfrastructureProvisioner terraformProvisioner) {
    if (terraformProvisioner.getSourceType() != null
        && terraformProvisioner.getSourceType().equals(TerraformSourceType.S3)) {
      validateS3Config(terraformProvisioner.getAwsConfigId(), terraformProvisioner.getS3URI(),
          terraformProvisioner.getSourceRepoSettingId());
    } else {
      validateSourceRepoConfig(terraformProvisioner.getSourceRepoBranch(), terraformProvisioner.getCommitId(),
          terraformProvisioner.getPath(), terraformProvisioner.getRepoName(),
          terraformProvisioner.getSourceRepoSettingId());
    }

    ensureNoDuplicateVars(terraformProvisioner.getBackendConfigs());
    ensureNoDuplicateVars(terraformProvisioner.getEnvironmentVariables());
    ensureSelectedSecretManagerExist(terraformProvisioner.getAccountId(), terraformProvisioner.getKmsId());

    boolean areVariablesValid = areKeysMongoCompliant(terraformProvisioner.getVariables(),
        terraformProvisioner.getBackendConfigs(), terraformProvisioner.getEnvironmentVariables());
    if (!areVariablesValid) {
      throw new InvalidRequestException("The following characters are not allowed in terraform "
          + "variable names: . and $");
    }
  }

  private void ensureSelectedSecretManagerExist(@NotNull String accountId, String secretManagerId) {
    if (isEmpty(secretManagerId)) {
      return;
    }
    if (secretManager.getSecretManager(accountId, secretManagerId) == null) {
      throw new InvalidRequestException(
          format("No secret manger found with id: %s", secretManagerId), WingsException.USER);
    }
  }

  private void validateTerragruntProvisioner(TerragruntInfrastructureProvisioner provisioner) {
    validateSourceRepoConfig(provisioner.getSourceRepoBranch(), provisioner.getCommitId(), provisioner.getPath(),
        provisioner.getRepoName(), provisioner.getSourceRepoSettingId());
    ensureSelectedSecretManagerExist(provisioner.getAccountId(), provisioner.getSecretManagerId());
  }

  private void validateSourceRepoConfig(
      String sourceRepoBranch, String commitId, String path, String repoName, String sourceRepoSettingId) {
    validateBranchCommitId(sourceRepoBranch, commitId);
    if (path == null) {
      throw new InvalidRequestException("Provisioner path cannot be null");
    } else if (isEmpty(sourceRepoSettingId)) {
      throw new InvalidRequestException("Provisioner should have a source repo");
    }
    GitConfig gitConfig = gitUtilsManager.getGitConfig(sourceRepoSettingId);
    if (gitConfig.getUrlType() == GitConfig.UrlType.ACCOUNT && isEmpty(repoName)) {
      throw new InvalidRequestException("Repo name cannot be empty for account level git connector");
    }
  }

  private void validateCloudFormationProvisioner(CloudFormationInfrastructureProvisioner cloudFormationProvisioner) {
    if (cloudFormationProvisioner.provisionByGit()) {
      gitFileConfigHelperService.validate(cloudFormationProvisioner.getGitFileConfig());
    } else if (cloudFormationProvisioner.provisionByUrl()) {
      if (isBlank(cloudFormationProvisioner.getTemplateFilePath())) {
        throw new InvalidRequestException("Template File Path can not be empty", USER);
      }
    } else if (cloudFormationProvisioner.provisionByBody()) {
      if (isBlank(cloudFormationProvisioner.getTemplateBody())) {
        throw new InvalidRequestException("Template Body can not be empty", USER);
      }
    }
  }

  private void validateShellScriptProvisioner(ShellScriptInfrastructureProvisioner shellScriptProvisioner) {
    if (isBlank(shellScriptProvisioner.getScriptBody())) {
      throw new InvalidRequestException("Script Body can not be empty", USER);
    }
  }

  private boolean areKeysMongoCompliant(List<NameValuePair>... variables) {
    Predicate<String> terraformVariableNameCheckFail = value -> value.contains(".") || value.contains("$");
    return Stream.of(variables)
        .filter(EmptyPredicate::isNotEmpty)
        .flatMap(Collection::stream)
        .map(NameValuePair::getName)
        .noneMatch(terraformVariableNameCheckFail);
  }

  private String normalizeScriptPath(String terraformDirectory) {
    return FilenameUtils.normalize(terraformDirectory);
  }

  @Override
  public StreamingOutput downloadTerraformState(String provisionerId, String envId) {
    String entityId = provisionerId + "-" + envId;
    String latestFileId = fileService.getLatestFileId(entityId, FileBucket.TERRAFORM_STATE);
    if (latestFileId == null) {
      throw new InvalidRequestException("No state file found");
    }
    return outputStream -> fileService.downloadToStream(latestFileId, outputStream, FileBucket.TERRAFORM_STATE);
  }

  @Override
  public ShellScriptInfrastructureProvisioner getShellScriptProvisioner(String appId, String provisionerId) {
    InfrastructureProvisioner infrastructureProvisioner = get(appId, provisionerId);
    if (!(infrastructureProvisioner instanceof ShellScriptInfrastructureProvisioner)) {
      throw new InvalidRequestException("Provisioner not of type Shell Script");
    }
    return (ShellScriptInfrastructureProvisioner) infrastructureProvisioner;
  }

  @Override
  public Map<String, String> extractUnresolvedTextVariables(List<NameValuePair> variables) {
    if (isEmpty(variables)) {
      return Collections.emptyMap();
    }
    return variables.stream()
        .filter(entry -> entry.getValue() != null)
        .filter(entry -> "TEXT".equals(entry.getValueType()))
        .collect(toMap(NameValuePair::getName, NameValuePair::getValue));
  }

  @Override
  public Map<String, String> extractTextVariables(List<NameValuePair> variables, ExecutionContext context) {
    if (isEmpty(variables)) {
      return Collections.emptyMap();
    }

    Map<String, String> map = new HashMap<>();
    for (NameValuePair entry : variables) {
      if (entry.getValue() != null && "TEXT".equals(entry.getValueType())) {
        map.merge(entry.getName(), context.renderExpression(entry.getValue()),
            (a, b) -> { throw new IllegalStateException(format("Duplicate key: %s", entry.getName())); });
      }
    }
    return map;
  }

  private EncryptedDataDetail getValue(String accountId, String workflowExecutionId, NameValuePair entry) {
    Optional<EncryptedDataDetail> encryptedDataDetailOptional =
        secretManager.encryptedDataDetails(accountId, null, entry.getValue(), workflowExecutionId);

    return encryptedDataDetailOptional.orElseThrow(
        () -> new InvalidRequestException(format("The encrypted variable %s was not found", entry.getName()), USER));
  }

  @Override
  public Map<String, EncryptedDataDetail> extractEncryptedTextVariables(
      List<NameValuePair> variables, String appId, String workflowExecutionId) {
    if (isEmpty(variables)) {
      return Collections.emptyMap();
    }
    String accountId = appService.getAccountIdByAppId(appId);

    Map<String, EncryptedDataDetail> map = new HashMap<>();
    for (NameValuePair entry : variables) {
      if (entry.getValue() != null && "ENCRYPTED_TEXT".equals(entry.getValueType())) {
        map.merge(entry.getName(), getValue(accountId, workflowExecutionId, entry),
            (a, b) -> { throw new IllegalStateException(format("Duplicate encrypted key: %s", entry.getName())); });
      }
    }
    return map;
  }

  @Override
  public String getEntityId(String provisionerId, String envId) {
    return provisionerId + "-" + envId;
  }

  @Override
  public ManagerExecutionLogCallback getManagerExecutionCallback(
      String appId, String activityId, String commandUnitName) {
    Builder logBuilder = Builder.aLog().commandUnitName(commandUnitName).appId(appId).activityId(activityId);
    return new ManagerExecutionLogCallback(logService, logBuilder, activityId);
  }

  @Override
  public Map<String, Object> resolveExpressions(InfrastructureDefinition infrastructureDefinition,
      Map<String, Object> contextMap, InfrastructureProvisioner infrastructureProvisioner) {
    List<BlueprintProperty> properties = getBlueprintProperties(infrastructureDefinition);
    addProvisionerKeys(properties, infrastructureProvisioner);
    return resolveProperties(
        contextMap, properties, Optional.empty(), Optional.empty(), infrastructureProvisioner.variableKey());
  }

  /**
   * Currently setting up only for Terraform Infra provisioner.
   * The purpose is to not allow creation of InfrastructureMapping if rendering fails for provisioner specific
   * variable key during PhaseSubStepWorkflow.
   * @param infrastructureProvisioner
   * @param resolvedExpressions
   * @return False if expressions contains provisioner specific variable key else True.
   */
  @Override
  public boolean areExpressionsValid(
      InfrastructureProvisioner infrastructureProvisioner, Map<String, Object> resolvedExpressions) {
    if (isNotEmpty(resolvedExpressions)
        && TerraformInfrastructureProvisioner.VARIABLE_KEY.equals(infrastructureProvisioner.variableKey())) {
      for (Object val : resolvedExpressions.values()) {
        if (val instanceof String) {
          String value = (String) val;
          if (EXPRESSION_PATTERN.matcher(value).find()
              && featureFlagService.isEnabled(
                  VALIDATE_PROVISIONER_EXPRESSION, infrastructureProvisioner.getAccountId())) {
            return false;
          }
        }
      }
    }
    return true;
  }

  void addProvisionerKeys(List<BlueprintProperty> properties, InfrastructureProvisioner infrastructureProvisioner) {
    if (infrastructureProvisioner instanceof ShellScriptInfrastructureProvisioner) {
      properties.forEach(property -> {
        if (property.getValue() != null) {
          property.setValue(format("${%s.%s}", infrastructureProvisioner.variableKey(), property.getValue()));
          if (isNotEmpty(property.getFields())) {
            property.getFields().forEach(field -> field.setValue(format("${%s}", field.getValue())));
          }
        }
      });
    }
  }

  List<BlueprintProperty> getBlueprintProperties(InfrastructureDefinition infrastructureDefinition) {
    List<BlueprintProperty> properties = new ArrayList<>();
    Map<String, String> expressions =
        ((ProvisionerAware) infrastructureDefinition.getInfrastructure()).getExpressions();
    if (infrastructureDefinition.getInfrastructure() instanceof PhysicalInfra) {
      BlueprintProperty hostArrayPathProperty = BlueprintProperty.builder()
                                                    .name(PhysicalInfra.hostArrayPath)
                                                    .value(expressions.get(PhysicalInfra.hostArrayPath))
                                                    .build();
      List<NameValuePair> fields =
          expressions.entrySet()
              .stream()
              .filter(expression -> !expression.getKey().equals(PhysicalInfra.hostArrayPath))
              .map(expression -> NameValuePair.builder().name(expression.getKey()).value(expression.getValue()).build())
              .collect(toList());
      hostArrayPathProperty.setFields(fields);
      properties.add(hostArrayPathProperty);

    } else {
      expressions.entrySet()
          .stream()
          .map(expression -> BlueprintProperty.builder().name(expression.getKey()).value(expression.getValue()).build())
          .forEach(properties::add);
    }
    return properties;
  }
}
