package com.can.serializables;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;

import jist.swans.mac.MacAddress;

public class RouteInformation implements Serializable {

	private static final long serialVersionUID = 1L;

	private Map<MacAddress, InetAddress> routeMap = new LinkedHashMap<MacAddress, InetAddress>();

	public void addPeerToRoute(MacAddress identifier, InetAddress ipAddress){

		this.routeMap.put(identifier, ipAddress);
	}

	public Map<MacAddress, InetAddress> getRoute(){
		return this.routeMap;
	}

	@Override
	public String toString() {

		StringBuilder builder = new StringBuilder("");
		for(MacAddress hostname : this.routeMap.keySet()){
			builder.append(hostname+" -> ");
		}

		builder.setLength(builder.length()-4);
		return builder.toString();
	}

}
