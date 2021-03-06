/**
 * Copyright (c) 2000-2015 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
package com.liferay.faces.alloy.component.paginator;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import javax.faces.component.FacesComponent;
import javax.faces.component.UIComponent;
import javax.faces.component.UIData;
import javax.faces.component.behavior.ClientBehaviorHolder;

import com.liferay.faces.util.component.ComponentUtil;


/**
 * @author  Neil Griffin
 */
@FacesComponent(value = Paginator.COMPONENT_TYPE)
public class Paginator extends PaginatorBase implements ClientBehaviorHolder {

	// Public Constants
	public static final String COMPONENT_FAMILY = "com.liferay.faces.alloy.component.paginator";
	public static final String COMPONENT_TYPE = "com.liferay.faces.alloy.component.paginator.Paginator";
	public static final String RENDERER_TYPE = "com.liferay.faces.alloy.component.paginator.internal.PaginatorRenderer";
	public static final String STYLE_CLASS_NAME = "alloy-paginator";

	// Private Constants
	private static final Collection<String> EVENT_NAMES = Collections.unmodifiableCollection(Arrays.asList("action"));

	public Paginator() {
		super();
		setRendererType(RENDERER_TYPE);
	}

	@Override
	public String getDefaultEventName() {
		return "action";
	}

	@Override
	public Collection<String> getEventNames() {
		return EVENT_NAMES;
	}

	@Override
	public String getFamily() {
		return COMPONENT_FAMILY;
	}

	@Override
	public String getStyleClass() {

		// getStateHelper().eval(PropertyKeys.styleClass, null) is called because super.getStyleClass() may return the
		// STYLE_CLASS_NAME of the super class.
		String styleClass = (String) getStateHelper().eval(PaginatorPropertyKeys.styleClass, null);

		return ComponentUtil.concatCssClasses(styleClass, STYLE_CLASS_NAME);
	}

	public UIData getUIData() {

		UIData uiData = null;
		String for_ = getFor();

		if (for_ == null) {

			uiData = getUIDataParent(this);
		}
		else {

			UIComponent forComponent = findComponent(for_);

			if (forComponent != null) {

				if (forComponent instanceof UIData) {
					uiData = (UIData) forComponent;
				}
			}
		}

		return uiData;
	}

	protected UIData getUIDataParent(UIComponent uiComponent) {

		UIData uiDataParent = null;

		UIComponent parent = uiComponent.getParent();

		if (parent != null) {

			if (parent instanceof UIData) {
				uiDataParent = (UIData) parent;
			}
			else {
				uiDataParent = getUIDataParent(parent);
			}
		}

		return uiDataParent;
	}
}
