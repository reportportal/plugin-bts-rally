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

import static java.util.Optional.ofNullable;

import com.epam.reportportal.api.model.PluginCommandRQ;
import com.epam.reportportal.base.infrastructure.model.externalsystem.Ticket;
import com.epam.reportportal.base.infrastructure.persistence.dao.IntegrationRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectUserRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.TicketRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationUserRepository;
import com.epam.reportportal.base.infrastructure.persistence.entity.integration.Integration;
import com.epam.reportportal.base.infrastructure.persistence.entity.organization.OrganizationRole;
import com.epam.reportportal.base.infrastructure.persistence.entity.project.ProjectRole;
import com.epam.reportportal.base.infrastructure.persistence.entity.user.UserRole;
import com.epam.reportportal.base.infrastructure.rules.commons.validation.Suppliers;
import com.epam.reportportal.base.infrastructure.rules.exception.ErrorType;
import com.epam.reportportal.base.infrastructure.rules.exception.ReportPortalException;
import com.epam.reportportal.extension.bugtracking.rally.Defect;
import com.epam.reportportal.extension.bugtracking.rally.RallyConstants;
import com.epam.reportportal.extension.bugtracking.rally.client.RallyClientProvider;
import com.epam.reportportal.extension.command.AbstractExtensionCommand;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.QueryRequest;
import com.rallydev.rest.response.QueryResponse;
import com.rallydev.rest.util.QueryFilter;
import com.rallydev.rest.util.Ref;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetIssueCommand extends AbstractExtensionCommand<Ticket> {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetIssueCommand.class);
  private static final String TICKET_ID = "ticketId";
  private static final String PROJECT_ID = "projectId";

  private final RallyClientProvider clientProvider;
  private final TicketRepository ticketRepository;
  private final IntegrationRepository integrationRepository;
  private final Gson gson = new Gson();

  public GetIssueCommand(RallyClientProvider clientProvider, TicketRepository ticketRepository,
      IntegrationRepository integrationRepository, ProjectRepository projectRepository,
      OrganizationUserRepository organizationUserRepository,
      OrganizationRepository organizationRepository, ProjectUserRepository projectUserRepository) {
    super(projectRepository, organizationUserRepository, organizationRepository,
        projectUserRepository);
    this.clientProvider = clientProvider;
    this.ticketRepository = ticketRepository;
    this.integrationRepository = integrationRepository;
    this.minProjectRole = ProjectRole.EDITOR;
    this.minOrgRole = OrganizationRole.MANAGER;
    this.minUserRole = UserRole.ADMINISTRATOR;
  }

  @Override
  public String getName() {
    return "getIssue";
  }

  @Override
  public Ticket executeCommand(PluginCommandRQ pluginCommandRq) {
    Map<String, Object> params = pluginCommandRq.getArguments();

    var ticket = ticketRepository.findByTicketId(
        (String) ofNullable(params.get(TICKET_ID)).orElseThrow(
            () -> new ReportPortalException(ErrorType.BAD_REQUEST_ERROR,
                TICKET_ID + " must be provided")
        )).orElseThrow(() -> new ReportPortalException(ErrorType.BAD_REQUEST_ERROR,
        "Ticket not found with id " + params.get(TICKET_ID)));

    final Long projectId = (Long) ofNullable(params.get(PROJECT_ID)).orElseThrow(
        () -> new ReportPortalException(ErrorType.BAD_REQUEST_ERROR,
            PROJECT_ID + " must be provided"));

    String btsUrl = (String) params.get("url");
    String btsProject = (String) params.get("project");

    Integration integration =
        integrationRepository.findProjectBtsByUrlAndLinkedProject(btsUrl, btsProject, projectId)
            .orElseGet(
                () -> integrationRepository.findGlobalBtsByUrlAndLinkedProject(btsUrl, btsProject)
                    .orElseThrow(() -> new ReportPortalException(ErrorType.BAD_REQUEST_ERROR,
                        "Integration with provided url and project isn't found")));

    try (RallyRestApi restApi = clientProvider.provide(integration.getParams())) {
      return findDefect(restApi, ticket.getTicketId())
          .map(defect -> toTicket(defect, integration))
          .orElseThrow(() -> new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
              Suppliers.formattedSupplier("Ticket with id {} not found",
                  ticket.getTicketId()).get()));
    } catch (ReportPortalException rpe) {
      throw rpe;
    } catch (Exception ex) {
      LOGGER.error("Unable to load ticket: {}", ex.getMessage(), ex);
      throw new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
          "Unable to load ticket");
    }
  }

  private Optional<Defect> findDefect(RallyRestApi restApi, String id) throws IOException {
    QueryRequest rq = new QueryRequest(RallyConstants.DEFECT);
    rq.setQueryFilter(new QueryFilter(RallyConstants.FORMATTED_ID, "=", id));
    QueryResponse rs = restApi.query(rq);
    if (!rs.wasSuccessful()) {
      return Optional.empty();
    }
    List<Defect> defects =
        gson.fromJson(rs.getResults(), new TypeToken<List<Defect>>() {}.getType());
    return defects.stream().findAny();
  }

  private Ticket toTicket(Defect defect, Integration integration) {
    Ticket ticket = new Ticket();
    String baseUrl =
        StringUtils.removeEnd((String) integration.getParams().getParams().get("url"), "/");
    String link = baseUrl + "/#/" + Ref.getOidFromRef(defect.getProject().getRef())
        + "/detail/defect/" + defect.getObjectId();
    ticket.setId(defect.getFormattedId());
    ticket.setSummary(defect.getName());
    ticket.setTicketUrl(link);
    ticket.setStatus(defect.getState());
    return ticket;
  }
}
