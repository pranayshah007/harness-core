/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.template;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static software.wings.graphql.datafetcher.connector.utils.ConnectorConstants.SSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.datafetcher.template.TemplateListDataFetcher;
import io.harness.app.schema.mutation.template.input.QLTemplateListInput;
import io.harness.app.schema.mutation.template.input.QLTemplateListInput.QLTemplateListInputBuilder;
import io.harness.app.schema.mutation.template.payload.QLTemplateListPayload;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.template.Template;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.service.intfc.template.TemplateService;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PL)
public class TemplateListDataFetcherTest extends AbstractDataFetcherTestBase {
  @Inject @InjectMocks TemplateListDataFetcher templateListDataFetcher;
  @Mock TemplateService templateService;

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testTemplateList() {
    String accountId = ACCOUNT1_ID;
    QLTemplateListInputBuilder qlTemplateListInputBuilder = QLTemplateListInput.builder().accountId(accountId);
    Template template = new Template();
    template.setAccountId(accountId);
    template.setName("templateName");
    template.setUuid(ACCOUNT2_ID);
    template.setType(SSH);
    PageResponse<Template> pageResponse = new PageResponse();
    pageResponse.add(template);
    when(templateListDataFetcher.templateService.list(any(), any(), any(), anyBoolean())).thenReturn(pageResponse);
    QLTemplateListPayload qlTemplateListPayload = templateListDataFetcher.mutateAndFetch(
        qlTemplateListInputBuilder.build(), MutationContext.builder().accountId(accountId).build());
    assertThat(qlTemplateListPayload.getNodes().size()).isEqualTo(1);
    List<Template> nodes = qlTemplateListPayload.getNodes();
    Template fetchedTemplate = nodes.get(0);
    assertThat(fetchedTemplate.getName()).isEqualTo(template.getName());
    assertThat(fetchedTemplate.getAccountId()).isEqualTo(template.getAccountId());
    assertThat(fetchedTemplate.getUuid()).isEqualTo(template.getUuid());
    assertThat(fetchedTemplate.getType()).isEqualTo(template.getType());
  }
}
