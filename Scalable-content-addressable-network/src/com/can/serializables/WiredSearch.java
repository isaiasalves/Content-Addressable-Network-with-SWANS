package com.can.serializables;

import java.io.Serializable;

import com.can.nodes.Peer.CommandType;

import jist.swans.misc.Message;

public class WiredSearch implements Serializable, Message, Cloneable {

	private static final long serialVersionUID = 1L;

	private CommandType command;
	private String keyword;
	private PeerInfo sourceInfo;
	private RouteInformation routeInformation;
	private NeighbourInfo neighbourToRoute;

	public WiredSearch(CommandType command, String keyword, PeerInfo sourceInfo,NeighbourInfo neighbourToRoute, RouteInformation routeInformation){

		this.command = command;
		this.keyword = keyword;
		this.sourceInfo = sourceInfo;
		this.neighbourToRoute = neighbourToRoute;
		this.routeInformation = routeInformation;
	}

	public NeighbourInfo getNeighbourToRoute() {
		return neighbourToRoute;
	}

	public void setNeighbourToRoute(NeighbourInfo neighbourToRoute) {
		this.neighbourToRoute = neighbourToRoute;
	}

	public CommandType getCommand() {
		return command;
	}

	public void setCommand(CommandType command) {
		this.command = command;
	}

	public String getKeyword() {
		return keyword;
	}

	public void setKeyword(String keyword) {
		this.keyword = keyword;
	}

	public PeerInfo getSourceInfo() {
		return sourceInfo;
	}

	public void setSourceInfo(PeerInfo sourceInfo) {
		this.sourceInfo = sourceInfo;
	}

	public RouteInformation getRouteInformation() {
		return routeInformation;
	}

	public void setRouteInformation(RouteInformation routeInformation) {
		this.routeInformation = routeInformation;
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
