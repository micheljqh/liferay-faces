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
package com.liferay.faces.alloy.component.datatable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.el.ValueExpression;
import javax.faces.FacesException;
import javax.faces.component.FacesComponent;
import javax.faces.component.behavior.AjaxBehavior;
import javax.faces.component.behavior.Behavior;
import javax.faces.component.behavior.ClientBehavior;
import javax.faces.component.behavior.ClientBehaviorHolder;
import javax.faces.context.FacesContext;
import javax.faces.event.AjaxBehaviorEvent;
import javax.faces.event.FacesEvent;

import com.liferay.faces.util.component.ComponentUtil;
import com.liferay.faces.util.helper.IntegerHelper;
import com.liferay.faces.util.lang.FacesConstants;


/**
 * @author  Neil Griffin
 */
@FacesComponent(value = DataTable.COMPONENT_TYPE)
public class DataTable extends DataTableBase implements ClientBehaviorHolder {

	// Public Constants
	public static final String COMPONENT_TYPE = "com.liferay.faces.alloy.component.datatable.DataTable";
	public static final String RENDERER_TYPE = "com.liferay.faces.alloy.component.datatable.internal.DataTableRenderer";
	public static final String STYLE_CLASS_NAME = "table table-bordered table-hover table-striped alloy-datatable";

	// Private Constants
	private static final Collection<String> EVENT_NAMES = Collections.unmodifiableCollection(Arrays.asList(
				RowSelectEvent.ROW_SELECT, RowSelectRangeEvent.ROW_SELECT_RANGE, RowDeselectEvent.ROW_DESELECT,
				RowDeselectRangeEvent.ROW_DESELECT_RANGE));

	public DataTable() {
		super();
		setRendererType(RENDERER_TYPE);
	}

	@Override
	public void addClientBehavior(String eventName, ClientBehavior clientBehavior) {

		// If the specified client behavior is an Ajax behavior, then the alloy:accordion component tag has an f:ajax
		// child tag. Register a listener that can respond to the Ajax behavior by invoking the tabCollapseListener or
		// tabExpandListener that may have been specified.
		if (clientBehavior instanceof AjaxBehavior) {
			AjaxBehavior ajaxBehavior = (AjaxBehavior) clientBehavior;
			ajaxBehavior.addAjaxBehaviorListener(new DataTableBehaviorListener());
		}

		super.addClientBehavior(eventName, clientBehavior);
	}

	@Override
	public void queueEvent(FacesEvent facesEvent) {

		// This method is called by the AjaxBehavior renderer's decode() method. If the specified event is an ajax
		// behavior event that indicates a row being selected/deselected, then
		if (facesEvent instanceof AjaxBehaviorEvent) {

			// Determine the client-side state of the selected tab index.
			FacesContext facesContext = FacesContext.getCurrentInstance();
			Map<String, String> requestParameterMap = facesContext.getExternalContext().getRequestParameterMap();
			String clientId = getClientId(facesContext);

			// Queue an accordion tab event rather than the specified faces event.
			AjaxBehaviorEvent behaviorEvent = (AjaxBehaviorEvent) facesEvent;
			Behavior behavior = behaviorEvent.getBehavior();
			String eventName = requestParameterMap.get(FacesConstants.JAVAX_FACES_BEHAVIOR_EVENT);

			if (RowSelectEvent.ROW_SELECT.equals(eventName) || RowDeselectEvent.ROW_DESELECT.equals(eventName)) {

				int originalRowIndex = getRowIndex();
				int selectedRowIndex = IntegerHelper.toInteger(requestParameterMap.get(clientId + "_rowIndex"));
				setRowIndex(selectedRowIndex);

				Object rowData = getRowData();
				setRowIndex(originalRowIndex);

				if (RowSelectEvent.ROW_SELECT.equals(eventName)) {
					facesEvent = new RowSelectEvent(this, behavior, selectedRowIndex, rowData);
				}
				else {
					facesEvent = new RowDeselectEvent(this, behavior, selectedRowIndex, rowData);
				}
			}
			else {

				String rowIndexRange = requestParameterMap.get(clientId + "_rowIndexRange");
				int[] rowIndexArray = toArray(rowIndexRange);

				if (RowSelectRangeEvent.ROW_SELECT_RANGE.equals(eventName)) {
					facesEvent = new RowSelectRangeEvent(this, behavior, rowIndexArray, getRowDataList(rowIndexArray));
				}
				else if (RowDeselectRangeEvent.ROW_DESELECT_RANGE.equals(eventName)) {
					facesEvent = new RowDeselectRangeEvent(this, behavior, rowIndexArray,
							getRowDataList(rowIndexArray));
				}
			}
		}

		super.queueEvent(facesEvent);
	}

	@Override
	public void restoreState(FacesContext context, Object state) {
		super.restoreState(context, state);
		getAttributes().put("oldRows", getRows());
	}

	protected int[] toArray(String commaDelimitedValue) {

		int[] intArray = null;

		if ((commaDelimitedValue != null) && (commaDelimitedValue.length() > 0)) {
			String[] stringArray = commaDelimitedValue.split(",");
			intArray = new int[stringArray.length];

			for (int i = 0; i < stringArray.length; i++) {

				try {
					intArray[i] = Integer.parseInt(stringArray[i]);
				}
				catch (NumberFormatException e) {
					throw new FacesException(e);
				}
			}
		}

		return intArray;
	}

	@Override
	public String getDefaultEventName() {
		return RowSelectEvent.ROW_SELECT;
	}

	@Override
	public Collection<String> getEventNames() {
		return EVENT_NAMES;
	}

	public List<Object> getRowDataList(int[] rowIndexes) {

		List<Object> rowDataList = null;

		if (rowIndexes != null) {

			int originalRowIndex = getRowIndex();
			rowDataList = new ArrayList<Object>(rowIndexes.length);

			for (int rowIndex : rowIndexes) {
				setRowIndex(rowIndex);
				rowDataList.add(getRowData());
			}

			setRowIndex(originalRowIndex);
		}

		return rowDataList;
	}

	@Override
	public String getStyleClass() {

		// getStateHelper().eval(PropertyKeys.styleClass, null) is called because super.getStyleClass() may return the
		// STYLE_CLASS_NAME of the super class.
		String styleClass = (String) getStateHelper().eval(PropertyKeys.styleClass, null);

		return ComponentUtil.concatCssClasses(styleClass, STYLE_CLASS_NAME);
	}

	@Override
	public void setValueExpression(String name, ValueExpression binding) {
		super.setValueExpression(name, binding);
	}

}
