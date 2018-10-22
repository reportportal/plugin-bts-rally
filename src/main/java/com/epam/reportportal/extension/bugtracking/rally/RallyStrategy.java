package com.epam.reportportal.extension.bugtracking.rally;

import com.epam.reportportal.extension.bugtracking.BtsExtension;
import com.epam.reportportal.extension.bugtracking.InternalTicket;
import com.epam.ta.reportportal.binary.DataStoreService;
import com.epam.ta.reportportal.dao.LogRepository;
import com.epam.ta.reportportal.entity.integration.Integration;
import com.epam.ta.reportportal.entity.integration.IntegrationParams;
import com.epam.ta.reportportal.entity.log.Log;
import com.epam.ta.reportportal.entity.project.Project;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.ws.model.externalsystem.AllowedValue;
import com.epam.ta.reportportal.ws.model.externalsystem.PostFormField;
import com.epam.ta.reportportal.ws.model.externalsystem.PostTicketRQ;
import com.epam.ta.reportportal.ws.model.externalsystem.Ticket;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.GetRequest;
import com.rallydev.rest.request.QueryRequest;
import com.rallydev.rest.response.GetResponse;
import com.rallydev.rest.response.QueryResponse;
import com.rallydev.rest.util.QueryFilter;
import org.jasypt.util.text.BasicTextEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import static com.epam.reportportal.extension.bugtracking.rally.RallyConstants.DEFECT;
import static com.epam.reportportal.extension.bugtracking.rally.RallyConstants.FORMATTED_ID;
import static com.epam.ta.reportportal.commons.Predicates.isPresent;
import static com.epam.ta.reportportal.commons.validation.BusinessRule.expect;
import static com.epam.ta.reportportal.ws.model.ErrorType.UNABLE_INTERACT_WITH_EXTRERNAL_SYSTEM;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public class RallyStrategy implements BtsExtension {

	private static final String BUG = "Bug";
	private static final Logger LOGGER = LoggerFactory.getLogger(RallyStrategy.class);

	private Gson gson;

	@Autowired
	private DataStoreService dataStorage;

	@Autowired
	private LogRepository logRepository;

/*	@Autowired
	private JIRATicketDescriptionService descriptionService;*/

	@Autowired
	private BasicTextEncryptor simpleEncryptor;

	public RallyStrategy() {
		this.gson = new Gson();
	}

	@Override
	public boolean connectionTest(Integration system) {
		String url = RallyProps.URL.getParam(system.getParams()).get();
		String apiKey = RallyProps.OAUTH_ACCESS_KEY.getParam(system.getParams()).get();
		String project = RallyProps.PROJECT.getParam(system.getParams()).get();

		validateExternalSystemDetails(system);

		try (RallyRestApi restApi = new RallyRestApi(new URI(url), apiKey)) {
			return restApi.get(new GetRequest("/project/" + project)).getObject() != null;
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
			return false;
		}
	}

	@Override
	//    @Cacheable(value = CacheConfiguration.EXTERNAL_SYSTEM_TICKET_CACHE, key = "#system.url + #system.project + #id")
	public Optional<Ticket> getTicket(final String id, Integration system) {
		Ticket ticket;
		try (RallyRestApi restApi = getClient(system.getParams())) {
			Optional<Defect> optionalDefect = findDefect(restApi, id);
			if (!optionalDefect.isPresent()) {
				return Optional.empty();
			}
			ticket = RallyTicketUtils.toTicket(optionalDefect.get(), system);
		} catch (Exception ex) {
			LOGGER.error("Unable load ticket :" + ex.getMessage(), ex);
			throw new ReportPortalException("Unable load ticket :" + ex.getMessage(), ex);
		}
		return Optional.of(ticket);
	}

	private Optional<Defect> findDefect(RallyRestApi restApi, String id) throws IOException {
		QueryRequest rq = new QueryRequest(DEFECT);
		rq.setQueryFilter(new QueryFilter(FORMATTED_ID, "=", id));
		QueryResponse rs = restApi.query(rq);
		if (!rs.wasSuccessful()) {
			return Optional.empty();
		}
		List<Defect> defects = gson.fromJson(rs.getResults(), new TypeToken<List<Defect>>() {
		}.getType());
		return defects.stream().findAny();

	}

	@Override
	public Ticket submitTicket(final PostTicketRQ ticketRQ, Integration details) {
		try (RallyRestApi restApi = getClient(details.getParams())) {
			List<InternalTicket.LogEntry> itemLogs = loadTestItemLogs(ticketRQ);
			Defect newDefect = postDefect(restApi, ticketRQ, externalSystem);
			String description = newDefect.getDescription();
			Map<String, String> attachments = new HashMap<>();
			for (LogEntry logEntry : itemLogs) {
				if (logEntry.getBinaryDataId() != null) {
					attachments.put(logEntry.getBinaryDataId(),
							String.valueOf(postImage(newDefect.getRef(), logEntry, restApi).getObjectId())
					);
				}
			}
			for (Map.Entry<String, String> binaryDataEntry : attachments.entrySet()) {
				description = description.replace(binaryDataEntry.getKey(),
						"/slm/attachment/" + binaryDataEntry.getValue() + "/" + binaryDataEntry.getKey()
				);
			}
			updateDescription(description, newDefect, restApi);
			return buildTicket(newDefect, externalSystem);
		} catch (Exception e) {
			LOGGER.error("Unable to submit ticket: " + e.getMessage(), e);
			throw new ReportPortalException("Unable to submit ticket: " + e.getMessage(), e);
		}
	}

	private List<InternalTicket.LogEntry> loadTestItemLogs(PostTicketRQ ticketRQ) {
		List<Log> logs = ticketRQ.getBackLinks().size() == 1 ?
				logRepository.findByTestItemId(ticketRQ.getTestItemId(),
						ticketRQ.getNumberOfLogs() == 0 ? 50 : ticketRQ.getNumberOfLogs()
				) :
				new ArrayList<>();
		return logs.stream()
				.map(log -> new InternalTicket.LogEntry(log, dataStorage.load(log.getAttachment()), ticketRQ.getIsIncludeLogs()))
				.collect(Collectors.toList());
	}

	/**
	 * Get jira's {@link Project} object.
	 *
	 * @param
	 * @param details System details
	 * @return Jira Project
	 */
	// paced in separate method because result object should be cached
	// TODO consider avoiding this method
	//    @Cacheable(value = CacheConfiguration.JIRA_PROJECT_CACHE, key = "#details")
	private JsonObject getProject(RallyRestApi rallyRestApi, Integration details) throws IOException {
		GetRequest rq = new GetRequest("/project/" + RallyProps.PROJECT.getParam(details.getParams()));
		return rallyRestApi.get(rq).getObject();
	}

	private JsonObject findIssue(String id, RallyRestApi rallyRestApi) throws IOException {
		GetRequest request = new GetRequest("/defect/" + id);
		GetResponse getResponse = rallyRestApi.get(request);
		return getResponse.getObject();
	}

	/**
	 * Parse ticket description and find binary data
	 *
	 * @param
	 * @return Parsed parameters
	 */
	private Map<String, String> findBinaryData() {
		/*Map<String, String> binary = new HashMap<>();
		String description = issueInput.getField(IssueFieldId.DESCRIPTION_FIELD.id).getValue().toString();
		if (null != description) {
			// !54086a2c3c0c7d4446beb3e6.jpg| or [^54086a2c3c0c7d4446beb3e6.xml]
			String regex = "(!|\\[\\^).{24}.{0,5}(\\||\\])";
			Matcher matcher = Pattern.compile(regex).matcher(description);
			while (matcher.find()) {
				String rawValue = description.subSequence(matcher.start(), matcher.end()).toString();
				String binaryDataName = rawValue.replace("!", "").replace("[", "").replace("]", "").replace("^", "").replace("|", "");
				String binaryDataId = binaryDataName.split("\\.")[0];
				binary.put(binaryDataId, binaryDataName);
			}
		}
		return binary;*/
		return null;
	}

	@Override
	public List<PostFormField> getTicketFields(final String ticketType, Integration details) {
		/*List<PostFormField> result = new ArrayList<>();
		try (RallyRestApi client = getClient(details.getParams())) {
			Project jiraProject = getProject(client, details);
			Optional<IssueType> issueType = StreamSupport.stream(jiraProject.getIssueTypes().spliterator(), false)
					.filter(input -> ticketType.equalsIgnoreCase(input.getName()))
					.findFirst();

			BusinessRule.expect(issueType, Preconditions.IS_PRESENT)
					.verify(ErrorType.UNABLE_INTERACT_WITH_EXTRERNAL_SYSTEM, "Ticket type '" + ticketType + "' not found");

			GetCreateIssueMetadataOptions options = new GetCreateIssueMetadataOptionsBuilder().withExpandedIssueTypesFields()
					.withProjectKeys(jiraProject.getKey())
					.build();
			Iterable<CimProject> projects = client.getIssueClient().getCreateIssueMetadata(options).claim();
			CimProject project = projects.iterator().next();
			CimIssueType cimIssueType = EntityHelper.findEntityById(project.getIssueTypes(), issueType.get().getId());
			for (String key : cimIssueType.getFields().keySet()) {
				List<String> defValue = null;
				CimFieldInfo issueField = cimIssueType.getFields().get(key);
				// Field ID for next JIRA POST ticket requests
				String fieldID = issueField.getId();
				String fieldType = issueField.getSchema().getType();
				List<AllowedValue> allowed = new ArrayList<>();

				// Provide values for custom fields with predefined options
				if (issueField.getAllowedValues() != null) {
					for (Object o : issueField.getAllowedValues()) {
						if (o instanceof CustomFieldOption) {
							CustomFieldOption customField = (CustomFieldOption) o;
							allowed.add(new AllowedValue(String.valueOf(customField.getId()), (customField).getValue()));
						}
					}
				}

				// Field NAME for user friendly UI output (for UI only)
				String fieldName = issueField.getName();

				if (fieldID.equalsIgnoreCase(IssueFieldId.COMPONENTS_FIELD.id)) {
					for (BasicComponent component : jiraProject.getComponents()) {
						allowed.add(new AllowedValue(String.valueOf(component.getId()), component.getName()));
					}
				}
				if (fieldID.equalsIgnoreCase(IssueFieldId.FIX_VERSIONS_FIELD.id)) {
					for (Version version : jiraProject.getVersions()) {
						allowed.add(new AllowedValue(String.valueOf(version.getId()), version.getName()));
					}
				}
				if (fieldID.equalsIgnoreCase(IssueFieldId.AFFECTS_VERSIONS_FIELD.id)) {
					for (Version version : jiraProject.getVersions()) {
						allowed.add(new AllowedValue(String.valueOf(version.getId()), version.getName()));
					}
				}
				if (fieldID.equalsIgnoreCase(IssueFieldId.PRIORITY_FIELD.id)) {
					if (null != cimIssueType.getField(IssueFieldId.PRIORITY_FIELD)) {
						Iterable<Object> allowedValuesForPriority = cimIssueType.getField(IssueFieldId.PRIORITY_FIELD).getAllowedValues();
						for (Object singlePriority : allowedValuesForPriority) {
							BasicPriority priority = (BasicPriority) singlePriority;
							allowed.add(new AllowedValue(String.valueOf(priority.getId()), priority.getName()));
						}
					}
				}
				if (fieldID.equalsIgnoreCase(IssueFieldId.ISSUE_TYPE_FIELD.id)) {
					defValue = Collections.singletonList(ticketType);
				}
				if (fieldID.equalsIgnoreCase(IssueFieldId.ASSIGNEE_FIELD.id)) {
					allowed = getJiraProjectAssignee(jiraProject);
				}

				//@formatter:off
                // Skip project field as external from list
                // Skip attachment cause we are not providing this functionality now
                // Skip timetracking field cause complexity. There are two fields with Original Estimation and Remaining Estimation.
                // Skip Story Link as greenhopper plugin field.
                // Skip Sprint field as complex one.
                //@formatter:on
				if ("project".equalsIgnoreCase(fieldID) || "attachment".equalsIgnoreCase(fieldID)
						|| "timetracking".equalsIgnoreCase(fieldID) || "Epic Link".equalsIgnoreCase(fieldName) || "Sprint".equalsIgnoreCase(
						fieldName)) {
					continue;
				}

				result.add(new PostFormField(fieldID, fieldName, fieldType, issueField.isRequired(), defValue, allowed));
			}
			return result;
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
			return new ArrayList<>();
		}*/
		return null;

	}

	@Override
	public List<String> getIssueTypes(Integration system) {
/*		try (JiraRestClient client = getClient(system.getParams())) {
			Project jiraProject = getProject(client, system);
			return StreamSupport.stream(jiraProject.getIssueTypes().spliterator(), false)
					.map(IssueType::getName)
					.collect(Collectors.toList());
		} catch (Exception e) {
			throw new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_EXTRERNAL_SYSTEM, "Check connection settings.");
		}*/
		return null;
	}

	/**
	 * JIRA properties validator
	 *
	 * @param details External system details
	 */
	private void validateExternalSystemDetails(Integration details) {
		expect(RallyProps.OAUTH_ACCESS_KEY.getParam(details.getParams()), isPresent()).verify(UNABLE_INTERACT_WITH_EXTRERNAL_SYSTEM,
				"AccessKey value cannot be NULL"
		);

		expect(RallyProps.PROJECT.getParam(details.getParams()), isPresent()).verify(UNABLE_INTERACT_WITH_EXTRERNAL_SYSTEM,
				"Rally project value cannot be NULL"
		);
		expect(RallyProps.URL.getParam(details.getParams()), isPresent()).verify(UNABLE_INTERACT_WITH_EXTRERNAL_SYSTEM,
				"Rally URL value cannot be NULL"
		);
	}

	/**
	 * Get list of project users available for assignee field
	 *
	 * @param jiraProject Project from JIRA
	 * @return List of allowed values
	 */
	private List<AllowedValue> getJiraProjectAssignee(Project jiraProject) {
		/*Iterable<BasicProjectRole> jiraProjectRoles = jiraProject.getProjectRoles();
		try {
			return StreamSupport.stream(jiraProjectRoles.spliterator(), false)
					.filter(role -> role instanceof ProjectRole)
					.map(role -> (ProjectRole) role)
					.flatMap(role -> StreamSupport.stream(role.getActors().spliterator(), false))
					.distinct()
					.map(actor -> new AllowedValue(String.valueOf(actor.getId()), actor.getDisplayName()))
					.collect(Collectors.toList());
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
			throw new ReportPortalException("There is a problem while getting issue types", e);
		}*/
		return null;

	}

/*	public RallyRestApi getClient(String uri, String providedUsername, String providePassword) {

	}*/

	public RallyRestApi getClient(IntegrationParams params) throws URISyntaxException {
		String url = (String) params.getParams().get("url");
		String apiKey = (String) params.getParams().get("api-key");
		//		String project = (String) params.getParams().get("project");
		return new RallyRestApi(new URI(url), apiKey);
	}
}
