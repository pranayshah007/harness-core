/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.ccm.msp.entities.MarginDetails;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.entities.BusinessMappingHistory;
import io.harness.ccm.views.entities.CEReportSchedule;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.CEViewFolder;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.entities.RuleEnforcement;
import io.harness.ccm.views.entities.RuleExecution;
import io.harness.ccm.views.entities.RuleRecommendation;
import io.harness.ccm.views.entities.RuleSet;
import io.harness.ccm.views.entities.ViewCustomField;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;

import java.util.Set;

public class ViewsMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(CEView.class);
    set.add(ViewCustomField.class);
    set.add(CEReportSchedule.class);
    set.add(BusinessMapping.class);
    set.add(BusinessMappingHistory.class);
    set.add(CEViewFolder.class);

    // governance
    set.add(RuleSet.class);
    set.add(Rule.class);
    set.add(RuleEnforcement.class);
    set.add(RuleExecution.class);
    set.add(RuleRecommendation.class);

    // msp
    set.add(MarginDetails.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    // Nothing to register
  }
}
