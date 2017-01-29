/*******************************************************************************
 * Copyright (c) 2013 Hypersocket Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.hypersocket.input;

public class CheckBoxField extends InputField {

	public CheckBoxField(String resourceKey, String defaultValue) {
		super(InputFieldType.checkbox, resourceKey, defaultValue, true, "");
	}

	public CheckBoxField(String resourceKey, String defaultValue, String infoKey) {
		super(InputFieldType.checkbox, resourceKey, defaultValue, true, "", infoKey);
	}
}
