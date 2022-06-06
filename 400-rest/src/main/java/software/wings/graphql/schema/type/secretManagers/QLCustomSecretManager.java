/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.schema.type.secretManagers;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import lombok.experimental.SuperBuilder;
import software.wings.graphql.schema.mutation.secretManager.QLEncryptedDataParams;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;
import lombok.Builder;
import lombok.Value;
import software.wings.graphql.schema.type.secrets.QLUsageScope;

@OwnedBy(PL)
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class QLCustomSecretManager extends QLSecretManager {
    private String id;
    String name;
    String templateId;
    Set<String> delegateSelectors;
    Set<QLEncryptedDataParams> testVariables;
    boolean executeOnDelegate;
    boolean isConnectorTemplatized;
    String host;
    String commandPath;
    String connectorId;
    QLUsageScope usageScope;
}
