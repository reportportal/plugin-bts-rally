/*
 * Copyright 2018 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.extension.bugtracking.rally.command;

import com.epam.reportportal.api.model.PluginCommandRQ;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectUserRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationUserRepository;
import com.epam.reportportal.base.infrastructure.persistence.entity.integration.Integration;
import com.epam.reportportal.base.infrastructure.persistence.entity.organization.OrganizationRole;
import com.epam.reportportal.base.infrastructure.persistence.entity.project.ProjectRole;
import com.epam.reportportal.base.infrastructure.persistence.entity.user.UserRole;
import com.epam.reportportal.base.infrastructure.rules.exception.ReportPortalException;
import com.epam.reportportal.extension.bugtracking.BtsConstants;
import com.epam.reportportal.extension.bugtracking.rally.RallyConstants;
import com.epam.reportportal.extension.bugtracking.rally.client.RallyClientProvider;
import com.epam.reportportal.extension.bugtracking.rally.validator.IntegrationValidator;
import com.epam.reportportal.extension.command.AbstractExtensionCommand;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.QueryRequest;
import com.rallydev.rest.util.QueryFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.epam.reportportal.base.infrastructure.rules.exception.ErrorType.UNABLE_INTERACT_WITH_INTEGRATION;

public class TestConnectionCommand extends AbstractExtensionCommand<Boolean> {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestConnectionCommand.class);

  private final RallyClientProvider clientProvider;

  public TestConnectionCommand(RallyClientProvider clientProvider,
      ProjectRepository projectRepository, OrganizationUserRepository organizationUserRepository,
      OrganizationRepository organizationRepository, ProjectUserRepository projectUserRepository) {
    super(projectRepository, organizationUserRepository, organizationRepository,
        projectUserRepository);
    this.clientProvider = clientProvider;
    this.minProjectRole = ProjectRole.EDITOR;
    this.minOrgRole = OrganizationRole.MANAGER;
    this.minUserRole = UserRole.USER;
  }

  @Override
  public String getName() {
    return "testConnection";
  }

  @Override
  protected Boolean invokeCommand(Integration integration, PluginCommandRQ pluginCommandRq) {
    String project = BtsConstants.PROJECT.getParam(integration.getParams(), String.class)
        .orElseThrow(() -> new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION,
            "Rally Project value cannot be NULL"
        ));
    IntegrationValidator.validateThirdPartyUrl(integration);

    try (RallyRestApi restApi = clientProvider.provide(integration.getParams())) {
      QueryRequest rq = new QueryRequest(RallyConstants.PROJECT);
      rq.setQueryFilter(new QueryFilter(RallyConstants.OBJECT_ID, "=", project));
      return restApi.query(rq).getTotalResultCount() > 0;
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
      return false;
    }
  }
}
