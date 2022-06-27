/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.schema.type.secretManagers;


import lombok.Builder;
import lombok.Value;
import software.wings.graphql.schema.mutation.secretManager.QLEncryptedDataParams;

import java.util.Set;
@Value
@Builder
public class QLCustomSecretManagerConfig implements QLSecretManagerConfig{

    String templateId;
    Set <String> delegateSelectors;
    Set <QLEncryptedDataParams> testVariables;
    Boolean executeOnDelegate;
    Boolean isConnectorTemplatized;
    String host;
    String commandPath;
    String connectorId;
    Boolean isDefault;
}
