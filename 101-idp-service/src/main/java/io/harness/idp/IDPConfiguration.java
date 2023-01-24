/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.MongoConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Getter
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class IDPConfiguration extends Configuration {
    private IDPConfiguration cg;
    @JsonProperty("mongo") private MongoConfig mongoConfig;

    public List<String> getDbAliases() {
        List<String> dbAliases = new ArrayList<>();
        if (mongoConfig != null) {
            dbAliases.add(mongoConfig.getAliasDBName());
        }
        return dbAliases;
    }
}
