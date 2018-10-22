package com.epam.reportportal.extension.bugtracking.rally;

import com.google.gson.annotations.SerializedName;

/**
 * @author Dzmitry_Kavalets
 */
public class Defect extends RallyObject {

	@SerializedName("Name")
	private String name;

	@SerializedName("Project")
	private Project project;

	@SerializedName("State")
	private String state;

	@SerializedName("Description")
	private String description;

	@SerializedName("FormattedID")
	private String formattedId;

	public String getFormattedId() {
		return formattedId;
	}

	public void setFormattedId(String formattedId) {
		this.formattedId = formattedId;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Project getProject() {
		return project;
	}

	public void setProject(Project project) {
		this.project = project;
	}
}
