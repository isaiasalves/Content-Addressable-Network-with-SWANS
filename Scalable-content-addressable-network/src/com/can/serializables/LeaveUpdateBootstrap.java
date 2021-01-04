package com.can.serializables;

import java.io.Serializable;
import java.net.InetAddress;

import jist.swans.mac.MacAddress;
import jist.swans.misc.Message;

public class LeaveUpdateBootstrap implements Serializable, Message, Cloneable {
	private static final long serialVersionUID = 1L;

	private String hostname;
	private InetAddress ipAddress;
	private MacAddress macAddress;

	public LeaveUpdateBootstrap(String hostname, InetAddress ipAddress, MacAddress macAddress){

		this.hostname = hostname;
		this.ipAddress = ipAddress;
		this.macAddress = macAddress;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	public String getHostname() {
		return hostname;
	}

	public InetAddress getIpAddress() {
		return ipAddress;
	}
	
	public MacAddress getMacAddress() {
		return this.macAddress;
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
