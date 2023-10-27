/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.govern.Switch.unhandled;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.EntityType.APPLICATION;
import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.PIPELINE;
import static software.wings.beans.EntityType.PROVISIONER;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.EntityType.TRIGGER;
import static software.wings.beans.EntityType.WORKFLOW;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.atteo.evo.inflector.English.plural;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TriggerException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;
import io.harness.validation.Update;

import software.wings.beans.EntityType;
import software.wings.beans.Event.Type;
import software.wings.beans.HarnessTag;
import software.wings.beans.HarnessTag.HarnessTagKeys;
import software.wings.beans.HarnessTagLink;
import software.wings.beans.HarnessTagLink.HarnessTagLinkKeys;
import software.wings.beans.User;
import software.wings.beans.trigger.Trigger;
import software.wings.dl.WingsPersistence;
import software.wings.features.TagsFeature;
import software.wings.features.api.GetAccountId;
import software.wings.features.api.PremiumFeature;
import software.wings.features.api.RestrictedApi;
import software.wings.features.extractors.HarnessTagAccountIdExtractor;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.HarnessTagService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ResourceLookupService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.yaml.YamlPushService;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.BasicDBObject;
import com.mongodb.ReadPreference;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@ValidateOnExecution
@Singleton
@Slf4j
public class HarnessTagServiceImpl implements HarnessTagService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AuthHandler authHandler;
  @Inject private ResourceLookupService resourceLookupService;
  @Inject private YamlPushService yamlPushService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private EnvironmentService environmentService;
  @Inject private WorkflowService workflowService;
  @Inject private PipelineService pipelineService;
  @Inject private InfrastructureProvisionerService infrastructureProvisionerService;
  @Inject private TriggerService triggerService;
  @Inject private AuthService authService;
  @Inject private AppService appService;
  @Inject private EntityNameCache entityNameCache;
  @Inject private FeatureFlagService featureFlagService;
  @Inject @Named(TagsFeature.FEATURE_NAME) private PremiumFeature tagsFeature;

  public static final Set<EntityType> supportedTagEntityTypes =
      ImmutableSet.of(SERVICE, ENVIRONMENT, WORKFLOW, PROVISIONER, PIPELINE, TRIGGER, APPLICATION);

  private static final String ALLOWED_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-_ /";
  private static final String ALLOWED_CHARS_WITH_EXPRESSIONS =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-_ /${}";
  private static final Set<Character> ALLOWED_CHARS_SET = Sets.newHashSet(Lists.charactersOf(ALLOWED_CHARS));
  private static final Set<Character> ALLOWED_CHARS_SET_WITH_EXPRESSIONS =
      Sets.newHashSet(Lists.charactersOf(ALLOWED_CHARS_WITH_EXPRESSIONS));
  private static final String SYSTEM_TAG_PREFIX = "system/";
  public static final String HARNESS_TAG_PREFIX = "harness.io/";

  private static final Pattern workflowVariablesPattern = Pattern.compile("\\$\\{workflow\\.variables\\.([^.{}]+)[}]");
  private static final Pattern workflowDefaultsPattern = Pattern.compile("\\$\\{workflow\\.([^.{}]+)[}]");
  private static final Pattern pipelineDefaultsPattern = Pattern.compile("\\$\\{pipeline\\.([^.{}]+)[}]");
  private static final Pattern appDefaultsPattern = Pattern.compile("\\$\\{app\\.defaults\\.([^.{}]+)[}]");
  private static final Pattern appPropertiesDefaultsPattern = Pattern.compile("\\$\\{app\\.([^.{}]+)[}]");
  private static final Pattern accountDefaultsPattern = Pattern.compile("\\$\\{account\\.defaults\\.([^.{}]+)[}]");

  private static int MAX_TAG_KEY_LENGTH = 128;
  private static int MAX_TAG_VALUE_LENGTH = 256;
  private static long MAX_TAGS_PER_ACCOUNT = 500L;
  private static long MAX_TAGS_PER_RESOURCE = 50L;

  @Override
  public HarnessTag createTag(HarnessTag tag, boolean syncFromGit, boolean allowSystemTagsCreate) {
    return createTag(tag, syncFromGit, allowSystemTagsCreate, false);
  }

  @Override
  public HarnessTag createTag(
      HarnessTag tag, boolean syncFromGit, boolean allowSystemTagsCreate, boolean allowExpressions) {
    sanitizeAndValidateHarnessTag(tag, allowExpressions);
    validateSystemTagNameCreation(tag, allowSystemTagsCreate);

    checkIfTagCanBeCreated(tag);

    wingsPersistence.save(tag);
    HarnessTag savedTag = get(tag.getAccountId(), tag.getKey());

    yamlPushService.pushYamlChangeSet(savedTag.getAccountId(), savedTag, savedTag, Type.UPDATE, syncFromGit, false);

    return savedTag;
  }

  private void checkIfTagCanBeCreated(HarnessTag tag) {
    HarnessTag existingTag = get(tag.getAccountId(), tag.getKey());

    if (existingTag != null) {
      throw new InvalidRequestException("Tag with given Tag Name already exists");
    }

    if (getTagCount(tag.getAccountId()) >= MAX_TAGS_PER_ACCOUNT) {
      throw new InvalidRequestException("Cannot add more tags. Maximum tags supported are " + MAX_TAGS_PER_ACCOUNT);
    }

    String accountId = tag.getAccountId();
    boolean isRestrictedTag = isNotEmpty(tag.getAllowedValues());
    if (!tagsFeature.isAvailableForAccount(accountId) && isRestrictedTag) {
      throw new InvalidRequestException(String.format("Operation not permitted for account [%s].", accountId), USER);
    }
  }

  @Override
  public HarnessTag create(HarnessTag tag) {
    return createTag(tag, false, true, false);
  }

  @Override
  @RestrictedApi(TagsFeature.class)
  public HarnessTag updateTag(@GetAccountId(HarnessTagAccountIdExtractor.class) HarnessTag tag, boolean syncFromGit) {
    return updateTag(tag, syncFromGit, false);
  }

  @Override
  @RestrictedApi(TagsFeature.class)
  public HarnessTag updateTag(
      @GetAccountId(HarnessTagAccountIdExtractor.class) HarnessTag tag, boolean syncFromGit, boolean allowExpressions) {
    sanitizeAndValidateHarnessTag(tag, allowExpressions);

    HarnessTag existingTag = get(tag.getAccountId(), tag.getKey());
    if (existingTag == null) {
      throw new InvalidRequestException("Tag with given Tag Name does not exist");
    }

    validateAllowedValuesUpdate(tag);

    Map<String, Object> keyValuePairsToAdd = new HashMap<>();
    Set<String> fieldsToRemove = new HashSet<>();
    if (isNotEmpty(tag.getAllowedValues())) {
      keyValuePairsToAdd.put(HarnessTagKeys.allowedValues, tag.getAllowedValues());
    } else {
      fieldsToRemove.add(HarnessTagKeys.allowedValues);
    }

    wingsPersistence.updateFields(HarnessTag.class, existingTag.getUuid(), keyValuePairsToAdd, fieldsToRemove);
    HarnessTag updatedTag = get(tag.getAccountId(), tag.getKey());

    yamlPushService.pushYamlChangeSet(
        updatedTag.getAccountId(), updatedTag, updatedTag, Type.UPDATE, syncFromGit, false);

    return updatedTag;
  }

  @Override
  @RestrictedApi(TagsFeature.class)
  public HarnessTag update(@GetAccountId(HarnessTagAccountIdExtractor.class) HarnessTag tag) {
    return updateTag(tag, false, false);
  }

  private void validateAllowedValuesUpdate(HarnessTag harnessTag) {
    Set<String> inUseValues = getInUseValues(harnessTag.getAccountId(), harnessTag.getKey());

    if (isEmpty(inUseValues)) {
      return;
    }

    if (isEmpty(harnessTag.getAllowedValues()) || !harnessTag.getAllowedValues().containsAll(inUseValues)) {
      if (isNotEmpty(harnessTag.getAllowedValues())) {
        inUseValues.removeAll(harnessTag.getAllowedValues());
      }

      String msg = format(
          "Allowed values must contain all in used values. %s [%s] %s missing in current allowed values list",
          plural("Value", inUseValues.size()), String.join(",", inUseValues), inUseValues.size() > 1 ? "are" : "is");

      throw new InvalidRequestException(msg, USER);
    }
  }

  @Override
  public HarnessTag get(@NotBlank String accountId, @NotBlank String key) {
    return wingsPersistence.createQuery(HarnessTag.class)
        .filter(HarnessTagKeys.accountId, accountId)
        .filter(HarnessTagKeys.key, key.trim())
        .get();
  }

  @Override
  public HarnessTag getTagWithInUseValues(@NotBlank String accountId, @NotBlank String key) {
    HarnessTag result = wingsPersistence.createQuery(HarnessTag.class)
                            .filter(HarnessTagKeys.accountId, accountId)
                            .filter(HarnessTagKeys.key, key.trim())
                            .get();

    if (result == null) {
      return null;
    }

    result.setInUseValues(getInUseValues(accountId, key));

    return result;
  }

  @Override
  public PageResponse<HarnessTag> list(PageRequest<HarnessTag> request) {
    return wingsPersistence.query(HarnessTag.class, request);
  }

  @Override
  public PageResponse<HarnessTag> listTagsWithInUseValues(PageRequest<HarnessTag> request) {
    PageResponse<HarnessTag> response = list(request);

    List<HarnessTag> tags = response.getResponse();
    if (isEmpty(tags)) {
      return response;
    }

    for (HarnessTag harnessTag : tags) {
      harnessTag.setInUseValues(getInUseValues(harnessTag.getAccountId(), harnessTag.getKey()));
    }

    return response;
  }

  @Override
  public List<HarnessTag> listTags(String accountId) {
    return wingsPersistence.createQuery(HarnessTag.class).filter(HarnessTagKeys.accountId, accountId).asList();
  }

  @Override
  public void deleteTag(@NotBlank String accountId, @NotBlank String key, boolean syncFromGit) {
    if (isTagInUse(accountId, key)) {
      throw new InvalidRequestException("Tag is in use. Cannot delete");
    }

    HarnessTag harnessTag = get(accountId, key);
    if (harnessTag == null) {
      return;
    }

    wingsPersistence.delete(wingsPersistence.createQuery(HarnessTag.class)
                                .filter(HarnessTagKeys.accountId, accountId)
                                .filter(HarnessTagKeys.key, key.trim()));

    yamlPushService.pushYamlChangeSet(
        harnessTag.getAccountId(), harnessTag, harnessTag, Type.UPDATE, syncFromGit, false);
  }

  @Override
  public void delete(@NotBlank String accountId, @NotBlank String key) {
    deleteTag(accountId, key, false);
  }

  @Override
  public void delete(@NotNull HarnessTag tag) {
    this.delete(tag.getAccountId(), tag.getKey());
  }

  @Override
  @ValidationGroups(Update.class)
  public void attachTag(HarnessTagLink tagLink) {
    attachTagWithoutGitPush(tagLink);
    pushTagLinkToGit(tagLink.getAccountId(), tagLink.getAppId(), tagLink.getEntityId(), tagLink.getEntityType(), false);
  }

  @Override
  public void attachTagWithoutGitPush(HarnessTagLink tagLink) {
    validateAndSanitizeTagLink(tagLink);
    boolean allowExpressions = false;
    if (tagLink.getEntityType() == WORKFLOW || tagLink.getEntityType() == PIPELINE) {
      allowExpressions = true;
    }

    validateAndCreateTagIfNeeded(tagLink.getAccountId(), tagLink.getKey(), tagLink.getValue(), allowExpressions);

    HarnessTagLink existingTagLink = wingsPersistence.createQuery(HarnessTagLink.class)
                                         .filter(HarnessTagLinkKeys.accountId, tagLink.getAccountId())
                                         .filter(HarnessTagLinkKeys.entityId, tagLink.getEntityId())
                                         .filter(HarnessTagLinkKeys.key, tagLink.getKey())
                                         .get();

    if (existingTagLink != null) {
      wingsPersistence.updateField(
          HarnessTagLink.class, existingTagLink.getUuid(), HarnessTagLinkKeys.value, tagLink.getValue());
    } else {
      if (getTagLinkCount(tagLink.getAccountId(), tagLink.getEntityId()) >= MAX_TAGS_PER_RESOURCE) {
        throw new InvalidRequestException(
            "Cannot attach more tags on resource. Maximum tags supported are " + MAX_TAGS_PER_RESOURCE);
      }
      wingsPersistence.save(tagLink);
    }

    resourceLookupService.updateResourceLookupRecordWithTags(
        tagLink.getAccountId(), tagLink.getEntityId(), tagLink.getKey(), tagLink.getValue(), true);
  }

  @Override
  @ValidationGroups(Update.class)
  public void detachTag(HarnessTagLink tagLink) {
    detachTagWithoutGitPush(tagLink.getAccountId(), tagLink.getEntityId(), tagLink.getKey());
    pushTagLinkToGit(tagLink.getAccountId(), tagLink.getAppId(), tagLink.getEntityId(), tagLink.getEntityType(), false);
  }

  @Override
  public void detachTagWithoutGitPush(@NotBlank String accountId, @NotBlank String entityId, @NotBlank String key) {
    wingsPersistence.delete(wingsPersistence.createQuery(HarnessTagLink.class)
                                .filter(HarnessTagLinkKeys.accountId, accountId)
                                .filter(HarnessTagLinkKeys.entityId, entityId)
                                .filter(HarnessTagLinkKeys.key, key.trim()));

    resourceLookupService.updateResourceLookupRecordWithTags(accountId, entityId, key, null, false);
  }

  @Override
  public PageResponse<HarnessTagLink> listResourcesWithTag(String accountId, PageRequest<HarnessTagLink> request) {
    PageRequest<HarnessTagLink> pageRequest = request.copy();
    int offset = pageRequest.getStart();
    int limit = pageRequest.getPageSize();

    pageRequest.setOffset("0");
    pageRequest.setLimit(String.valueOf(Integer.MAX_VALUE));

    PageResponse<HarnessTagLink> pageResponse = wingsPersistence.query(HarnessTagLink.class, pageRequest);
    List<HarnessTagLink> filteredResourcesWithTag = applyAuthFilters(pageResponse.getResponse());

    List<HarnessTagLink> response;
    int total = filteredResourcesWithTag.size();
    if (total <= offset) {
      response = new ArrayList<>();
    } else {
      int endIdx = Math.min(offset + limit, total);
      response = filteredResourcesWithTag.subList(offset, endIdx);
    }

    if (isNotEmpty(response)) {
      for (HarnessTagLink harnessTagLink : response) {
        try {
          String entityName =
              entityNameCache.getEntityName(harnessTagLink.getEntityType(), harnessTagLink.getEntityId());
          String appName = entityNameCache.getEntityName(APPLICATION, harnessTagLink.getAppId());

          harnessTagLink.setEntityName(entityName);
          harnessTagLink.setAppName(appName);
        } catch (ExecutionException ex) {
          throw new InvalidRequestException("Failed to find entity name", ex, USER);
        }
      }
    }

    return aPageResponse()
        .withResponse(response)
        .withTotal(filteredResourcesWithTag.size())
        .withOffset(request.getOffset())
        .withLimit(request.getLimit())
        .build();
  }

  @Override
  public void pruneTagLinks(String accountId, String entityId) {
    wingsPersistence.delete(wingsPersistence.createQuery(HarnessTagLink.class)
                                .filter(HarnessTagLinkKeys.accountId, accountId)
                                .filter(HarnessTagLinkKeys.entityId, entityId));
  }

  @Override
  @ValidationGroups(Update.class)
  public void authorizeTagAttachDetach(String appId, HarnessTagLink tagLink) {
    validateTagResourceAccess(
        appId, tagLink.getAccountId(), tagLink.getEntityId(), tagLink.getEntityType(), Action.UPDATE);
  }

  private void sanitizeAndValidateHarnessTag(HarnessTag tag, boolean allowExpressions) {
    tag.setKey(validateTagKey(tag.getKey(), allowExpressions));

    if (isNotEmpty(tag.getAllowedValues())) {
      Set<String> sanitizedAllowedValues = new HashSet<>();
      for (String value : tag.getAllowedValues()) {
        sanitizedAllowedValues.add(validateTagValue(value, allowExpressions));
      }
      tag.setAllowedValues(sanitizedAllowedValues);
    }
  }

  private String validateTagKey(String key, boolean allowExpressions) {
    if (isBlank(key)) {
      throw new InvalidRequestException("Tag name cannot be blank");
    }

    String trimmedKey = key.trim();
    if (trimmedKey.length() > MAX_TAG_KEY_LENGTH) {
      throw new InvalidRequestException("Max allowed size for tag name is " + MAX_TAG_KEY_LENGTH);
    }

    validateTagNameValueCharacterSet(trimmedKey, allowExpressions);

    if (trimmedKey.startsWith(HARNESS_TAG_PREFIX)) {
      throw new InvalidRequestException("Unauthorized: harness.io is a reserved Tag name prefix");
    }

    return trimmedKey;
  }

  private String validateTagValue(String value, boolean allowExpressions) {
    if (value == null) {
      throw new InvalidRequestException("Tag value cannot be null");
    }

    String trimmedValue = value.trim();
    if (trimmedValue.length() > MAX_TAG_VALUE_LENGTH) {
      throw new InvalidRequestException("Max allowed size for tag value is " + MAX_TAG_VALUE_LENGTH);
    }

    validateTagNameValueCharacterSet(trimmedValue, allowExpressions);
    return trimmedValue;
  }

  private void validateTagNameValueCharacterSet(String value, boolean allowExpressions) {
    if (isBlank(value)) {
      return;
    }

    if (allowExpressions) {
      if (!ALLOWED_CHARS_SET_WITH_EXPRESSIONS.containsAll(Lists.charactersOf(value))) {
        throw new InvalidRequestException("Tag name/value can contain only " + ALLOWED_CHARS_WITH_EXPRESSIONS);
      }
      // allow only workflow variables, app level and account level defaults to be added here
      validateExpression(value);
    } else {
      if (!ALLOWED_CHARS_SET.containsAll(Lists.charactersOf(value))) {
        throw new InvalidRequestException("Tag name/value can contain only " + ALLOWED_CHARS);
      }
    }

    if (Sets.newHashSet('.', '_', '-', '/').contains(value.charAt(0))) {
      throw new InvalidRequestException("Tag name/value cannot begin with .-_/");
    }
  }

  private void validateExpression(String expression) {
    if (expression.startsWith("${")) {
      Matcher workflowVariableMatcher = workflowVariablesPattern.matcher(expression);
      Matcher workflowDefaultsMatcher = workflowDefaultsPattern.matcher(expression);
      Matcher pipelineDefaultsMatcher = pipelineDefaultsPattern.matcher(expression);
      Matcher appDefaultsMatcher = appDefaultsPattern.matcher(expression);
      Matcher appPropertiesDefaultsMatcher = appPropertiesDefaultsPattern.matcher(expression);
      Matcher accountDefaultsMatcher = accountDefaultsPattern.matcher(expression);
      if (!workflowVariableMatcher.matches() && !workflowDefaultsMatcher.matches() && !pipelineDefaultsMatcher.matches()
          && !appDefaultsMatcher.matches() && !appPropertiesDefaultsMatcher.matches()
          && !accountDefaultsMatcher.matches()) {
        throw new InvalidRequestException(
            "Only workflow variables, app defaults and account defaults can be added as expressions");
      }
    }
  }

  private void validateAndSanitizeTagLink(HarnessTagLink tagLink) {
    if (!supportedTagEntityTypes.contains(tagLink.getEntityType())) {
      throw new InvalidRequestException("Unsupported entityType specified. " + tagLink.getEntityType());
    }

    notNullCheck("appId", tagLink.getAppId());

    if (tagLink.getValue() == null) {
      throw new InvalidRequestException("Tag value cannot be null");
    }

    boolean allowExpressions = false;
    if (tagLink.getEntityType() == WORKFLOW || tagLink.getEntityType() == PIPELINE) {
      allowExpressions = true;
      if (tagLink.getKey().startsWith("${") && !tagLink.getValue().equals("")) {
        throw new InvalidRequestException("Tag value should be empty as key contains expression");
      }
    }
    // extra check to validate pipeline defaults are not used in workflow tags
    validateNoPipelineDefaultsAsWorkflowTag(tagLink);

    tagLink.setKey(validateTagKey(tagLink.getKey(), allowExpressions));
    tagLink.setValue(validateTagValue(tagLink.getValue(), allowExpressions));
  }

  private void validateNoPipelineDefaultsAsWorkflowTag(HarnessTagLink tagLink) {
    if (tagLink.getEntityType() == WORKFLOW) {
      if (tagLink.getKey().startsWith("${pipeline.")) {
        throw new InvalidRequestException("Pipeline defaults cannot be used as tags in workflow");
      }
      if (tagLink.getValue().startsWith("${pipeline.")) {
        throw new InvalidRequestException("Pipeline defaults cannot be used as tags in workflow");
      }
    }
  }

  private void validateAndCreateTagIfNeeded(String accountId, String key, String value, boolean allowExpressions) {
    HarnessTag existingTag = get(accountId, key);
    if (existingTag == null) {
      createTag(HarnessTag.builder().accountId(accountId).key(key).build(), false, false, allowExpressions);
      return;
    }

    if (isNotEmpty(existingTag.getAllowedValues()) && !existingTag.getAllowedValues().contains(value)) {
      throw new InvalidRequestException(
          String.format("'%s' is not in allowedValues:%s for Tag:%s", value, existingTag.getAllowedValues(), key));
    }
  }

  private long getTagCount(String accountId) {
    return wingsPersistence.createQuery(HarnessTag.class).filter(HarnessTagKeys.accountId, accountId).count();
  }

  private long getTagLinkCount(String accountId, String entityId) {
    return wingsPersistence.createQuery(HarnessTagLink.class)
        .filter(HarnessTagLinkKeys.accountId, accountId)
        .filter(HarnessTagLinkKeys.entityId, entityId)
        .count();
  }

  private boolean isTagInUse(String accountId, String key) {
    BasicDBObject andQuery = new BasicDBObject();
    List<BasicDBObject> conditions = new ArrayList<>();
    conditions.add(new BasicDBObject(HarnessTagLinkKeys.accountId, accountId));
    conditions.add(new BasicDBObject(HarnessTagLinkKeys.key, key));
    andQuery.put("$and", conditions);

    return wingsPersistence.getCollection(HarnessTagLink.class).findOne(andQuery) != null;
  }

  private Set<String> getInUseValues(String accountId, String key) {
    BasicDBObject andQuery = new BasicDBObject();
    List<BasicDBObject> conditions = new ArrayList<>();
    conditions.add(new BasicDBObject(HarnessTagLinkKeys.accountId, accountId));
    conditions.add(new BasicDBObject(HarnessTagLinkKeys.key, key));
    andQuery.put("$and", conditions);

    return new HashSet<>(
        wingsPersistence.getCollection(HarnessTagLink.class).distinct(HarnessTagLinkKeys.value, andQuery));
  }

  private List<HarnessTagLink> applyAuthFilters(List<HarnessTagLink> tagLinks) {
    List<HarnessTagLink> filteredTagLinks = new ArrayList<>();

    if (tagLinks == null) {
      return filteredTagLinks;
    }

    tagLinks.forEach(tagLink -> {
      try {
        validateTagResourceAccess(
            tagLink.getAppId(), tagLink.getAccountId(), tagLink.getEntityId(), tagLink.getEntityType(), Action.READ);
        filteredTagLinks.add(tagLink);
      } catch (Exception ex) {
        // Exception is thrown if the user does not have permissions on the entity
      }
    });

    return filteredTagLinks;
  }

  @Override
  public void validateTagResourceAccess(
      String appId, String accountId, String entityId, EntityType entityType, Action action) {
    notNullCheck("appId cannot be null", appId);

    if (EntityType.APPLICATION == entityType) {
      authorizeApplication(appId, accountId, action);
      return;
    }

    if (EntityType.TRIGGER == entityType) {
      // For Read action, we check if the user has access to App or not.
      // This is consistent with what is done in trigger resource list
      if (Action.READ == action) {
        authorizeApplication(appId, accountId, action);
      } else {
        authorizeTriggers(appId, entityId, accountId);
      }

      return;
    }

    PermissionType permissionType = getPermissionType(entityType);
    PermissionAttribute permissionAttribute = new PermissionAttribute(permissionType, action);

    authHandler.authorize(asList(permissionAttribute), asList(appId), entityId);
  }

  @Override
  public void convertRestrictedTagsToNonRestrictedTags(Collection<String> accounts) {
    UpdateOperations<HarnessTag> updateOp =
        wingsPersistence.createUpdateOperations(HarnessTag.class).unset(HarnessTagKeys.allowedValues);
    Query<HarnessTag> query =
        wingsPersistence.createQuery(HarnessTag.class).field(HarnessTagKeys.accountId).in(accounts);

    wingsPersistence.update(query, updateOp);
  }

  private void authorizeTriggers(String appId, String entityId, String accountId) {
    Trigger existingTrigger = triggerService.get(appId, entityId);
    if (existingTrigger == null) {
      throw new TriggerException("Trigger does not exist", USER);
    }
    triggerService.authorize(existingTrigger, true);
  }

  private void authorizeApplication(String appId, String accountId, Action action) {
    User user = UserThreadLocal.get();

    authService.authorizeAppAccess(accountId, appId, user, action);
  }

  private PermissionType getPermissionType(EntityType entityType) {
    PermissionType permissionType;

    switch (entityType) {
      case SERVICE:
        permissionType = PermissionType.SERVICE;
        break;

      case ENVIRONMENT:
        permissionType = PermissionType.ENV;
        break;

      case WORKFLOW:
        permissionType = PermissionType.WORKFLOW;
        break;

      case PIPELINE:
        permissionType = PermissionType.PIPELINE;
        break;

      case PROVISIONER:
        permissionType = PermissionType.PROVISIONER;
        break;

      default:
        unhandled(entityType);
        throw new InvalidRequestException(format("Unsupported entity type %s for tags", entityType), USER);
    }

    return permissionType;
  }

  @Override
  public List<HarnessTagLink> getTagLinksWithEntityId(String accountId, String entityId, boolean hitSecondary) {
    FindOptions findOptions = new FindOptions();
    if (hitSecondary && accountId != null
        && featureFlagService.isEnabled(FeatureName.CDS_QUERY_OPTIMIZATION_V2, accountId)) {
      findOptions.readPreference(ReadPreference.secondaryPreferred());
    }
    return wingsPersistence.createQuery(HarnessTagLink.class)
        .filter(HarnessTagLinkKeys.accountId, accountId)
        .filter(HarnessTagLinkKeys.entityId, entityId)
        .order(HarnessTagLinkKeys.key)
        .asList(findOptions);
  }

  @Override
  public void pushTagLinkToGit(
      String accountId, String appId, String entityId, EntityType entityType, boolean syncFromGit) {
    PersistentEntity resource = getPersistentEntity(appId, entityId, entityType);

    yamlPushService.pushYamlChangeSet(accountId, resource, resource, Type.UPDATE, syncFromGit, false);
  }

  private PersistentEntity getPersistentEntity(String appId, String entityId, EntityType entityType) {
    switch (entityType) {
      case SERVICE:
        return serviceResourceService.get(appId, entityId, false);

      case ENVIRONMENT:
        return environmentService.get(appId, entityId, false);

      case WORKFLOW:
        return workflowService.readWorkflow(appId, entityId);

      case PIPELINE:
        return pipelineService.readPipeline(appId, entityId, false);

      case PROVISIONER:
        return infrastructureProvisionerService.get(appId, entityId);

      case TRIGGER:
        return triggerService.get(appId, entityId);

      case APPLICATION:
        return appService.get(entityId, false);

      default:
        unhandled(entityType);
    }

    return null;
  }

  private void validateSystemTagNameCreation(HarnessTag tag, boolean allowSystemTagsCreate) {
    if (!allowSystemTagsCreate && tag.getKey().startsWith(SYSTEM_TAG_PREFIX)) {
      throw new InvalidRequestException("Unauthorized: User need to have MANAGE_TAGS permission to create system tags");
    }
  }

  @Override
  public Set<HarnessTagLink> getTagLinks(String accountId, EntityType entityType, Set<String> entityIds, String key) {
    List<HarnessTagLink> harnessTagLinks = wingsPersistence.createQuery(HarnessTagLink.class)
                                               .filter(HarnessTagLinkKeys.accountId, accountId)
                                               .field(HarnessTagLinkKeys.entityId)
                                               .in(entityIds)
                                               .filter(HarnessTagLinkKeys.entityType, entityType)
                                               .filter(HarnessTagLinkKeys.key, key)
                                               .asList();
    return new HashSet<>(harnessTagLinks);
  }

  @Override
  public Set<String> getEntityIdsWithTag(String accountId, String key, EntityType entityType, String value) {
    Query<HarnessTagLink> query = wingsPersistence.createQuery(HarnessTagLink.class)
                                      .filter(HarnessTagLinkKeys.accountId, accountId)
                                      .filter(HarnessTagLinkKeys.entityType, entityType.name())
                                      .filter(HarnessTagLinkKeys.key, key);
    if (isNotEmpty(value)) {
      query.field(HarnessTagLinkKeys.value).equal(value);
    }

    List<HarnessTagLink> harnessTagLinks = query.project("entityId", true).asList();
    return harnessTagLinks.stream().map(HarnessTagLink::getEntityId).collect(Collectors.toSet());
  }

  @Override
  public <T extends UuidAccess> PageResponse<HarnessTagLink> fetchTagsForEntity(String accountId, T entity) {
    PageRequest<HarnessTagLink> request = PageRequestBuilder.aPageRequest()
                                              .addFilter(HarnessTagLinkKeys.entityId, Operator.EQ, entity.getUuid())
                                              .addFilter(HarnessTagLinkKeys.accountId, Operator.EQ, accountId)
                                              .build();

    return this.listResourcesWithTag(accountId, request);
  }
}
