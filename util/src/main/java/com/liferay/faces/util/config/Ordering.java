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
package com.liferay.faces.util.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.liferay.faces.util.logging.Logger;
import com.liferay.faces.util.logging.LoggerFactory;


/**
 * @author  Vernon Singleton
 */
public class Ordering {

	// Logger
	private static final Logger logger = LoggerFactory.getLogger(Ordering.class);

	public static final String OTHERS = Ordering.class.getName() + ".OTHERS";

	protected static final int BEFORE = 0;
	protected static final int AFTER = 1;

	private static final int[] paths = { BEFORE, AFTER };
	protected static final int WAYS = paths.length;

	// default LIMIT is 1048 as of Java 1.7
	public static final int LIMIT = (Integer.MAX_VALUE / (Byte.MAX_VALUE * Byte.MAX_VALUE * Byte.MAX_VALUE));

	public static final String CIRCULAR_DEPENDENCIES_DETECTED = "Circular dependencies detected";
	public static final String BOTH_BEFORE_AND_AFTER = "both before and after";
	public static final String TOO_MANY_ATTEMPTS_NEEDED_TO_SORT = "Too many attempts needed to sort";

	protected String[][] routes;

	public Ordering() {
		this.routes = new String[WAYS][];
		this.routes[BEFORE] = new String[0];
		this.routes[AFTER] = new String[0];
	}

	public static String[] appendAndSort(String[]... groups) {
		HashMap<String, Integer> map = new HashMap<String, Integer>();

		// retain OTHERS, if it is in the first group, but do not allow OTHERS to be appended
		if (groups[0] != null) {

			if (containsOthers(groups[0])) {
//              System.err.println("appendAndSort: retaining OTHERS ...");
				map.put(OTHERS, 1);
			}
		}

		for (String[] group : groups) {

			for (String name : group) {

				if (!name.equals(OTHERS)) {
					map.put(name, 1);
				}
			}
		}

		String[] orderedNames = map.keySet().toArray(new String[map.keySet().size()]);
		Arrays.sort(orderedNames);

		return orderedNames;
	}

	// TODO check
	public static void checkForSpecExceptions(List<FacesConfigDescriptor> configs) throws Exception {

//      String[] names = Ordering.extractNames(configs);
//      System.err.println("checkForSpecExceptions: names = " + Arrays.asList(names).toString());

		for (FacesConfigDescriptor config : configs) {

			// Check for "duplicate name exception"
			checkForBothBeforeAndAfter(config);

//			System.err.println("mapRoutes: =========================== ");
			
			// map the routes along both paths, checking for "circular references" along each path
			for (int route : paths) {
				mapRoutes(config, route, configs);
			}
		}
	}

	public static boolean containsOthers(String[] route) {
		return (Arrays.binarySearch(route, OTHERS) >= 0);
	}

	public static boolean disordered(FacesConfigDescriptor thisConfig, FacesConfigDescriptor thatConfig) {

		String thisName = thisConfig.getName();
		String thatName = thatConfig.getName();

		Ordering thisOrdering = thisConfig.getOrdering();
		Ordering thatOrdering = thatConfig.getOrdering();

		// thisConfig has named afters and is not before others
		//
		// ((thisOrdering.routes[AFTER].length != 0) && !thisOrdering.isBeforeOthers() &&
		// !thatOrdering.isOrdered()) ||

		if (thisOrdering.isOrdered() && !thatOrdering.isOrdered()) {

			if ((thisOrdering.routes[AFTER].length != 0) && !thisOrdering.isBeforeOthers()) {
				return true;
			}
		}

		// they are not in the specified order
		if (thatOrdering.isBefore(thisName) || thisOrdering.isAfter(thatName)) {
			return true;
		}

		// thisConfig should be after others, but it is not
		if (thisOrdering.isAfterOthers() && !thisOrdering.isBefore(thatName) &&
				!(thisOrdering.isAfterOthers() && thatOrdering.isAfterOthers())) {
			return true;
		}

		// thatConfig should be before others, but it is not
		if (thatOrdering.isBeforeOthers() && !thatOrdering.isAfter(thisName) &&
				!(thisOrdering.isBeforeOthers() && thatOrdering.isBeforeOthers())) {
			return true;
		}

		return false;

	}

	public static boolean disorderedE(FacesConfigDescriptor thisConfig, FacesConfigDescriptor thatConfig) {

		String thisName = thisConfig.getName();
		String thatName = thatConfig.getName();

		Ordering thisOrdering = thisConfig.getOrdering();
		Ordering thatOrdering = thatConfig.getOrdering();

		if (
				// they are not in the specified order
				(thatOrdering.isBefore(thisName) || thisOrdering.isAfter(thatName)) ||

				// thisConfig has named afters and is not before others
				((thisOrdering.routes[AFTER].length != 0) && !thisOrdering.isBeforeOthers() &&
					!thatOrdering.isOrdered()) ||

				// thisConfig should be before others, but it is not
				(thisOrdering.isAfterOthers() && !thisOrdering.isBefore(thatName) &&
					!(thisOrdering.isAfterOthers() && thatOrdering.isAfterOthers())) ||

				// thatConfig should be before others, but it is not
				(thatOrdering.isBeforeOthers() && !thatOrdering.isAfter(thisName) &&
					!(thisOrdering.isBeforeOthers() && thatOrdering.isBeforeOthers()))) {
			return true;
		}

		return false;

	}

	public static String[] extractNames(List<FacesConfigDescriptor> facesConfigDescriptors) {
		String[] extractedNames = new String[facesConfigDescriptors.size()];
		int i = 0;

		for (FacesConfigDescriptor facesConfigDescriptor : facesConfigDescriptors) {
			extractedNames[i] = facesConfigDescriptor.getName();
			i++;
		}

		return extractedNames;
	}

	// TODO check
	public static void mapRoutes(FacesConfigDescriptor config, int route, List<FacesConfigDescriptor> facesConfigs)
			throws Exception {
		
		String name = config.getName();
//		System.err.println("mapRoutes: name: " + name + " path: " + route);
		Ordering ordering = config.getOrdering();
		String[][] theseNames = ordering.getRoutes();
		
		for (String thisName : theseNames[route]) {
			if (!thisName.equals(OTHERS)) {
				for (FacesConfigDescriptor otherConfig : facesConfigs) {
					String thatName = otherConfig.getName();
					if (thisName.equals(thatName)) {
						Ordering otherOrdering = otherConfig.getOrdering();
						String[][] otherRoutes = otherConfig.getOrdering().getRoutes();
						String[] otherNames = otherRoutes[route];
//						System.err.println("mapRoutes: circular ?= " + Arrays.asList(otherNames));
						if (Arrays.binarySearch(otherNames, name) >= 0) {
							throwExceptionForCircularDependency(route, facesConfigs);
						}
						
						// I'm before/after them, so they should know that they are after/before me, respectively.
//						System.err.println("mapRoutes: checking (" + thisName + ") otherRoutes[path ^ 1] for name = " + Arrays.asList(otherRoutes[route ^ 1]));
						if (Arrays.binarySearch(otherRoutes[route ^ 1], name) < 0) {
							String[][] routes = new String[WAYS][];
							routes[route] = otherRoutes[route];
							routes[route ^ 1] = appendAndSort(otherRoutes[route ^ 1], new String[] { name } );
							otherOrdering.setRoutes(routes);
//							System.err.println("mapRoutes: otherConfig was: " + Arrays.asList(otherRoutes[BEFORE]) + " " + thisName + " " + Arrays.asList(otherRoutes[AFTER]));
//							System.err.println("mapRoutes: otherConfig now: " + Arrays.asList(routes[BEFORE]) + " " + thisName + " " + Arrays.asList(routes[AFTER]));
						}
						
						// I'm before/after them, and they are before/after others, so i should know that I am before/after those others, respectively
						if (otherNames.length > 0) {
							String[][] routes = new String[WAYS][];
							routes[route] = appendAndSort(theseNames[route], otherNames);
							routes[route ^ 1] = theseNames[route ^ 1];
							ordering.setRoutes(routes);
//							System.err.println("mapRoutes: config was: " + Arrays.asList(theseNames[BEFORE]) + " " + name + " " + Arrays.asList(theseNames[AFTER]));
//							System.err.println("mapRoutes: config now: " + Arrays.asList(routes[BEFORE]) + " " + name + " " + Arrays.asList(routes[AFTER]));
						}
					}
				}
			}
		}
	}

	public static void throwExceptionForCircularDependency(int direction, List<FacesConfigDescriptor> facesConfigs)
		throws Exception {

		String route = (direction == BEFORE) ? "before" : "after";

		String message = CIRCULAR_DEPENDENCIES_DETECTED + " when traversing '" + route + "' declarations:\n";

		for (FacesConfigDescriptor facesConfigDescriptor : facesConfigs) {
			Ordering someOrdering = facesConfigDescriptor.getOrdering();
			String[][] someRoutes = someOrdering.getRoutes();
			String[] someNames = someRoutes[direction];

			if (someNames.length != 0) {
				message += "  " + facesConfigDescriptor.getName() + " " + route + ": " +
					Arrays.asList(someNames).toString() + "\n";
			}
		}

		throw new Exception(message);
	}

	private static void checkForBothBeforeAndAfter(FacesConfigDescriptor config) throws Exception {

		String configName = config.getName();
		Ordering ordering = config.getOrdering();
		String[][] theseRoutes = ordering.getRoutes();

		HashMap<String, Integer> map = new HashMap<String, Integer>();

		for (String name : theseRoutes[BEFORE]) {
			Integer value = map.get(name);
			value = (value == null) ? 1 : (value + 1);
			map.put(name, value);
		}

		for (String name : theseRoutes[AFTER]) {
			Integer value = map.get(name);
			value = (value == null) ? 1 : (value + 1);
			map.put(name, value);
		}

		String[] namesToCheck = map.keySet().toArray(new String[map.keySet().size()]);

		for (String name : namesToCheck) {

			if (map.get(name) > 1) {
				throw new Exception(configName + " cannot be " + BOTH_BEFORE_AND_AFTER + " " + name + "\n");
			}
		}
	}

	public static HashMap<String, FacesConfigDescriptor> getConfigHashMap(
		List<FacesConfigDescriptor> facesConfigDescriptors) {
		HashMap<String, FacesConfigDescriptor> configMap = new HashMap<String, FacesConfigDescriptor>();

		for (FacesConfigDescriptor facesConfigDescriptor : facesConfigDescriptors) {
			String name = facesConfigDescriptor.getName();
			configMap.put(name, facesConfigDescriptor);
		}

		return configMap;
	}

	// getOrder when no absolute order is specified
	public static List<FacesConfigDescriptor> getOrder(List<FacesConfigDescriptor> configs) throws Exception {

//      for (FacesConfigDescriptor config : configs) {
//          String name = config.getName();
//          Ordering ordering = config.getOrdering();
//
//          if (ordering != null) {
//              String[][] routes = ordering.getRoutes();
//
//              if (routes != null) {
//
//                  if (routes[0].length != 0) {
//                	  System.err.println("getOrder: before: " + name + " b: " + Arrays.asList(routes[0]).toString() + " " + ordering.isBeforeOthers());
//                  }
//
//                  if (routes[1].length != 0) {
//                	  System.err.println("getOrder: before: " + name + " a: " + Arrays.asList(routes[1]).toString() + " " + ordering.isAfterOthers());
//                  }
//              }
//          }
//      }

		// Check for "duplicate name exception" and "circular references" as described in 11.4.8 Ordering of Artifacts
		checkForSpecExceptions(configs);

//      for (FacesConfigDescriptor config : configs) {
//          String name = config.getName();
//          Ordering ordering = config.getOrdering();
//
//          if (ordering != null) {
//              String[][] routes = ordering.getRoutes();
//
//              if (routes != null) {
//
//                  if (routes[0].length != 0) {
//                	  System.err.println("getOrder:  after: " + name + " b: " + Arrays.asList(routes[0]).toString() + " " + ordering.isBeforeOthers());
//                  }
//
//                  if (routes[1].length != 0) {
//                	  System.err.println("getOrder:  after: " + name + " a: " + Arrays.asList(routes[1]).toString() + " " + ordering.isAfterOthers());
//                  }
//              }
//          }
//      }

		String[] extractedNames = extractNames(configs);
//		System.err.println("getOrder: before preSort:  extractedNames = " + Arrays.asList(extractedNames).toString());
		
		// Sort the documents such that specified ordering will be considered.
		//
		// It turns out that some of the specified ordering, if it was not discovered by the sort routine 
		// until later in its processing, was not being considered correctly in the ordering algorithm.
		//
		// This preSort method puts all of the documents with specified ordering as early on in the
		// list of documents as possible for to consider it quickly, and be 
		// able to use its ordering algorithm to the best of its ability to achieve the specified ordering.
		configs = preSort(configs);

		extractedNames = extractNames(configs);
//		System.err.println("getOrder: after preSort:  extractedNames = " + Arrays.asList(extractedNames).toString());
		
		HashMap<String, FacesConfigDescriptor> configMap = getConfigHashMap(configs);

		boolean attempting = true;
		int attempts = 0;

		while (attempting) {

			if (attempts > LIMIT) {
				throw new Exception(TOO_MANY_ATTEMPTS_NEEDED_TO_SORT + " the faces-config files.  More than " + LIMIT +
					" passes.");
			}
			else {
				attempting = false;
			}

			int last = extractedNames.length - 1;

			for (int i = 0; i < extractedNames.length; i++) {
				int first = i;
				int second = first + 1;

				if (first == last) {
					second = first;
					first = 0;
				}

				String firstName = extractedNames[first];
				String secondName = extractedNames[second];

				FacesConfigDescriptor firstConfig = configMap.get(firstName);
				FacesConfigDescriptor secondConfig = configMap.get(secondName);

				if (disordered(firstConfig, secondConfig)) {
					String[] thisStringArray = { firstName, secondName };
					Arrays.sort(thisStringArray);
					extractedNames[first] = secondName;
					extractedNames[second] = firstName;
					attempting = true;
				}
			}

//			logger.info("getOrder: during:  extractedNames = " + Arrays.asList(extractedNames).toString());
			attempts++;
		}

		logger.warn("getOrder: " + attempts + "/" + LIMIT);

		List<FacesConfigDescriptor> orderedList = new ArrayList<FacesConfigDescriptor>();

//		logger.info("getOrder: after:  extractedNames = " + Arrays.asList(extractedNames).toString());
		for (String name : extractedNames) {
			FacesConfigDescriptor facesConfigDescriptor = configMap.get(name);
			orderedList.add(facesConfigDescriptor);
		}

		return orderedList;
	}
	
	public static List<FacesConfigDescriptor> preSort(List<FacesConfigDescriptor> configs) {

		List<FacesConfigDescriptor> newConfigList = new ArrayList<FacesConfigDescriptor>();
		
		List<FacesConfigDescriptor> unnamedList = new ArrayList<FacesConfigDescriptor>();
		Map<String, Integer> namedMap = new LinkedHashMap<String, Integer>();

		for (FacesConfigDescriptor config : configs) {

			String[] bfs = (config.getOrdering().getRoutes())[BEFORE];
			String[] afs = (config.getOrdering().getRoutes())[AFTER];
			int knowledge = bfs.length + afs.length;

			if (config.getName() == null || "".equals(config.getName())) {
				unnamedList.add(config);
			} else {
				namedMap.put(config.getName(), knowledge);
			}
		}
        
		namedMap = descendingByValue(namedMap);

		HashMap<String, FacesConfigDescriptor> configHashMap = getConfigHashMap(configs);

		// add named configs to the list in the correct preSorted order
		for (Map.Entry<String, Integer> entry : namedMap.entrySet()) {
			String key = entry.getKey();
			newConfigList.add(configHashMap.get(key));            
		}
        // add unammed configs, if any, to the list in their original, incoming order
		for (FacesConfigDescriptor config : unnamedList) {
			newConfigList.add(config);  
		}

		return newConfigList;

    }
	
	public static <K, V extends Comparable<? super V>> Map<K, V> descendingByValue(Map<K, V> map) {
		List<Map.Entry<K, V>> list = new LinkedList<Map.Entry<K, V>>(map.entrySet());
		Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
			public int compare(Map.Entry<K, V> a, Map.Entry<K, V> b) {
				return (b.getValue()).compareTo(a.getValue());
			}
		});

		Map<K, V> result = new LinkedHashMap<K, V>();
		for (Map.Entry<K, V> entry : list) {
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}

	// getOrder when an absolute order is specified
	public static List<FacesConfigDescriptor> getOrder(List<FacesConfigDescriptor> configs,
		List<String> absoluteOrder) {

		List<FacesConfigDescriptor> orderedList = new ArrayList<FacesConfigDescriptor>();

		List<FacesConfigDescriptor> configList = new CopyOnWriteArrayList<FacesConfigDescriptor>();
		configList.addAll(configs);

		for (String name : absoluteOrder) {

			if (OTHERS.equals(name)) {
				continue;
			}

			boolean found = false;

			for (FacesConfigDescriptor config : configList) {

				if (!found && name.equals(config.getName())) {
					found = true;
					orderedList.add(config);
					configList.remove(config);
				}
				else if (found && name.equals(config.getName())) {
					logger.warn("getOrder: " + name + " found more than once ...");

					break;
				}
			}

			if (!found) {
				logger.warn("getOrder: " + name + " specified in absolute-ordering was not found ...");
			}
		}

		int othersIndex = absoluteOrder.indexOf(OTHERS);

		if (othersIndex != -1) {

			for (FacesConfigDescriptor config : configList) {
				orderedList.add(othersIndex, config);
			}
		}

		return orderedList;

	}

	public boolean isOrdered() {
		return ((routes[BEFORE].length != 0) || (routes[AFTER].length != 0));
	}

	public boolean isBefore(String name) {

		return (Arrays.binarySearch(routes[BEFORE], name) >= 0);
	}

	public boolean isAfter(String name) {

		return (Arrays.binarySearch(routes[AFTER], name) >= 0);
	}

	public String[][] getRoutes() {
		return routes;
	}

	public void setRoutes(String[][] routes) {
		this.routes = routes;
	}

	public boolean isAfterOthers() {

		boolean value = false;

		if (routes[AFTER] != null) {
			value = (Arrays.binarySearch(routes[AFTER], OTHERS) >= 0);
		}

		return value;
	}

	public boolean isBeforeOthers() {

		boolean value = false;

		if (routes[BEFORE] != null) {
			value = (Arrays.binarySearch(routes[BEFORE], OTHERS) >= 0);
		}

		return value;
	}

}
