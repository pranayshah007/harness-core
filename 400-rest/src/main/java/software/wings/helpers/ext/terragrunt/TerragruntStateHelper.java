/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.provision.TerraformConstants.BACKEND_CONFIGS_KEY;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.utils.Utils.splitCommaSeparatedFilePath;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SweepingOutputInstance;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.FileMetadata;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HIterator;
import io.harness.provision.TfVarScriptRepositorySource;
import io.harness.provision.TfVarSource;
import io.harness.provision.TfVarSource.TfVarSourceType;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.api.terraform.TfVarGitSource;
import software.wings.api.terragrunt.TerragruntApplyMarkerParam;
import software.wings.api.terragrunt.TerragruntExecutionData;
import software.wings.api.terragrunt.TerragruntOutputVariables;
import software.wings.app.MainConfiguration;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.TerragruntInfrastructureProvisioner;
import software.wings.beans.delegation.TerragruntProvisionParameters;
import software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand;
import software.wings.beans.infrastructure.TerraformConfig;
import software.wings.beans.infrastructure.TerraformConfig.TerraformConfigKeys;
import software.wings.beans.infrastructure.instance.TerragruntConfig;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.GitFileConfigHelperService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.utils.GitUtilsManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonStreamParser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.validation.constraints.NotNull;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDP)
@Singleton
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class TerragruntStateHelper {
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private InfrastructureProvisionerService infrastructureProvisionerService;
  @Inject private SecretManagerConfigService secretManagerConfigService;
  @Inject private GitUtilsManager gitUtilsManager;
  @Inject protected MainConfiguration configuration;
  @Inject private GitConfigHelperService gitConfigHelperService;
  @Inject private GitFileConfigHelperService gitFileConfigHelperService;
  @Inject private SecretManager secretManager;
  @Inject protected WingsPersistence wingsPersistence;
  @Inject protected FeatureFlagService featureFlagService;

  private static final String DUPLICATE_VAR_MSG_PREFIX =
      "variable names should be unique, duplicate variable(s) found: ";

  public static Map<String, Object> parseTerragruntOutputs(String all) {
    Map<String, Object> parsedOutputs = new LinkedHashMap<>();
    if (isBlank(all)) {
      return parsedOutputs;
    }
    InputStream inputStream = IOUtils.toInputStream(all, StandardCharsets.UTF_8);
    Reader streamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
    Gson gson = new GsonBuilder().create();
    JsonStreamParser jsonStreamParser = new JsonStreamParser(streamReader);

    while (jsonStreamParser.hasNext()) {
      JsonElement element = jsonStreamParser.next();
      if (element.isJsonObject()) {
        Map<String, Object> mapObjectfromJson = gson.fromJson(element, Map.class);
        // to remove exception due to mapObjectfromJson being object of LinkedTreeMap.class
        mapObjectfromJson.forEach((key, object) -> parsedOutputs.put(key, ((Map<String, Object>) object).get("value")));
      }
    }

    return parsedOutputs;
  }

  public static String getMarkerName(String provisionerId, String pathToModule) {
    return format("tfApplyCompleted_%s_%s", provisionerId, pathToModule).trim();
  }

  public void markApplyExecutionCompleted(ExecutionContext context, String provisionerId, String pathToModue) {
    String markerName = getMarkerName(provisionerId, pathToModue);
    SweepingOutputInstance sweepingOutputInstance =
        sweepingOutputService.find(context.prepareSweepingOutputInquiryBuilder().name(markerName).build());
    if (sweepingOutputInstance != null) {
      return;
    }
    sweepingOutputInstance = context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
                                 .name(markerName)
                                 .value(TerragruntApplyMarkerParam.builder()
                                            .applyCompleted(true)
                                            .provisionerId(provisionerId)
                                            .pathToModule(pathToModue)
                                            .build())
                                 .build();
    sweepingOutputService.save(sweepingOutputInstance);
  }

  public GitConfig populateAndGetGitConfig(
      ExecutionContext context, TerragruntInfrastructureProvisioner terragruntProvisioner) {
    GitConfig gitConfig = gitUtilsManager.getGitConfig(terragruntProvisioner.getSourceRepoSettingId());
    String branch = context.renderExpression(terragruntProvisioner.getSourceRepoBranch());
    if (isNotEmpty(branch)) {
      gitConfig.setBranch(branch);
    }
    if (isNotEmpty(terragruntProvisioner.getCommitId())) {
      String commitId = context.renderExpression(terragruntProvisioner.getCommitId());
      if (isNotEmpty(commitId)) {
        gitConfig.setReference(commitId);
      }
    }
    return gitConfig;
  }

  public static List<String> resolveTargets(List<String> targets, ExecutionContext context) {
    if (isEmpty(targets)) {
      return targets;
    }
    return targets.stream().map(context::renderExpression).collect(toList());
  }

  private static boolean isRunAndExportEncryptedPlan(boolean runPlanOnly, boolean exportPlanToApplyStep) {
    return runPlanOnly && exportPlanToApplyStep;
  }

  private static boolean isInheritingEncryptedPlan(boolean runPlanOnly, boolean inheritApprovedPlan) {
    return !runPlanOnly && inheritApprovedPlan;
  }

  private static boolean isExportingDestroyPlan(boolean runPlanOnly, TerragruntCommand command) {
    return runPlanOnly && (TerragruntProvisionParameters.TerragruntCommand.DESTROY == command);
  }

  public static boolean isSecretManagerRequired(boolean runPlanOnly, boolean exportPlanToApplyStep,
      boolean inheritApprovedPlan, boolean runAll, TerragruntCommand command) {
    return (isRunAndExportEncryptedPlan(runPlanOnly, exportPlanToApplyStep)
               || isInheritingEncryptedPlan(runPlanOnly, inheritApprovedPlan)
               || isExportingDestroyPlan(runPlanOnly, command))
        && !runAll;
  }

  public SecretManagerConfig getSecretManagerContainingTfPlan(String secretManagerId, String accountId) {
    SecretManagerConfig secretManagerConfig = isEmpty(secretManagerId)
        ? secretManagerConfigService.getDefaultSecretManager(accountId)
        : secretManagerConfigService.getSecretManager(accountId, secretManagerId, false);

    if (featureFlagService.isEnabled(FeatureName.CDS_TERRAFORM_TERRAGRUNT_PLAN_ENCRYPTION_ON_MANAGER_CG, accountId)
        && isHarnessSecretManager(secretManagerConfig)) {
      removeCredFromHarnessSM(secretManagerConfig);
    }
    return secretManagerConfig;
  }

  public boolean isHarnessSecretManager(SecretManagerConfig secretManagerConfig) {
    if (secretManagerConfig != null) {
      return secretManagerConfig.isGlobalKms();
    } else {
      return false;
    }
  }

  private void removeCredFromHarnessSM(SecretManagerConfig secretManagerConfig) {
    secretManagerConfig.maskSecrets();
  }

  public static List<String> getRenderedTaskTags(String rawTag, ExecutionContextImpl executionContext) {
    if (isEmpty(rawTag)) {
      return null;
    }
    return singletonList(executionContext.renderExpression(rawTag));
  }

  public static String handleDefaultWorkspace(String workspace) {
    // Default is as good as no workspace
    return isNotEmpty(workspace) && workspace.equals("default") ? null : workspace;
  }

  public Map<String, String> extractData(FileMetadata fileMetadata, String dataKey) {
    Map<String, Object> rawData = (Map<String, Object>) fileMetadata.getMetadata().get(dataKey);
    if (isNotEmpty(rawData)) {
      return infrastructureProvisionerService.extractUnresolvedTextVariables(extractVariables(rawData, "TEXT"));
    }
    return null;
  }

  public Map<String, EncryptedDataDetail> extractEncryptedData(
      ExecutionContext context, FileMetadata fileMetadata, String encryptedDataKey) {
    Map<String, Object> rawData = (Map<String, Object>) fileMetadata.getMetadata().get(encryptedDataKey);
    Map<String, EncryptedDataDetail> encryptedData = null;
    if (isNotEmpty(rawData)) {
      encryptedData = infrastructureProvisionerService.extractEncryptedTextVariables(
          extractVariables(rawData, "ENCRYPTED_TEXT"), context.getAppId(), context.getWorkflowExecutionId());
    }
    return encryptedData;
  }

  public Map<String, String> extractBackendConfigs(FileMetadata fileMetadata) {
    Map<String, Object> rawBackendConfigs = (Map<String, Object>) fileMetadata.getMetadata().get(BACKEND_CONFIGS_KEY);
    if (isNotEmpty(rawBackendConfigs)) {
      return infrastructureProvisionerService.extractUnresolvedTextVariables(
          extractVariables(rawBackendConfigs, "TEXT"));
    }
    return null;
  }

  public static List<NameValuePair> extractVariables(Map<String, Object> variables, String valueType) {
    return variables.entrySet()
        .stream()
        .map(entry
            -> NameValuePair.builder()
                   .valueType(valueType)
                   .name(entry.getKey())
                   .value((String) entry.getValue())
                   .build())
        .collect(toList());
  }

  public static TfVarScriptRepositorySource fetchTfVarScriptRepositorySource(
      ExecutionContext context, List<String> tfVarFiles) {
    return TfVarScriptRepositorySource.builder().tfVarFilePaths(getRenderedTfVarFiles(tfVarFiles, context)).build();
  }

  public TfVarGitSource fetchTfVarGitSource(ExecutionContext context, GitFileConfig tfVarGitFileConfig) {
    GitConfig tfVarGitConfig = gitUtilsManager.getGitConfig(tfVarGitFileConfig.getConnectorId());
    gitConfigHelperService.renderGitConfig(context, tfVarGitConfig);
    gitFileConfigHelperService.renderGitFileConfig(context, tfVarGitFileConfig);

    gitConfigHelperService.convertToRepoGitConfig(
        tfVarGitConfig, context.renderExpression(tfVarGitFileConfig.getRepoName()));
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails(tfVarGitConfig, GLOBAL_APP_ID, context.getWorkflowExecutionId());

    String filePath = tfVarGitFileConfig.getFilePath();

    if (isNotEmpty(filePath)) {
      List<String> multipleFiles = splitCommaSeparatedFilePath(filePath);
      tfVarGitFileConfig.setFilePathList(multipleFiles);
    }

    return TfVarGitSource.builder()
        .gitConfig(tfVarGitConfig)
        .encryptedDataDetails(encryptionDetails)
        .gitFileConfig(tfVarGitFileConfig)
        .build();
  }

  public static List<String> getRenderedTfVarFiles(List<String> tfVarFiles, ExecutionContext context) {
    if (isEmpty(tfVarFiles)) {
      return tfVarFiles;
    }
    return tfVarFiles.stream().map(context::renderExpression).collect(toList());
  }

  // to be used in Terraform
  public void collectVariables(Map<String, Object> others, List<NameValuePair> nameValuePairList, String varsKey,
      String encyptedVarsKey, boolean valueTypeCanBeNull) {
    if (isNotEmpty(nameValuePairList)) {
      others.put(varsKey,
          nameValuePairList.stream()
              .filter(item -> item.getValue() != null)
              .filter(item -> (valueTypeCanBeNull && item.getValueType() == null) || "TEXT".equals(item.getValueType()))
              .collect(toMap(NameValuePair::getName, NameValuePair::getValue)));
      others.put(encyptedVarsKey,
          nameValuePairList.stream()
              .filter(item -> item.getValue() != null)
              .filter(item -> "ENCRYPTED_TEXT".equals(item.getValueType()))
              .collect(toMap(NameValuePair::getName, NameValuePair::getValue)));
    }
  }

  public void saveOutputs(ExecutionContext context, Map<String, Object> outputs) {
    SweepingOutputInstance instance = sweepingOutputService.find(
        context.prepareSweepingOutputInquiryBuilder().name(TerragruntOutputVariables.SWEEPING_OUTPUT_NAME).build());
    TerragruntOutputVariables terragruntOutputVariables =
        instance != null ? (TerragruntOutputVariables) instance.getValue() : new TerragruntOutputVariables();

    terragruntOutputVariables.putAll(outputs);

    if (instance != null) {
      sweepingOutputService.deleteById(context.getAppId(), instance.getUuid());
    }

    sweepingOutputService.save(context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
                                   .name(TerragruntOutputVariables.SWEEPING_OUTPUT_NAME)
                                   .value(terragruntOutputVariables)
                                   .build());
  }

  // Todo: save this element via sweeping output
  public void saveTerragruntConfig(
      ExecutionContext context, String sourceRepoSettingId, TerragruntExecutionData executionData, String enitityId) {
    TfVarSource tfVarSource = executionData.getTfVarSource();
    GitFileConfig gitFileConfig = null != tfVarSource && tfVarSource.getTfVarSourceType() == TfVarSourceType.GIT
        ? ((TfVarGitSource) tfVarSource).getGitFileConfig()
        : null;

    TerragruntConfig terragruntConfig = (TerragruntConfig) TerragruntConfig.builder()
                                            .entityId(enitityId)
                                            .sourceRepoSettingId(sourceRepoSettingId)
                                            .sourceRepoReference(executionData.getSourceRepoReference())
                                            .variables(executionData.getVariables())
                                            .delegateTag(executionData.getDelegateTag())
                                            .backendConfigs(executionData.getBackendConfigs())
                                            .environmentVariables(executionData.getEnvironmentVariables())
                                            .tfVarFiles(executionData.getTfVarFiles())
                                            .tfVarGitFileConfig(gitFileConfig)
                                            .workflowExecutionId(context.getWorkflowExecutionId())
                                            .targets(executionData.getTargets())
                                            .terragruntCommand(executionData.getCommandExecuted())
                                            .pathToModule(executionData.getPathToModule())
                                            .runAll(executionData.isRunAll())
                                            .appId(context.getAppId())
                                            .accountId(context.getAccountId())
                                            .build();
    wingsPersistence.save(terragruntConfig);
  }

  public void deleteTerragruntConfig(String entityId) {
    Query<TerraformConfig> query =
        wingsPersistence.createQuery(TerraformConfig.class).filter(TerraformConfigKeys.entityId, entityId);

    wingsPersistence.delete(query);
  }

  public void deleteTerragruntConfiguUsingOekflowExecutionId(ExecutionContext context, String entityId) {
    Query<TerraformConfig> query =
        wingsPersistence.createQuery(TerraformConfig.class)
            .filter(TerraformConfigKeys.entityId, entityId)
            .filter(TerraformConfigKeys.workflowExecutionId, context.getWorkflowExecutionId());
    wingsPersistence.delete(query);
  }

  /**
   * @param configParameter of the last successful workflow execution.
   * @param executionContext context.
   * @return last successful workflow execution url.
   */
  @NotNull
  public StringBuilder getLastSuccessfulWorkflowExecutionUrl(
      TerraformConfig configParameter, ExecutionContextImpl executionContext) {
    return new StringBuilder()
        .append(configuration.getPortal().getUrl())
        .append("/#/account/")
        .append(configParameter.getAccountId())
        .append("/app/")
        .append(configParameter.getAppId())
        .append("/env/")
        .append(executionContext.getEnv() != null ? executionContext.getEnv().getUuid() : "null")
        .append("/executions/")
        .append(configParameter.getWorkflowExecutionId())
        .append("/details");
  }

  public TfVarSource getTfVarSource(
      ExecutionContext context, List<String> tfVarFiles, GitFileConfig tfVarGitFileConfig) {
    TfVarSource tfVarSource = null;
    if (isNotEmpty(tfVarFiles)) {
      tfVarSource = fetchTfVarScriptRepositorySource(context, tfVarFiles);
    } else if (null != tfVarGitFileConfig) {
      tfVarSource = fetchTfVarGitSource(context, tfVarGitFileConfig);
    }
    return tfVarSource;
  }

  public HIterator<TerraformConfig> getSavedTerraformConfig(String appId, String entityId) {
    return new HIterator(wingsPersistence.createQuery(TerraformConfig.class)
                             .filter(TerraformConfigKeys.appId, appId)
                             .filter(TerraformConfigKeys.entityId, entityId)
                             .order(Sort.descending(TerraformConfigKeys.createdAt))
                             .fetch());
  }

  public GitConfig getGitConfigAndPopulate(TerraformConfig terraformConfig, String branch) {
    final GitConfig gitConfig = gitUtilsManager.getGitConfig(terraformConfig.getSourceRepoSettingId());
    if (StringUtils.isNotEmpty(terraformConfig.getSourceRepoReference())) {
      gitConfig.setReference(terraformConfig.getSourceRepoReference());
      if (isNotEmpty(branch)) {
        gitConfig.setBranch(branch);
      }
    }
    return gitConfig;
  }

  public static void validateTerragruntVariables(
      List<NameValuePair> variables, List<NameValuePair> backendConfigs, List<NameValuePair> environmentVariables) {
    ensureNoDuplicateVars(variables);
    ensureNoDuplicateVars(backendConfigs);
    ensureNoDuplicateVars(environmentVariables);
    boolean areVariablesValid = areKeysMongoCompliant(variables, backendConfigs, environmentVariables);
    if (!areVariablesValid) {
      throw new InvalidRequestException("The following characters are not allowed in terragrunt "
          + "variable names: . and $");
    }
  }

  private static void ensureNoDuplicateVars(List<NameValuePair> variables) {
    if (isEmpty(variables)) {
      return;
    }

    Set<String> distinctVariableNames = new HashSet<>();
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

  private static boolean areKeysMongoCompliant(List<NameValuePair>... variables) {
    Predicate<String> terraformVariableNameCheckFail = value -> value.contains(".") || value.contains("$");
    return Stream.of(variables)
        .filter(EmptyPredicate::isNotEmpty)
        .flatMap(Collection::stream)
        .map(NameValuePair::getName)
        .noneMatch(terraformVariableNameCheckFail);
  }
}
