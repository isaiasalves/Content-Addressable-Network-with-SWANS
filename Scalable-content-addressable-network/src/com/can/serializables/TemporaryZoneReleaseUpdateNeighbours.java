package com.can.serializables;

import java.io.Serializable;
import java.net.InetAddress;

import jist.swans.mac.MacAddress;
import jist.swans.misc.Message;

public class TemporaryZoneReleaseUpdateNeighbours implements Serializable, Message, Cloneable{

	private static final long serialVersionUID = 1L;

	private String releasingHostname;
	private InetAddress releasingIpAddress;
	private MacAddress releasingMacAddress;
	private String newNodeHostname;
	private InetAddress newNodeIpAddress;
	private MacAddress newNodeMacAddress;
	private Zone releasedZone;
	private String hostnameToRoute;
	private InetAddress ipAddressToRoute;
	private MacAddress macAddressToRoute;

	public TemporaryZoneReleaseUpdateNeighbours(String releasingHostname, InetAddress releasingIpAddress, MacAddress releasingMacAddress,String newNodeHostname, InetAddress newNodeIpAddress, MacAddress newNodeMacAddress, Zone releasedZone, String hostnameToRoute, InetAddress ipAddressToRoute, MacAddress macAddressToRoute) {

		this.releasingHostname = releasingHostname;
		this.releasingIpAddress = releasingIpAddress;
		this.newNodeHostname = newNodeHostname;
		this.newNodeIpAddress = newNodeIpAddress;
		this.hostnameToRoute = hostnameToRoute;
		this.ipAddressToRoute = ipAddressToRoute;
		this.releasingMacAddress = releasingMacAddress;
		this.newNodeMacAddress = newNodeMacAddress;
		this.macAddressToRoute = macAddressToRoute;
		
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	public String getReleasingHostname() {
		return releasingHostname;
	}

	public InetAddress getReleasingIpAddress() {
		return releasingIpAddress;
	}
	
	public MacAddress getReleasingMacAddress(){
		return releasingMacAddress;
	}

	public String getNewNodeHostname() {
		return newNodeHostname;
	}

	public InetAddress getNewNodeIpAddress() {
		return newNodeIpAddress;
	}
	
	public MacAddress getNewNodeMacAddress() {
		return newNodeMacAddress;
	}

	public Zone getReleasedZone() {
		return releasedZone;
	}

	public String getHostnameToRoute() {
		return hostnameToRoute;
	}

	public InetAddress getIpAddressToRoute() {
		return ipAddressToRoute;
	}
	
	public MacAddress getMacAddressToRoute() {
		return macAddressToRoute;
	}

	@Override
	public int getSize() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void getBytes(byte[] msg, int offset) {
		// TODO Auto-generated method stub
		
	}


}
