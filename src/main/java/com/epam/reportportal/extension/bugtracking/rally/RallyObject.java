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
