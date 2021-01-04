package com.can.serializables;

import java.io.Serializable;
import java.net.InetAddress;

import jist.swans.mac.MacAddress;
import jist.swans.misc.Message;

public class TakeoverConfirmation implements Serializable, Message, Cloneable {

	private static final long serialVersionUID = 1L;

	private String destinationHostname;
	private InetAddress destinationIpAddress;
	private MacAddress destinationMacAddress;

	public TakeoverConfirmation(String destinationHostname, InetAddress destinationIpAddress, MacAddress destinationMacAddress) {

		this.destinationHostname = destinationHostname;
		this.destinationIpAddress = destinationIpAddress;
		this.destinationMacAddress = destinationMacAddress;
	}

	public String getDestinationHostname(){

		return this.destinationHostname;
	}

	public InetAddress getDestinationIpAddress(){

		return this.destinationIpAddress;
	}
	
	public MacAddress getDestinationMacAddress() {
		return this.destinationMacAddress;
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
