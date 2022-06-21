/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.mapper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.constants.Constants.AMZ_SUBSCRIPTION_CONFIRMATION_TYPE;
import static io.harness.constants.Constants.X_AMZ_SNS_MESSAGE_TYPE;
import static io.harness.constants.Constants.X_BIT_BUCKET_EVENT;
import static io.harness.constants.Constants.X_GIT_HUB_EVENT;
import static io.harness.constants.Constants.X_GIT_LAB_EVENT;
import static io.harness.constants.Constants.X_HARNESS_TRIGGER_ID;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ngtriggers.beans.source.NGTriggerType.ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MANIFEST;
import static io.harness.ngtriggers.beans.source.NGTriggerType.WEBHOOK;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.AWS_CODECOMMIT;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.BITBUCKET;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.CUSTOM;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.GITHUB;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.GITLAB;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.USE_NATIVE_TYPE_ID;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HeaderConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TriggerException;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ngtriggers.beans.config.NGTriggerConfig;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.BuildDetails;
import io.harness.ngtriggers.beans.dto.LastTriggerExecutionDetails;
import io.harness.ngtriggers.beans.dto.NGTriggerDetailsResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerDetailsResponseDTO.NGTriggerDetailsResponseDTOBuilder;
import io.harness.ngtriggers.beans.dto.NGTriggerResponseDTO;
import io.harness.ngtriggers.beans.dto.PollingConfig;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.WebhookDetails;
import io.harness.ngtriggers.beans.dto.WebhookDetails.WebhookDetailsBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityBuilder;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory.TriggerEventHistoryKeys;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent.TriggerWebhookEventBuilder;
import io.harness.ngtriggers.beans.entity.metadata.BuildMetadata;
import io.harness.ngtriggers.beans.entity.metadata.CronMetadata;
import io.harness.ngtriggers.beans.entity.metadata.CustomMetadata;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata.WebhookMetadataBuilder;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerSpecV2;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.BuildAware;
import io.harness.ngtriggers.beans.source.artifact.HelmManifestSpec;
import io.harness.ngtriggers.beans.source.artifact.ManifestTriggerConfig;
import io.harness.ngtriggers.beans.source.artifact.ManifestTypeSpec;
import io.harness.ngtriggers.beans.source.scheduled.CronTriggerSpec;
import io.harness.ngtriggers.beans.source.scheduled.ScheduledTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAware;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.ngtriggers.exceptions.InvalidTriggerYamlException;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.helpers.WebhookConfigHelper;
import io.harness.ngtriggers.utils.WebhookEventPayloadParser;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.utils.YamlPipelineUtils;
import io.harness.webhook.WebhookConfigProvider;
import io.harness.webhook.WebhookHelper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class NGTriggerElementMapper {
  public static final int DAYS_BEFORE_CURRENT_DATE = 6;
  private TriggerEventHistoryRepository triggerEventHistoryRepository;
  private WebhookEventPayloadParser webhookEventPayloadParser;
  private WebhookConfigProvider webhookConfigProvider;

  public NGTriggerConfigV2 toTriggerConfigV2(String yaml) {
    try {
      return YamlPipelineUtils.read(yaml, NGTriggerConfigV2.class);
    } catch (IOException e) {
      throw new InvalidRequestException(e.getMessage()); // update this message
    }
  }

  public String generateNgTriggerConfigV2Yaml(
      NGTriggerEntity ngTriggerEntity, boolean throwExceptionIfYamlConversionFails) {
    String yaml = ngTriggerEntity.getYaml();

    try {
      if (ngTriggerEntity.getYmlVersion() == null || ngTriggerEntity.getYmlVersion().longValue() < 2) {
        NGTriggerConfigV2 ngTriggerConfigV2 = toTriggerConfigV2(ngTriggerEntity);
        ngTriggerConfigV2.setEnabled(ngTriggerEntity.getEnabled());
        yaml = generateNgTriggerConfigV2Yaml(ngTriggerConfigV2);
      }
    } catch (Exception e) {
      log.error("Failed while converting Trigger V0 yaml to V2 Yaml. \n" + yaml, e);
      if (throwExceptionIfYamlConversionFails) {
        throw new TriggerException("Failed while converting trigger yaml from version V0 to V2" + e, USER_SRE);
      }
    }
    return yaml;
  }

  public String generateNgTriggerConfigV2Yaml(NGTriggerConfigV2 ngTriggerConfigV2) {
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()
                                                     .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                                                     .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                                                     .disable(USE_NATIVE_TYPE_ID));
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    try {
      return objectMapper.writeValueAsString(ngTriggerConfigV2);
    } catch (Exception e) {
      throw new TriggerException("Failed while converting trigger yaml with version V0" + e, USER_SRE);
    }
  }

  public String generateNgTriggerConfigYaml(NGTriggerConfig ngTriggerConfig) throws Exception {
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()
                                                     .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                                                     .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                                                     .disable(USE_NATIVE_TYPE_ID));
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    return objectMapper.writeValueAsString(ngTriggerConfig);
  }

  public NGTriggerConfigV2 toTriggerConfigV2(NGTriggerEntity ngTriggerEntity) {
    try {
      if (ngTriggerEntity.getYmlVersion() == null || ngTriggerEntity.getYmlVersion() < 2) {
        NGTriggerConfig ngTriggerConfig = YamlPipelineUtils.read(ngTriggerEntity.getYaml(), NGTriggerConfig.class);
        return NgTriggerConfigAdaptor.convertFromV0ToV2(ngTriggerConfig, ngTriggerEntity);
      } else {
        return toTriggerConfigV2(ngTriggerEntity.getYaml());
      }
    } catch (Exception e) {
      throw new TriggerException("Failed while generating NGTriggerConfigV2", e, USER_SRE); // update this message
    }
  }

  public TriggerDetails toTriggerDetails(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String yaml) {
    NGTriggerConfigV2 config = toTriggerConfigV2(yaml);
    NGTriggerEntity ngTriggerEntity =
        toTriggerEntity(accountIdentifier, orgIdentifier, projectIdentifier, config, yaml);
    return TriggerDetails.builder().ngTriggerConfigV2(config).ngTriggerEntity(ngTriggerEntity).build();
  }

  public TriggerDetails toTriggerDetails(NGTriggerEntity ngTriggerEntity) {
    NGTriggerConfigV2 config = toTriggerConfigV2(ngTriggerEntity.getYaml());
    return TriggerDetails.builder().ngTriggerConfigV2(config).ngTriggerEntity(ngTriggerEntity).build();
  }

  public TriggerDetails mergeTriggerEntity(NGTriggerEntity existingEntity, String newYaml) {
    NGTriggerConfigV2 config = toTriggerConfigV2(newYaml);
    NGTriggerEntity entity = toTriggerEntity(existingEntity.getAccountId(), existingEntity.getOrgIdentifier(),
        existingEntity.getProjectIdentifier(), existingEntity.getIdentifier(), newYaml);

    copyEntityFieldsOutsideOfYml(existingEntity, entity);
    return TriggerDetails.builder().ngTriggerConfigV2(config).ngTriggerEntity(entity).build();
  }

  public void copyEntityFieldsOutsideOfYml(NGTriggerEntity existingEntity, NGTriggerEntity newEntity) {
    if (newEntity.getType() == ARTIFACT || newEntity.getType() == MANIFEST) {
      PollingConfig existingPollingConfig = existingEntity.getMetadata().getBuildMetadata().getPollingConfig();

      if (existingPollingConfig != null && isNotEmpty(existingPollingConfig.getSignature())) {
        newEntity.getMetadata().getBuildMetadata().getPollingConfig().setSignature(
            existingPollingConfig.getSignature());
      }
      if (existingPollingConfig != null && isNotEmpty(existingPollingConfig.getPollingDocId())) {
        newEntity.getMetadata().getBuildMetadata().getPollingConfig().setPollingDocId(
            existingPollingConfig.getPollingDocId());
      }
    }
  }

  public NGTriggerEntity toTriggerEntity(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, String yaml) {
    NGTriggerConfigV2 config = toTriggerConfigV2(yaml);
    if (!identifier.equals(config.getIdentifier())) {
      throw new InvalidRequestException("Identifier in url and yaml do not match");
    }
    return toTriggerEntity(accountIdentifier, orgIdentifier, projectIdentifier, config, yaml);
  }

  public NGTriggerEntity toTriggerEntity(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, NGTriggerConfigV2 config, String yaml) {
    NGTriggerEntityBuilder entityBuilder = NGTriggerEntity.builder()
                                               .name(config.getName())
                                               .identifier(config.getIdentifier())
                                               .description(config.getDescription())
                                               .yaml(yaml)
                                               .type(config.getSource().getType())
                                               .accountId(accountIdentifier)
                                               .orgIdentifier(orgIdentifier)
                                               .projectIdentifier(projectIdentifier)
                                               .targetIdentifier(config.getPipelineIdentifier())
                                               .targetType(TargetType.PIPELINE)
                                               .metadata(toMetadata(config.getSource()))
                                               .enabled(config.getEnabled())
                                               .tags(TagMapper.convertToList(config.getTags()));

    if (config.getSource().getType() == NGTriggerType.SCHEDULED) {
      entityBuilder.nextIterations(new ArrayList<>());
    }
    NGTriggerEntity entity = entityBuilder.build();
    if (config.getSource().getType() == NGTriggerType.SCHEDULED) {
      List<Long> nextIterations = entity.recalculateNextIterations("unused", true, 0);
      if (!nextIterations.isEmpty()) {
        entity.setNextIterations(nextIterations);
      }
    }
    return entity;
  }

  NGTriggerMetadata toMetadata(NGTriggerSourceV2 triggerSource) {
    switch (triggerSource.getType()) {
      case WEBHOOK:
        WebhookTriggerConfigV2 webhookTriggerConfig = (WebhookTriggerConfigV2) triggerSource.getSpec();

        WebhookMetadataBuilder metadata = WebhookMetadata.builder();
        if (webhookTriggerConfig.getType() != null) {
          metadata.type(webhookTriggerConfig.getType().getEntityMetadataName());
        }

        if (webhookTriggerConfig.getType() == CUSTOM) {
          metadata.custom(CustomMetadata.builder().build());
        } else if (WebhookConfigHelper.isGitSpec(webhookTriggerConfig)) {
          metadata.git(prepareGitMetadata(webhookTriggerConfig));
        }

        return NGTriggerMetadata.builder().webhook(metadata.build()).build();
      case SCHEDULED:
        ScheduledTriggerConfig scheduledTriggerConfig = (ScheduledTriggerConfig) triggerSource.getSpec();
        CronTriggerSpec cronTriggerSpec = (CronTriggerSpec) scheduledTriggerConfig.getSpec();
        return NGTriggerMetadata.builder()
            .cron(CronMetadata.builder().expression(cronTriggerSpec.getExpression()).build())
            .build();
      case ARTIFACT:
        ArtifactTypeSpec artifactTypeSpec = ((ArtifactTriggerConfig) triggerSource.getSpec()).getSpec();
        String artifactSourceType = artifactTypeSpec.getClass().getName();

        return NGTriggerMetadata.builder()
            .buildMetadata(BuildMetadata.builder()
                               .type(ARTIFACT)
                               .buildSourceType(artifactSourceType)
                               .pollingConfig(PollingConfig.builder().buildRef(EMPTY).signature(generateUuid()).build())
                               .build())
            .build();
      case MANIFEST:
        ManifestTypeSpec manifestTypeSpec = ((ManifestTriggerConfig) triggerSource.getSpec()).getSpec();
        String manifestSourceType = null;
        if (HelmManifestSpec.class.isAssignableFrom(manifestTypeSpec.getClass())) {
          manifestSourceType = manifestTypeSpec.getClass().getName();
        }

        return NGTriggerMetadata.builder()
            .buildMetadata(BuildMetadata.builder()
                               .type(MANIFEST)
                               .buildSourceType(manifestSourceType)
                               .pollingConfig(PollingConfig.builder().buildRef(EMPTY).signature(generateUuid()).build())
                               .build())
            .build();
      default:
        throw new InvalidRequestException("Type " + triggerSource.getType().toString() + " is invalid");
    }
  }

  @VisibleForTesting
  GitMetadata prepareGitMetadata(WebhookTriggerConfigV2 webhookTriggerConfig) {
    if (webhookTriggerConfig == null) {
      return null;
    }

    GitAware gitAware = WebhookConfigHelper.retrieveGitAware(webhookTriggerConfig);
    if (gitAware != null) {
      return GitMetadata.builder()
          .connectorIdentifier(gitAware.fetchConnectorRef())
          .repoName(gitAware.fetchRepoName())
          .build();
    }

    return null;
  }

  public NGTriggerResponseDTO toResponseDTO(NGTriggerEntity ngTriggerEntity) {
    return NGTriggerResponseDTO.builder()
        .name(ngTriggerEntity.getName())
        .identifier(ngTriggerEntity.getIdentifier())
        .description(ngTriggerEntity.getDescription())
        .type(ngTriggerEntity.getType())
        .accountIdentifier(ngTriggerEntity.getAccountId())
        .orgIdentifier(ngTriggerEntity.getOrgIdentifier())
        .projectIdentifier(ngTriggerEntity.getProjectIdentifier())
        .targetIdentifier(ngTriggerEntity.getTargetIdentifier())
        .version(ngTriggerEntity.getVersion())
        .yaml(generateNgTriggerConfigV2Yaml(ngTriggerEntity, true))
        .enabled(ngTriggerEntity.getEnabled() == null || ngTriggerEntity.getEnabled())
        .errorResponse(false)
        .build();
  }

  public NGTriggerResponseDTO toErrorDTO(InvalidTriggerYamlException e) {
    NGTriggerEntity ngTriggerEntity = e.getTriggerDetails().getNgTriggerEntity();
    return NGTriggerResponseDTO.builder()
        .name(ngTriggerEntity.getName())
        .identifier(ngTriggerEntity.getIdentifier())
        .description(ngTriggerEntity.getDescription())
        .type(ngTriggerEntity.getType())
        .orgIdentifier(ngTriggerEntity.getOrgIdentifier())
        .projectIdentifier(ngTriggerEntity.getProjectIdentifier())
        .targetIdentifier(ngTriggerEntity.getTargetIdentifier())
        .version(ngTriggerEntity.getVersion())
        .yaml(generateNgTriggerConfigV2Yaml(ngTriggerEntity, true))
        .enabled(ngTriggerEntity.getEnabled() == null || ngTriggerEntity.getEnabled())
        .errors(e.getErrors())
        .errorResponse(true)
        .build();
  }

  public TriggerWebhookEventBuilder toNGTriggerWebhookEvent(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String payload, List<HeaderConfig> headerConfigs) {
    WebhookTriggerType webhookTriggerType;
    Map<String, List<String>> headers =
        headerConfigs.stream().collect(Collectors.toMap(HeaderConfig::getKey, HeaderConfig::getValues));
    boolean isConfirmationMessage = false;
    if (webhookEventPayloadParser.containsHeaderKey(headers, X_GIT_HUB_EVENT)) {
      webhookTriggerType = GITHUB;
    } else if (webhookEventPayloadParser.containsHeaderKey(headers, X_GIT_LAB_EVENT)) {
      webhookTriggerType = GITLAB;
    } else if (webhookEventPayloadParser.containsHeaderKey(headers, X_BIT_BUCKET_EVENT)) {
      webhookTriggerType = BITBUCKET;
    } else if (webhookEventPayloadParser.containsHeaderKey(headers, X_AMZ_SNS_MESSAGE_TYPE)) {
      webhookTriggerType = AWS_CODECOMMIT;
      List<String> headerValues = webhookEventPayloadParser.getHeaderValue(headers, X_AMZ_SNS_MESSAGE_TYPE);
      if (isNotEmpty(headerValues)) {
        isConfirmationMessage = headerValues.stream().anyMatch(AMZ_SUBSCRIPTION_CONFIRMATION_TYPE::equalsIgnoreCase);
      }
    } else {
      if (isEmpty(accountIdentifier) || isEmpty(orgIdentifier) || isEmpty(projectIdentifier)) {
        throw new InvalidRequestException(
            "AccountIdentifier, OrgIdentifier, ProjectIdentifier can not be null for custom webhook executions");
      }
      webhookTriggerType = CUSTOM;
    }

    TriggerWebhookEventBuilder triggerWebhookEventBuilder =
        TriggerWebhookEvent.builder()
            .accountId(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .sourceRepoType(webhookTriggerType.getEntityMetadataName())
            .headers(headerConfigs)
            .payload(payload)
            .isSubscriptionConfirmation(isConfirmationMessage);

    HeaderConfig customTriggerIdentifier = headerConfigs.stream()
                                               .filter(header -> header.getKey().equalsIgnoreCase(X_HARNESS_TRIGGER_ID))
                                               .findAny()
                                               .orElse(null);

    if (customTriggerIdentifier != null && isNotBlank(customTriggerIdentifier.getValues().get(0))) {
      triggerWebhookEventBuilder.triggerIdentifier(customTriggerIdentifier.getValues().get(0));
    }

    return triggerWebhookEventBuilder;
  }

  public TriggerWebhookEventBuilder toNGTriggerWebhookEventForCustom(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String triggerIdentifier, String payload,
      List<HeaderConfig> headerConfigs) {
    WebhookTriggerType webhookTriggerType = CUSTOM;

    return TriggerWebhookEvent.builder()
        .accountId(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .triggerIdentifier(triggerIdentifier)
        .pipelineIdentifier(pipelineIdentifier)
        .sourceRepoType(webhookTriggerType.getEntityMetadataName())
        .headers(headerConfigs)
        .payload(payload);
  }

  public NGTriggerDetailsResponseDTO toNGTriggerDetailsResponseDTO(
      NGTriggerEntity ngTriggerEntity, boolean includeYaml, boolean throwExceptionIfYamlConversionFails) {
    String webhookUrl = EMPTY;
    if (ngTriggerEntity.getType() == WEBHOOK) {
      WebhookMetadata webhookMetadata = ngTriggerEntity.getMetadata().getWebhook();
      if (webhookMetadata.getGit() != null) {
        webhookUrl = WebhookHelper.generateWebhookUrl(webhookConfigProvider, ngTriggerEntity.getAccountId());
      } else if (webhookMetadata.getCustom() != null) {
        webhookUrl = WebhookHelper.generateCustomWebhookUrl(webhookConfigProvider, ngTriggerEntity.getAccountId(),
            ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getProjectIdentifier(),
            ngTriggerEntity.getTargetIdentifier(), ngTriggerEntity.getIdentifier());
      }
    }

    NGTriggerDetailsResponseDTOBuilder ngTriggerDetailsResponseDTO =
        NGTriggerDetailsResponseDTO.builder()
            .name(ngTriggerEntity.getName())
            .identifier(ngTriggerEntity.getIdentifier())
            .description(ngTriggerEntity.getDescription())
            .type(ngTriggerEntity.getType())
            .yaml(includeYaml ? generateNgTriggerConfigV2Yaml(ngTriggerEntity, throwExceptionIfYamlConversionFails)
                              : StringUtils.EMPTY)
            .tags(TagMapper.convertToMap(ngTriggerEntity.getTags()))
            .enabled(ngTriggerEntity.getEnabled() == null || ngTriggerEntity.getEnabled())
            .triggerStatus(ngTriggerEntity.getTriggerStatus())
            .webhookUrl(webhookUrl);

    // Webhook Details
    if (ngTriggerEntity.getType() == WEBHOOK) {
      WebhookDetailsBuilder webhookDetails = WebhookDetails.builder();
      webhookDetails.webhookSourceRepo(ngTriggerEntity.getMetadata().getWebhook().getType()).build();
      ngTriggerDetailsResponseDTO.webhookDetails(webhookDetails.build());
      ngTriggerDetailsResponseDTO.registrationStatus(
          ngTriggerEntity.getMetadata().getWebhook().getRegistrationStatus());
    } else if (ngTriggerEntity.getType() == MANIFEST || ngTriggerEntity.getType() == ARTIFACT) {
      NGTriggerConfigV2 ngTriggerConfigV2 = toTriggerConfigV2(ngTriggerEntity.getYaml());
      NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerConfigV2.getSource().getSpec();
      if (BuildAware.class.isAssignableFrom(ngTriggerSpecV2.getClass())) {
        BuildAware buildAware = (BuildAware) ngTriggerSpecV2;
        ngTriggerDetailsResponseDTO.buildDetails(BuildDetails.builder().buildType(buildAware.fetchBuildType()).build());
      }
    }

    Optional<TriggerEventHistory> triggerEventHistory = fetchLatestExecutionForTrigger(ngTriggerEntity);

    List<Integer> executions = generateLastWeekActivityData(ngTriggerEntity);
    if (isNotEmpty(executions)) {
      ngTriggerDetailsResponseDTO.executions(executions);
    }

    if (triggerEventHistory.isPresent()) {
      LastTriggerExecutionDetails lastTriggerExecutionDetails =
          LastTriggerExecutionDetails.builder()
              .lastExecutionStatus(triggerEventHistory.get().getFinalStatus())
              .lastExecutionSuccessful(!triggerEventHistory.get().isExceptionOccurred())
              .message(triggerEventHistory.get().getMessage())
              .planExecutionId(triggerEventHistory.get().getPlanExecutionId())
              .lastExecutionTime(triggerEventHistory.get().getCreatedAt())
              .build();
      ngTriggerDetailsResponseDTO.lastTriggerExecutionDetails(lastTriggerExecutionDetails);
    }

    return ngTriggerDetailsResponseDTO.build();
  }

  private List<Integer> generateLastWeekActivityData(NGTriggerEntity ngTriggerEntity) {
    long startTime = System.currentTimeMillis() - Duration.ofDays(DAYS_BEFORE_CURRENT_DATE).toMillis();
    Criteria criteria = TriggerFilterHelper.createCriteriaForTriggerEventCountLastNDays(ngTriggerEntity.getAccountId(),
        ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getIdentifier(),
        ngTriggerEntity.getTargetIdentifier(), startTime);

    List<TriggerEventHistory> triggerActivityList =
        triggerEventHistoryRepository.findAllActivationTimestampsInRange(criteria);

    Integer[] executions = prepareExecutionDataArray(startTime, triggerActivityList);
    return Arrays.asList(executions);
  }

  @VisibleForTesting
  Integer[] prepareExecutionDataArray(long startTime, List<TriggerEventHistory> triggerActivityList) {
    Integer[] executions = new Integer[] {0, 0, 0, 0, 0, 0, 0};
    if (isNotEmpty(triggerActivityList)) {
      List<Long> timeStamps =
          triggerActivityList.stream().map(event -> event.getCreatedAt()).sorted().collect(Collectors.toList());
      timeStamps.forEach(timeStamp -> {
        long diff = DAYS.between(Instant.ofEpochMilli(startTime).atZone(ZoneId.systemDefault()).toLocalDate(),
            Instant.ofEpochMilli(timeStamp).atZone(ZoneId.systemDefault()).toLocalDate());
        int index = (int) Math.abs(diff);
        if (index >= 0 && index < 7) {
          executions[index]++;
        }
      });
    }
    return executions;
  }

  public Optional<TriggerEventHistory> fetchLatestExecutionForTrigger(NGTriggerEntity ngTriggerEntity) {
    List<TriggerEventHistory> triggerEventHistoryList =
        triggerEventHistoryRepository
            .findFirst1ByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndTriggerIdentifier(
                ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(),
                ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getTargetIdentifier(),
                ngTriggerEntity.getIdentifier(), Sort.by(TriggerEventHistoryKeys.createdAt).descending());
    if (!isEmpty(triggerEventHistoryList)) {
      return Optional.of(triggerEventHistoryList.get(0));
    }
    return Optional.empty();
  }

  public void updateEntityYmlWithEnabledValue(NGTriggerEntity ngTriggerEntity) {
    try {
      YamlField yamlField = YamlUtils.readTree(ngTriggerEntity.getYaml());
      YamlNode triggerNode = yamlField.getNode().getField("trigger").getNode();
      ((ObjectNode) triggerNode.getCurrJsonNode()).put("enabled", ngTriggerEntity.getEnabled());
      String updateYml = YamlUtils.writeYamlString(yamlField);
      ngTriggerEntity.setYaml(updateYml);
    } catch (Exception e) {
      log.error(new StringBuilder("Failed to update enable attribute to ")
                    .append(ngTriggerEntity.getEnabled())
                    .append("in trigger yml for Trigger: ")
                    .append(TriggerHelper.getTriggerRef(ngTriggerEntity))
                    .toString());
    }
  }
}
