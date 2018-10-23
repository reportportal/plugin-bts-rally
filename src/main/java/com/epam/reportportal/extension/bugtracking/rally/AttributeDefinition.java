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

import com.google.gson.annotations.SerializedName;

/**
 * @author Dzmitry_Kavalets
 */
public class AttributeDefinition extends RallyObject {

	@SerializedName("Count")
	private int count;

	@SerializedName("ElementName")
	private String elementName;

	@SerializedName("Name")
	private String name;

	@SerializedName("Required")
	private boolean required;

	@SerializedName("Type")
	private String type;

	@SerializedName("ReadOnly")
	private boolean readOnly;

	@SerializedName("AllowedValues")
	private AllowedAttributeValue allowedValue;

	public AllowedAttributeValue getAllowedValue() {
		return allowedValue;
	}

	public void setAllowedValue(AllowedAttributeValue allowedValue) {
		this.allowedValue = allowedValue;
	}

	public String getElementName() {
		return elementName;
	}

	public void setElementName(String elementName) {
		this.elementName = elementName;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isRequired() {
		return required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public boolean isReadOnly() {
		return readOnly;
	}

	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}
}
