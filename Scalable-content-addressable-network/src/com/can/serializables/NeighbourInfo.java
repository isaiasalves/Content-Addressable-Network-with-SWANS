package com.can.serializables;

import java.io.Serializable;
import java.net.InetAddress;

import jist.swans.mac.MacAddress;

public class NeighbourInfo implements Serializable{

	private static final long serialVersionUID = 1L;

	private Zone zone, tempZone;
	private InetAddress ipAddress;
	private MacAddress macAddress;
	private String hostname;
	private int portNumber;

	public NeighbourInfo(Zone zone, InetAddress ipAddress, MacAddress macAddress, int portNumber, String hostname) {

		if(zone != null && ipAddress != null){
			this.zone = zone;
			this.ipAddress = ipAddress;
			this.portNumber = portNumber;
			this.hostname = hostname;
			this.macAddress = macAddress;
		}
	}

	public Zone getZone() {
		return zone;
	}
	public void setZone(Zone zone) {
		this.zone = zone;
	}
	public Zone getTempZone() {
		return tempZone;
	}

	public void setTempZone(Zone tempZone) {
		this.tempZone = tempZone;
	}
	
	public void setMacAddress(MacAddress macAddress) {
		this.macAddress = macAddress;
	}

	
	public int getPortNumber() {
		return portNumber;
	}
	
	public MacAddress getMacAddress() {
		return macAddress;
	}

	public void setPortNumber(int portNumber) {
		this.portNumber = portNumber;
	}

	public InetAddress getIpAddress() {
		return ipAddress;
	}
	public void setIpAddress(InetAddress ipAddress) {
		this.ipAddress = ipAddress;
	}

	public String getHostname(){

		return this.hostname;
	}

	public boolean hasContents(){

		if(this.zone != null && this.ipAddress != null){

			return true;
		}
		return false;
	}

	@Override
	public String toString() {

		StringBuilder builder = new StringBuilder("");
		builder.append("hostname : "+this.getHostname()+"\n");
		builder.append("ipAddress : "+this.getIpAddress().getHostAddress()+"\n");
		builder.append("zone : "+this.getZone()+"\n");
		return builder.toString();

	}

	@Override
	public boolean equals(Object neighbour) {

		if(neighbour instanceof NeighbourInfo){
			NeighbourInfo n = (NeighbourInfo)neighbour;
			if(this.hostname.equals(n.hostname) && this.ipAddress.equals(n.ipAddress)){

				return true;
			}
			else{
				return false;
			}
		}
		else{
			return false;
		}
	}
}
