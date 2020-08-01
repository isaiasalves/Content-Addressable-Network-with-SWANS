package com.can.serializables;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jist.swans.mac.MacAddress;
import jist.swans.misc.Message;

public class WiredZoneTransfer implements Serializable, Message, Cloneable {

	private static final long serialVersionUID = 1L;

	private Zone zoneToTakeover;
	private NeighbourInfo neighbourToRoute;
	private boolean isAlongX, isAlongY, isCompleteTakeOver;
	private ConcurrentHashMap<String, NeighbourInfo> leavingNodeRoutingTable;
	private String sourceHostname;
	private InetAddress sourceIpAddress;
	private MacAddress sourceMacAddress;
	private HashSet<String> filesToBeTransfered;

	public WiredZoneTransfer(Zone zoneToTakeover, HashSet<String> filesToBeTransfered, NeighbourInfo neighbourToRoute, boolean alongX, boolean alongY,
			boolean isCompleteTakeOver, ConcurrentHashMap<String, NeighbourInfo> leavingNodeRoutingTable, String sourceHostname, InetAddress sourceIpAddress, MacAddress sourceMacAddress){

		this.zoneToTakeover = zoneToTakeover;
		this.neighbourToRoute = neighbourToRoute;
		this.isAlongX = alongX;
		this.isAlongY = alongY;
		this.isCompleteTakeOver = isCompleteTakeOver;
		this.leavingNodeRoutingTable = leavingNodeRoutingTable;
		this.filesToBeTransfered = filesToBeTransfered;
		this.sourceHostname = sourceHostname;
		this.sourceIpAddress = sourceIpAddress;
		this.sourceMacAddress = sourceMacAddress;
	}

	public ConcurrentHashMap<String, NeighbourInfo> getLeavingNodeRoutingTable() {
		return leavingNodeRoutingTable;
	}

	public String getSourceHostname() {
		return sourceHostname;
	}

	public InetAddress getSourceIpAddress() {
		return sourceIpAddress;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	public Zone getZoneToTakeover() {
		return zoneToTakeover;
	}

	public NeighbourInfo getNeighbourToRoute() {
		return neighbourToRoute;
	}

	public HashSet<String> getFilesToBeTransfered(){

		return this.filesToBeTransfered;
	}

	public boolean isAlongX(){

		return this.isAlongX;
	}

	public boolean isAlongY(){

		return this.isAlongY;
	}

	public boolean isCompleteTakeOver(){

		return this.isCompleteTakeOver;
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
