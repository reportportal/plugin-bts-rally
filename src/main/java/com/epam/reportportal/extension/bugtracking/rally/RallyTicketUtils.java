package com.epam.reportportal.extension.bugtracking.rally;

import com.epam.ta.reportportal.entity.integration.Integration;
import com.epam.ta.reportportal.ws.model.externalsystem.Ticket;
import com.rallydev.rest.util.Ref;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public class RallyTicketUtils {

	public static Ticket toTicket(Defect defect, Integration externalSystem) {
		Ticket ticket = new Ticket();
		String link = RallyProps.URL.getParam(externalSystem.getParams()) + "/#/" + Ref.getOidFromRef(defect.getProject().getRef())
				+ "/detail/defect/" + defect.getObjectId();
		ticket.setId(defect.getFormattedId());
		ticket.setSummary(defect.getName());
		ticket.setTicketUrl(link);
		ticket.setStatus(defect.getState());
		return ticket;
	}

}
