package com.cabintech.fxcoremp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wrapper for a Map<String,Object> which provides safe ways to get
 * strings, lists, and submaps from the Map. The getXXX methods will
 * never return null, but will return empty strings, lists, or maps
 * if the given key does not exist or if the value is of the wrong type.
 * 
 * This can be useful when there is no important distinction between a
 * missing value and an empty value as it eliminates the need for null
 * checks by callers. Callers can safely assume that a value is always
 * returned, never null.
 * 
 * The get...byPath() methods allow getting data from submaps (and lists) by
 * specifying the path of keys (and array indexes). get...byPath2() allows
 * navigation by paths from a single delimited string (e.g. key1/key2/key3).
 * 
 * This is usually used as a wrapper around an existing map, but the
 * default ctor will create a HashMap and wrap it (allowing this class
 * to be used in Json deserialization).
 * @author Mark
 *
 * @param <String>
 * @param <Object>
 */

public class SafeMap implements Map<String,Object> {
	
	public static final String PATH_DELIM = "/";
	
	private Map<String,Object> map;
	
	/**
	 * If no map is supplied, a (non-concurrent) HashMap is created and wrapped.
	 */
	
	public SafeMap() {
		map = new HashMap<String,Object>();
	}
	
	/**
	 * Creates a new, empty SafeMap backed by a ConcurrentHashMap.
	 * @param concurrent
	 */
	public SafeMap(boolean concurrent) {
		map = concurrent ? new ConcurrentHashMap<String,Object>() : new HashMap<String,Object>();
	}
	
	/**
	 * Creates a new SafeMap backed by the given Map, which must have String type keys.
	 * @param map
	 */
	@SuppressWarnings({"unchecked" })
	public SafeMap(Map<String,? extends Object> map) {
		this.map = (Map<String,Object>)map;
	}	
	
	/**
	 * Creates and initializes the content of a new SafeMap. The argument values must
	 * be key-value pairs (there must be an even number of args). All even number args
	 * (0,2,4,..) must be of type String or they will be converted to Strings.
	 * @param initKeyValues
	 */
	public SafeMap(Object key1, Object value1, Object... initKeyValues) {
		map = new HashMap<String,Object>();
		map.put(key1.toString(),  value1);
		for (int i=0; i<initKeyValues.length; i=i+2) {
			put(initKeyValues[i].toString(), initKeyValues[i+1]);
		}
	}

	
	
	/**
	 * Recursive method to walk the hierarchy and return a string from the last element.
	 * If the full path to the last element does not exist, null is returned.
	 * @param curr
	 * @param pos
	 * @param path
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Object byPath(Object curr, int pos, java.lang.String...path) {
		// Prior level did not exist in the map
		if (curr == null) return null;
		
		boolean isIndex = path[pos].charAt(0)>='0' && path[pos].charAt(0)<='0';
		
		if (pos == path.length-1) {
			// This is the last element, return it's content to the caller
			if (isIndex) {
				return ((List<Object>)curr).get(Integer.parseInt(path[pos]));
			}
			return ((Map<String,Object>)curr).get(path[pos]);
		}
		
		// Move one level down the hierarchy either through a list index or map element
		// by recursive call
		if (isIndex) {
			return byPath(((List<Object>)curr).get(Integer.parseInt(path[pos])), pos+1, path);
		}
		return byPath(((Map<String,Object>)curr).get(path[pos]), pos+1, path);
	}
	
	/*
	 * TEST MAIN
	 */
	public static void main(String[] args) {
		try {
		SafeMap m = new SafeMap();
		System.out.println("result = '"+m.getStrByPath2("pa/pb")+"'");
		}
		catch (Throwable t) {
			t.printStackTrace(System.out);
		}
	}
	
	/*
	 * If the given object is a SafeMap it is returned as-is. If it is a Map then
	 * new SafeMap is returned which wraps it. If it is neither type, an empty
	 * SafeMap is returned.
	 */
	@SuppressWarnings("unchecked")
	private SafeMap makeSafe(Object obj) {
		
		if (obj instanceof SafeMap) {
			return (SafeMap)obj;
		}
		if (obj instanceof Map) {
			return new SafeMap((Map<String,Object>)obj);
		}
		return new SafeMap(Collections.EMPTY_MAP);
	}
	
	
	/**
	 * Returns a string from the map/array hierarchy by following the given
	 * path through the map/array levels. Each entry in the path is either
	 * the name of a map key, or a numeric list index. The result is the final
	 * element of the path's toString() method result.
	 * 
	 * if the full path does not exist, an empty string is returned.
	 * 
	 * String firstname = order.getStrByPath("customer","contacts","0","firstname")
	 * @param path
	 * @return
	 */
	public String getStrByPath(String...path) {
		Object obj = byPath(map, 0, path);
		if (obj == null) return "";
		return obj.toString();
	}
	
	/**
	 * Returns a string from the map/array hierarchy by following the given
	 * path through the map/array levels. The path is a "/" separated list of
	 * entry names. Each entry in the path is either
	 * the name of a map key, or a numeric list index. The result is the
	 * toString() result on the final element of the path.
	 * 
	 * If the full path does not exist, an empty string is returned.
	 * 
	 * This cannot be used if a key name contains the "/" character. In that
	 * case the getStrByPath(...) must be used.
	 * 
	 * This is a more readable API than getStrByPath(...) when the path is
	 * a constant string.
	 * 
	 * String firstname = order.getStrByPath2("customer/contacts/0/firstname")
	 * @param path
	 * @return
	 */
	public java.lang.String getStrByPath2(String path) {
		String[] parts = path.split(PATH_DELIM);
		return getStrByPath(parts);
	}
	
	/**
	 * Returns a map by navigation from this map through a set of keys
	 * provided as individual strings. Each string represents either a string
	 * key, or if of the form "[nnnn]" is represents an index in a list. For
	 * example, getMapByPathList("car", "suspension", "tires", "0", "brake-type")
	 * @param path
	 * @return
	 */
	public SafeMap getMapByPathList(String...path) {
		return makeSafe(byPath(map, 0, path));
	}
	
	public SafeMap getMapByDelimPath(String path) {
		String[] parts = path.split(PATH_DELIM);
		return makeSafe(byPath(map, 0, parts));
	}	
	
	/**
	 * Returns a non-null string for the given key. If the value is not a string
	 * its toString() method result is returned. If the key does not exist or
	 * its value is null then an empty string is returned.
	 * @param key
	 * @return
	 */
	public java.lang.String getStr(String key) {
		return getStr(key, "");
	}

	/**
	 * Returns a non-null string for the given key. If the value is not a string
	 * its toString() method result is returned. If the key does not exist or
	 * its value is null then the supplied default string is returned.
	 * 
	 * @param key
	 * @param def
	 * @return
	 */
	public java.lang.String getStr(String key, String def) {
		Object v = map.get(key);
		if (v == null) return def;
		return v.toString();
	}	
	
	/**
	 * Returns a list of generic <String,Object> maps - a representation
	 * of a JSON array that contains a list of JSON name/value objects.
	 * If the given key does not exist an empty map is returned. 
	 * 
	 * If the value of the key is a list it is assumed to be of
	 * type <String,Object> but this is not verified.
	 * 
	 * Use the getListOfSafeMaps() method to get the list as a set
	 * of SafeMap objects.
	 * @param key
	 * @return
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public List<Map<String,Object>> getListOfMaps(String key) {
		Object v = map.get(key);
		if (v == null) {
			// If the key did not exist, create empty list, add to the map, and return it.
			v = new ArrayList<Map<String,Object>>();
			map.put(key,  v);
		}
		
		if (v instanceof List) {
			return (List)v;
		}
		
		// A value existed in the map for this key, but it is
		// not a List. Return an empty, unmodifiable list. We do
		// not replace the existing value in the map.
		return Collections.emptyList();
		
	}
	
	/**
	 * Returns a List of Maps. If the maps are not of type
	 * SafeMap, they are wrapped in new SafeMap objects
	 * before the list is returned.
	 * @param key
	 * @return
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public List<SafeMap> getListOfSafeMaps(String key) {
		Object v = map.get(key);
		
		if (v == null) {
			v = new ArrayList<SafeMap>();
			map.put(key, v);
		}
		
		if (v instanceof List) {
			List itemList = (List)v;
			for (int i=0; i<itemList.size(); i++) {
				if (!(itemList.get(i) instanceof SafeMap)) {
					itemList.set(i,  new SafeMap((Map<String,Object>)(itemList.get(i))));
				}
			}
			return itemList;
		}
		
		// The existing value is not a List, leave it there and
		// return an empty, unmodifiable list.
		return Collections.emptyList();
	}	
	/**
	 * Returns a list of Strings for the given key. If the key does
	 * not exist or is not a list, an empty list is returned. If the
	 * key does exist and its value is a list, it is assumed to be
	 * a list of <String> (the content of the list is not verified).
	 * @param key
	 * @return
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public List<String> getListOfStrings(String key) {
		Object v = map.get(key);
		if (v == null) {
			v = new ArrayList<String>();
			map.put(key, v);
		}
		
		if (v instanceof List) {
			return (List)v;
		}
		
		// Value was not a List, leave it in the map and return
		// an empty, unmodifiable list
		return Collections.emptyList();
	}
	
	/**
	 * Returns the map from the given key. If the map is already
	 * a SafeMap it is returned as-is, otherwise it is wrapped
	 * in a new SafeMap. If the key does not exist or the value is
	 * not a map, an empty map is returned.
	 * @param key
	 * @return
	 */
	public SafeMap getMap(String key) {
		return makeSafe(map.get(key));
	}
	
	/**v7.15
	 * Returns an integer for the given key. If the value does not exist
	 * or is not of type Integer then zero is returned.
	 * If the value is a string an attempt is made to convert to double,
	 * if that fails, the 0 is returned.
	 * @param key
	 * @return
	 */
	public int getInt(String key) {
		Object v = map.get(key);
		if (v == null) return 0;
		if (v instanceof Integer) return (int)v;
		if (v instanceof String) try {
			return Integer.parseInt(v.toString());
		}
		catch (Throwable ignore) {}
		return 0;
	}
	
	/**v11.00
	 * Returns a long for the given key. If the value does not exist
	 * or is not of type Long or Integer then zero is returned.
	 * If the value is a string an attempt is made to convert to long,
	 * if that fails, the 0 is returned.
	 * @param key
	 * @return
	 */
	public long getLong(String key) {
		Object v = map.get(key);
		if (v == null) return 0;
		//if (v instanceof Integer || v instanceof Long) return (long)v; // Cannot trust unboxing to avoid class cast
		if (v instanceof Integer || v instanceof Long) return ((Number)v).longValue();
		if (v instanceof String) try {
			return Long.parseLong(v.toString());
		}
		catch (Throwable ignore) {}
		return 0;
	}	
	
	/**v8.41
	 * Returns a boolean for the given key. If the value does not exist
	 * or is not of type Boolean, FALSE is returned. Note this does not
	 * attempt to interpret string values (e.g 'true/false' or 'T/F, etc).
	 * @param key
	 * @return
	 */
	public boolean getBool(String key) {
		Object v = map.get(key);
		if (v == null) return false;
		if (v instanceof Boolean) return (boolean)v;
		return false;
	}
	
	
	/**v8.10
	 * Returns a double for the given key. If the value does not exist
	 * or is not of type Integer, Double or Float then zero is returned.
	 * If the value is a string an attempt is made to convert to double,
	 * if that fails, the 0.0 is returned.
	 * @param key
	 * @return
	 */
	public double getDouble(String key) {
		Object v = map.get(key);
		if (v == null) return 0.0;
		if (v instanceof Integer) return (int)v;
		if (v instanceof Float) return (float)v;
		if (v instanceof Double) return (double)v;
		if (v instanceof String) try {
			return Double.parseDouble(v.toString());
		}
		catch (Throwable ignore) {}
		return 0.0;
	}


	@Override
	public void clear() {
		map.clear();
	}

	@Override
	public boolean containsKey(java.lang.Object key) {
		return map.containsKey(key);
	}

	@Override
	public boolean containsValue(java.lang.Object value) {
		return map.containsValue(value);
	}

	@Override
	public Set<Map.Entry<String,Object>> entrySet() {
		return map.entrySet();
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Set keySet() {
		return map.keySet();
	}


	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void putAll(Map m) {
		map.putAll(m);
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Collection values() {
		return map.values();
	}

	@Override
	public Object get(java.lang.Object key) {
		return map.get(key);
	}

	@Override
	public Object put(String key, Object value) {
		return map.put(key, value);
	}

	@Override
	public Object remove(java.lang.Object key) {
		return map.remove(key);
	}
	
	@Override
	public String toString() {
		return map.toString();
	}
	
//	public String toJson() throws JsonProcessingException {
//		// Run the Jackson mapper
//		ObjectMapper m = new ObjectMapper();
//		m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // Write dates in ISO-8601 (readable) format
//		return m.writeValueAsString(this);
//	}
//	
//	public static SafeMap fromJson(String json) throws JsonParseException, JsonMappingException, IOException {
//		return new ObjectMapper().readValue(json, SafeMap.class);
//	}

}
