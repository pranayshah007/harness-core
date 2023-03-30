/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.migration;

import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.dao.CEViewDao;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.CEView.CEViewKeys;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewIdOperator;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.entities.ViewType;
import io.harness.migration.NGMigration;
import io.harness.persistence.HPersistence;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class DefaultCEViewsMigration implements NGMigration {
  private static final String DEFAULT_AWS_PERSPECTIVE_NAME = "Aws";
  private static final String DEFAULT_GCP_PERSPECTIVE_NAME = "Gcp";
  private static final String DEFAULT_AZURE_PERSPECTIVE_NAME = "Azure";
  private static final String DEFAULT_FIELD_ID = "cloudProvider";
  private static final String DEFAULT_FIELD_NAME = "Cloud Provider";

  @Inject private CEViewDao ceViewDao;
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    try {
      log.info("Starting migration (updates) of all default CEViews (AWS, GCP and Azure)");
      final List<CEView> ceViewList = getDefaultCEViews();
      for (final CEView ceView : ceViewList) {
        try {
          migrateDefaultCEView(ceView);
        } catch (final Exception e) {
          log.error("Migration Failed for Account {}, ViewId {}", ceView.getAccountId(), ceView.getUuid(), e);
        }
      }
    } catch (final Exception e) {
      log.error("Failure occurred in DefaultCEViewsMigration", e);
    }
    log.info("DefaultCEViewsMigration has been completed");
  }

  private List<CEView> getDefaultCEViews() {
    final List<String> defaultPerspectiveNames =
        ImmutableList.of(DEFAULT_AWS_PERSPECTIVE_NAME, DEFAULT_GCP_PERSPECTIVE_NAME, DEFAULT_AZURE_PERSPECTIVE_NAME);
    // noinspection deprecation
    return hPersistence.createQuery(CEView.class, excludeValidate)
        .field(CEViewKeys.viewType)
        .equal(ViewType.DEFAULT)
        .field(CEViewKeys.name)
        .in(defaultPerspectiveNames)
        .asList();
  }

  private void migrateDefaultCEView(final CEView ceView) {
    modifyCEView(ceView);
    ceViewDao.update(ceView);
  }

  private void modifyCEView(final CEView ceView) {
    updateDefaultCEViewNameAndRules(ceView);
    setCEViewRequiredFields(ceView);
  }

  private void updateDefaultCEViewNameAndRules(final CEView ceView) {
    ViewIdCondition condition = null;
    if (DEFAULT_AWS_PERSPECTIVE_NAME.equals(ceView.getName())) {
      condition = getDefaultViewIdCondition(ViewFieldIdentifier.AWS);
      ceView.setName(ceView.getName().toUpperCase(Locale.ROOT));
    } else if (DEFAULT_GCP_PERSPECTIVE_NAME.equals(ceView.getName())) {
      condition = getDefaultViewIdCondition(ViewFieldIdentifier.GCP);
      ceView.setName(ceView.getName().toUpperCase(Locale.ROOT));
    } else if (DEFAULT_AZURE_PERSPECTIVE_NAME.equals(ceView.getName())) {
      condition = getDefaultViewIdCondition(ViewFieldIdentifier.AZURE);
    }
    if (Objects.nonNull(condition)) {
      final ViewRule rule = ViewRule.builder().viewConditions(Collections.singletonList(condition)).build();
      ceView.setViewRules(Collections.singletonList(rule));
    }
  }

  private ViewIdCondition getDefaultViewIdCondition(final ViewFieldIdentifier identifier) {
    return ViewIdCondition.builder()
        .viewField(ViewField.builder()
                       .fieldId(DEFAULT_FIELD_ID)
                       .fieldName(DEFAULT_FIELD_NAME)
                       .identifier(ViewFieldIdentifier.COMMON)
                       .identifierName(ViewFieldIdentifier.COMMON.getDisplayName())
                       .build())
        .viewOperator(ViewIdOperator.EQUALS)
        .values(Collections.singletonList(identifier.name().toUpperCase(Locale.ROOT)))
        .build();
  }

  private void setCEViewRequiredFields(final CEView ceView) {
    if (Objects.isNull(ceView.getViewRules())) {
      ceView.setViewRules(Collections.emptyList());
    }
    if (Objects.isNull(ceView.getDataSources())) {
      ceView.setDataSources(Collections.emptyList());
    }
    if (Objects.isNull(ceView.getViewType())) {
      ceView.setViewType(ViewType.CUSTOMER);
    }
  }
}
