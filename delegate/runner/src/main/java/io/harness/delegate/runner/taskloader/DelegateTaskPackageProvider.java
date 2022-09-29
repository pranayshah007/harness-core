/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.runner.taskloader;

import com.google.inject.Inject;
import com.google.inject.Provider;
import io.harness.delegate.beans.DelegateTaskPackage;


public class DelegateTaskPackageProvider implements Provider<DelegateTaskPackage> {
    @Inject TaskPackageReader taskPackageReader;
    @Override
    public DelegateTaskPackage get() {
        return taskPackageReader.readTask();
    }
}
