package com.epam.reportportal.extension.bugtracking.rally;

import com.epam.ta.reportportal.ws.model.externalsystem.Ticket;
import com.google.gson.JsonObject;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public class RallyTicketUtils {

	public static Ticket toTicket(JsonObject jsonObject) {
		Ticket ticket = new Ticket();
		ticket.setId(jsonObject.get("ObjectID").getAsString());
		ticket.setStatus(jsonObject.get("TaskStatus").getAsString());
		//		ticket.getSummary()
		ticket.setTicketUrl(jsonObject.get("_ref").getAsString());
		return ticket;
	}

}
