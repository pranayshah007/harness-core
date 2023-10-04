/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.persistence.UuidAccess;
import io.harness.validation.Update;

import software.wings.beans.EntityType;
import software.wings.beans.HarnessTag;
import software.wings.beans.HarnessTagLink;
import software.wings.security.PermissionAttribute.Action;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotBlank;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

public interface HarnessTagService {
  HarnessTag create(HarnessTag tag);
  HarnessTag update(HarnessTag tag);
  HarnessTag get(@NotBlank String accountId, @NotBlank String key);
  HarnessTag getTagWithInUseValues(@NotBlank String accountId, @NotBlank String key);
  PageResponse<HarnessTag> list(PageRequest<HarnessTag> request);
  void delete(@NotBlank String accountId, @NotBlank String key);
  void delete(@NotNull HarnessTag tag);
  @ValidationGroups(Update.class) void attachTag(@Valid HarnessTagLink tagLink);
  @ValidationGroups(Update.class) void detachTag(@Valid HarnessTagLink tagLink);
  PageResponse<HarnessTagLink> listResourcesWithTag(String accountId, PageRequest<HarnessTagLink> request);
  void pruneTagLinks(String accountId, String entityId);
  @ValidationGroups(Update.class) void authorizeTagAttachDetach(String appId, @Valid HarnessTagLink tagLink);
  List<HarnessTagLink> getTagLinksWithEntityId(String accountId, String entityId, boolean hitSecondary);
  void pushTagLinkToGit(String accountId, String appId, String entityId, EntityType entityType, boolean syncFromGit);
  void attachTagWithoutGitPush(HarnessTagLink tagLink);
  void detachTagWithoutGitPush(@NotBlank String accountId, @NotBlank String entityId, @NotBlank String key);

  PageResponse<HarnessTag> listTagsWithInUseValues(PageRequest<HarnessTag> request);
  List<HarnessTag> listTags(String accountId);
  HarnessTag createTag(HarnessTag tag, boolean syncFromGit, boolean allowSystemTagCreate);
  HarnessTag createTag(HarnessTag tag, boolean syncFromGit, boolean allowSystemTagCreate, boolean allowExpressions);
  HarnessTag updateTag(HarnessTag tag, boolean syncFromGit);
  HarnessTag updateTag(HarnessTag tag, boolean syncFromGit, boolean allowExpressions);
  void deleteTag(@NotBlank String accountId, @NotBlank String key, boolean syncFromGit);

  void validateTagResourceAccess(String appId, String accountId, String entityId, EntityType entityType, Action action);
  void convertRestrictedTagsToNonRestrictedTags(Collection<String> accounts);

  Set<HarnessTagLink> getTagLinks(String accountId, EntityType entityType, Set<String> entityIds, String key);

  /**
   * @param accountId
   * @param key
   * @param entityType
   * @param value if value is not present, returns all the entityIds with the key and any value
   * @return set of entityIds
   */
  Set<String> getEntityIdsWithTag(String accountId, String key, EntityType entityType, String value);

  // fetches tags for an entity
  <T extends UuidAccess> PageResponse<HarnessTagLink> fetchTagsForEntity(String accountId, T entity);
}
