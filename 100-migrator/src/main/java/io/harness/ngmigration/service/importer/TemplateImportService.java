package io.harness.ngmigration.service.importer;

import com.google.inject.Inject;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ngmigration.beans.DiscoverEntityInput;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.dto.ServiceFilter;
import io.harness.ngmigration.dto.TemplateFilter;
import io.harness.ngmigration.service.DiscoveryService;
import io.harness.ngmigration.service.servicev2.ServiceV2Factory;
import io.harness.ngmigration.template.NgTemplateService;
import software.wings.beans.Service;
import software.wings.beans.template.Template;
import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.template.TemplateService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static io.harness.beans.SearchFilter.Operator.IN;

public class TemplateImportService implements ImportService {
    @Inject
    DiscoveryService discoveryService;
    @Inject
    TemplateService templateService;

    public DiscoveryResult discover(String authToken, ImportDTO importConnectorDTO) {
        TemplateFilter filter = (TemplateFilter) importConnectorDTO.getFilter();
        String accountId = importConnectorDTO.getAccountIdentifier();
        String appId = filter.getAppId();

        PageRequest<Template> pageRequest = new PageRequest<>();
        pageRequest.addFilter(Template.TemplateKeys.accountId, IN, accountId);
        pageRequest.addFilter(Template.TemplateKeys.appId, IN, appId);
        PageResponse<Template> pageResponse = templateService.list(pageRequest, new ArrayList<>(), accountId, true);
        List<Template> templates = pageResponse.getResponse();
        if (EmptyPredicate.isEmpty(templates)) {
            throw new InvalidRequestException("No services found for given app");
        }
        return discoveryService.discoverMulti(accountId,
                DiscoveryInput.builder()
                        .entities(templates.stream()
                                .filter(NgTemplateService::isMigrationSupported)
                                .map(template
                                        -> DiscoverEntityInput.builder()
                                        .entityId(template.getUuid())
                                        .appId(appId)
                                        .type(NGMigrationEntityType.TEMPLATE)
                                        .build())
                                .collect(Collectors.toList()))
                        .exportImage(false)
                        .build());
    }
}
