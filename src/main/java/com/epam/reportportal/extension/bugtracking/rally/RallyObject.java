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
public class RallyObject {
	@SerializedName("_rallyAPIMajor")
	private String rallyApiMajor;

	@SerializedName("_rallyAPIMinor")
	private String rallyApiMinor;

	@SerializedName("_ref")
	private String ref;

	@SerializedName("_refObjectUUID")
	private String refObjectUUID;

	@SerializedName("_objectVersion")
	private String objectVersion;

	@SerializedName("_refObjectName")
	private String refObjectName;

	@SerializedName("ObjectID")
	private long objectId;

	public String getRallyApiMajor() {
		return rallyApiMajor;
	}

	public void setRallyApiMajor(String rallyApiMajor) {
		this.rallyApiMajor = rallyApiMajor;
	}

	public String getRallyApiMinor() {
		return rallyApiMinor;
	}

	public void setRallyApiMinor(String rallyApiMinor) {
		this.rallyApiMinor = rallyApiMinor;
	}

	public String getRef() {
		return ref;
	}

	public void setRef(String ref) {
		this.ref = ref;
	}

	public String getRefObjectUUID() {
		return refObjectUUID;
	}

	public void setRefObjectUUID(String refObjectUUID) {
		this.refObjectUUID = refObjectUUID;
	}

	public String getObjectVersion() {
		return objectVersion;
	}

	public void setObjectVersion(String objectVersion) {
		this.objectVersion = objectVersion;
	}

	public String getRefObjectName() {
		return refObjectName;
	}

	public void setRefObjectName(String refObjectName) {
		this.refObjectName = refObjectName;
	}

	public long getObjectId() {
		return objectId;
	}

	public void setObjectId(long objectId) {
		this.objectId = objectId;
	}
}
