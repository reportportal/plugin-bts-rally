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
package com.epam.reportportal.extension.bugtracking.rally;

import com.epam.reportportal.commons.template.TemplateEngine;
import com.epam.reportportal.extension.bugtracking.BtsConstants;
import com.epam.reportportal.extension.bugtracking.BtsExtension;
import com.epam.reportportal.extension.bugtracking.InternalTicket;
import com.epam.reportportal.extension.bugtracking.InternalTicketAssembler;
import com.epam.ta.reportportal.binary.DataStoreService;
import com.epam.ta.reportportal.dao.TestItemRepository;
import com.epam.ta.reportportal.entity.integration.Integration;
import com.epam.ta.reportportal.entity.integration.IntegrationParams;
import com.epam.ta.reportportal.entity.item.TestItem;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.ws.model.externalsystem.AllowedValue;
import com.epam.ta.reportportal.ws.model.externalsystem.PostFormField;
import com.epam.ta.reportportal.ws.model.externalsystem.PostTicketRQ;
import com.epam.ta.reportportal.ws.model.externalsystem.Ticket;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.CreateRequest;
import com.rallydev.rest.request.QueryRequest;
import com.rallydev.rest.request.UpdateRequest;
import com.rallydev.rest.response.CreateResponse;
import com.rallydev.rest.response.QueryResponse;
import com.rallydev.rest.response.Response;
import com.rallydev.rest.response.UpdateResponse;
import com.rallydev.rest.util.Fetch;
import com.rallydev.rest.util.QueryFilter;
import com.rallydev.rest.util.Ref;
import org.apache.commons.codec.binary.Base64;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static com.epam.reportportal.extension.bugtracking.rally.RallyConstants.*;
import static com.epam.ta.reportportal.commons.validation.Suppliers.formattedSupplier;
import static com.epam.ta.reportportal.ws.model.ErrorType.UNABLE_INTERACT_WITH_EXTRERNAL_SYSTEM;

/**
 * @author Dzmitry_Kavalets
 */
@Extension
public class RallyStrategy implements BtsExtension {

	private static final String BUG_TEMPLATE_PATH = "bug_template.ftl";
	private static final Logger LOGGER = LoggerFactory.getLogger(RallyStrategy.class);

	private Gson gson;

	private DataStoreService dataStorage;

	private TestItemRepository testItemRepository;

	private TemplateEngine templateEngine;

	@Autowired
	InternalTicketAssembler ticketAssembler;

	public RallyStrategy() {
		this.gson = new Gson();
	}

	@Autowired
	public RallyStrategy(DataStoreService dataStorage, TestItemRepository testItemRepository, TemplateEngine templateEngine) {
		this.gson = new Gson();
		this.dataStorage = dataStorage;
		this.testItemRepository = testItemRepository;
		this.templateEngine = templateEngine;
	}

	@Override
	public boolean connectionTest(Integration integration) {
		String project = BtsConstants.PROJECT.getParam(integration.getParams(), String.class)
				.orElseThrow(() -> new ReportPortalException(UNABLE_INTERACT_WITH_EXTRERNAL_SYSTEM, "Rally URL value cannot be NULL"));

		try (RallyRestApi restApi = getClient(integration.getParams())) {
			QueryRequest rq = new QueryRequest(PROJECT);
			rq.setQueryFilter(new QueryFilter(FORMATTED_ID, "=", project));
			return restApi.query(rq).wasSuccessful();
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
			return false;
		}
	}

	@Override
	public Optional<Ticket> getTicket(final String id, Integration integration) {
		Ticket ticket;
		try (RallyRestApi restApi = getClient(integration.getParams())) {
			Optional<Defect> optionalDefect = findDefect(restApi, id);
			if (!optionalDefect.isPresent()) {
				return Optional.empty();
			}
			ticket = toTicket(optionalDefect.get(), integration);
		} catch (Exception ex) {
			LOGGER.error("Unable load ticket :" + ex.getMessage(), ex);
			throw new ReportPortalException(UNABLE_INTERACT_WITH_EXTRERNAL_SYSTEM, "Unable load ticket :" + ex.getMessage(), ex);
		}
		return Optional.of(ticket);
	}

	@Override
	public Ticket submitTicket(final PostTicketRQ ticketRQ, Integration integration) {
		try (RallyRestApi restApi = getClient(integration.getParams())) {
			List<InternalTicket.LogEntry> logs = ticketAssembler.apply(ticketRQ).getLogs();
			Defect newDefect = postDefect(restApi, ticketRQ, integration);
			String description = newDefect.getDescription();
			Map<String, String> attachments = new HashMap<>();
			for (InternalTicket.LogEntry logEntry : logs) {
				if (logEntry.getAttachment() != null) {
					attachments.put(logEntry.getLog().getAttachment(),
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
			return toTicket(newDefect, integration);
		} catch (Exception e) {
			LOGGER.error("Unable to submit ticket: " + e.getMessage(), e);
			throw new ReportPortalException(UNABLE_INTERACT_WITH_EXTRERNAL_SYSTEM, "Unable to submit ticket: " + e.getMessage(), e);
		}
	}

	@Override
	public List<PostFormField> getTicketFields(final String ticketType, Integration details) {

		try (RallyRestApi restApi = getClient(details.getParams())) {
			ArrayList<PostFormField> fields = new ArrayList<>();
			List<AttributeDefinition> attributeDefinitions = findDefectAttributeDefinitions(restApi);
			for (AttributeDefinition attributeDefinition : attributeDefinitions) {
				if (!attributeDefinition.isReadOnly()) {
					PostFormField postFormField = new PostFormField();
					// load predefined values
					if (attributeDefinition.getAllowedValue().getCount() > 0) {
						List<AllowedValue> definedValues = new ArrayList<>();
						List<AllowedAttributeValue> allowedAttributeValues = findAllowedAttributeValues(restApi, attributeDefinition);
						for (AllowedAttributeValue allowedAttributeValue : allowedAttributeValues) {
							AllowedValue allowedValue = new AllowedValue();
							if (allowedAttributeValue.getStringValue() != null && !allowedAttributeValue.getStringValue().isEmpty()) {
								allowedValue.setValueName(allowedAttributeValue.getStringValue());
								if (!"null".equals(allowedAttributeValue.getRef())) {
									allowedValue.setValueId(Ref.getRelativeRef(allowedAttributeValue.getRef()));
								}
								definedValues.add(allowedValue);
							}
						}
						postFormField.setDefinedValues(definedValues);
					}
					postFormField.setId(attributeDefinition.getElementName());
					postFormField.setFieldName(attributeDefinition.getName());
					postFormField.setIsRequired(attributeDefinition.isRequired());
					postFormField.setFieldType(attributeDefinition.getType());
					fields.add(postFormField);
				}
			}
			return fields;
		} catch (IOException | URISyntaxException e) {
			LOGGER.error("Unable to load ticket fields: " + e.getMessage(), e);
			throw new ReportPortalException(UNABLE_INTERACT_WITH_EXTRERNAL_SYSTEM, "Unable to load ticket fields: " + e.getMessage(), e);
		}
	}

	@Override
	public List<String> getIssueTypes(Integration integration) {
		return Collections.singletonList(DEFECT);
	}

	public RallyRestApi getClient(IntegrationParams params) throws URISyntaxException {
		String url = BtsConstants.URL.getParam(params, String.class)
				.orElseThrow(() -> new ReportPortalException(UNABLE_INTERACT_WITH_EXTRERNAL_SYSTEM, "AccessKey value cannot be NULL"));
		String apiKey = BtsConstants.OAUTH_ACCESS_KEY.getParam(params, String.class)
				.orElseThrow(() -> new ReportPortalException(UNABLE_INTERACT_WITH_EXTRERNAL_SYSTEM, "Rally project value cannot be NULL"));
		return new RallyRestApi(new URI(url), apiKey);
	}

	private Ticket toTicket(Defect defect, Integration externalSystem) {
		Ticket ticket = new Ticket();
		String link = BtsConstants.URL.getParam(externalSystem.getParams(), String.class) + "/#/" + Ref.getOidFromRef(defect.getProject()
				.getRef()) + "/detail/defect/" + defect.getObjectId();
		ticket.setId(defect.getFormattedId());
		ticket.setSummary(defect.getName());
		ticket.setTicketUrl(link);
		ticket.setStatus(defect.getState());
		return ticket;
	}

	private Defect postDefect(RallyRestApi restApi, PostTicketRQ ticketRQ, Integration externalSystem) throws IOException {
		JsonObject newDefect = new JsonObject();
		List<PostFormField> fields = ticketRQ.getFields();
		List<PostFormField> savedFields = new ArrayList<>();
		BtsConstants.DEFECT_FORM_FIELDS.getParam(externalSystem.getParams(), List.class).ifPresent(savedFields::addAll);
		for (PostFormField field : fields) {
			// skip empty fields
			if (!field.getValue().isEmpty()) {
				String value = field.getValue().get(0);
				for (PostFormField savedField : savedFields) {
					if (savedField.getId().equalsIgnoreCase(field.getId())) {
						List<AllowedValue> definedValues = savedField.getDefinedValues();
						if (definedValues != null) {
							for (AllowedValue definedValue : definedValues) {
								if (definedValue.getValueName().equals(field.getValue().get(0)) && definedValue.getValueId() != null) {
									value = definedValue.getValueId();
								}
							}
						}
					}
				}
				newDefect.addProperty(field.getId(), value);
			}
		}

		String description = createDescription(ticketRQ, ticketAssembler.apply(ticketRQ).getLogs());
		newDefect.addProperty(DESCRIPTION,
				newDefect.get(DESCRIPTION) != null ? (newDefect.get(DESCRIPTION).getAsString() + "<br>" + description) : description
		);
		CreateRequest createRequest = new CreateRequest(DEFECT, newDefect);
		try {
			CreateResponse createResponse = restApi.create(createRequest);
			checkResponse(createResponse);
			return gson.fromJson(createResponse.getObject(), Defect.class);
		} catch (Exception e) {
			LOGGER.error("Errored request: {}", gson.toJson(createRequest));
			throw new ReportPortalException(UNABLE_INTERACT_WITH_EXTRERNAL_SYSTEM, "Errored request:" + gson.toJson(createRequest));
		}
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

	private List<AttributeDefinition> findDefectAttributeDefinitions(RallyRestApi restApi) throws IOException {
		QueryRequest typeDefRequest = new QueryRequest(TYPE_DEFINITION);
		typeDefRequest.setFetch(new Fetch(OBJECT_ID, ATTRIBUTES));
		typeDefRequest.setQueryFilter(new QueryFilter(NAME, "=", DEFECT));
		QueryResponse typeDefQueryResponse = restApi.query(typeDefRequest);
		JsonObject typeDefJsonObject = typeDefQueryResponse.getResults().get(0).getAsJsonObject();
		QueryRequest attributeRequest = new QueryRequest((JsonObject) gson.toJsonTree(gson.fromJson(typeDefJsonObject, TypeDefinition.class)
				.getAttributeDefinition()));
		attributeRequest.setFetch(new Fetch(ALLOWED_VALUES, ELEMENT_NAME, NAME, REQUIRED, TYPE, OBJECT_ID, READ_ONLY));
		QueryResponse attributesQueryResponse = restApi.query(attributeRequest);
		return gson.fromJson(attributesQueryResponse.getResults(), new TypeToken<List<AttributeDefinition>>() {
		}.getType());
	}

	private List<AllowedAttributeValue> findAllowedAttributeValues(RallyRestApi restApi, AttributeDefinition attributeDefinition)
			throws IOException {
		QueryRequest allowedValuesRequest = new QueryRequest((JsonObject) gson.toJsonTree(attributeDefinition.getAllowedValue()));
		allowedValuesRequest.setFetch(new Fetch(STRING_VALUE));
		QueryResponse allowedValuesResponse = restApi.query(allowedValuesRequest);
		return gson.fromJson(allowedValuesResponse.getResults(), new TypeToken<List<AllowedAttributeValue>>() {
		}.getType());
	}

	private RallyObject postImage(String itemRef, InternalTicket.LogEntry log, RallyRestApi restApi) throws IOException {
		String filename = log.getLog().getAttachment();
		InputStream file = dataStorage.load(filename);
		byte[] bytes = ByteStreams.toByteArray(file);
		JsonObject attach = new JsonObject();
		attach.addProperty(CONTENT, Base64.encodeBase64String(bytes));
		CreateResponse attachmentContentResponse = restApi.create(new CreateRequest(ATTACHMENT_CONTENT, attach));
		JsonObject attachment = new JsonObject();
		attachment.addProperty(ARTIFACT, itemRef);
		attachment.addProperty(CONTENT, attachmentContentResponse.getObject().get(REF).getAsString());
		attachment.addProperty(NAME, filename);
		attachment.addProperty(DESCRIPTION, filename);
		attachment.addProperty(CONTENT_TYPE, log.getLog().getContentType());
		attachment.addProperty(SIZE, bytes.length);
		CreateRequest attachmentCreateRequest = new CreateRequest(ATTACHMENT, attachment);
		CreateResponse attachmentResponse = restApi.create(attachmentCreateRequest);
		checkResponse(attachmentResponse);
		return gson.fromJson(attachmentResponse.getObject(), RallyObject.class);
	}

	private void checkResponse(Response response) {
		if (response.getErrors().length > 0) {
			throw new ReportPortalException(UNABLE_INTERACT_WITH_EXTRERNAL_SYSTEM,
					"Error during interacting with Rally: " + String.join(" ", response.getErrors())
			);
		}
	}

	private String createDescription(PostTicketRQ ticketRQ, List<InternalTicket.LogEntry> itemLogs) {
		TestItem testItem = testItemRepository.findById(ticketRQ.getTestItemId())
				.orElseThrow(() -> new ReportPortalException(UNABLE_INTERACT_WITH_EXTRERNAL_SYSTEM,
						formattedSupplier("Test item {} not found", ticketRQ.getTestItemId())
				));
		HashMap<Object, Object> templateData = new HashMap<>();
		if (ticketRQ.getIsIncludeComments()) {
			templateData.put("comments", testItem.getItemResults().getIssue().getIssueDescription());
		}
		if (ticketRQ.getBackLinks() != null) {
			templateData.put("backLinks", ticketRQ.getBackLinks());
		}
		if (itemLogs != null && (ticketRQ.getIsIncludeLogs() || ticketRQ.getIsIncludeScreenshots())) {
			templateData.put("logs", itemLogs);
		}
		return templateEngine.merge(BUG_TEMPLATE_PATH, templateData);
	}

	private Defect updateDescription(String description, Defect defect, RallyRestApi restApi) throws IOException {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty(DESCRIPTION, description);
		UpdateRequest updateRequest = new UpdateRequest(defect.getRef(), jsonObject);
		UpdateResponse update = restApi.update(updateRequest);
		checkResponse(update);
		return gson.fromJson(update.getObject(), Defect.class);
	}
}
