package com.can.serializables;

import java.io.Serializable;
import java.net.InetAddress;

import jist.swans.mac.MacAddress;
import jist.swans.misc.Message;

public class JoinUpdateNeighbours implements Serializable, Message, Cloneable  {

	private static final long serialVersionUID = 1L;

	private String activePeerHostname;
	private String newNodeHostname;
	private InetAddress activePeerIpAddress;
	private InetAddress newNodeIpAddress;
	private MacAddress activePeerMacAddress;
	private MacAddress newNodeMacAddress;
	private Zone activePeerUpdatedZone;
	private Zone newNodeZone;
	private NeighbourInfo neighbourToRoute;

	public JoinUpdateNeighbours(String activePeerHostname, String newNodeHostname, InetAddress activePeerIpAddress, InetAddress newNodeIpAddress, MacAddress activePeerMacAddress, MacAddress newNodeMacAddress ,Zone activePeerUpdatedZone, Zone newNodeZone, NeighbourInfo neighbourToRoute){

		this.activePeerHostname = activePeerHostname;
		this.newNodeHostname = newNodeHostname;
		this.activePeerIpAddress = activePeerIpAddress;
		this.newNodeIpAddress = newNodeIpAddress;
		this.activePeerMacAddress = activePeerMacAddress;
		this.newNodeMacAddress = newNodeMacAddress;
		this.activePeerUpdatedZone = activePeerUpdatedZone;
		this.newNodeZone = newNodeZone;
		this.neighbourToRoute = neighbourToRoute;
	}

	public NeighbourInfo getNeighbourToRoute() {
		return neighbourToRoute;
	}

	public String getActivePeerHostname() {
		return activePeerHostname;
	}

	public MacAddress getActivePeerMacAddress() {
		return activePeerMacAddress;
	}
	
	public String getNewNodeHostname() {
		return newNodeHostname;
	}
	
	public MacAddress getNewNodeMacAddress() {
		return newNodeMacAddress;
	}

	public InetAddress getActivePeerIpAddress() {
		return activePeerIpAddress;
	}

	public InetAddress getNewNodeIpAddress() {
		return newNodeIpAddress;
	}
	

	public Zone getActivePeerUpdatedZone() {
		return activePeerUpdatedZone;
	}

	public Zone getNewNodeZone() {
		return newNodeZone;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
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
