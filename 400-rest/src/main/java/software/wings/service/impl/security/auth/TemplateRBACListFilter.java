/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security.auth;

import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.SearchFilter;
import io.harness.data.structure.EmptyPredicate;

import software.wings.beans.template.Template;
import software.wings.beans.template.Template.TemplateKeys;

import dev.morphia.query.Query;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.PL)
public class TemplateRBACListFilter {
  Set<String> appIds;
  Set<String> templateIds;

  public boolean empty() {
    return EmptyPredicate.isEmpty(this.appIds) && EmptyPredicate.isEmpty(templateIds);
  }

  public void addToPageRequest(PageRequest<Template> pageRequest) {
    if (isNotEmpty(this.appIds)) {
      pageRequest.addFilter(
          SearchFilter.builder().fieldName(TemplateKeys.appId).op(IN).fieldValues(this.appIds.toArray()).build());
      if (isNotEmpty(templateIds)) {
        pageRequest.addFilter(
            SearchFilter.builder().fieldName(TemplateKeys.uuid).op(IN).fieldValues(this.templateIds.toArray()).build());
      }
    }
  }

  public void addToQuery(Query<Template> query) {
    if (isNotEmpty(this.appIds)) {
      query.field(TemplateKeys.appId).in(this.appIds);
      if (isNotEmpty(templateIds)) {
        query.field(TemplateKeys.uuid).in(this.templateIds);
      }
    }
  }
}
