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

package com.epam.reportportal.extension.bugtracking.rally.utils;

import static com.epam.reportportal.base.infrastructure.rules.exception.ErrorType.UNABLE_INTERACT_WITH_INTEGRATION;

import com.epam.reportportal.base.infrastructure.model.externalsystem.Ticket;
import com.epam.reportportal.base.infrastructure.persistence.entity.integration.Integration;
import com.epam.reportportal.base.infrastructure.rules.exception.ReportPortalException;
import com.epam.reportportal.extension.bugtracking.BtsConstants;
import com.epam.reportportal.extension.bugtracking.rally.model.Defect;
import com.rallydev.rest.util.Ref;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;

@Slf4j
public final class RallyTicketConverter {

  private RallyTicketConverter() {
  }

  public static Ticket toTicket(Defect defect, Integration integration) {
    Ticket ticket = new Ticket();
    String baseUrl = Strings.CS.removeEnd(BtsConstants.URL.getParam(integration.getParams(), String.class).get(), "/");
    String link = baseUrl + "/#/"
        + Ref.getOidFromRef(defect.getProject().getRef()) + "/detail/defect/"
        + defect.getObjectId();
    ticket.setId(defect.getFormattedId());
    ticket.setSummary(defect.getName());
    ticket.setTicketUrl(link);
    ticket.setStatus(defect.getState());
    log.debug("Rally ticket: {}", ticket);
    return ticket;
  }
}
