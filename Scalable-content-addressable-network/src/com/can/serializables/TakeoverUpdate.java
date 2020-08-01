package com.can.serializables;

import java.io.Serializable;
import java.net.InetAddress;

import jist.swans.mac.MacAddress;
import jist.swans.misc.Message;

public class TakeoverUpdate implements Serializable, Message, Cloneable {

	private static final long serialVersionUID = 1L;

	private String updatedHostname;
	private InetAddress updatedIpAddress;
	private MacAddress updatedMacAddress;
	private String destinationHostname;
	private InetAddress destinationIpAddress;
	private MacAddress destinationdMacAddress;
	private Zone updatedZone;
	private boolean isCompleteTakeover;

	public TakeoverUpdate(String updatedNodeHostname, InetAddress updateNodeIpAddress, MacAddress updatedMacAddress,String destinationHostname,
			Zone updatedZone, boolean isCompleteTakeover, InetAddress destinationIpAddress, MacAddress destinationdMacAddress) {

		this.updatedHostname = updatedNodeHostname;
		this.updatedIpAddress = updateNodeIpAddress;
		this.destinationHostname = destinationHostname;
		this.updatedZone = updatedZone;
		this.isCompleteTakeover = isCompleteTakeover;
		this.updatedMacAddress = updatedMacAddress;
		this.destinationIpAddress = destinationIpAddress;
		this.destinationdMacAddress = destinationdMacAddress;
	}

	public String getUpdatedHostname() {
		return this.updatedHostname;
	}

	public InetAddress getUpdatedIpAddress() {
		return this.updatedIpAddress;
	}

	public Zone getUpdatedZone() {
		return this.updatedZone;
	}
	
	public MacAddress getUpdatedMacAddress() {
		return this.updatedMacAddress;
	}

	public String getDestinationHostname() {
		return this.destinationHostname;
	}

	public InetAddress getDestinationIpAddress() {
		return this.destinationIpAddress;
	}
	
	public MacAddress getDestinationMacAddress() {
		return this.destinationdMacAddress;
	}
	
	public boolean isCompleteTakeover(){

		return this.isCompleteTakeover;
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
