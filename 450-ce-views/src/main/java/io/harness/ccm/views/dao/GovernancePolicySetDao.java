/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.dao;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.ccm.views.entities.CEViewFolder;
import io.harness.ccm.views.entities.GovernancePolicySet;
import io.harness.persistence.HPersistence;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class GovernancePolicySetDao {
    @Inject
    private HPersistence hPersistence;

    public GovernancePolicySet save(GovernancePolicySet governancePolicySet) {
        String id = hPersistence.save(governancePolicySet);
        return hPersistence.createQuery(GovernancePolicySet.class).field(GovernancePolicySet.GovernancePolicySetKeys.uuid).equal(id).get();
    }
}
