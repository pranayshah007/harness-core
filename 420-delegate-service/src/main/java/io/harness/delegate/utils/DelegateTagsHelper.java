package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.MongoUtils.setUnset;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroup.DelegateGroupKeys;
import io.harness.delegate.beans.DelegateTags;
import io.harness.persistence.HPersistence;

import software.wings.beans.Event;
import software.wings.service.impl.AuditServiceHelper;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@OwnedBy(DEL)
@Slf4j
public class DelegateTagsHelper {
  @Inject private HPersistence persistence;
  @Inject private AuditServiceHelper auditServiceHelper;

  public void updateYamlTagsForGroupedDelegates(
      String accountId, String delegateGroupId, List<String> yamlTagsFromDelegateParams) {
    Query<DelegateGroup> delegateGroupQuery = persistence.createQuery(DelegateGroup.class)
                                                  .filter(DelegateGroupKeys.accountId, accountId)
                                                  .filter(DelegateGroupKeys.uuid, delegateGroupId);

    UpdateOperations<DelegateGroup> updateOperations = persistence.createUpdateOperations(DelegateGroup.class);
    setUnset(updateOperations, DelegateGroupKeys.tagsFromYaml,
        isNotEmpty(yamlTagsFromDelegateParams) ? new HashSet<>(yamlTagsFromDelegateParams) : null);

    persistence.update(delegateGroupQuery, updateOperations);
  }

  public void updateDelegateYamlTagsAfterReRegistering(
      @NotBlank String accountId, @NotBlank String delegateId, @Nullable List<String> yamlTags) {
    Query<Delegate> delegateQuery = persistence.createQuery(Delegate.class)
                                        .filter(DelegateKeys.accountId, accountId)
                                        .filter(DelegateKeys.uuid, delegateId);

    UpdateOperations<Delegate> updateOperations = persistence.createUpdateOperations(Delegate.class);
    setUnset(updateOperations, DelegateKeys.tagsFromYaml, yamlTags);

    persistence.update(delegateQuery, updateOperations);
  }

  public Delegate updateTagsFromUIForGroupedCGDelegates(Delegate delegate, @NotNull DelegateTags delegateTags) {
    Query<DelegateGroup> delegateGroupQuery = persistence.createQuery(DelegateGroup.class)
                                                  .filter(DelegateGroupKeys.accountId, delegate.getAccountId())
                                                  .filter(DelegateGroupKeys.name, delegate.getDelegateGroupName())
                                                  .filter(DelegateGroupKeys.ng, false);

    DelegateGroup delegateGroup = delegateGroupQuery.get();

    Set<String> tagsFromYaml = Optional.ofNullable(delegateGroup.getTagsFromYaml()).orElseGet(Collections::emptySet);

    List<String> tagsFromUI = delegateTags.getTags();

    // keep those tags which are in UI List
    Set<String> updatedSetOfTagsFromYaml =
        tagsFromYaml.stream().filter(tag -> tagsFromUI.contains(tag)).collect(Collectors.toSet());

    // remove yaml tags from UI List
    Set<String> updatedSetOfTagsFromUI =
        tagsFromUI.stream().filter(tag -> !tagsFromYaml.contains(tag)).collect(Collectors.toSet());

    UpdateOperations<DelegateGroup> updateOperations = persistence.createUpdateOperations(DelegateGroup.class);
    setUnset(updateOperations, DelegateGroupKeys.tags, updatedSetOfTagsFromUI);
    setUnset(updateOperations, DelegateGroupKeys.tagsFromYaml, updatedSetOfTagsFromYaml);

    log.info("Updating delegate tags for grouped cg delegate : Delegate:{} DelegateGroupName:{} tags:{}",
        delegate.getUuid(), delegate.getDelegateGroupName(), delegateTags.getTags());

    DelegateGroup updatedDelegateGroup =
        persistence.findAndModify(delegateGroupQuery, updateOperations, HPersistence.returnNewOptions);

    auditServiceHelper.reportForAuditingUsingAccountId(
        delegateGroup.getAccountId(), delegateGroup, updatedDelegateGroup, Event.Type.UPDATE_TAG);
    log.info("Auditing updation of Tags for delegate={} in account={}", delegate.getUuid(), delegate.getAccountId());

    // not great, but no other options
    delegate.setTags(delegateTags.getTags());
    return delegate;
  }

  public Set<String> getUnionOfDelegateGroupSelectors(final DelegateGroup delegateGroup) {
    Set<String> delegateGroupSelectors = new HashSet<>();
    if (delegateGroup == null) {
      return delegateGroupSelectors;
    }
    if (isNotEmpty(delegateGroup.getTagsFromYaml())) {
      delegateGroupSelectors.addAll(delegateGroup.getTagsFromYaml());
    }
    if (isNotEmpty(delegateGroup.getTags())) {
      delegateGroupSelectors.addAll(delegateGroup.getTags());
    }
    return delegateGroupSelectors;
  }
}
