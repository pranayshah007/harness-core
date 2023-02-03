/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.executor.config;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.Value;
import org.apache.commons.codec.binary.Base64;

@Value
@Builder
public class Configuration {
    @Builder.Default
    private String taskInputPath = "/etc/config/taskfile";
    // TODO: (not implemented) there should be a limit on threads to limit cpu utilization and avoid memory leak
    // This helps product stability as well, because if a task pod exceeds cpu limit, it will be evicted.
    @Builder.Default
    private int maxThreads=3;
    private String delegateToken;
    private String delegateName;
    /**
     * set to false when testing locally without delegate core
     */
    @Builder.Default
    private boolean shouldSendResponse = false;
    private String delegateHost;
    private int delegatePort;
}
