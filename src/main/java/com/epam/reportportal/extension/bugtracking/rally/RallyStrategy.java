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
import com.epam.reportportal.commons.template.TemplateEngineProvider;
import com.epam.reportportal.extension.CommonPluginCommand;
import com.epam.reportportal.extension.IntegrationGroupEnum;
import com.epam.reportportal.extension.PluginCommand;
import com.epam.reportportal.extension.ReportPortalExtensionPoint;
import com.epam.reportportal.extension.bugtracking.BtsConstants;
import com.epam.reportportal.extension.bugtracking.BtsExtension;
import com.epam.reportportal.extension.bugtracking.InternalTicket;
import com.epam.reportportal.extension.bugtracking.InternalTicketAssembler;
import com.epam.reportportal.extension.util.FileNameExtractor;
import com.epam.ta.reportportal.binary.impl.AttachmentDataStoreService;
import com.epam.ta.reportportal.dao.LogRepository;
import com.epam.ta.reportportal.dao.TestItemRepository;
import com.epam.ta.reportportal.entity.integration.Integration;
import com.epam.ta.reportportal.entity.integration.IntegrationParams;
import com.epam.ta.reportportal.entity.item.TestItem;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.filesystem.DataEncoder;
import com.epam.ta.reportportal.ws.model.ErrorType;
import com.epam.ta.reportportal.ws.model.externalsystem.AllowedValue;
import com.epam.ta.reportportal.ws.model.externalsystem.PostFormField;
import com.epam.ta.reportportal.ws.model.externalsystem.PostTicketRQ;
import com.epam.ta.reportportal.ws.model.externalsystem.Ticket;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
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
import org.apache.commons.collections.CollectionUtils;
import org.jasypt.util.text.BasicTextEncryptor;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Supplier;

import static com.epam.reportportal.extension.bugtracking.rally.RallyConstants.*;
import static com.epam.ta.reportportal.commons.validation.Suppliers.formattedSupplier;
import static com.epam.ta.reportportal.ws.model.ErrorType.UNABLE_INTERACT_WITH_INTEGRATION;
import static com.epam.ta.reportportal.ws.model.ErrorType.UNABLE_TO_LOAD_BINARY_DATA;
import static java.util.Optional.ofNullable;

/**
 * @author Dzmitry_Kavalets
 */
@Extension
@Component
public class RallyStrategy implements ReportPortalExtensionPoint, BtsExtension {

	private static final String BUG_TEMPLATE_PATH = "bug_template.ftl";
	private static final Logger LOGGER = LoggerFactory.getLogger(RallyStrategy.class);

	private final Gson gson = new Gson();

	private final TemplateEngine templateEngine = new TemplateEngineProvider().get();

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private AttachmentDataStoreService attachmentDataStoreService;

	@Autowired
	private TestItemRepository testItemRepository;

	@Autowired
	private LogRepository logRepository;

	@Autowired
	private DataEncoder dataEncoder;

	@Autowired
	private BasicTextEncryptor encryptor;

	@Override
	public Map<String, ?> getPluginParams() {
		return Collections.emptyMap();
	}

	@Override
	public CommonPluginCommand getCommonCommand(String commandName) {
		throw new UnsupportedOperationException("Not working with commands");
	}

	@Override
	public PluginCommand getIntegrationCommand(String commandName) {
		throw new UnsupportedOperationException("Not working with commands");
	}

	@Override
	public IntegrationGroupEnum getIntegrationGroup() {
		return IntegrationGroupEnum.BTS;
	}

	private Supplier<InternalTicketAssembler> ticketAssembler = Suppliers.memoize(() -> new InternalTicketAssembler(logRepository,
			testItemRepository,
			attachmentDataStoreService,
			dataEncoder
	));

	@Override
	public boolean testConnection(Integration integration) {
		String project = BtsConstants.PROJECT.getParam(integration.getParams(), String.class)
				.orElseThrow(() -> new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION, "Rally Project value cannot be NULL"));

		try (RallyRestApi restApi = getClient(integration.getParams())) {
			QueryRequest rq = new QueryRequest(PROJECT);
			rq.setQueryFilter(new QueryFilter(OBJECT_ID, "=", project));
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
			throw new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION, "Unable load ticket :" + ex.getMessage(), ex);
		}
		return Optional.of(ticket);
	}

	@Override
	public Ticket submitTicket(final PostTicketRQ ticketRQ, Integration integration) {
		try (RallyRestApi restApi = getClient(integration.getParams(), ticketRQ)) {
			List<InternalTicket.LogEntry> logs = ofNullable(ticketAssembler.get().apply(ticketRQ).getLogs()).orElseGet(Lists::newArrayList);

			Defect newDefect = postDefect(restApi, ticketRQ, integration);
			String description = newDefect.getDescription();

			Map<String, String> attachments = new HashMap<>();
			logs.stream()
					.filter(InternalTicket.LogEntry::isHasAttachment)
					.forEach(entry -> attachments.put(entry.getDecodedFileName(),
							String.valueOf(postImage(newDefect.getRef(), entry, restApi).getObjectId())
					));

			for (Map.Entry<String, String> binaryDataEntry : attachments.entrySet()) {
				description = description.replace(binaryDataEntry.getKey(),
						"/slm/attachment/" + binaryDataEntry.getValue() + "/" + binaryDataEntry.getKey()
				);
			}
			updateDescription(description, newDefect, restApi);
			return toTicket(newDefect, integration);
		} catch (Exception e) {
			LOGGER.error("Unable to submit ticket: " + e.getMessage(), e);
			throw new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION, "Unable to submit ticket: " + e.getMessage(), e);
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
			throw new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION, "Unable to load ticket fields: " + e.getMessage(), e);
		}
	}

	@Override
	public List<String> getIssueTypes(Integration integration) {
		return Collections.singletonList(DEFECT);
	}

	public RallyRestApi getClient(IntegrationParams params) throws URISyntaxException {
		String url = BtsConstants.URL.getParam(params, String.class)
				.orElseThrow(() -> new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION, "Rally URL value cannot be NULL"));
		String apiKey = encryptor.decrypt(BtsConstants.OAUTH_ACCESS_KEY.getParam(params, String.class)
				.orElseThrow(() -> new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION, "OAUTH key cannot be NULL")));
		return new RallyRestApi(new URI(url), apiKey);
	}

	public RallyRestApi getClient(IntegrationParams params, PostTicketRQ postTicketRQ) throws URISyntaxException {
		String url = BtsConstants.URL.getParam(params, String.class)
				.orElseThrow(() -> new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION, "Rally URL value cannot be NULL"));
		String apiKey = ofNullable(postTicketRQ.getToken()).orElseThrow(() -> new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION,
				"OAUTH key cannot be NULL"
		));
		return new RallyRestApi(new URI(url), apiKey);
	}

	private Ticket toTicket(Defect defect, Integration externalSystem) {
		Ticket ticket = new Ticket();
		String link =
				BtsConstants.URL.getParam(externalSystem.getParams(), String.class).get() + "/#/" + Ref.getOidFromRef(defect.getProject()
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
		BtsConstants.DEFECT_FORM_FIELDS.getParam(externalSystem.getParams()).ifPresent(integrationFields -> {
			try {
				savedFields.addAll(objectMapper.readValue(objectMapper.writeValueAsBytes(integrationFields),
						objectMapper.getTypeFactory().constructParametricType(List.class, PostFormField.class)
				));
			} catch (IOException e) {
				LOGGER.error("Unable to parse post form fields: ", e.getMessage());
				throw new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION, e);
			}

		});
		for (PostFormField field : fields) {
			// skip empty fields
			if (CollectionUtils.isNotEmpty(field.getValue())) {
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

		String description = createDescription(ticketRQ, ticketAssembler.get().apply(ticketRQ).getLogs());
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
			throw new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION, "Errored request:" + gson.toJson(createRequest));
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

	private RallyObject postImage(String itemRef, InternalTicket.LogEntry logEntry, RallyRestApi restApi) {
		String fileId = logEntry.getFileId();
		Optional<InputStream> fileOptional = attachmentDataStoreService.load(fileId);
		if (fileOptional.isPresent()) {
			try (InputStream file = fileOptional.get()) {
				byte[] bytes = ByteStreams.toByteArray(file);
				JsonObject attach = new JsonObject();
				attach.addProperty(CONTENT, Base64.encodeBase64String(bytes));
				CreateResponse attachmentContentResponse = restApi.create(new CreateRequest(ATTACHMENT_CONTENT, attach));
				JsonObject attachmentObject = new JsonObject();
				attachmentObject.addProperty(ARTIFACT, itemRef);
				attachmentObject.addProperty(CONTENT, attachmentContentResponse.getObject().get(REF).getAsString());
				attachmentObject.addProperty(NAME, FileNameExtractor.extractFileName(dataEncoder, fileId));
				attachmentObject.addProperty(DESCRIPTION, fileId);
				attachmentObject.addProperty(CONTENT_TYPE, logEntry.getContentType());
				attachmentObject.addProperty(SIZE, bytes.length);
				CreateRequest attachmentCreateRequest = new CreateRequest(ATTACHMENT, attachmentObject);
				CreateResponse attachmentResponse = restApi.create(attachmentCreateRequest);
				checkResponse(attachmentResponse);
				return gson.fromJson(attachmentResponse.getObject(), RallyObject.class);
			} catch (IOException e) {
				LOGGER.error("Unable to post ticket image: " + e.getMessage() + "\n" + Arrays.toString(e.getStackTrace()), e);
				throw new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION, "Unable to post ticket image: " + e.getMessage(), e);
			}
		} else {
			throw new ReportPortalException(UNABLE_TO_LOAD_BINARY_DATA);
		}
	}

	private void checkResponse(Response response) {
		if (response.getErrors().length > 0) {
			throw new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION,
					"Error during interacting with Rally: " + String.join(" ", response.getErrors())
			);
		}
	}

	private String createDescription(PostTicketRQ ticketRQ, List<InternalTicket.LogEntry> itemLogs) {
		TestItem testItem = testItemRepository.findById(ticketRQ.getTestItemId())
				.orElseThrow(() -> new ReportPortalException(UNABLE_INTERACT_WITH_INTEGRATION,
						formattedSupplier("Test item {} not found", ticketRQ.getTestItemId())
				));
		HashMap<Object, Object> templateData = new HashMap<>();
		if (ticketRQ.getIsIncludeComments()) {
			ofNullable(testItem.getItemResults().getIssue()).ifPresent(issue -> templateData.put("comments", issue.getIssueDescription()));

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
