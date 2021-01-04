package com.can.serializables;

import java.io.Serializable;
import java.net.InetAddress;

import jist.swans.mac.MacAddress;
import jist.swans.misc.Message;

public class JoinUpdateBootstrap implements Serializable, Message, Cloneable {

	private static final long serialVersionUID = 1L;

	private String newHostname;
	private InetAddress newIpAddress;
	private MacAddress newMacAddress;

	public JoinUpdateBootstrap(String hostname, InetAddress ipAddress, MacAddress macAddress){

		this.newHostname = hostname;
		this.newIpAddress = ipAddress;
		this.newMacAddress = macAddress;
	}

	public String getNewHostname(){

		return this.newHostname;
	}

	public InetAddress getNewIpAddress(){

		return this.newIpAddress;
	}
	
	public MacAddress getNewMacAddress() {
		return this.newMacAddress;
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
