/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.docker;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.expression.Expression;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DockerServiceDependency {
    @NotNull String identifier;
    String name;
    @NotNull String image;
    @NotNull String logKey;
    ConnectorDetails imageConnector;
    Map<String, String> portBindings;
    String pullPolicy; // always, if-not-exists or never
    String runAsUser;
    boolean privileged;
    @Expression(ALLOW_SECRETS) private Map<String, String> envVariables;
    @Expression(ALLOW_SECRETS) private List<String> secrets;
}
