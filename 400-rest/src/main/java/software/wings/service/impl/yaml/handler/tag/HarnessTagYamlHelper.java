/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.tag;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.HarnessTag;
import software.wings.beans.HarnessTag.HarnessTagAbstractYaml;
import software.wings.beans.HarnessTag.Yaml;
import software.wings.beans.HarnessTagLink;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.beans.entityinterface.TagAware;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.yaml.ChangeContext;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.HarnessTagService;
import software.wings.yaml.BaseEntityYaml;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@Singleton
@Slf4j
public class HarnessTagYamlHelper {
  @Inject HarnessTagService harnessTagService;
  @Inject AppService appService;

  public void updateYamlWithHarnessTagLinks(PersistentEntity entity, String appId, BaseEntityYaml yaml) {
    if (!(entity instanceof TagAware)) {
      return;
    }

    String accountId = getAccountId(entity, appId);
    String entityId = ((UuidAware) entity).getUuid();
    List<HarnessTagLink> tagLinks = harnessTagService.getTagLinksWithEntityId(accountId, entityId, false);

    Map<String, String> harnessTags = new LinkedHashMap<>();
    tagLinks.forEach(harnessTagLink -> harnessTags.put(harnessTagLink.getKey(), harnessTagLink.getValue()));

    if (isNotEmpty(harnessTags)) {
      yaml.setTags(harnessTags);
    }
  }

  private String getAccountId(PersistentEntity entity, String appId) {
    if (!GLOBAL_APP_ID.equals(appId)) {
      return appService.getAccountIdByAppId(appId);
    }

    return null;
  }

  public void upsertTagLinksIfRequired(ChangeContext changeContext) {
    PersistentEntity entity = changeContext.getEntity();

    if (!(entity instanceof TagAware)) {
      return;
    }

    String accountId = changeContext.getChange().getAccountId();
    String appId = ((ApplicationAccess) entity).getAppId();
    if (isEmpty(appId)) {
      appId = GLOBAL_APP_ID;
    }

    BaseEntityYaml yaml = (BaseEntityYaml) changeContext.getYaml();
    Map<String, String> tagsFromYaml = yaml.getTags();
    if (isEmpty(tagsFromYaml)) {
      tagsFromYaml = new HashMap<>();
    }

    String entityId = ((UuidAware) entity).getUuid();
    List<HarnessTagLink> tagLinks = harnessTagService.getTagLinksWithEntityId(accountId, entityId, false);

    Map<String, String> tagsFromDB = new HashMap<>();
    tagLinks.forEach(harnessTagLink -> tagsFromDB.put(harnessTagLink.getKey(), harnessTagLink.getValue()));

    Map<String, String> tagsToBeAdded = new HashMap<>();
    List<String> tagsToBeRemoved = new ArrayList<>();

    for (Entry<String, String> entry : tagsFromYaml.entrySet()) {
      String tagKey = entry.getKey();
      String tagValue = entry.getValue();

      if (tagsFromDB.containsKey(tagKey)) {
        if (!tagValue.equals(tagsFromDB.get(tagKey))) {
          tagsToBeAdded.put(tagKey, tagValue);
        }
      } else {
        tagsToBeAdded.put(tagKey, tagValue);
      }
    }

    for (Entry<String, String> entry : tagsFromDB.entrySet()) {
      String tagKey = entry.getKey();

      if (!tagsFromYaml.containsKey(tagKey)) {
        tagsToBeRemoved.add(tagKey);
      }
    }

    for (Entry<String, String> entry : tagsToBeAdded.entrySet()) {
      String tagKey = entry.getKey();
      String tagValue = entry.getValue();

      EntityType entityType = getEntityType(entity);

      HarnessTagLink harnessTagLink = HarnessTagLink.builder()
                                          .accountId(accountId)
                                          .appId(appId)
                                          .entityId(entityId)
                                          .entityType(entityType)
                                          .key(tagKey)
                                          .value(tagValue)
                                          .build();
      harnessTagService.attachTagWithoutGitPush(harnessTagLink);
    }

    for (String tagKey : tagsToBeRemoved) {
      harnessTagService.detachTagWithoutGitPush(accountId, entityId, tagKey);
    }

    // We don't want to generate multiple yaml changeSets. Once all the tags are processed, then push to git
    // Don't push to git if nothing changed. Avoid generating unnecessary changesets
    if (isEmpty(tagsToBeAdded) && isEmpty(tagsToBeRemoved)) {
      return;
    }

    boolean syncFromGit = changeContext.getChange().isSyncFromGit();
    EntityType entityType = getEntityType(entity);
    harnessTagService.pushTagLinkToGit(accountId, appId, entityId, entityType, syncFromGit);
  }

  private EntityType getEntityType(PersistentEntity entity) {
    if (entity instanceof Application) {
      return EntityType.APPLICATION;
    } else if (entity instanceof Service) {
      return EntityType.SERVICE;
    } else if (entity instanceof Environment) {
      return EntityType.ENVIRONMENT;
    } else if (entity instanceof Workflow) {
      return EntityType.WORKFLOW;
    } else if (entity instanceof Pipeline) {
      return EntityType.PIPELINE;
    } else if (entity instanceof Trigger) {
      return EntityType.TRIGGER;
    } else if (entity instanceof InfrastructureProvisioner) {
      return EntityType.PROVISIONER;
    } else {
      throw new InvalidRequestException("Unhandled entity " + entity.getClass().getSimpleName(), USER);
    }
  }

  public List<HarnessTagAbstractYaml> getHarnessTagsYamlList(List<HarnessTag> harnessTags) {
    List<HarnessTagAbstractYaml> harnessTagsYamlList = new ArrayList<>();

    if (isEmpty(harnessTags)) {
      return harnessTagsYamlList;
    }

    for (HarnessTag harnessTag : harnessTags) {
      List<String> allowedValues = new ArrayList<>();

      if (isNotEmpty(harnessTag.getAllowedValues())) {
        allowedValues = new ArrayList<>(harnessTag.getAllowedValues());
      }

      harnessTagsYamlList.add(
          HarnessTagAbstractYaml.builder().name(harnessTag.getKey()).allowedValues(allowedValues).build());
    }

    return harnessTagsYamlList;
  }

  public void upsertHarnessTags(Yaml yaml, String accountId, boolean syncFromGit) {
    List<HarnessTag> currentTags = harnessTagService.listTags(accountId);
    Map<String, Set<String>> currentTagsMap = new HashMap<>();
    for (HarnessTag harnessTag : currentTags) {
      Set<String> allowedValues = new HashSet<>();
      if (isNotEmpty(harnessTag.getAllowedValues())) {
        allowedValues = harnessTag.getAllowedValues();
      }

      currentTagsMap.put(harnessTag.getKey(), allowedValues);
    }

    List<HarnessTagAbstractYaml> updatedTags = yaml.getTag();
    Map<String, Set<String>> updatedTagsMap = new HashMap<>();
    if (isNotEmpty(updatedTags)) {
      for (HarnessTagAbstractYaml harnessTagAbstractYaml : updatedTags) {
        Set<String> allowedValues = new HashSet<>();
        if (isNotEmpty(harnessTagAbstractYaml.getAllowedValues())) {
          allowedValues = new HashSet<>(harnessTagAbstractYaml.getAllowedValues());
        }

        updatedTagsMap.put(harnessTagAbstractYaml.getName(), allowedValues);
      }
    }

    List<HarnessTag> tagsToBeAdded = new ArrayList<>();
    List<HarnessTag> tagsToBeUpdated = new ArrayList<>();
    List<String> tagsToBeRemoved = new ArrayList<>();

    // Tags to be added/updated
    for (Entry<String, Set<String>> entry : updatedTagsMap.entrySet()) {
      String key = entry.getKey();
      Set<String> value = entry.getValue();

      if (!currentTagsMap.containsKey(key)) {
        tagsToBeAdded.add(HarnessTag.builder().key(key).allowedValues(value).build());
      } else if (!value.equals(currentTagsMap.get(key))) {
        tagsToBeUpdated.add(HarnessTag.builder().key(key).allowedValues(value).build());
      }
    }

    // Tags to be removed
    for (Entry<String, Set<String>> entry : currentTagsMap.entrySet()) {
      if (!updatedTagsMap.containsKey(entry.getKey())) {
        tagsToBeRemoved.add(entry.getKey());
      }
    }

    for (HarnessTag harnessTag : tagsToBeAdded) {
      harnessTag.setAccountId(accountId);
      harnessTagService.createTag(harnessTag, syncFromGit, true);
    }

    for (HarnessTag harnessTag : tagsToBeUpdated) {
      harnessTag.setAccountId(accountId);
      harnessTagService.updateTag(harnessTag, syncFromGit);
    }

    for (String tagKey : tagsToBeRemoved) {
      harnessTagService.deleteTag(accountId, tagKey, syncFromGit);
    }
  }

  public void deleteTags(Yaml yaml, String accountId, boolean syncFromGit) {
    List<HarnessTagAbstractYaml> tags = yaml.getTag();

    if (isNotEmpty(tags)) {
      for (HarnessTagAbstractYaml harnessTagAbstractYaml : tags) {
        harnessTagService.deleteTag(accountId, harnessTagAbstractYaml.getName(), syncFromGit);
      }
    }
  }
}
