/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventDetailsResponse;
import io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventsType;

import java.util.List;

public interface SecondaryEventDetailsService {
  SecondaryEventDetailsResponse getInstanceByUuids(List<String> uuids, SecondaryEventsType eventType);
}
