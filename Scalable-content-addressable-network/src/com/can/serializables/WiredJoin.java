package com.can.serializables;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jist.swans.mac.MacAddress;
import jist.swans.misc.Message;

public class WiredJoin implements Serializable, Message, Cloneable {

	private static final long serialVersionUID = 1L;

	/** Type of a Route Request option. */
	public static final byte OPT_JOIN =           1;
	/** Type of a Route Request option. */
	public static final byte OPT_SEARCH =         2;
	/** Type of a Route Request option. */
	public static final byte OPT_LEAVE =          3;
	/** Type of a Route Request option. */
	public static final byte OPT_VIEW =           4;
	
	private String sourceHostName;
	private InetAddress sourceIpAddress;
	private MacAddress sourceMacAddress;
	private Coordinate randomCoordinate;
	private String hostnameToRoute;
	private InetAddress ipAddressToRoute;
	private MacAddress macAddressToRoute;
	private Map<MacAddress, InetAddress> activePeersInfo;
	private ConcurrentHashMap<String , NeighbourInfo> routingTable;
	private int numberOfHops;
	private RouteInformation routeInformation;
	private ArrayList<MacAddress> nodesVisited;


	public WiredJoin(String sourceHostname, InetAddress sourceIpAddress, MacAddress sourceMacAddress, String hostnameToRoute, InetAddress ipAddressToRoute, MacAddress macAddressToRoute, RouteInformation routeInformation){

		this.sourceHostName = sourceHostname;
		this.sourceIpAddress = sourceIpAddress;
		this.sourceMacAddress = sourceMacAddress;
		this.hostnameToRoute = hostnameToRoute;
		this.ipAddressToRoute = ipAddressToRoute;
		this.numberOfHops = 0;
		this.routeInformation = routeInformation;
		this.macAddressToRoute = macAddressToRoute;
		
	}


	public Coordinate getRandomCoordinate() {
		return randomCoordinate;
	}
	
	public ArrayList<MacAddress> getNodesVisiteds(){
		return this.nodesVisited;
	}

	public void setNodesVisiteds(MacAddress nodeIP) {
		this.nodesVisited.add(nodeIP);
	}

	public void setRandomCoordinate(Coordinate randomCoordinate) {
		this.randomCoordinate = randomCoordinate;
	}


	public String getSourceHostname() {
		return this.sourceHostName;
	}


	public void setSourceHostname(String hostname) {
		this.sourceHostName = hostname;
	}


	public InetAddress getSourceIpAddress() {
		return sourceIpAddress;
	}

	public MacAddress getSourceMacAddress() {
		return sourceMacAddress;
	}

	public void setSourceIpAddress(InetAddress ipAddress) {
		this.sourceIpAddress = ipAddress;
	}


	public Map<MacAddress, InetAddress> getActivePeersInfo() {
		return activePeersInfo;
	}


	public void setActivePeersInfo(Map<MacAddress, InetAddress> activePeersInfo) {
		this.activePeersInfo = activePeersInfo;
	}


	public ConcurrentHashMap<String, NeighbourInfo> getRoutingTable() {
		return routingTable;
	}


	public void setRoutingTable(
			ConcurrentHashMap<String, NeighbourInfo> routingTable) {
		this.routingTable = routingTable;
	}


	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	public void setNumberofHops(int n){

		this.numberOfHops = n;
	}

	public void setHostnameToRoute(String hostnameToRoute){

		this.hostnameToRoute = hostnameToRoute;
	}
	
	public void setMacAddressToRoute(MacAddress macAddress)
	{
		this.macAddressToRoute = macAddress;
	}

	public void setIpAddressToRoute(InetAddress ipAddressToRoute){
		this.ipAddressToRoute = ipAddressToRoute;
	}

	public int getNumberOfHops(){

		return this.numberOfHops;
	}

	public String getHostnameToRoute(){

		return this.hostnameToRoute;
	}

	public InetAddress getIpAddressToRoute(){

		return this.ipAddressToRoute;
	}
	
	public MacAddress getMacAddressToRoute() {
		return this.macAddressToRoute;
	}

	public void setRouteInformation(MacAddress macAddress, InetAddress ipAddress){
		this.routeInformation.addPeerToRoute(macAddress, ipAddress);
	}

	public RouteInformation getRouteInformation(){
		return this.routeInformation;
	}

	@Override
	public String toString() {

		StringBuilder builder = new StringBuilder("");
		builder.append("------------------------------------------------------------------\n");
		builder.append("Source hostname : "+this.sourceHostName+"\n");
		builder.append("Source ipAddress : "+this.sourceIpAddress+"\n");
		builder.append("hostname to route : "+this.hostnameToRoute+"\n");
		builder.append("ipAddress to route : "+this.ipAddressToRoute);
		builder.append("------------------------------------------------------------------\n");

		return builder.toString();
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
