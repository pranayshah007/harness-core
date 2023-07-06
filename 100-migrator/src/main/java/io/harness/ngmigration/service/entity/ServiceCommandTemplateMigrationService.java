/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.ngmigration.utils.NGMigrationConstants.SERVICE_COMMAND_TEMPLATE_SEPARATOR;
import static io.harness.ngmigration.utils.NGMigrationConstants.UNKNOWN_SERVICE;

import static software.wings.ngmigration.NGMigrationEntityType.SERVICE_COMMAND_TEMPLATE;
import static software.wings.ngmigration.NGMigrationEntityType.TEMPLATE;

import io.harness.beans.MigratedEntityMapping;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.exception.MigrationException;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.service.MigratorMappingService;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.template.NgTemplateService;
import io.harness.ngmigration.template.TemplateFactory;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.serializer.JsonUtils;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.resources.beans.TemplateWrapperResponseDTO;
import io.harness.template.resources.beans.yaml.NGTemplateConfig;
import io.harness.template.resources.beans.yaml.NGTemplateInfoConfig;

import software.wings.api.DeploymentType;
import software.wings.beans.Service;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitType;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateType;
import software.wings.beans.template.command.SshCommandTemplate;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.ServiceResourceService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import retrofit2.Response;

@Slf4j
// Merge this class with TemplateMigrationService
public class ServiceCommandTemplateMigrationService extends NgMigrationService {
  @Inject ServiceResourceService serviceResourceService;
  @Inject private TemplateResourceClient templateResourceClient;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    CgBasicInfo basicInfo = yamlFile.getCgBasicInfo();
    NGTemplateInfoConfig templateInfoConfig = ((NGTemplateConfig) yamlFile.getYaml()).getTemplateInfoConfig();
    String orgIdentifier = yamlFile.getNgEntityDetail().getOrgIdentifier();
    String projectIdentifier = yamlFile.getNgEntityDetail().getProjectIdentifier();
    return MigratedEntityMapping.builder()
        .appId(basicInfo.getAppId())
        .accountId(basicInfo.getAccountId())
        .cgEntityId(basicInfo.getId())
        .entityType(SERVICE_COMMAND_TEMPLATE.name())
        .accountIdentifier(basicInfo.getAccountId())
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(templateInfoConfig.getIdentifier())
        .scope(MigratorMappingService.getScope(orgIdentifier, projectIdentifier))
        .fullyQualifiedIdentifier(MigratorMappingService.getFullyQualifiedIdentifier(
            basicInfo.getAccountId(), orgIdentifier, projectIdentifier, templateInfoConfig.getIdentifier()))
        .build();
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    ServiceCommand template = (ServiceCommand) entity;
    Set<CgEntityId> children = new HashSet<>();
    CgEntityNode templateNode =
        CgEntityNode.builder()
            .appId(template.getAppId())
            .entity(template)
            .entityId(CgEntityId.builder()
                          .id(template.getServiceId() + SERVICE_COMMAND_TEMPLATE_SEPARATOR + template.getName())
                          .type(NGMigrationEntityType.SERVICE_COMMAND_TEMPLATE)
                          .build())
            .type(NGMigrationEntityType.SERVICE_COMMAND_TEMPLATE)
            .id(template.getServiceId() + SERVICE_COMMAND_TEMPLATE_SEPARATOR + template.getName())
            .build();
    if (StringUtils.isNotBlank(template.getTemplateUuid())) {
      children.add(CgEntityId.builder().id(template.getTemplateUuid()).type(NGMigrationEntityType.TEMPLATE).build());
    }
    return DiscoveryNode.builder().children(children).entityNode(templateNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    String serviceId = entityId.split(SERVICE_COMMAND_TEMPLATE_SEPARATOR)[0];
    String serviceCommandName = entityId.split(SERVICE_COMMAND_TEMPLATE_SEPARATOR)[1];
    if (serviceId.equals(UNKNOWN_SERVICE)) {
      List<Service> services = serviceResourceService.listByDeploymentType(appId, DeploymentType.SSH.name(), null);
      for (Service service : services) {
        DiscoveryNode node = getDiscoveryNodeForService(appId, service.getUuid(), serviceCommandName);
        if (node != null) {
          return node;
        }
      }
      return null;
    } else {
      return getDiscoveryNodeForService(appId, serviceId, serviceCommandName);
    }
  }

  @Nullable
  private DiscoveryNode getDiscoveryNodeForService(String appId, String serviceId, String serviceCommandName) {
    Map<String, ServiceCommand> serviceCommandMap =
        serviceResourceService.getServiceCommands(appId, serviceId, true)
            .stream()
            .collect(Collectors.toMap(sc -> sc.getName().toLowerCase(), Function.identity()));

    Set<String> serviceCommandsProcessed = new HashSet<>();
    try {
      List<CommandUnit> commandUnitsResult =
          getFlattened(serviceCommandName, serviceCommandMap, serviceCommandsProcessed);

      ServiceCommand serviceCommand = serviceCommandMap.get(serviceCommandName.toLowerCase());
      serviceCommand.getCommand().setCommandUnits(commandUnitsResult);
      return discover(serviceCommand);
    } catch (MigrationException me) {
      log.warn("Service command exception ", me);
      return null;
    }
  }

  private List<CommandUnit> getFlattened(
      String serviceCommandName, Map<String, ServiceCommand> serviceCommandMap, Set<String> serviceCommandsProcessed) {
    if (serviceCommandsProcessed.contains(serviceCommandName.toLowerCase())) {
      throw new MigrationException("Cycle forming in service command " + serviceCommandName);
    }

    if (!serviceCommandMap.containsKey(serviceCommandName.toLowerCase())) {
      throw new MigrationException("Key not found in serviceCommandMap " + serviceCommandName);
    }

    serviceCommandsProcessed.add(serviceCommandName.toLowerCase());

    ServiceCommand serviceCommand = serviceCommandMap.get(serviceCommandName.toLowerCase());
    List<CommandUnit> commandUnits = serviceCommand.getCommand().getCommandUnits();

    List<CommandUnit> result = new LinkedList<>();

    for (CommandUnit commandUnit : commandUnits) {
      if (commandUnit.getCommandUnitType() != CommandUnitType.COMMAND) {
        result.add(commandUnit);
      } else {
        String referenceId = ((Command) commandUnit).getReferenceId();
        result.addAll(getFlattened(referenceId.toLowerCase(), serviceCommandMap, serviceCommandsProcessed));
      }
    }
    return result;
  }

  @Override
  public MigrationImportSummaryDTO migrate(NGClient ngClient, PmsClient pmsClient, TemplateClient templateClient,
      MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    Response<ResponseDTO<TemplateWrapperResponseDTO>> resp =
        templateClient
            .createTemplate(inputDTO.getDestinationAuthToken(), inputDTO.getDestinationAccountIdentifier(),
                inputDTO.getOrgIdentifier(), inputDTO.getProjectIdentifier(),
                RequestBody.create(MediaType.parse("application/yaml"), YamlUtils.writeYamlString(yamlFile.getYaml())),
                StoreType.INLINE)
            .execute();
    log.info("Template creation Response details {} {}", resp.code(), resp.message());
    return handleResp(yamlFile, resp);
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    MigrationInputDTO inputDTO = migrationContext.getInputDTO();
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    ServiceCommand template = (ServiceCommand) entities.get(entityId).getEntity();

    // Check if service command is referencing an existing template, skip creation
    if (StringUtils.isNotBlank(template.getTemplateUuid())) {
      return null;
    }

    String identifierSource = template.getName();
    String serviceId = entityId.getId().split(SERVICE_COMMAND_TEMPLATE_SEPARATOR)[0];
    if (!UNKNOWN_SERVICE.equals(serviceId)) {
      identifierSource += serviceId;
    }

    // Check if name has to cleaned up
    String name = MigratorUtility.generateName(inputDTO.getOverrides(), entityId, template.getName());
    String identifier = MigratorUtility.generateIdentifier(identifierSource, inputDTO.getIdentifierCaseFormat());
    Scope scope = MigratorUtility.getDefaultScope(inputDTO, entityId, Scope.PROJECT);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);
    String description = "";
    MigratorExpressionUtils.render(migrationContext, template, inputDTO.getCustomExpressions());

    // Converting service commands to Template object
    List<CommandUnit> commandUnits = template.getCommand().getCommandUnits();
    Set<String> ids = new HashSet<>();
    for (CommandUnit commandUnit : commandUnits) {
      String commandUnitName = commandUnit.getName();
      if (ids.contains(commandUnitName)) {
        commandUnitName += "_" + generateUuid();
        commandUnit.setName(commandUnitName);
      }
      ids.add(commandUnitName);
    }

    SshCommandTemplate sshCommandTemplate = SshCommandTemplate.builder().commandUnits(commandUnits).build();

    Template cgtemplate = Template.builder()
                              .type(TemplateType.SSH.name())
                              .templateObject(sshCommandTemplate)
                              .variables(template.getCommand().getTemplateVariables())
                              .build();

    NgTemplateService ngTemplateService = TemplateFactory.getTemplateService(cgtemplate);
    JsonNode spec =
        ngTemplateService.getNgTemplateConfigSpec(migrationContext, cgtemplate, orgIdentifier, projectIdentifier);

    if (ngTemplateService.isMigrationSupported() && spec != null) {
      List<NGYamlFile> files = new ArrayList<>();
      NGYamlFile ngYamlFile =
          NGYamlFile.builder()
              .type(SERVICE_COMMAND_TEMPLATE)
              .filename("template/" + template.getName() + ".yaml")
              .yaml(NGTemplateConfig.builder()
                        .templateInfoConfig(NGTemplateInfoConfig.builder()
                                                .type(ngTemplateService.getTemplateEntityType())
                                                .identifier(identifier)
                                                .name(name)
                                                .description(ParameterField.createValueField(description))
                                                .projectIdentifier(projectIdentifier)
                                                .orgIdentifier(orgIdentifier)
                                                .versionLabel("v" + template.getDefaultVersion())
                                                .spec(getSpec(spec, cgtemplate))
                                                .build())
                        .build())
              .ngEntityDetail(NgEntityDetail.builder()
                                  .entityType(TEMPLATE)
                                  .identifier(MigratorUtility.generateIdentifier(
                                      identifierSource, inputDTO.getIdentifierCaseFormat()))
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .build())
              .cgBasicInfo(template.getCgBasicInfo())
              .build();
      files.add(ngYamlFile);
      migratedEntities.putIfAbsent(entityId, ngYamlFile);
      return YamlGenerationDetails.builder().yamlFileList(files).build();
    }
    return null;
  }

  @Override
  protected YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    try {
      // Note: We are passing versionLabel as `null` because we do not know the version label.
      // It will return a stable version by default.
      TemplateResponseDTO response = NGRestUtils.getResponse(templateResourceClient.get(ngEntityDetail.getIdentifier(),
          accountIdentifier, ngEntityDetail.getOrgIdentifier(), ngEntityDetail.getProjectIdentifier(), null, false));
      if (response == null || StringUtils.isBlank(response.getYaml())) {
        return null;
      }
      return YamlUtils.read(response.getYaml(), NGTemplateConfig.class);
    } catch (Exception ex) {
      log.warn("Error when getting templates - ", ex);
      return null;
    }
  }

  private JsonNode getSpec(JsonNode configSpec, Template template) {
    NgTemplateService ngTemplateService = TemplateFactory.getTemplateService(template);
    if (TemplateType.CUSTOM_DEPLOYMENT_TYPE.name().equals(template.getType())) {
      return configSpec;
    } else {
      return JsonUtils.asTree(ImmutableMap.<String, Object>builder()
                                  .put("spec", configSpec)
                                  .put("type", ngTemplateService.getNgTemplateStepName(template))
                                  .put("timeout", ngTemplateService.getTimeoutString(template))
                                  .build());
    }
  }

  @Override
  protected boolean isNGEntityExists() {
    return true;
  }
}
