/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewPreferences;
import io.harness.ccm.views.graphql.QLCEViewPreferenceAggregation;

import java.util.List;
import java.util.Set;

@OwnedBy(CE)
public interface CEViewPreferenceService {
  ViewPreferences getCEViewPreferences(CEView ceView, Set<String> viewPreferencesFieldsToUpdateWithDefaultSettings);
  List<QLCEViewPreferenceAggregation> getViewPreferenceAggregations(CEView ceView, ViewPreferences viewPreferences);
}
