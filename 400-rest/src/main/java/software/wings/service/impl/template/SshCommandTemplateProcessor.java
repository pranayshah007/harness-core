/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.template;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.template.Template.FOLDER_ID_KEY;
import static software.wings.beans.template.Template.GALLERY_ID_KEY;
import static software.wings.beans.template.Template.NAME_KEY;
import static software.wings.beans.template.Template.TYPE_KEY;
import static software.wings.common.TemplateConstants.GENERIC_JSON_PATH;
import static software.wings.common.TemplateConstants.LATEST_TAG;
import static software.wings.common.TemplateConstants.POWER_SHELL_IIS_APP_V4_INSTALL_PATH;
import static software.wings.common.TemplateConstants.POWER_SHELL_IIS_WEBSITE_V4_INSTALL_PATH;
import static software.wings.common.TemplateConstants.TOMCAT_WAR_INSTALL_PATH;
import static software.wings.common.TemplateConstants.TOMCAT_WAR_START_PATH;
import static software.wings.common.TemplateConstants.TOMCAT_WAR_STOP_PATH;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.persistence.HIterator;

import software.wings.beans.EntityType;
import software.wings.beans.GraphNode;
import software.wings.beans.Variable;
import software.wings.beans.command.AbstractCommandUnit.Yaml;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitType;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.template.BaseTemplate;
import software.wings.beans.template.ReferencedTemplate;
import software.wings.beans.template.ReferencedTemplate.ReferencedTemplateBuilder;
import software.wings.beans.template.Template;
import software.wings.beans.template.Template.TemplateKeys;
import software.wings.beans.template.TemplateHelper;
import software.wings.beans.template.TemplateType;
import software.wings.beans.template.command.SshCommandTemplate;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.impl.yaml.handler.command.CommandUnitYamlHandler;
import software.wings.service.intfc.ServiceResourceService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.danielbechler.diff.ObjectDifferBuilder;
import de.danielbechler.diff.node.DiffNode;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDC)
@TargetModule(_870_CG_ORCHESTRATION)
public class SshCommandTemplateProcessor extends AbstractTemplateProcessor {
  private static final String COMMAND_UNITS = "commandUnits";
  private static final String REFERENCED_TEMPLATE_LIST = "referencedTemplateList";
  private static final String COMMAND_TYPE = "commandType";
  private static final String COMMAND_PATH = "commandPath";
  private static final String ACCOUNT = "Account";
  private static final String APPLICATION = "Application";
  private static final String VARIABLES = "variables";
  private static final String TEMPLATE_UUID = "templateUuid";
  private static final String TEMPLATE_VERSION = "templateVersion";
  private static final String IMPORTED_TEMPLATE_DETAILS = "importedTemplateDetails";
  private static final String TEMPLATE_METADATA = "templateMetadata";
  private static final String TEMPLATE_VARIABLES = "templateVariables";

  @Inject YamlHandlerFactory yamlHandlerFactory;
  @Inject ServiceResourceService serviceResourceService;

  @Override
  public Template process(Template template) {
    template = super.process(template);
    validateTemplate(template);
    SshCommandTemplate sshCommandTemplate = (SshCommandTemplate) template.getTemplateObject();
    if (isNotEmpty(sshCommandTemplate.getCommands())) {
      template.setTemplateObject(convertYamlCommandToCommandUnits(template));
    }
    List<Variable> templateVariables = template.getVariables();
    List<ReferencedTemplate> referencedTemplateList = new ArrayList<>();
    if (isNotEmpty(sshCommandTemplate.getCommandUnits())) {
      for (CommandUnit commandUnit : sshCommandTemplate.getCommandUnits()) {
        ReferencedTemplateBuilder referencedTemplateBuilder = ReferencedTemplate.builder();
        if (commandUnit.getCommandUnitType() == CommandUnitType.COMMAND) {
          if (((Command) commandUnit).getTemplateReference() != null) {
            referencedTemplateBuilder.templateReference(((Command) commandUnit).getTemplateReference());
            List<Variable> commandVariables = ((Command) commandUnit).getTemplateVariables();
            Map<String, Variable> variableMap = new HashMap<>();
            for (Variable commandVariable : commandVariables) {
              // Check if variable's value already defined in top-level template variables i.e. variable references
              // another variable
              String variable = ExpressionEvaluator.getName(commandVariable.getValue());
              if (isNotEmpty(variable) && variable.equals(commandVariable.getValue())) {
                variable = commandVariable.getName();
              }
              Variable parentVariable = getTopLevelTemplateVariable(templateVariables, variable);
              variableMap.put(commandVariable.getName(), parentVariable);
            }
            referencedTemplateBuilder.variableMapping(variableMap);
          }
        }
        referencedTemplateList.add(referencedTemplateBuilder.build());
      }
      template.setTemplateObject(
          ((SshCommandTemplate) template.getTemplateObject()).withReferencedTemplateList(referencedTemplateList));
    }
    return template;
  }

  private Variable getTopLevelTemplateVariable(List<Variable> variables, String lookupVariable) {
    if (isNotEmpty(variables)) {
      for (Variable variable : variables) {
        if (variable.getName().equals(lookupVariable)) {
          return variable;
        }
      }
    }
    return null;
  }

  private void validateTemplate(Template template) {
    String parentScope = getTemplateScope(template.getAppId());
    List<Variable> topLevelVariables = template.getVariables();
    // holds only the variables that are provided with fixed values
    Map<String, String> fixedValueMap = new HashMap<>();

    SshCommandTemplate sshCommandTemplate = (SshCommandTemplate) template.getTemplateObject();
    if (isNotEmpty(sshCommandTemplate.getCommandUnits())) {
      for (CommandUnit commandUnit : sshCommandTemplate.getCommandUnits()) {
        if (commandUnit.getCommandUnitType() == CommandUnitType.COMMAND) {
          validateTemplateReference(parentScope, template.getAppId(), (Command) commandUnit);
          List<Variable> commandVariables = ((Command) commandUnit).getTemplateVariables();
          for (Variable commandVariable : commandVariables) {
            // evaluate expression
            String value = ExpressionEvaluator.getName(commandVariable.getValue());
            // if value is a fixed value check if the current variable is not already defined with another fixed value
            // in a prior command unit
            if (isNotEmpty(value)) {
              if (value.equals(commandVariable.getValue())) {
                if (fixedValueMap.containsKey(commandVariable.getName())
                    && !fixedValueMap.get(commandVariable.getName()).equals(value)) {
                  throw new InvalidRequestException(format(
                      "Variable \"%s\" already has a value %s assigned in template: %s. The same fixed value or a reference variable is allowed.",
                      commandVariable.getName(), fixedValueMap.get(commandVariable.getName()), template.getName()));
                } else if (!fixedValueMap.containsKey(commandVariable.getName())) {
                  Variable topLevelVariable = getTopLevelTemplateVariable(topLevelVariables, commandVariable.getName());
                  if (topLevelVariable == null) { // variable not provided in top level -> fail
                    throw new InvalidRequestException(
                        format("Variable \"%s\" is being referenced but not provided in template: %s",
                            commandVariable.getName(), template.getName()));
                  }
                  fixedValueMap.put(commandVariable.getName(), commandVariable.getValue());
                }
              } else { // value mapped to another variable
                // check if variable defined in top level
                Variable topLevelVariable = getTopLevelTemplateVariable(topLevelVariables, value);
                if (topLevelVariable == null) { // variable not provided in top level -> fail
                  throw new InvalidRequestException(
                      format("Variable \"%s\" is being referenced but not provided in template: %s", value,
                          template.getName()));
                }
              }
            }
          }
        }
      }
    }
  }

  private void validateTemplateReference(String parentScope, String parentAppId, Command commandUnit) {
    Template template;
    if (commandUnit.getTemplateReference() != null) { // service command following new format
      template = templateService.get(commandUnit.getTemplateReference().getTemplateUuid());
    } else { // old format template like our default Install command which has no TemplateReference
      template = templateService.get(commandUnit.getReferenceUuid());
    }
    validateScope(parentScope, parentAppId, template.getAppId());
  }

  private void validateScope(String parentScope, String parentAppId, String childAppId) {
    String childScope = getTemplateScope(childAppId);
    // account level templates should not be able to reference app level templates
    if (parentScope.equals(ACCOUNT) && childScope.equals(APPLICATION)) {
      throw new InvalidRequestException("Account level templates cannot reference Application level templates", USER);
    } else if (parentScope.equals(APPLICATION) && childScope.equals(APPLICATION) && !parentAppId.equals(childAppId)) {
      throw new InvalidRequestException(
          "Application level template cannot reference template belonging to another application", USER);
    }
  }

  private String getTemplateScope(String appId) {
    if (appId.equals(GLOBAL_APP_ID)) {
      return ACCOUNT;
    } else {
      return APPLICATION;
    }
  }

  @Override
  public TemplateType getTemplateType() {
    return TemplateType.SSH;
  }

  @Override
  public void loadDefaultTemplates(String accountId, String accountName) {
    super.loadDefaultTemplates(
        asList(TOMCAT_WAR_STOP_PATH, TOMCAT_WAR_START_PATH, TOMCAT_WAR_INSTALL_PATH, GENERIC_JSON_PATH,
            POWER_SHELL_IIS_WEBSITE_V4_INSTALL_PATH, POWER_SHELL_IIS_APP_V4_INSTALL_PATH),
        accountId, accountName);
  }

  @Override
  public void updateLinkedEntities(Template template) {
    Template savedTemplate = templateService.get(template.getUuid());
    if (savedTemplate == null) {
      log.info("Template {} was deleted. Not updating linked entities", template.getUuid());
      return;
    }
    // Read all the service commands that references the given command template
    try (HIterator<ServiceCommand> iterator =
             new HIterator<>(wingsPersistence.createQuery(ServiceCommand.class, excludeAuthority)
                                 .filter(ServiceCommand.TEMPLATE_UUID_KEY, template.getUuid())
                                 .fetch())) {
      for (ServiceCommand serviceCommand : iterator) {
        try {
          String templateVersion = serviceCommand.getTemplateVersion();
          if (templateVersion == null || (templateVersion.equalsIgnoreCase(LATEST_TAG))
              || isDefaultVersionOfTemplateChanged(template)) {
            log.info("Updating the linked commands");
            serviceCommand.setSetAsDefault(true);
            setCommandFromTemplate(template, serviceCommand);
            serviceResourceService.updateCommand(
                serviceCommand.getAppId(), serviceCommand.getServiceId(), serviceCommand, true);
          } else {
            log.info("The linked template is not the latest. So, not updating it");
          }
        } catch (Exception e) {
          log.warn("Failed to update the linked Service Command {}", serviceCommand.getUuid(), e);
        }
      }
    }

    // Read all the workflows that reference the given command template
    updateLinkedEntitiesInWorkflow(template);
  }

  @Override
  public Object constructEntityFromTemplate(Template template, EntityType entityType) {
    return transform(template, entityType);
  }

  private SshCommandTemplate convertYamlCommandToCommandUnits(Template template) {
    SshCommandTemplate sshCommandTemplate = (SshCommandTemplate) template.getTemplateObject();
    List<CommandUnit> commandUnits = new ArrayList<>();
    for (Yaml commandUnitYaml : sshCommandTemplate.getCommands()) {
      CommandUnitYamlHandler commandUnitYamlHandler =
          yamlHandlerFactory.getYamlHandler(YamlType.COMMAND_UNIT, commandUnitYaml.getCommandUnitType());
      CommandUnit commandUnit = commandUnitYamlHandler.toBean(commandUnitYaml);
      if (commandUnit instanceof Command) {
        Command command = (Command) commandUnit;
        String referenceId = command.getReferenceId();
        if (referenceId != null) {
          Query<Template> templateQuery = wingsPersistence.createQuery(Template.class)
                                              .project("name", true)
                                              .filter(TemplateKeys.accountId, template.getAccountId())
                                              .filter(TYPE_KEY, template.getType())
                                              .filter(FOLDER_ID_KEY, template.getFolderId())
                                              .filter(GALLERY_ID_KEY, template.getGalleryId())
                                              .filter(NAME_KEY, referenceId);
          Template referencedTemplate = templateQuery.get();
          if (referencedTemplate != null) {
            command.setReferenceUuid(referencedTemplate.getUuid());
          }
        }
      }
      commandUnits.add(commandUnit);
    }
    return sshCommandTemplate.withCommandUnits(commandUnits);
  }

  private void setCommandFromTemplate(Template template, ServiceCommand serviceCommand) {
    Command command = (Command) transform(template, EntityType.COMMAND);
    serviceCommand.setCommand(command);
    serviceCommand.setTemplateMetadata(template.getTemplateMetadata());
    serviceCommand.setImportedTemplateDetails(
        TemplateHelper.getImportedTemplateDetails(template, serviceCommand.getTemplateVersion()));
  }

  private Object transform(Template template, EntityType entityType) {
    SshCommandTemplate commandTemplate = (SshCommandTemplate) template.getTemplateObject();
    switch (entityType) {
      case COMMAND:
        return aCommand()
            .withTemplateVariables(template.getVariables())
            .withCommandUnits(commandTemplate.getCommandUnits())
            .withName(template.getName())
            .withCommandType(commandTemplate.getCommandType())
            .withTemplateId(template.getUuid())
            .withTemplateVersion(String.valueOf(template.getVersion()))
            .withImportedTemplateVersion(template.getImportedTemplateDetails())
            .withTemplateMetadata(template.getTemplateMetadata())
            .build();
      case WORKFLOW:
        return GraphNode.builder()
            .name(template.getName())
            .type(commandTemplate.getCommandType().name())
            .templateUuid(template.getUuid())
            .templateVersion(String.valueOf(template.getVersion()))
            .templateVariables(template.getVariables())
            .importedTemplateDetails(template.getImportedTemplateDetails())
            .templateMetadata(template.getTemplateMetadata())
            .build();
      default:
        throw new InvalidRequestException("Unsupported Entity Type");
    }
  }

  public Command fetchEntityFromTemplate(Template template, EntityType entityType) {
    Command command = null;
    if (template != null) {
      command = (Command) templateService.constructEntityFromTemplate(
          template.getUuid(), String.valueOf(template.getVersion()), entityType);
      command.setTemplateVersion("latest");
    }
    return command;
  }

  @Override
  public List<String> fetchTemplateProperties() {
    return asList(COMMAND_UNITS, REFERENCED_TEMPLATE_LIST, COMMAND_TYPE, COMMAND_PATH, VARIABLES, TEMPLATE_UUID,
        TEMPLATE_VERSION, TEMPLATE_VARIABLES, IMPORTED_TEMPLATE_DETAILS, TEMPLATE_METADATA);
  }

  @Override
  public boolean checkTemplateDetailsChanged(BaseTemplate oldTemplate, BaseTemplate newTemplate) {
    // check if command units changed
    List<CommandUnit> newCommandUnits = ((SshCommandTemplate) newTemplate).getCommandUnits();
    List<CommandUnit> oldCommandUnits = ((SshCommandTemplate) oldTemplate).getCommandUnits();
    DiffNode commandUnitDiff = ObjectDifferBuilder.buildDefault().compare(newCommandUnits, oldCommandUnits);
    if (isCommandUnitsSizeChanged(newCommandUnits, oldCommandUnits)
        || isCommandUnitsOrderChanged(newCommandUnits, oldCommandUnits) || commandUnitDiff.hasChanges()) {
      return true;
    }
    return false;
  }

  private boolean isCommandUnitsOrderChanged(List<CommandUnit> commandUnits, List<CommandUnit> oldCommandUnits) {
    if (commandUnits != null && oldCommandUnits != null) {
      if (commandUnits.size() == oldCommandUnits.size()) {
        List<String> commandNames = commandUnits.stream().map(CommandUnit::getName).collect(toList());
        List<String> oldCommandNames = oldCommandUnits.stream().map(CommandUnit::getName).collect(toList());
        return !commandNames.equals(oldCommandNames);
      }
    }
    return false;
  }

  private boolean isCommandUnitsSizeChanged(List<CommandUnit> commandUnits, List<CommandUnit> oldCommandUnits) {
    if (commandUnits != null && oldCommandUnits != null) {
      if (commandUnits.size() != oldCommandUnits.size()) {
        return true;
      }
    }
    return false;
  }
}
