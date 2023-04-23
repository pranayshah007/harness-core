/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.beans.AuditPreferenceResponse;
import software.wings.beans.Preference;

import org.hibernate.validator.constraints.NotEmpty;

public interface PreferenceService {
  Preference save(String accountId, String userId, Preference preference);
  PageResponse<Preference> list(
      PageRequest<Preference> pageRequest, @NotEmpty String accountId, @NotEmpty String userId);
  Preference get(String accountId, String userId, String preferenceId);
  Preference getPreferenceByName(String accountId, String userId, String name);
  Preference update(String accountId, String userId, String preferenceId, Preference preference);
  void delete(@NotEmpty String accountId, @NotEmpty String userId, @NotEmpty String preferenceId);
  AuditPreferenceResponse listAuditPreferences(String accountId, String userId);
}
