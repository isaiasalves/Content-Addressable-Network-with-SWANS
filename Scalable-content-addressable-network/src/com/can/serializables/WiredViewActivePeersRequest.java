package com.can.serializables;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jist.swans.mac.MacAddress;

public class WiredViewActivePeersRequest implements Serializable{

	private static final long serialVersionUID = 1L;

	private Map<MacAddress, InetAddress> activePeers = new ConcurrentHashMap<MacAddress, InetAddress>(100,0.75f,100);
	private String sourceHostname;
	private InetAddress sourceIpAddress;
	private MacAddress sourceMacAddress;

	public Map<MacAddress, InetAddress> getActivePeers(){

		return this.activePeers;
	}

	public void setActivePeers(Map<MacAddress, InetAddress> activePeers){

		this.activePeers = activePeers;
	}

	public static long getSerialVersionUID(){

		return serialVersionUID;
	}

	public void setSourceHostname(String sourceHostname){

		this.sourceHostname = sourceHostname;
	}

	public void setSourceIpAddress(InetAddress sourceIpAddress){

		this.sourceIpAddress = sourceIpAddress;
	}

	public String getSourceHostname(){

		return this.sourceHostname;
	}

	public InetAddress getSourceIpAddress(){

		return this.sourceIpAddress;
	}

}
