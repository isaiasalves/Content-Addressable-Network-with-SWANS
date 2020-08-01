package com.can.serializables;

import java.io.Serializable;
import java.net.InetAddress;

import jist.swans.mac.MacAddress;
import jist.swans.misc.Message;

public class LeaveUpdateNeighbours implements Serializable,  Message, Cloneable {

	private static final long serialVersionUID = 1L;

	private String leavingHostname;
	private InetAddress leavingIpAddress;
	private MacAddress leavingMacAddress;
	private InetAddress destinationIpAddress;
	private String destinationHostName;
	private MacAddress destinationMacAddress;
	private NeighbourInfo neighbourTakingOver;

	public LeaveUpdateNeighbours(String leavingHostname, InetAddress leavingIpAddress, MacAddress leavingMacAddress ,InetAddress destinationIpAddress,String destinationHostName, MacAddress destinationMacAddress, NeighbourInfo neighbourTakingOver){

		this.leavingHostname = leavingHostname;
		this.leavingIpAddress = leavingIpAddress;
		this.destinationIpAddress = destinationIpAddress;
		this.destinationHostName = destinationHostName;
		this.neighbourTakingOver = neighbourTakingOver;
	}

	public String getDestinationHostName() {
		return destinationHostName;
	}

	public String getLeavingHostname(){

		return this.leavingHostname;
	}

	public InetAddress getLeavingIpAddress(){

		return this.leavingIpAddress;
	}

	public NeighbourInfo getNeighbourTakingOver(){

		return this.neighbourTakingOver;
	}

	public InetAddress getDestinationIpAddress(){

		return this.destinationIpAddress;
	}
	
	public MacAddress getLeavingMacAddress() {
		return this.leavingMacAddress;
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
