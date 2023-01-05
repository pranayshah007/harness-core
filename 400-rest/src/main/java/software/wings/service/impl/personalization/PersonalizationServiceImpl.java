/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.personalization;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HPersistence.returnNewOptions;
import static io.harness.persistence.HPersistence.upsertReturnNewOptions;

import static java.lang.String.format;
import static java.util.Arrays.asList;

import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import software.wings.beans.peronalization.Personalization;
import software.wings.beans.peronalization.Personalization.PersonalizationKeys;
import software.wings.beans.template.Template;
import software.wings.service.intfc.personalization.PersonalizationService;
import software.wings.service.intfc.template.TemplateService;
import software.wings.sm.StateType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

@Singleton
public class PersonalizationServiceImpl implements PersonalizationService {
  private static final int RECENT_OPTIMIZATION_AMOUNT = 50;

  @Inject private HPersistence persistence;
  @Inject private ExecutorService executorService;
  @Inject private TemplateService templateService;

  public static void normalizeRecent(LinkedList<String> recent) {
    if (recent == null) {
      return;
    }

    Set<String> seen = new HashSet<>();

    final Iterator<String> stringIterator = recent.descendingIterator();
    while (stringIterator.hasNext()) {
      final String next = stringIterator.next();
      if (seen.size() >= MAX_ALLOWED_RECENT || seen.contains(next)) {
        stringIterator.remove();
        continue;
      }
      seen.add(next);
    }
  }

  private Query<Personalization> prepareQuery(String accountId, String userId) {
    return persistence.createQuery(Personalization.class)
        .filter(PersonalizationKeys.accountId, accountId)
        .filter(PersonalizationKeys.userId, userId);
  }

  @Override
  public Personalization fetch(String accountId, String userId, List<String> objects) {
    final Query<Personalization> query = prepareQuery(accountId, userId);
    if (isNotEmpty(objects)) {
      query.project(PersonalizationKeys.accountId, true);
      query.project(PersonalizationKeys.userId, true);

      for (String object : objects) {
        query.project(object, true);
      }
    }

    final Personalization personalization = query.get();
    if (personalization != null && personalization.getSteps() != null) {
      normalizeRecent(personalization.getSteps().getRecent());
    }

    return personalization;
  }

  @Override
  public Personalization addFavoriteStep(StateType step, String accountId, String userId) {
    final Query<Personalization> query = prepareQuery(accountId, userId);

    final UpdateOperations<Personalization> updateOperations =
        persistence.createUpdateOperations(Personalization.class)
            .addToSet(PersonalizationKeys.steps_favorites, step.name());

    return persistence.upsert(query, updateOperations, upsertReturnNewOptions);
  }

  @Override
  public Personalization removeFavoriteStep(StateType step, String accountId, String userId) {
    final Query<Personalization> query = prepareQuery(accountId, userId);

    final UpdateOperations<Personalization> updateOperations =
        persistence.createUpdateOperations(Personalization.class)
            .removeAll(PersonalizationKeys.steps_favorites, step.name());

    return persistence.upsert(query, updateOperations, upsertReturnNewOptions);
  }

  @Override
  public Personalization addRecentStep(StateType step, String accountId, String userId) {
    final Query<Personalization> query = prepareQuery(accountId, userId);

    final UpdateOperations<Personalization> updateOperations =
        persistence.createUpdateOperations(Personalization.class).push(PersonalizationKeys.steps_recent, step.name());

    final Personalization Personalization = persistence.upsert(query, updateOperations, upsertReturnNewOptions);

    if (Personalization.getSteps().getRecent().size() > RECENT_OPTIMIZATION_AMOUNT) {
      normalizeRecent(Personalization.getSteps().getRecent());
      executorService.submit(() -> {
        // Note that this create a race between the obtaining the values and updating with the optimized version.
        // The impact for the customer is considered ignorable.
        final UpdateOperations<Personalization> updateRecentOperations =
            persistence.createUpdateOperations(Personalization.class)
                .set(PersonalizationKeys.steps_recent, Personalization.getSteps().getRecent());

        persistence.findAndModify(query, updateRecentOperations, returnNewOptions);
      });
    }

    return Personalization;
  }

  @Override
  public Personalization addFavoriteTemplate(String templateId, String accountId, String userId) {
    Template template = templateService.get(templateId);
    if (template == null) {
      throw new InvalidRequestException(format("Template with id [%s] not found", templateId));
    }
    final Query<Personalization> query = prepareQuery(accountId, userId);

    final UpdateOperations<Personalization> updateOperations =
        persistence.createUpdateOperations(Personalization.class)
            .addToSet(PersonalizationKeys.templates_favorites, templateId);

    return persistence.upsert(query, updateOperations, upsertReturnNewOptions);
  }

  @Override
  public Personalization removeFavoriteTemplate(String templateId, String accountId, String userId) {
    Template template = templateService.get(templateId);
    if (template == null) {
      throw new InvalidRequestException(format("Template with id [%s] not found", templateId));
    }
    final Query<Personalization> query = prepareQuery(accountId, userId);

    final UpdateOperations<Personalization> updateOperations =
        persistence.createUpdateOperations(Personalization.class)
            .removeAll(PersonalizationKeys.templates_favorites, templateId);

    return persistence.upsert(query, updateOperations, upsertReturnNewOptions);
  }

  @Override
  public Set<String> fetchFavoriteTemplates(String accountId, String userId) {
    Set<String> favorites = new HashSet<>();
    final Personalization personalization = fetch(accountId, userId, asList(PersonalizationKeys.templates));
    if (personalization != null && personalization.getTemplates() != null) {
      favorites = personalization.getTemplates().getFavorites();
    }
    return favorites;
  }
}
