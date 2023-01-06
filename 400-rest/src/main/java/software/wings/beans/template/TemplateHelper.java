/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.template;

import static io.harness.data.structure.CollectionUtils.trimmedLowercaseSet;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.govern.Switch.unhandled;
import static io.harness.persistence.HPersistence.upToOne;
import static io.harness.persistence.HQuery.excludeValidate;

import static software.wings.beans.EntityType.ARTIFACT_STREAM;
import static software.wings.beans.EntityType.INFRASTRUCTURE_DEFINITION;
import static software.wings.beans.EntityType.SECRETS_MANAGER;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.EntityType.WORKFLOW;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.template.Template.FOLDER_ID_KEY;
import static software.wings.beans.template.Template.NAME_KEY;
import static software.wings.beans.template.Template.TYPE_KEY;
import static software.wings.common.TemplateConstants.APP_PREFIX;
import static software.wings.common.TemplateConstants.DEFAULT_TAG;
import static software.wings.common.TemplateConstants.GALLERY_TOP_LEVEL_PATH_DELIMITER;
import static software.wings.common.TemplateConstants.PATH_DELIMITER;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.persistence.PersistentEntity;

import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.CommandCategory;
import software.wings.beans.EntityType;
import software.wings.beans.NameValuePair;
import software.wings.beans.Service;
import software.wings.beans.User;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStream.ArtifactStreamKeys;
import software.wings.beans.command.CommandUnitType;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.template.Template.TemplateKeys;
import software.wings.beans.template.TemplateFolder.TemplateFolderKeys;
import software.wings.beans.template.dto.HarnessImportedTemplateDetails;
import software.wings.beans.template.dto.ImportedTemplateDetails;
import software.wings.common.TemplateConstants;
import software.wings.dl.WingsPersistence;
import software.wings.infra.InfrastructureDefinition;
import software.wings.security.AppPermissionSummary;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.UserThreadLocal;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig.CustomSecretsManagerConfigKeys;
import software.wings.service.impl.command.CommandHelper;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.ServiceResourceService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.validator.constraints.NotEmpty;

@Singleton
@OwnedBy(HarnessTeam.PL)
public class TemplateHelper {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;

  public List<Class<? extends PersistentEntity>> lookupEntityClass(TemplateType templateType) {
    switch (templateType) {
      case SSH:
        return Arrays.asList(ServiceCommand.class, Workflow.class);
      case HTTP:
      case SHELL_SCRIPT:
        return Arrays.asList(Workflow.class, CustomSecretsManagerConfig.class);
      case PCF_PLUGIN:
        return Collections.singletonList(Workflow.class);
      case ARTIFACT_SOURCE:
        return Collections.singletonList(ArtifactStream.class);
      case CUSTOM_DEPLOYMENT_TYPE:
        return Collections.emptyList();
      default:
        unhandled(templateType);
    }
    return null;
  }

  public static List<EntityType> mappedEntities(TemplateType templateType) {
    switch (templateType) {
      case SSH:
        return Collections.singletonList(SERVICE);
      case SHELL_SCRIPT:
        return Arrays.asList(WORKFLOW, SECRETS_MANAGER);
      case HTTP:
      case PCF_PLUGIN:
        return Collections.singletonList(WORKFLOW);
      case ARTIFACT_SOURCE:
        return Collections.singletonList(ARTIFACT_STREAM);
      case CUSTOM_DEPLOYMENT_TYPE:
        return Arrays.asList(SERVICE, INFRASTRUCTURE_DEFINITION);
      default:
        throw new InvalidArgumentsException(String.format("TemplateType [%s] is not supported", templateType), null);
    }
  }

  private boolean serviceLinked(String accountId, TemplateType templateType, List<String> templateUuids) {
    if (TemplateType.CUSTOM_DEPLOYMENT_TYPE == templateType) {
      final List<Service> services = serviceResourceService.listByCustomDeploymentTypeId(accountId, templateUuids, 1);
      if (isNotEmpty(services)) {
        return true;
      }
    }
    return false;
  }

  private boolean infraDefinitionLinked(String accountId, TemplateType templateType, List<String> templateUuids) {
    if (TemplateType.CUSTOM_DEPLOYMENT_TYPE == templateType) {
      final List<InfrastructureDefinition> infraDefs =
          infrastructureDefinitionService.listByCustomDeploymentTypeIds(accountId, templateUuids, 1);
      if (isNotEmpty(infraDefs)) {
        return true;
      }
    }
    return false;
  }

  public boolean templatesLinked(String accountId, TemplateType templateType, List<String> templateUuids) {
    if (isEmpty(templateUuids)) {
      return false;
    }

    long templatesOfType = wingsPersistence.createQuery(Template.class)
                               .field(TYPE_KEY)
                               .equal(templateType.name())
                               .field(Template.ID_KEY2)
                               .in(templateUuids)
                               .count(upToOne);

    long linkedTemplates = 0;
    List<Class<? extends PersistentEntity>> lookupClasses = lookupEntityClass(templateType);
    for (Class<? extends PersistentEntity> lookupClass : lookupClasses) {
      linkedTemplates = wingsPersistence.createQuery(lookupClass)
                            .field(lookupLinkedTemplateField(templateType, lookupClass))
                            .in(templateUuids)
                            .count(upToOne);
      if (linkedTemplates > 0) {
        break;
      }
    }

    long templatesReferencedInOtherTemplates;
    if (templateType == TemplateType.SSH) {
      templatesReferencedInOtherTemplates =
          wingsPersistence.createQuery(VersionedTemplate.class, excludeValidate)
              .field("templateObject.referencedTemplateList.templateReference.templateUuid")
              .in(templateUuids)
              .count(upToOne);
      return templatesOfType != 0 && (linkedTemplates != 0 || templatesReferencedInOtherTemplates != 0);
    }

    final boolean serviceLinked = serviceLinked(accountId, templateType, templateUuids);
    final boolean infraDefinitionLinked = infraDefinitionLinked(accountId, templateType, templateUuids);

    return (templatesOfType != 0 && linkedTemplates != 0) || serviceLinked || infraDefinitionLinked;
  }

  private String lookupLinkedTemplateField(TemplateType templateType, Class<? extends PersistentEntity> lookUpClass) {
    if (Workflow.class.equals(lookUpClass)) {
      return WorkflowKeys.linkedTemplateUuids;
    } else if (ServiceCommand.class.equals(lookUpClass)) {
      return ServiceCommand.TEMPLATE_UUID_KEY;
    } else if (ArtifactStream.class.equals(lookUpClass)) {
      return ArtifactStreamKeys.templateUuid;
    } else if (CustomSecretsManagerConfig.class.equals(lookUpClass)) {
      return CustomSecretsManagerConfigKeys.templateId;
    } else {
      unhandled(lookUpClass);
    }
    return null;
  }

  public List<Variable> overrideVariables(List<Variable> templateVariables, List<Variable> existingVariables) {
    return overrideVariables(templateVariables, existingVariables, true);
  }

  public List<Variable> overrideVariables(
      List<Variable> templateVariables, List<Variable> existingVariables, boolean doNotOverride) {
    List<Variable> updatedVariables = new ArrayList<>();
    if (isNotEmpty(templateVariables)) {
      for (Variable variable : templateVariables) {
        updatedVariables.add(variable.cloneInternal());
      }
    }

    Map<String, Variable> oldVariablesMap = obtainVariableMap(existingVariables);
    for (Variable variable : updatedVariables) {
      if (oldVariablesMap.containsKey(variable.getName())) {
        // Do not override the value if it is from template
        Variable oldVariable = oldVariablesMap.get(variable.getName());
        if (doNotOverride) {
          if (isNotEmpty(oldVariable.getValue())) {
            variable.setValue(oldVariable.getValue());
          }
          if (isNotEmpty(oldVariable.getDescription())) {
            variable.setDescription(oldVariable.getDescription());
          }
        }
        oldVariablesMap.remove(variable.getName());
      }
    }
    return updatedVariables;
  }

  public boolean variablesChanged(List<Variable> updatedVariables, List<Variable> existingVariables) {
    if (isEmpty(updatedVariables) && isEmpty(existingVariables)) {
      return false;
    } else if (isEmpty(updatedVariables) || isEmpty(existingVariables)) {
      return true;
    }
    Map<String, Variable> oldVariablesMap = obtainVariableMap(existingVariables);
    boolean variablesChanged = false;
    for (Variable variable : updatedVariables) {
      if (oldVariablesMap.containsKey(variable.getName())) {
        Variable oldVariable = oldVariablesMap.get(variable.getName());
        if (variable.getValue() != null) {
          if (!variable.getValue().equals(oldVariable.getValue())) {
            variablesChanged = true;
            break;
          }
        }
        if (variable.getDescription() != null) {
          if (!variable.getDescription().equals(oldVariable.getDescription())) {
            variablesChanged = true;
            break;
          }
        }
        if (variable.getType() != null) {
          if (variable.getType() != oldVariable.getType()) {
            variablesChanged = true;
            break;
          }
        }
        oldVariablesMap.remove(variable.getName());
      } else {
        // New variable added check if any default value present
        variablesChanged = true;
        break;
      }
    }
    return variablesChanged || oldVariablesMap.size() != 0;
  }

  private Map<String, Variable> obtainVariableMap(List<Variable> variables) {
    if (isEmpty(variables)) {
      return new HashMap<>();
    }
    Map<String, Variable> map = new HashMap<>();
    for (Variable variable : variables) {
      if (variable.getName() != null && variable.getValue() != null) {
        if (!map.containsKey(variable.getName())) {
          map.put(variable.getName(), variable);
        }
      }
    }
    return map;
  }

  public static List<NameValuePair> convertToTemplateVariables(List<Variable> entityTemplateVariables) {
    List<NameValuePair> templateVariables = new ArrayList<>();
    if (isNotEmpty(entityTemplateVariables)) {
      entityTemplateVariables.forEach(variable
          -> templateVariables.add(
              NameValuePair.builder().name(variable.getName()).value(variable.getValue()).build()));
    }
    return templateVariables;
  }

  public static List<Variable> convertToEntityVariables(List<NameValuePair> entityTemplateVariables) {
    List<Variable> templateVariables = new ArrayList<>();
    if (isNotEmpty(entityTemplateVariables)) {
      entityTemplateVariables.forEach(
          variable -> templateVariables.add(aVariable().name(variable.getName()).value(variable.getValue()).build()));
    }
    return templateVariables;
  }

  public static ImportedTemplateDetails getImportedTemplateDetails(Template template, String templateVersion) {
    if (template.getImportedTemplateDetails() instanceof HarnessImportedTemplateDetails) {
      HarnessImportedTemplateDetails importedTemplateDetails =
          (HarnessImportedTemplateDetails) template.getImportedTemplateDetails();
      if (DEFAULT_TAG.equals(templateVersion)) {
        importedTemplateDetails.setCommandVersion(DEFAULT_TAG);
      }
      return importedTemplateDetails;
    }
    return null;
  }

  // Don't directly use this method as it isn't imported template version aware.
  public static String obtainTemplateVersion(String templateUri) {
    String[] templateUris = fetchTemplateUris(templateUri);
    if (templateUris.length == 1) {
      return TemplateConstants.LATEST_TAG;
    }
    return templateUris[templateUris.length - 1];
  }

  public static String obtainTemplateName(String templateUri) {
    String cleanedTemplatePath = templateUri;
    if (templateUri.contains(":")) {
      String[] templateUris = fetchTemplateUris(templateUri);
      if (templateUris.length == 1) {
        cleanedTemplatePath = templateUris[0];
      } else {
        cleanedTemplatePath = templateUris[templateUris.length - 1 - 1];
      }
    }
    return cleanedTemplatePath.substring(cleanedTemplatePath.lastIndexOf(PATH_DELIMITER) + 1);
  }

  public static String obtainTemplateNameForImportedCommands(String templateUri) {
    String uri = templateUri.split(String.valueOf(GALLERY_TOP_LEVEL_PATH_DELIMITER))[1];
    if (uri.startsWith(APP_PREFIX)) {
      return uri.split(String.valueOf(PATH_DELIMITER))[1];
    }
    return uri;
  }

  public static boolean isAppLevelImportedCommand(String templateUri) {
    String uri = templateUri.split(String.valueOf(GALLERY_TOP_LEVEL_PATH_DELIMITER))[1];
    if (uri.startsWith(APP_PREFIX)) {
      return true;
    }
    return false;
  }

  public static String obtainTemplateFolderPath(String templateUri) {
    String[] templateUris = fetchTemplateUris(templateUri);
    String cleanedTemplateUri;
    if (templateUris.length == 1) {
      cleanedTemplateUri = templateUris[0];
    } else {
      cleanedTemplateUri = templateUris[templateUris.length - 1 - 1];
    }
    int endIndex = cleanedTemplateUri.lastIndexOf('/');
    return cleanedTemplateUri.substring(0, endIndex);
  }

  private static String[] fetchTemplateUris(String templateUri) {
    String[] templateUris = templateUri.split(":");
    if (templateUris.length < 1) {
      throw new InvalidRequestException("Invalid TemplateUri [" + templateUri + "]", WingsException.USER);
    }
    return templateUris;
  }

  /**
   * Get Command categories of service and service command
   * @return List of Command Categories
   */
  public List<CommandCategory> getCommandCategories(
      @NotEmpty String accountId, @NotEmpty String appId, @NotEmpty String templateId) {
    Template template = wingsPersistence.get(Template.class, templateId);

    Query<Template> templateQuery = wingsPersistence.createQuery(Template.class)
                                        .project("name", true)
                                        .filter(ApplicationKeys.accountId, accountId)
                                        .filter(ApplicationKeys.appId, appId)
                                        .filter(FOLDER_ID_KEY, template.getFolderId())
                                        .filter(TYPE_KEY, template.getType())
                                        .filter(TemplateKeys.galleryId, template.getGalleryId())
                                        .field(NAME_KEY)
                                        .notEqual(template.getName());

    List<Template> templates = templateQuery.asList();
    List<CommandCategory.CommandUnit> commands = templates.stream()
                                                     .map(commandTemplate
                                                         -> CommandCategory.CommandUnit.builder()
                                                                .uuid(commandTemplate.getUuid())
                                                                .name(commandTemplate.getName())
                                                                .type(CommandUnitType.COMMAND)
                                                                .build())
                                                     .collect(toList());

    return CommandHelper.prepareCommandCategoriesFromCommands(commands);
  }

  public static Set<String> addUserKeyWords(Set<String> keywords, Set<String> generatedKeywords) {
    Set<String> userKeywords = trimmedLowercaseSet(keywords);
    if (isNotEmpty(userKeywords)) {
      generatedKeywords.addAll(userKeywords);
    }
    return trimmedLowercaseSet(generatedKeywords);
  }
  public static Map<String, Object> convertToVariableMap(List<Variable> variables) {
    if (isEmpty(variables)) {
      return null;
    }
    return variables.stream()
        .filter(variable -> variable.getName() != null && variable.getValue() != null)
        .collect(Collectors.toMap(Variable::getName, Variable::getValue));
  }

  public List<TemplateFolder> getFolderDetails(String accountId, Set<String> templateFolderIds, List<String> appIds) {
    Query<TemplateFolder> query = wingsPersistence.createQuery(TemplateFolder.class)
                                      .filter(TemplateFolderKeys.accountId, accountId)
                                      .field(TemplateFolderKeys.appId)
                                      .in(appIds)
                                      .field(TemplateFolderKeys.uuid)
                                      .in(templateFolderIds);

    return query.asList().stream().collect(Collectors.toList());
  }

  public boolean shouldAllowTemplateFolderDeletion(String appId, Set<String> templateIdsToBeDeleted) {
    if (isEmpty(templateIdsToBeDeleted)) {
      return true;
    }
    User user = UserThreadLocal.get();
    if (user == null) {
      return true;
    }
    final AppPermissionSummary appPermissionSummary =
        user.getUserRequestContext().getUserPermissionInfo().getAppPermissionMapInternal().get(appId);
    if (appPermissionSummary != null) {
      final Set<String> templateIdsWithDeletePermissions =
          appPermissionSummary.getTemplatePermissions().get(Action.DELETE);
      return templateIdsWithDeletePermissions != null
          && templateIdsWithDeletePermissions.containsAll(templateIdsToBeDeleted);
    }
    return true;
  }
}
