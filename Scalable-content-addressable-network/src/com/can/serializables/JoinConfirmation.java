package com.can.serializables;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jist.swans.mac.MacAddress;
import jist.swans.misc.Message;

public class JoinConfirmation implements Serializable, Message, Cloneable{

	private static final long serialVersionUID = 1L;

	private Zone zone;
	private ConcurrentHashMap<String, NeighbourInfo> routingTable;
	private InetAddress sourceIpAddress;
	private String sourceHostName;
	private int numberOfSplits;
	private MacAddress sourceMacAddress;
	private HashSet<String> transferedFiles;

	public JoinConfirmation(Zone zone, ConcurrentHashMap<String, NeighbourInfo> routingTable, InetAddress sourceIpAddress, String sourceHostName, MacAddress sourceMacAddress ,int numberOfSplits, HashSet<String> transferedFiles){

		this.zone = zone;
		this.routingTable = routingTable;
		this.sourceIpAddress = sourceIpAddress;
		this.sourceHostName = sourceHostName;
		this.sourceMacAddress = sourceMacAddress;
		this.numberOfSplits = numberOfSplits;
		if(transferedFiles != null){
			this.transferedFiles = transferedFiles;
		}
	}

	public Zone getZone(){

		return this.zone;
	}

	public ConcurrentHashMap<String, NeighbourInfo> getRoutingTable(){

		return this.routingTable;
	}

	public InetAddress getSourceIpAddress(){

		return this.sourceIpAddress;
	}

	public String getSourceHostName(){

		return this.sourceHostName;
	}
	
	public MacAddress getSourceMaccAddres() {
		return this.sourceMacAddress;
	}

	public int getNumberOfSplits(){

		return this.numberOfSplits;
	}

	public HashSet<String> getTransferedFiles(){

		if(this.transferedFiles == null){
			return null;
		}
		else{
			return this.transferedFiles;
		}
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
