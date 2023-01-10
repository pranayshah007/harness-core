/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.transformer;

import io.harness.cvng.downtime.beans.DowntimeSpec;
import io.harness.cvng.downtime.entities.Downtime.DowntimeDetails;

public interface DowntimeSpecDetailsTransformer<E extends DowntimeDetails, T extends DowntimeSpec> {
  E getDowntimeDetails(T spec);
  T getDowntimeSpec(E entity);
}
