/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.utils.CaseFormat.CAMEL_CASE;
import static io.harness.ngmigration.utils.CaseFormat.LOWER_CASE;
import static io.harness.ngmigration.utils.CaseFormat.SNAKE_CASE;
import static io.harness.ngmigration.utils.NGMigrationConstants.PLEASE_FIX_ME;
import static io.harness.when.beans.WhenConditionStatus.SUCCESS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.network.Http;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.ngmigration.beans.BaseProvidedInput;
import io.harness.ngmigration.beans.FileYamlDTO;
import io.harness.ngmigration.beans.InputDefaults;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.expressions.step.StepExpressionFunctor;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.serializer.JsonUtils;
import io.harness.steps.wait.WaitStepInfo;
import io.harness.steps.wait.WaitStepNode;
import io.harness.when.beans.StepWhenCondition;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.GitFileConfig;
import software.wings.beans.GraphNode;
import software.wings.beans.HarnessTagLink;
import software.wings.beans.PhaseStep;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.container.ContainerTask;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.NGMigrationEntityType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Sets;
import io.serializer.HObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.CaseUtils;
import org.apache.commons.validator.routines.UrlValidator;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class MigratorUtility {
  public static final ParameterField<String> RUNTIME_INPUT =
      ParameterField.createValueField(NGMigrationConstants.RUNTIME_INPUT);
  public static final ParameterField<List<TaskSelectorYaml>> RUNTIME_DELEGATE_INPUT =
      ParameterField.createExpressionField(true, NGMigrationConstants.RUNTIME_INPUT, null, false);
  public static final ParameterField<Boolean> RUNTIME_BOOLEAN_INPUT =
      ParameterField.createExpressionField(true, NGMigrationConstants.RUNTIME_INPUT, null, false);

  public static final Pattern cgPattern = Pattern.compile("\\$\\{[\\w-.\"()]+}");
  public static final Pattern ngPattern = Pattern.compile("<\\+[\\w-.\"()]+>");

  private static final String[] schemes = {"https", "http"};

  private MigratorUtility() {}

  public static <T> T getRestClient(ServiceHttpClientConfig ngClientConfig, Class<T> clazz) {
    OkHttpClient okHttpClient = Http.getOkHttpClient(ngClientConfig.getBaseUrl(), false);
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(ngClientConfig.getBaseUrl())
                            .addConverterFactory(JacksonConverterFactory.create(HObjectMapper.NG_DEFAULT_OBJECT_MAPPER))
                            .build();
    return retrofit.create(clazz);
  }

  public static String generateManifestIdentifier(String name, CaseFormat caseFormat) {
    return generateIdentifier(name, caseFormat);
  }

  public static String generateIdentifier(String name, CaseFormat caseFormat) {
    String identifier = generateCamelCaseIdentifier(name);
    if (LOWER_CASE == caseFormat) {
      return identifier.toLowerCase();
    }
    if (SNAKE_CASE == caseFormat) {
      return generateSnakeCaseIdentifier(name);
    }
    return identifier;
  }

  public static String generateCamelCaseIdentifier(String name) {
    if (StringUtils.isBlank(name)) {
      return "";
    }
    name = StringUtils.stripAccents(name);
    String generated = CaseUtils.toCamelCase(name.replaceAll("[^A-Za-z0-9]", " ").trim(), false, ' ');
    return Character.isDigit(generated.charAt(0)) ? "_" + generated : generated;
  }

  private static String generateSnakeCaseIdentifier(String name) {
    if (StringUtils.isBlank(name)) {
      return "";
    }
    name = StringUtils.stripAccents(name).toLowerCase();
    StringBuilder snakeCase = new StringBuilder();

    for (char c : name.toCharArray()) {
      if (Character.isLetterOrDigit(c)) {
        snakeCase.append(c);
      } else {
        snakeCase.append('_');
      }
    }
    String generated = snakeCase.toString();
    return Character.isDigit(generated.charAt(0)) ? "_" + generated : generated;
  }

  public static ParameterField<Timeout> getTimeout(Integer timeoutInMillis) {
    if (timeoutInMillis == null) {
      return ParameterField.createValueField(Timeout.builder().timeoutString("10m").build());
    }
    long t = timeoutInMillis / 1000;
    String timeoutString = Math.max(60, t) + "s";
    return ParameterField.createValueField(Timeout.builder().timeoutString(timeoutString).build());
  }

  public static ParameterField<String> getParameterField(String value) {
    if (StringUtils.isBlank(value)) {
      return ParameterField.createValueField("");
    }
    return ParameterField.createValueField(value);
  }

  public static void sort(List<NGYamlFile> files) {
    files.sort(new MigrationEntityComparator());
  }

  public static ParameterField<List<TaskSelectorYaml>> getDelegateSelectors(List<String> strings) {
    return EmptyPredicate.isEmpty(strings)
        ? ParameterField.createValueField(Collections.emptyList())
        : ParameterField.createValueField(strings.stream().map(TaskSelectorYaml::new).collect(Collectors.toList()));
  }

  public static Scope getDefaultScope(MigrationInputDTO inputDTO, CgEntityId entityId, Scope defaultScope) {
    NGMigrationEntityType entityType = entityId.getType();
    return getDefaultScope(inputDTO, entityId, defaultScope, entityType);
  }

  public static Scope getDefaultScope(MigrationInputDTO inputDTO, CgEntityId entityId, Scope defaultScope,
      NGMigrationEntityType destinationEntityType) {
    if (inputDTO == null) {
      return defaultScope;
    }
    Scope scope = defaultScope;
    Map<NGMigrationEntityType, InputDefaults> defaults = inputDTO.getDefaults();
    if (defaults != null && defaults.containsKey(destinationEntityType)
        && defaults.get(destinationEntityType).getScope() != null) {
      scope = defaults.get(destinationEntityType).getScope();
    }
    Map<CgEntityId, BaseProvidedInput> inputs = inputDTO.getOverrides();
    if (inputs != null && inputs.containsKey(entityId) && inputs.get(entityId).getScope() != null) {
      scope = inputs.get(entityId).getScope();
    }
    return scope;
  }

  public static Scope getScope(NgEntityDetail entityDetail) {
    String orgId = entityDetail.getOrgIdentifier();
    String projectId = entityDetail.getProjectIdentifier();
    if (StringUtils.isAllBlank(orgId, projectId)) {
      return Scope.ACCOUNT;
    }
    if (StringUtils.isNotBlank(projectId)) {
      return Scope.PROJECT;
    }
    return Scope.ORG;
  }

  public static SecretRefData getSecretRefDefaultNull(Map<CgEntityId, NGYamlFile> migratedEntities, String secretId) {
    if (StringUtils.isBlank(secretId)) {
      return null;
    }
    CgEntityId secretEntityId = CgEntityId.builder().id(secretId).type(NGMigrationEntityType.SECRET).build();
    if (!migratedEntities.containsKey(secretEntityId)) {
      return null;
    }
    return getSecretRef(migratedEntities, secretId);
  }

  public static SecretRefData getSecretRef(Map<CgEntityId, NGYamlFile> migratedEntities, String secretId) {
    return getSecretRef(migratedEntities, secretId, NGMigrationEntityType.SECRET);
  }

  public static SecretRefData getSecretRef(
      Map<CgEntityId, NGYamlFile> migratedEntities, String entityId, NGMigrationEntityType entityType) {
    if (entityId == null) {
      return SecretRefData.builder().identifier(PLEASE_FIX_ME).scope(Scope.PROJECT).build();
    }
    CgEntityId secretEntityId = CgEntityId.builder().id(entityId).type(entityType).build();
    if (!migratedEntities.containsKey(secretEntityId)) {
      return SecretRefData.builder().identifier(PLEASE_FIX_ME).scope(Scope.PROJECT).build();
    }
    NgEntityDetail migratedSecret = migratedEntities.get(secretEntityId).getNgEntityDetail();
    return SecretRefData.builder()
        .identifier(migratedSecret.getIdentifier())
        .scope(MigratorUtility.getScope(migratedSecret))
        .build();
  }

  public static String getIdentifierWithScopeDefaults(Map<CgEntityId, NGYamlFile> migratedEntities, String entityId,
      NGMigrationEntityType entityType, String defaultValue) {
    NGYamlFile detail = migratedEntities.get(CgEntityId.builder().type(entityType).id(entityId).build());
    if (detail == null) {
      return defaultValue;
    }
    return getIdentifierWithScope(detail.getNgEntityDetail());
  }

  public static String getIdentifierWithScopeDefaults(
      Map<CgEntityId, NGYamlFile> migratedEntities, String entityId, NGMigrationEntityType entityType) {
    return getIdentifierWithScopeDefaults(migratedEntities, entityId, entityType, PLEASE_FIX_ME);
  }

  public static String getIdentifierWithScope(
      Map<CgEntityId, NGYamlFile> migratedEntities, String entityId, NGMigrationEntityType entityType) {
    NGYamlFile yamlFile = migratedEntities.get(CgEntityId.builder().type(entityType).id(entityId).build());
    if (yamlFile == null) {
      return NGMigrationConstants.RUNTIME_INPUT;
    }
    NgEntityDetail detail = yamlFile.getNgEntityDetail();
    return getIdentifierWithScope(detail);
  }

  public static String getIdentifierWithScope(Scope scope, String name, CaseFormat caseFormat) {
    String identifier = MigratorUtility.generateIdentifier(name, caseFormat);
    return getScopedIdentifier(scope, identifier);
  }

  public static String getScopedIdentifier(Scope scope, String identifier) {
    switch (scope) {
      case ACCOUNT:
        return "account." + identifier;
      case ORG:
        return "org." + identifier;
      default:
        return identifier;
    }
  }

  public static String getIdentifierWithScope(NgEntityDetail entityDetail) {
    String orgId = entityDetail.getOrgIdentifier();
    String projectId = entityDetail.getProjectIdentifier();
    String identifier = entityDetail.getIdentifier();
    if (StringUtils.isAllBlank(orgId, projectId)) {
      return "account." + identifier;
    }
    if (StringUtils.isNotBlank(projectId)) {
      return identifier;
    }
    return "org." + identifier;
  }

  public static List<NGVariable> getVariables(MigrationContext migrationContext, List<Variable> cgVariables) {
    List<NGVariable> variables = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(cgVariables)) {
      cgVariables.forEach(serviceVariable -> variables.add(getNGVariable(migrationContext, serviceVariable)));
    }
    return variables;
  }

  public static List<NGVariable> getServiceVariables(
      MigrationContext migrationContext, List<ServiceVariable> serviceVariables) {
    List<NGVariable> variables = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(serviceVariables)) {
      serviceVariables.forEach(serviceVariable -> variables.add(getNGVariable(migrationContext, serviceVariable)));
    }
    return variables;
  }

  public static NGVariable getNGVariable(MigrationContext migrationContext, Variable variable) {
    String value = "<+input>";
    if (EmptyPredicate.isNotEmpty(variable.getValue())) {
      value = String.valueOf(MigratorExpressionUtils.render(migrationContext, variable.getValue(), new HashMap<>()));
    }
    String name = variable.getName();
    name = name.replace('-', '_');
    return StringNGVariable.builder()
        .type(NGVariableType.STRING)
        .name(name)
        .value(ParameterField.createValueField(value))
        .build();
  }

  public static NGVariable getNGVariable(MigrationContext migrationContext, ServiceVariable serviceVariable) {
    if (serviceVariable.getType().equals(ServiceVariableType.ENCRYPTED_TEXT)) {
      return SecretNGVariable.builder()
          .type(NGVariableType.SECRET)
          .value(ParameterField.createValueField(MigratorUtility.getSecretRef(
              migrationContext.getMigratedEntities(), serviceVariable.getEncryptedValue())))
          .name(StringUtils.trim(serviceVariable.getName()))
          .build();
    } else {
      String value = "";
      if (EmptyPredicate.isNotEmpty(serviceVariable.getValue())) {
        value = String.valueOf(MigratorExpressionUtils.render(
            migrationContext, String.valueOf(serviceVariable.getValue()), new HashMap<>()));
      }
      String name = StringUtils.trim(serviceVariable.getName());
      name = name.replace('-', '_');
      return StringNGVariable.builder()
          .type(NGVariableType.STRING)
          .name(name)
          .value(ParameterField.createValueField(value))
          .build();
    }
  }

  public static String generateIdentifier(
      Map<CgEntityId, BaseProvidedInput> inputs, CgEntityId entityId, String defaultIdentifier) {
    if (inputs == null || !inputs.containsKey(entityId) || StringUtils.isBlank(inputs.get(entityId).getIdentifier())) {
      return defaultIdentifier;
    }
    return inputs.get(entityId).getIdentifier();
  }

  public static String generateIdentifierDefaultName(
      Map<CgEntityId, BaseProvidedInput> inputs, CgEntityId entityId, String name, CaseFormat caseFormat) {
    if (inputs == null || !inputs.containsKey(entityId) || StringUtils.isBlank(inputs.get(entityId).getIdentifier())) {
      return generateIdentifier(name, caseFormat);
    }
    return inputs.get(entityId).getIdentifier();
  }

  public static String generateName(
      Map<CgEntityId, BaseProvidedInput> inputs, CgEntityId entityId, String defaultName) {
    if (inputs == null || !inputs.containsKey(entityId) || StringUtils.isBlank(inputs.get(entityId).getName())) {
      return generateName(defaultName);
    }
    return inputs.get(entityId).getName();
  }

  public static String getOrgIdentifier(Scope scope, MigrationInputDTO inputDTO) {
    if (Scope.ACCOUNT.equals(scope)) {
      return null;
    }
    if (StringUtils.isBlank(inputDTO.getOrgIdentifier())) {
      throw new InvalidRequestException("Trying to scope entity to Org but no org identifier provided in input");
    }
    return inputDTO.getOrgIdentifier();
  }

  public static String getProjectIdentifier(Scope scope, MigrationInputDTO inputDTO) {
    if (Scope.ACCOUNT.equals(scope) || Scope.ORG.equals(scope)) {
      return null;
    }
    if (StringUtils.isAllBlank(inputDTO.getOrgIdentifier(), inputDTO.getProjectIdentifier())) {
      throw new InvalidRequestException("Trying to scope entity to Project but org/project identifier(s) are missing");
    }
    return inputDTO.getProjectIdentifier();
  }

  public static boolean endsWithIgnoreCase(String str, String arg, String... args) {
    if (str.toLowerCase().endsWith(arg)) {
      return true;
    }
    for (String arg1 : args) {
      if (str.toLowerCase().endsWith(arg1)) {
        return true;
      }
    }
    return false;
  }

  public static ParameterField<List<String>> getFileStorePaths(List<NGYamlFile> files) {
    if (EmptyPredicate.isEmpty(files)) {
      return ParameterField.ofNull();
    }

    List<String> paths = new ArrayList<>();
    for (NGYamlFile file : files) {
      FileYamlDTO yamlDTO = (FileYamlDTO) file.getYaml();
      if (StringUtils.isBlank(yamlDTO.getFilePath())) {
        paths.add("/" + yamlDTO.getName());
      } else {
        paths.add(yamlDTO.getFilePath());
      }
    }

    return ParameterField.createValueField(paths);
  }

  public static ParameterField<List<String>> splitWithComma(String str) {
    return ParameterField.createValueField(
        Arrays.stream(str.split(",")).map(String::trim).filter(StringUtils::isNotBlank).collect(Collectors.toList()));
  }

  public static ParameterField<String> getIdentifierWithScopeDefaultsRuntime(
      Map<CgEntityId, NGYamlFile> migratedEntities, String entityId, NGMigrationEntityType entityType) {
    NGYamlFile ngYamlFile = migratedEntities.get(CgEntityId.builder().type(entityType).id(entityId).build());
    if (ngYamlFile == null) {
      return RUNTIME_INPUT;
    }
    NgEntityDetail detail = ngYamlFile.getNgEntityDetail();
    return ParameterField.createValueField(getIdentifierWithScope(detail));
  }

  public static String generateName(String str) {
    if (StringUtils.isBlank(str)) {
      return str;
    }
    str = StringUtils.stripAccents(str.trim());
    Pattern p = Pattern.compile("[^-0-9a-zA-Z_\\s]", Pattern.CASE_INSENSITIVE);
    Matcher m = p.matcher(str);
    String generated = m.replaceAll("_");
    return !Character.isLetter(generated.charAt(0)) ? "_" + generated : generated;
  }

  @Nullable
  public static NGYamlFile getYamlConfigFile(MigrationInputDTO inputDTO, byte[] content, String identifier) {
    return getYamlFile(inputDTO, content, identifier, FileUsage.CONFIG);
  }

  @Nullable
  public static NGYamlFile getYamlManifestFile(MigrationInputDTO inputDTO, byte[] content, String identifier) {
    return getYamlFile(inputDTO, content, identifier, FileUsage.MANIFEST_FILE);
  }

  @Nullable
  private static NGYamlFile getYamlFile(
      MigrationInputDTO inputDTO, byte[] content, String identifier, FileUsage fileUsage) {
    if (isEmpty(content)) {
      return null;
    }

    String projectIdentifier = MigratorUtility.getProjectIdentifier(Scope.PROJECT, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(Scope.PROJECT, inputDTO);

    return NGYamlFile.builder()
        .type(NGMigrationEntityType.CONFIG_FILE)
        .yaml(FileYamlDTO.builder()
                  .identifier(identifier)
                  .fileUsage(fileUsage.name())
                  .name(identifier)
                  .content(new String(content))
                  .rootIdentifier("Root")
                  .depth(Integer.MAX_VALUE)
                  .filePath("")
                  .orgIdentifier(orgIdentifier)
                  .projectIdentifier(projectIdentifier)
                  .build())
        .ngEntityDetail(NgEntityDetail.builder()
                            .entityType(NGMigrationEntityType.FILE_STORE)
                            .identifier(identifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .build())
        .cgBasicInfo(null)
        .build();
  }

  public static boolean containsExpressions(String str) {
    return containsCgExpressions(str) || containsNgExpressions(str);
  }

  public static boolean containsCgExpressions(String str) {
    return cgPattern.matcher(str).find();
  }

  public static boolean containsNgExpressions(String str) {
    return ngPattern.matcher(str).find();
  }

  public static NgEntityDetail getGitConnector(
      Map<CgEntityId, NGYamlFile> migratedEntities, GitFileConfig gitFileConfig) {
    CgEntityId cgEntityId =
        CgEntityId.builder().id(gitFileConfig.getConnectorId()).type(NGMigrationEntityType.CONNECTOR).build();
    if (!migratedEntities.containsKey(cgEntityId)) {
      log.error(String.format("Could not find GitConnector %s", gitFileConfig.getConnectorId()));
      return null;
    }
    return migratedEntities.get(cgEntityId).getNgEntityDetail();
  }

  public static String generateFileIdentifier(String fileName, CaseFormat caseFormat) {
    String prefix = fileName + ' ';
    return MigratorUtility.generateManifestIdentifier(prefix, caseFormat);
  }

  public static boolean checkIfStringIsValidUrl(String value) {
    UrlValidator urlValidator = new UrlValidator(schemes);
    return urlValidator.isValid(value);
  }

  public static List<GraphNode> getSteps(Workflow workflow) {
    List<GraphNode> stepYamls = new ArrayList<>();
    if (workflow == null || workflow.getOrchestrationWorkflow() == null
        || !(workflow.getOrchestrationWorkflow() instanceof CanaryOrchestrationWorkflow)) {
      return stepYamls;
    }
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    PhaseStep postDeploymentPhaseStep = orchestrationWorkflow.getPostDeploymentSteps();
    if (postDeploymentPhaseStep != null && EmptyPredicate.isNotEmpty(postDeploymentPhaseStep.getSteps())) {
      stepYamls.addAll(postDeploymentPhaseStep.getSteps());
    }
    PhaseStep preDeploymentPhaseStep = orchestrationWorkflow.getPreDeploymentSteps();
    if (preDeploymentPhaseStep != null && EmptyPredicate.isNotEmpty(preDeploymentPhaseStep.getSteps())) {
      stepYamls.addAll(preDeploymentPhaseStep.getSteps());
    }
    List<WorkflowPhase> phases = orchestrationWorkflow.getWorkflowPhases();
    if (EmptyPredicate.isNotEmpty(phases)) {
      stepYamls.addAll(getStepsFromPhases(phases));
    }
    List<WorkflowPhase> rollbackPhases = getRollbackPhases(workflow);
    if (EmptyPredicate.isNotEmpty(rollbackPhases)) {
      stepYamls.addAll(getStepsFromPhases(rollbackPhases));
    }
    return stepYamls;
  }

  public static List<GraphNode> getStepsFromPhases(List<WorkflowPhase> phases) {
    return phases.stream()
        .filter(phase -> isNotEmpty(phase.getPhaseSteps()))
        .flatMap(phase -> phase.getPhaseSteps().stream())
        .filter(phaseStep -> isNotEmpty(phaseStep.getSteps()))
        .flatMap(phaseStep -> phaseStep.getSteps().stream())
        .collect(Collectors.toList());
  }

  public static List<WorkflowPhase> getRollbackPhases(Workflow workflow) {
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    Map<String, WorkflowPhase> rollbackWorkflowPhaseIdMap = orchestrationWorkflow.getRollbackWorkflowPhaseIdMap();
    if (EmptyPredicate.isEmpty(orchestrationWorkflow.getWorkflowPhaseIds())) {
      return Collections.emptyList();
    }
    return orchestrationWorkflow.getWorkflowPhaseIds()
        .stream()
        .filter(phaseId
            -> rollbackWorkflowPhaseIdMap.containsKey(phaseId) && rollbackWorkflowPhaseIdMap.get(phaseId) != null)
        .map(rollbackWorkflowPhaseIdMap::get)
        .collect(Collectors.toList());
  }

  public static WaitStepNode getWaitStepNode(String name, int waitInterval, boolean skipAlways, CaseFormat caseFormat) {
    WaitStepNode waitStepNode = new WaitStepNode();
    waitStepNode.setName(name);
    waitStepNode.setIdentifier(generateIdentifier(name, caseFormat));
    waitStepNode.setWaitStepInfo(
        WaitStepInfo.infoBuilder().duration(MigratorUtility.getTimeout(waitInterval * 1000)).build());
    if (skipAlways) {
      waitStepNode.setWhen(ParameterField.createValueField(StepWhenCondition.builder()
                                                               .condition(ParameterField.createValueField("false"))
                                                               .stageStatus(SUCCESS)
                                                               .build()));
    }
    return waitStepNode;
  }

  public static <T> MigrationImportSummaryDTO handleEntityMigrationResp(
      NGYamlFile yamlFile, Response<ResponseDTO<T>> resp) throws IOException {
    if (resp.code() >= 200 && resp.code() < 300) {
      return MigrationImportSummaryDTO.builder().success(true).errors(Collections.emptyList()).build();
    }
    log.info("The Yaml of the generated data was - {}", yamlFile.getYaml());
    Map<String, Object> error = JsonUtils.asObject(
        resp.errorBody() != null ? resp.errorBody().string() : "{}", new TypeReference<Map<String, Object>>() {});
    log.error(String.format("There was error creating the %s. Response from NG - %s with error body errorBody -  %s",
        yamlFile.getType(), resp, error));
    return MigrationImportSummaryDTO.builder()
        .errors(Collections.singletonList(
            ImportError.builder()
                .message(error.containsKey("message")
                        ? error.get("message").toString()
                        : String.format("There was an error creating the %s", yamlFile.getType()))
                .entity(yamlFile.getCgBasicInfo())
                .build()))
        .build();
  }

  public static MigrationInputDTO getMigrationInput(String requestAuthToken, ImportDTO importDTO) {
    Map<NGMigrationEntityType, InputDefaults> defaults = new HashMap<>();
    Map<CgEntityId, BaseProvidedInput> overrides = new HashMap<>();
    Map<String, Object> expressions = new HashMap<>();
    if (importDTO.getInputs() != null) {
      overrides = importDTO.getInputs().getOverrides();
      defaults = importDTO.getInputs().getDefaults();
      expressions = importDTO.getInputs().getExpressions();
    }

    // We do not want to auto migrate WFs/Pipelines. We want customers to migrate WFs/Pipelines by choice.
    if (!Sets.newHashSet(NGMigrationEntityType.WORKFLOW, NGMigrationEntityType.PIPELINE)
             .contains(importDTO.getEntityType())) {
      InputDefaults wfDefaults = defaults.getOrDefault(NGMigrationEntityType.WORKFLOW, new InputDefaults());
      wfDefaults.setSkipMigration(true);
      defaults.put(NGMigrationEntityType.WORKFLOW, wfDefaults);

      InputDefaults pipelineDefaults = defaults.getOrDefault(NGMigrationEntityType.PIPELINE, new InputDefaults());
      pipelineDefaults.setSkipMigration(true);
      defaults.put(NGMigrationEntityType.PIPELINE, pipelineDefaults);
    }

    return MigrationInputDTO.builder()
        .destinationAccountIdentifier(StringUtils.defaultIfBlank(
            importDTO.getDestinationDetails().getAccountIdentifier(), importDTO.getAccountIdentifier()))
        .destinationAuthToken(
            StringUtils.defaultIfBlank(importDTO.getDestinationDetails().getAuthToken(), requestAuthToken))
        .accountIdentifier(importDTO.getAccountIdentifier())
        .orgIdentifier(importDTO.getDestinationDetails().getOrgIdentifier())
        .projectIdentifier(importDTO.getDestinationDetails().getProjectIdentifier())
        .migrateReferencedEntities(importDTO.isMigrateReferencedEntities())
        .overrides(overrides)
        .defaults(defaults)
        .customExpressions(expressions)
        .identifierCaseFormat(
            importDTO.getIdentifierCaseFormat() == null ? CAMEL_CASE : importDTO.getIdentifierCaseFormat())
        .build();
  }

  public static Map<String, Object> getExpressions(
      WorkflowPhase phase, List<StepExpressionFunctor> functors, CaseFormat caseFormat) {
    String stageIdentifier = MigratorUtility.generateIdentifier(phase.getName(), caseFormat);
    return getExpressions(stageIdentifier, functors);
  }

  public static Map<String, Object> getExpressions(String phaseIdentifier, List<StepExpressionFunctor> functors) {
    Map<String, Object> expressions = new HashMap<>();

    for (StepExpressionFunctor functor : functors) {
      functor.setCurrentStageIdentifier(phaseIdentifier);
      expressions.put(functor.getCgExpression(), functor);
    }
    return expressions;
  }

  public static boolean containsEcsTask(Set<CgEntityId> containerTaskIds, Map<CgEntityId, CgEntityNode> entities) {
    if (isEmpty(containerTaskIds)) {
      return false;
    }

    for (CgEntityId configEntityId : containerTaskIds) {
      CgEntityNode configNode = entities.get(configEntityId);
      if (configNode != null) {
        ContainerTask specification = (ContainerTask) configNode.getEntity();
        if ("ECS".equalsIgnoreCase(specification.getDeploymentType())) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isGitFileConfigSimilar(GitFileConfig gitFileConfig1, GitFileConfig gitFileConfig2) {
    if (gitFileConfig1 == null && gitFileConfig2 == null) {
      return true;
    } else if (gitFileConfig1 == null || gitFileConfig2 == null) {
      return false;
    } else {
      return gitFileConfig1.equals(gitFileConfig2);
    }
  }

  public static Map<String, String> getTags(List<HarnessTagLink> tagLinks) {
    if (EmptyPredicate.isEmpty(tagLinks)) {
      return Collections.emptyMap();
    }
    return tagLinks.stream()
        .filter(tl -> StringUtils.isNoneBlank(tl.getKey(), tl.getValue()))
        .collect(Collectors.toMap(HarnessTagLink::getKey, HarnessTagLink::getValue));
  }

  public static String getSafeNotEmptyString(String val) {
    if (isEmpty(val) || isEmpty(val.trim())) {
      return PLEASE_FIX_ME;
    } else {
      return val;
    }
  }

  public static String toTimeoutString(long timestamp) {
    long tSec = timestamp / 1000;
    String timeString = "10m";

    long days = TimeUnit.SECONDS.toDays(tSec);
    if (days > 0) {
      timeString = days + "d";
      tSec -= TimeUnit.DAYS.toSeconds(days);
    }

    long hours = TimeUnit.SECONDS.toHours(tSec);
    if (hours > 0) {
      timeString = hours + "h";
      tSec -= TimeUnit.HOURS.toSeconds(hours);
    }

    long minutes = TimeUnit.SECONDS.toMinutes(tSec);
    if (minutes > 0) {
      timeString = minutes + "m";
      tSec -= TimeUnit.MINUTES.toSeconds(minutes);
    }

    long seconds = tSec;
    if (seconds > 0) {
      timeString = seconds + "s";
    }
    return timeString;
  }
}
