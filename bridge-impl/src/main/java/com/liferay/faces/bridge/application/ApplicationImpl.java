/**
 * Copyright (c) 2000-2014 Liferay, Inc. All rights reserved.
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
package com.liferay.faces.bridge.application;

import java.util.List;

import javax.faces.FacesException;
import javax.faces.application.Application;
import javax.faces.application.NavigationHandler;
import javax.faces.component.UIComponent;
import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;
import javax.portlet.PortletRequest;
import javax.portlet.faces.BridgeUtil;
import javax.portlet.faces.component.PortletNamingContainerUIViewRoot;

import com.liferay.faces.bridge.component.UIViewRootBridgeImpl;
import com.liferay.faces.bridge.config.BridgeConfig;
import com.liferay.faces.bridge.context.BridgeContext;
import com.liferay.faces.util.config.ConfiguredSystemEventListener;


/**
 * This class is a portlet-specific implementation of the Faces Application. Its purpose is to override the {@link
 * createComponent(String)} and {@link getResourceHandler()} methods so that additional portlet-specific instances can
 * be introduced into the Faces lifecycle. Note that instances of this class are generated by the custom {@link
 * ApplicationFactoryImpl}.
 *
 * @author  Neil Griffin
 */
public class ApplicationImpl extends ApplicationCompatImpl {

	// Private Constants
	private static final String UIVIEWROOT_FQCN = UIViewRoot.class.getName();

	// Private Data Members
	private Boolean subscribeToEventsAtRuntime = Boolean.TRUE;
	private Boolean wrapHandlerAtRuntime = Boolean.TRUE;

	public ApplicationImpl(Application application) {
		super(application);
	}

	/**
	 * This method provides the ability to supply an instance of the bridge API's {@link
	 * PortletNamingContainerUIViewRoot} class which properly handles namespacing of "id" attributes for portlets.
	 *
	 * @see  Application#createComponent(String)
	 */
	@Override
	public UIComponent createComponent(String componentType) throws FacesException {

		if (componentType.equals(UIViewRoot.COMPONENT_TYPE) && BridgeUtil.isPortletRequest()) {

			// NOTE: Mojarra uses a servlet context listener to pre-load all the faces-config.xml files in the
			// classpath. During this initialization, it will call subscribeToEvent() for source-class
			// "javax.faces.component.UIViewRoot". But since, Liferay Faces Bridge uses a subclass of UIViewRoot named
			// UIViewRootBridgeImpl, the events must instead be subscribed for source-class
			// "com.liferay.faces.bridge.component.UIViewRootBridgeImpl". While this is necessary in general, it is
			// especially the case for Mojarra since the com.sun.faces.application.view.ViewScopeManager class relies
			// on javax.faces.event.PostConstructViewMapEvent and javax.faces.event.PreDestroyViewMapEvent in order
			// to cleanup @ViewScoped managed-beans.
			if (subscribeToEventsAtRuntime) {

				// Since multiple requests could come in at the same time, event subscription must be synchronized in
				// order to ensure that it only happens once.
				synchronized (subscribeToEventsAtRuntime) {

					// Need to check again within the synchronization block, just in case.
					if (subscribeToEventsAtRuntime) {
						BridgeContext bridgeContext = BridgeContext.getCurrentInstance();
						BridgeConfig bridgeConfig = bridgeContext.getBridgeConfig();
						List<ConfiguredSystemEventListener> configuredSystemEventListeners =
							bridgeConfig.getConfiguredSystemEventListeners();

						if (configuredSystemEventListeners != null) {

							for (ConfiguredSystemEventListener configuredSystemEventListener :
								configuredSystemEventListeners) {

								if (UIVIEWROOT_FQCN.equals(configuredSystemEventListener.getSourceClass())) {
									subscribeToJSF2SystemEvent(configuredSystemEventListener);
								}
							}
						}
					}

					subscribeToEventsAtRuntime = false;
				}
			}

			return new UIViewRootBridgeImpl();
		}
		else {
			return getWrapped().createComponent(componentType);
		}
	}

	@Override
	public UIComponent createComponent(FacesContext facesContext, String componentType, String rendererType) {

		if (componentType.equals(UIViewRoot.COMPONENT_TYPE) && BridgeUtil.isPortletRequest()) {

			// FACES-1967: Apache MyFaces calls this 3-arg overload of createComponent rather than the 1-arg version
			// when creating a UIViewRoot.
			return createComponent(componentType);
		}
		else {
			return super.createComponent(facesContext, componentType, rendererType);
		}
	}

	/**
	 * The normal way of adding a {@link NavigationHandler} to a JSF application is to have a navigation-handler element
	 * in the faces-config.xml descriptor. Unfortunately the bridge can't use this mechanism, because it must ensure
	 * that the {@link BridgeNavigationHandler} is the outermost instance in the chain-of-responsibility. While this
	 * could be done with &lt;ordering&gt;&lt;after&gt;&lt;others/&gt;&lt;/after&gt;&lt;/others&gt; in the bridge's
	 * META-INF/faces-config.xml file, the bridge must use
	 * &lt;ordering&gt;&lt;before&gt;&lt;others/&gt;&lt;/before&gt;&lt;/others&gt; in order to maintain compatibility
	 * with ICEfaces and other component libraries. So because of this, it is necessary to provide this override of the
	 * {@link #getNavigationHandler()} method in order to ensure that the {@link BridgeNavigationHandler} is the
	 * outermost instance.
	 */
	@Override
	public NavigationHandler getNavigationHandler() {

		// NOTE: Mojarra uses a servlet context listener to pre-load all the faces-config.xml files in the classpath.
		// During this initialization, it will call this method override for whatever reason. But we can't ask the
		// BridgeFactoryFinder to find the BridgeNavigationHandler factory at that time because it relies on the
		// PortletContext object which can only be retrieved at runtime. So for this reason, we have to delay the
		// wrapping the Faces default NavigationHandler until a PortletRequest happens at runtime.
		if (wrapHandlerAtRuntime) {

			// Since multiple requests could come in at the same time, wrapping must be synchronized in order to
			// ensure that it only happens once.
			synchronized (wrapHandlerAtRuntime) {

				// Need to check again within the synchronization block, just in case.
				if (wrapHandlerAtRuntime) {
					FacesContext facesContext = FacesContext.getCurrentInstance();

					if (facesContext != null) {

						try {

							PortletRequest portletRequest = (PortletRequest) facesContext.getExternalContext()
								.getRequest();

							if (portletRequest != null) {

								NavigationHandler wrappableNavigationHandler = super.getNavigationHandler();

								if (!(wrappableNavigationHandler instanceof BridgeNavigationHandlerImpl)) {
									BridgeNavigationHandler bridgeNavigationHandler = new BridgeNavigationHandlerImpl(
											wrappableNavigationHandler);
									super.setNavigationHandler(bridgeNavigationHandler);
									wrapHandlerAtRuntime = false;
								}
							}
						}
						catch (UnsupportedOperationException e) {
							// ignore -- MyFaces does not permit calling getRequest() during startup.
						}
					}
				}
			}
		}

		return super.getNavigationHandler();
	}
}
