/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.importer;

import static io.harness.ngmigration.utils.NGMigrationConstants.RUNTIME_INPUT;

import static software.wings.ngmigration.NGMigrationEntityType.ARTIFACT_STREAM;
import static software.wings.ngmigration.NGMigrationEntityType.SERVICE;
import static software.wings.ngmigration.NGMigrationEntityType.TRIGGER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.WorkflowType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.ngmigration.beans.DiscoverEntityInput;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.dto.SaveSummaryDTO;
import io.harness.ngmigration.dto.TriggerFilter;
import io.harness.ngmigration.service.DiscoveryService;
import io.harness.ngmigration.service.MigrationHelperService;
import io.harness.ngmigration.service.artifactstream.ArtifactStreamFactory;
import io.harness.ngmigration.service.artifactstream.ArtifactStreamMapper;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.config.NgTriggerConfigSchemaWrapper;
import io.harness.ngtriggers.beans.dto.NGTriggerResponseDTO;
import io.harness.ngtriggers.beans.source.ManifestType;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerSpecV2;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.artifact.ManifestTriggerConfig;
import io.harness.ngtriggers.beans.source.scheduled.CronTriggerSpec;
import io.harness.ngtriggers.beans.source.scheduled.ScheduledTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.custom.CustomTriggerSpec;
import io.harness.persistence.HPersistence;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.ServiceHttpClientConfig;

import software.wings.beans.Base;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.trigger.ArtifactSelection;
import software.wings.beans.trigger.ArtifactTriggerCondition;
import software.wings.beans.trigger.ScheduledTriggerCondition;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.Trigger.TriggerKeys;
import software.wings.infra.InfrastructureDefinition;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class TriggerImportService implements ImportService {
  @Inject private EnvironmentService environmentService;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject DiscoveryService discoveryService;
  @Inject HPersistence hPersistence;
  @Inject private MigrationHelperService migrationHelperService;
  @Inject @Named("pipelineServiceClientConfig") private ServiceHttpClientConfig pipelineServiceClientConfig;
  @Inject WorkflowImportService workflowImportService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public DiscoveryResult discover(ImportDTO importConnectorDTO) {
    TriggerFilter filter = (TriggerFilter) importConnectorDTO.getFilter();
    String accountId = importConnectorDTO.getAccountIdentifier();
    String appId = filter.getAppId();
    Set<String> triggerIds = filter.getTriggerIds();

    if (EmptyPredicate.isEmpty(triggerIds)) {
      List<Trigger> triggerList = hPersistence.createQuery(Trigger.class)
                                      .filter(Trigger.ACCOUNT_ID_KEY, accountId)
                                      .filter(TriggerKeys.appId, appId)
                                      .project(TriggerKeys.uuid, true)
                                      .asList();
      if (EmptyPredicate.isNotEmpty(triggerList)) {
        triggerIds = triggerList.stream().map(Base::getUuid).collect(Collectors.toSet());
      }
    }
    if (EmptyPredicate.isEmpty(triggerIds)) {
      return null;
    }
    return discoveryService.discoverMulti(accountId,
        DiscoveryInput.builder()
            .exportImage(false)
            .entities(triggerIds.stream()
                          .map(trigger
                              -> DiscoverEntityInput.builder()
                                     .entityId(trigger)
                                     .type(NGMigrationEntityType.TRIGGER)
                                     .appId(appId)
                                     .build())
                          .collect(Collectors.toList()))
            .build());
  }

  public void postMigrationSteps(
      String authToken, ImportDTO importDTO, DiscoveryResult discoveryResult, SaveSummaryDTO summaryDTO) {
    if (EmptyPredicate.isEmpty(discoveryResult.getEntities())) {
      return;
    }

    List<Trigger> triggers = discoveryResult.getEntities()
                                 .keySet()
                                 .stream()
                                 .filter(cgEntityId -> cgEntityId.getType().equals(NGMigrationEntityType.TRIGGER))
                                 .map(cgEntityId -> discoveryResult.getEntities().get(cgEntityId).getEntity())
                                 .map(entity -> (Trigger) entity)
                                 .collect(Collectors.toList());

    Set<String> workflowIds = getWorkflowIds(triggers);

    Map<CgEntityId, NGYamlFile> yamlFileMap = summaryDTO.getNgYamlFiles()
                                                  .stream()
                                                  .filter(ngYamlFile -> ngYamlFile.getCgBasicInfo() != null)
                                                  .distinct()
                                                  .collect(Collectors.toMap(yamlFile
                                                      -> CgEntityId.builder()
                                                             .id(yamlFile.getCgBasicInfo().getId())
                                                             .type(yamlFile.getCgBasicInfo().getType())
                                                             .build(),
                                                      yamlFile -> yamlFile, (yamlFile1, yamlFile2) -> yamlFile1));

    MigrationInputDTO inputDTO = MigratorUtility.getMigrationInput(authToken, importDTO);
    PmsClient pmsClient = MigratorUtility.getRestClient(inputDTO, pipelineServiceClientConfig, PmsClient.class);

    if (EmptyPredicate.isNotEmpty(workflowIds)) {
      workflowImportService.createWorkflowsAsPipeline(authToken, importDTO, discoveryResult, summaryDTO, workflowIds);
    }

    for (Trigger trigger : triggers) {
      NGYamlFile yamlFile = generateTriggerPayload(inputDTO, discoveryResult, yamlFileMap, trigger);
      if (yamlFile != null) {
        MigrationImportSummaryDTO triggerImportSummary = createTrigger(inputDTO, pmsClient, yamlFile);
        DiscoveryService.addToSummary(summaryDTO, yamlFile, triggerImportSummary);
      }
    }
  }

  private static Set<String> getWorkflowIds(List<Trigger> triggers) {
    if (EmptyPredicate.isEmpty(triggers)) {
      return new HashSet<>();
    }
    return triggers.stream()
        .filter(trigger -> WorkflowType.ORCHESTRATION.equals(trigger.getWorkflowType()))
        .map(Trigger::getWorkflowId)
        .collect(Collectors.toSet());
  }

  private MigrationImportSummaryDTO createTrigger(
      MigrationInputDTO inputDTO, PmsClient pmsClient, NGYamlFile ngYamlFile) {
    NgTriggerConfigSchemaWrapper triggerConfigSchemaWrapper = (NgTriggerConfigSchemaWrapper) ngYamlFile.getYaml();
    String yaml = YamlUtils.writeYamlString(triggerConfigSchemaWrapper.getTrigger());

    try {
      Response<ResponseDTO<NGTriggerResponseDTO>> resp =
          pmsClient
              .createTrigger(inputDTO.getDestinationAuthToken(), inputDTO.getDestinationAccountIdentifier(),
                  inputDTO.getOrgIdentifier(), inputDTO.getProjectIdentifier(),
                  triggerConfigSchemaWrapper.getTrigger().getPipelineIdentifier(),
                  RequestBody.create(MediaType.parse("application/yaml"), yaml))
              .execute();
      log.info("Trigger creation Response details {} {}", resp.code(), resp.message());
      log.info("Trigger yaml is \n - {}", NGYamlUtils.getYamlString(triggerConfigSchemaWrapper));
      return MigratorUtility.handleEntityMigrationResp(ngYamlFile, resp);
    } catch (Exception e) {
      log.error("Error creating the trigger", e);
      return MigrationImportSummaryDTO.builder()
          .errors(Lists.newArrayList(
              ImportError.builder().message(e.getMessage()).entity(ngYamlFile.getCgBasicInfo()).build()))
          .build();
    }
  }

  private Service getServiceFromArtifactStream(Map<CgEntityId, CgEntityNode> entities, String artifactStreamId) {
    CgEntityId cgEntityId = CgEntityId.builder().id(artifactStreamId).type(ARTIFACT_STREAM).build();
    ArtifactStream artifactStream = (ArtifactStream) entities.get(cgEntityId).getEntity();
    if (artifactStream != null) {
      CgEntityId serviceCgEntityId = CgEntityId.builder().id(artifactStream.getServiceId()).type(SERVICE).build();
      return (Service) entities.get(serviceCgEntityId).getEntity();
    }
    return null;
  }

  private NGYamlFile generateTriggerPayload(MigrationInputDTO inputDTO, DiscoveryResult discoveryResult,
      Map<CgEntityId, NGYamlFile> yamlFileMap, Trigger trigger) {
    NgEntityDetail pipelineDetail = getPipelineIdentifier(trigger, yamlFileMap);
    if (pipelineDetail == null) {
      return null;
    }

    CgEntityId entityId = CgEntityId.builder().type(TRIGGER).id(trigger.getUuid()).build();
    String name = MigratorUtility.generateName(inputDTO.getOverrides(), entityId, trigger.getName());
    String identifier = MigratorUtility.generateIdentifierDefaultName(
        inputDTO.getOverrides(), entityId, name, inputDTO.getIdentifierCaseFormat());
    String projectIdentifier = inputDTO.getProjectIdentifier();
    String orgIdentifier = inputDTO.getOrgIdentifier();
    String description = StringUtils.isBlank(trigger.getDescription()) ? "" : trigger.getDescription();

    Map<String, Object> inputDetails = new HashMap<>();
    JsonNode inputNode = getInputDetails(inputDTO, pipelineDetail, inputDetails);
    Object jsonStagesNode = inputDetails.get("stages");

    if (jsonStagesNode instanceof ArrayNode) {
      ArrayNode stagesNode = (ArrayNode) jsonStagesNode;
      for (int i = 0; i < stagesNode.size(); i++) {
        JsonNode stageNode = stagesNode.get(i);
        resolveServiceDetails(stageNode, trigger, inputDTO, discoveryResult, yamlFileMap);
        resolveEnvDetails(stageNode, trigger, inputDTO);
        resolveInfraDetails(stageNode, trigger, inputDTO);
      }
    }

    // We need to get the FG trigger inputs & set it
    NGTriggerConfigV2 triggerConfig = NGTriggerConfigV2.builder()
                                          .identifier(identifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .description(description)
                                          .name(name)
                                          .enabled(false)
                                          .pipelineIdentifier(pipelineDetail.getIdentifier())
                                          .source(getSourceInfo(discoveryResult, trigger, yamlFileMap))
                                          .inputYaml(inputNode != null ? inputNode.toString() : null)
                                          .build();

    return NGYamlFile.builder()
        .type(TRIGGER)
        .filename("triggers/" + trigger.getName() + ".yaml")
        .yaml(NgTriggerConfigSchemaWrapper.builder().trigger(triggerConfig).build())
        .ngEntityDetail(NgEntityDetail.builder()
                            .entityType(TRIGGER)
                            .identifier(identifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .build())
        .cgBasicInfo(trigger.getCgBasicInfo())
        .build();
  }

  private void resolveServiceDetails(JsonNode stageNode, Trigger trigger, MigrationInputDTO inputDTO,
      DiscoveryResult discoveryResult, Map<CgEntityId, NGYamlFile> yamlFileMap) {
    Optional<ArtifactSelection> firstArtifactSelection = trigger.getArtifactSelections().stream().findFirst();
    String artifactStreamId = null;
    if (trigger.getCondition() instanceof ArtifactTriggerCondition) {
      artifactStreamId = ((ArtifactTriggerCondition) trigger.getCondition()).getArtifactStreamId();
    }

    if (firstArtifactSelection.isPresent()) {
      ArtifactSelection artifactSelection = firstArtifactSelection.get();
      String triggerServiceRef = artifactSelection.getServiceName();
      String serviceNGId = MigratorUtility.generateIdentifier(triggerServiceRef, inputDTO.getIdentifierCaseFormat());
      String serviceRef = stageNode.at("/stage/template/templateInputs/spec/service/serviceRef").asText();
      if (RUNTIME_INPUT.equals(serviceRef) && !RUNTIME_INPUT.equals(triggerServiceRef)) {
        ObjectNode serviceNode = (ObjectNode) stageNode.at("/stage/template/templateInputs/spec/service");
        serviceNode.put("serviceRef", serviceNGId);
      }
    } else if (artifactStreamId != null) {
      Service service = getServiceFromArtifactStream(discoveryResult.getEntities(), artifactStreamId);
      if (service != null) {
        String serviceNGId = MigratorUtility.generateIdentifier(service.getName(), inputDTO.getIdentifierCaseFormat());
        String serviceRef = stageNode.at("/stage/template/templateInputs/spec/service/serviceRef").asText();
        if (RUNTIME_INPUT.equals(serviceRef)) {
          ObjectNode serviceNode = (ObjectNode) stageNode.at("/stage/template/templateInputs/spec/service");
          serviceNode.put("serviceRef", serviceNGId);
        }
      }
    }
  }

  private void resolveEnvDetails(JsonNode stageNode, Trigger trigger, MigrationInputDTO inputDTO) {
    Map<String, String> workflowVariables = trigger.getWorkflowVariables();
    if (workflowVariables != null) {
      String envCGId = workflowVariables.get("Environment");
      if (envCGId != null) {
        Environment env = environmentService.get(trigger.getAppId(), envCGId);
        if (env != null) {
          String envNGId = MigratorUtility.generateIdentifier(env.getName(), inputDTO.getIdentifierCaseFormat());

          String envRef = stageNode.at("/stage/template/templateInputs/spec/environment/environmentRef").asText();
          if (RUNTIME_INPUT.equals(envRef) && envNGId != null) {
            ObjectNode envNode = (ObjectNode) stageNode.at("/stage/template/templateInputs/spec/environment");
            envNode.put("environmentRef", envNGId);
          }
        }
      }
    }
  }

  private void resolveInfraDetails(JsonNode stageNode, Trigger trigger, MigrationInputDTO inputDTO) {
    Map<String, String> workflowVariables = trigger.getWorkflowVariables();
    if (workflowVariables != null) {
      String pattern = ".*InfraDefinition.*";
      Pattern regexPattern = Pattern.compile(pattern);
      String infraCGId = null;
      for (Map.Entry<String, String> entry : workflowVariables.entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        Matcher matcher = regexPattern.matcher(key);
        if (matcher.matches()) {
          infraCGId = value;
        }
      }
      if (infraCGId != null) {
        InfrastructureDefinition infra = infrastructureDefinitionService.get(trigger.getAppId(), infraCGId);
        if (infra != null) {
          String infraNGId = MigratorUtility.generateIdentifier(infra.getName(), inputDTO.getIdentifierCaseFormat());
          String infraRef =
              stageNode.at("/stage/template/templateInputs/spec/environment/infrastructureDefinitions").asText();
          if (RUNTIME_INPUT.equals(infraRef) && infraNGId != null) {
            ObjectNode infraNodeObj = (ObjectNode) stageNode.at("/stage/template/templateInputs/spec/environment");
            JsonNode infraNode = objectMapper.createObjectNode().put("identifier", infraNGId);
            ArrayNode infraArrayNode = objectMapper.createArrayNode().add(infraNode);
            infraNodeObj.set("infrastructureDefinitions", infraArrayNode);
          }
        }
      }
    }
  }

  private NgEntityDetail getPipelineIdentifier(Trigger trigger, Map<CgEntityId, NGYamlFile> yamlFileMap) {
    CgEntityId entityId =
        CgEntityId.builder()
            .id(trigger.getWorkflowId())
            .type(WorkflowType.PIPELINE.equals(trigger.getWorkflowType()) ? NGMigrationEntityType.PIPELINE
                                                                          : NGMigrationEntityType.WORKFLOW)
            .build();
    if (!yamlFileMap.containsKey(entityId)) {
      return null;
    }
    return yamlFileMap.get(entityId).getNgEntityDetail();
  }

  private NGTriggerSourceV2 getSourceInfo(
      DiscoveryResult discoveryResult, Trigger trigger, Map<CgEntityId, NGYamlFile> yamlFileMap) {
    NGTriggerType type;
    NGTriggerSpecV2 spec = null;
    switch (trigger.getCondition().getConditionType()) {
      case WEBHOOK:
        type = NGTriggerType.WEBHOOK;
        spec = WebhookTriggerConfigV2.builder()
                   .spec(CustomTriggerSpec.builder().build())
                   .type(WebhookTriggerType.CUSTOM)
                   .build();
        //        spec = WebhookFactory.getHandler((WebHookTriggerCondition) trigger.getCondition())
        //            .getConfig((WebHookTriggerCondition) trigger.getCondition(), yamlFileMap);
        break;
      case SCHEDULED:
        ScheduledTriggerCondition scheduledTriggerCondition = (ScheduledTriggerCondition) trigger.getCondition();
        type = NGTriggerType.SCHEDULED;
        String expression = scheduledTriggerCondition.getCronExpression().replace("?", "*");
        spec = ScheduledTriggerConfig.builder()
                   .type("Cron")
                   .spec(CronTriggerSpec.builder().type("UNIX").expression(expression).build())
                   .build();
        break;
      case NEW_ARTIFACT:
        ArtifactTriggerCondition artifactTriggerCondition = (ArtifactTriggerCondition) trigger.getCondition();
        ArtifactStreamMapper artifactStreamMapper =
            ArtifactStreamFactory.getArtifactStreamMapper(ArtifactStreamType.DOCKER.name());
        CgEntityNode entityNode = discoveryResult.getEntities().get(
            new CgEntityId(artifactTriggerCondition.getArtifactStreamId(), NGMigrationEntityType.ARTIFACT_STREAM));
        ArtifactStream artifactStream = null;
        if (entityNode != null) {
          artifactStream = (ArtifactStream) entityNode.getEntity();
          artifactStreamMapper = ArtifactStreamFactory.getArtifactStreamMapper(artifactStream);
        }
        type = NGTriggerType.ARTIFACT;
        spec = ArtifactTriggerConfig.builder()
                   .type(artifactStreamMapper.getArtifactType(yamlFileMap, artifactStream))
                   .spec(artifactStreamMapper.getTriggerSpec(
                       discoveryResult.getEntities(), artifactStream, yamlFileMap, trigger))
                   .build();
        break;
      case NEW_MANIFEST:
        // TODO
        type = NGTriggerType.MANIFEST;
        spec = ManifestTriggerConfig.builder().type(ManifestType.HELM_MANIFEST).spec(null).build();
        break;
      case PIPELINE_COMPLETION:
      case NEW_INSTANCE:
      default:
        type = NGTriggerType.WEBHOOK;
        spec = WebhookTriggerConfigV2.builder()
                   .type(WebhookTriggerType.CUSTOM)
                   .spec(CustomTriggerSpec.builder().build())
                   .build();
    }

    return NGTriggerSourceV2.builder().type(type).spec(spec).build();
  }

  private JsonNode getInputDetails(
      MigrationInputDTO inputDTO, NgEntityDetail pipelineDetail, Map<String, Object> inputDetails) {
    String inputYaml =
        migrationHelperService.getPipelineInput(inputDTO, pipelineDetail, inputDTO.getDestinationAccountIdentifier());

    JsonNode inputNode;
    if (StringUtils.isBlank(inputYaml)) {
      return null;
    }
    try {
      inputNode = YamlUtils.read(inputYaml, JsonNode.class);
    } catch (Exception ex) {
      log.warn("Error when getting inputs - ", ex);
      return null;
    }
    inputDetails.put("identifier", inputNode.at("/pipeline/identifier"));
    inputDetails.put("stages", inputNode.at("/pipeline/stages"));
    return inputNode;
  }
}
