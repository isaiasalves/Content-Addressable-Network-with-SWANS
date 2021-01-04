package com.can.nodes;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

//import com.can.connections.RevisedSend;
import com.can.exceptions.ClosestNeighbourUnavailableException;
import com.can.nodes.Peer.CommandType;
import com.can.nodes.Peer.ViewCategory;
import com.can.serializables.Coordinate;
import com.can.serializables.JoinConfirmation;
import com.can.serializables.JoinUpdateBootstrap;
import com.can.serializables.JoinUpdateNeighbours;
import com.can.serializables.LeaveUpdateBootstrap;
import com.can.serializables.LeaveUpdateNeighbours;
import com.can.serializables.NeighbourInfo;
import com.can.serializables.PeerInfo;
import com.can.serializables.RouteInformation;
import com.can.serializables.TakeoverConfirmation;
import com.can.serializables.TakeoverUpdate;
import com.can.serializables.TemporaryZoneReleaseUpdateNeighbours;
import com.can.serializables.WiredFailure;
import com.can.serializables.WiredInsert;
import com.can.serializables.WiredJoin;
import com.can.serializables.WiredSearch;
import com.can.serializables.WiredSuccess;
import com.can.serializables.WiredView;
import com.can.serializables.WiredViewActivePeersRequest;
import com.can.serializables.WiredZoneTransfer;
import com.can.serializables.Zone;
import com.can.utilities.Utils;

import driver.CAN;
//import driver.CBR.ServerInterface;
import driver.threaded.worker;
import jist.runtime.JistAPI;
import jist.runtime.JistAPI.Continuation;
import jist.swans.Constants;
import jist.swans.app.AppJava;
import jist.swans.mac.MacAddress;
import jist.swans.misc.Message;
import jist.swans.net.NetAddress;
import jist.swans.net.NetInterface;
import jist.swans.net.NetMessage;
import jist.swans.route.RouteInterface;
import jist.swans.trans.TransInterface;

//import jist.swans.net.NetAddress;

public class Peer implements RouteInterface.Can {

	// HashMap to store the commands possible
	public static HashMap<CommandType, Boolean> possibleCommands = new HashMap<Peer.CommandType, Boolean>();

	// HashMap to store the input formats for the commands
	public static HashMap<CommandType, String> formats = new HashMap<Peer.CommandType, String>();

	// IP address of this node
	private InetAddress IPaddress = getIpAddress();

	// Identifier of this peer on the network
	private String hostname;

	// The zone that the peer belongs to.
	private Zone zone;

	// temporary zone that has been taken over
	private Zone tempZone;

	// specifying the boundaries of the coordinate space
	private static final int LOWER_BOUND_X = 0;
	private static final int LOWER_BOUND_Y = 0;
	private static final int UPPER_BOUND_X = 100;
	private static final int UPPER_BOUND_Y = 100;

	/*
	 * variable to hold the identifiers and the IP addresses of all the active peers
	 * in the can. This variable will contain values only in the case of a bootstrap
	 * node.
	 */
	private static Map<MacAddress, InetAddress> activePeers;

	// constant variable to hold the number of peers possible in the network
	private int peerLimit = 1;

	// List of all the files that are stored in the peer.
	// private List<String> fileNames = new ArrayList<String>();
	private HashSet<String> fileNames = new HashSet<String>();

	// List of all files stored at the temporary zone taken over
	private HashSet<String> tempFileNames = new HashSet<String>();

	// final variable to store the bootstrap hostname
	private static final String BOOTSTRAP_HOSTNAME = "0.0.0.1";

	private static final MacAddress BOOTSTRAP_MACADDRESS = new MacAddress(1);
	
	private static final int PORT = 3001;

	// ipAddress of Bootstrap node
	private static InetAddress bootstrapIp;

	// number of times split
	private static int numberOfSplits;

	/*
	 * HashTable to map id to IPaddress. The IPaddress in in turn mapped to a zone.
	 * This is done for all the neighbors only. Here we are implementing
	 * ConcurrentHashMap because HashTables are not used anymore.
	 */
	private ConcurrentHashMap<String, NeighbourInfo> routingTable = new ConcurrentHashMap<String, NeighbourInfo>(100,
			0.75f, 100);

	// temporary routing table to store the neighbour information of the temporary
	// zone taken over
	private ConcurrentHashMap<String, NeighbourInfo> tempRoutingTable = new ConcurrentHashMap<String, NeighbourInfo>(
			100, 0.75f, 100);

	// singleton instance
	public static Peer instance = null;
	
	TransInterface.TransUdpInterface udp;

	/** The IP address of this node. */
	private NetAddress localAddr;

	private MacAddress macAddress;

	private RouteInterface.Can self;

	/** The interface to the network layer. */
	private NetInterface netEntity;
	
	//private Message lastMessage;
	
	private ArrayList<Message> messagesReceived = new ArrayList<Message>();

	/** RevisedReceive Parameters */
	public volatile int viewsReturned = 1;
	private int totalViewsRequired;
	private volatile List<String> peerInformation = new ArrayList<String>();

	/** RevisedReceive Parameters */

	// creating private singleton constructor
	public Peer(NetAddress localAddr, MacAddress macAddress, TransInterface.TransUdpInterface udp) throws UnknownHostException {
		this.localAddr = localAddr;
		this.macAddress = macAddress;
		setBootstrapIp();
		setHostName(localAddr.toString());
		setIPaddress(localAddr.getIP());
		numberOfSplits = 0;
		bootstrapIp = InetAddress.getByName(BOOTSTRAP_HOSTNAME);

		this.udp = udp;  
				
		self = (RouteInterface.Can) JistAPI.proxy(this, RouteInterface.Can.class);
	}

	// Sobrecarga do Construtor
	public Peer() {

	}

	// method to return the singleton instance of Peer.
	public Peer getInstance() throws UnknownHostException {
		if (instance != null) {
			return instance;
		} else {
			instance = new Peer();
			return instance;
		}
	}
	
	public boolean compareMessages(Message msg) {
		return this.messagesReceived.contains(msg);
	}
	
	public void setInstance(Peer peer) {
		this.instance = peer;
	}

	private void setMessageReceived(Message msg) {
		// TODO Auto-generated method stub
		this.messagesReceived.add(msg);
	}

	
	// enum of commands
	public enum CommandType {

		INSERT, SEARCH, VIEW, UPDATE, JOIN, LEAVE, SUCCESS, FAILURE;
	}

	// enum of view categories
	public enum ViewCategory {

		SINGLE, MULTI;
	}

	public synchronized void insert(WiredInsert wiredInsert) {

		if (wiredInsert == null) {
			String file = "C:\\Testes\\ArchTest.txt";
			wiredInsert = new WiredInsert(CommandType.INSERT, file, null, null, new RouteInformation());
			// this.insert(wiredInsert);
		}

		Coordinate mappedCoordinate;
		String successMessage;

		/*
		 * if source is null then it means that the current peer is the source. Hence we
		 * create a new PeerInfo object and then set all the required information of the
		 * current peer. Else if source is not null then it will contain the information
		 * of the peer that initiated this message.
		 */
		if (wiredInsert.getSourceInfo() == null) {
			PeerInfo sourceInfo = new PeerInfo();
			sourceInfo.setHostName(this.getHostName());
			sourceInfo.setIpAddress(this.getIpAddress());
			sourceInfo.setMacAddress(this.macAddress);
			sourceInfo.setFileNames(this.getFileNames());
			sourceInfo.setNeighbours(this.getNeighbours());
			sourceInfo.setZone(this.getZone());

			wiredInsert.setSourceInfo(sourceInfo);

		}

		// adding current peer's identifier and ipAddress to the routeMap
		wiredInsert.getRouteInformation().addPeerToRoute(this.getMacAddress(), this.getIpAddress());

		/*
		 * use the hash function to map the keyword to a coordinate. Check if coordinate
		 * is present in the current peer's zone. If yes then perform the operation and
		 * return status. Else find the closest neighbour, add current peer's ipAddress
		 * to the routeMap, and forward the request.
		 */
		mappedCoordinate = Utils.mapKeyToCoordinate(wiredInsert.getKeyword());

		if (isDestination(mappedCoordinate, this.getZone())) {

			// adding the keyword (file name) to the list of filenames in the current Peer
			this.fileNames.add(wiredInsert.getKeyword());

			// creating the success message
			successMessage = "INSERT operation successful.\nInserted file : " + wiredInsert.getKeyword()
					+ ".\nPeer hostName : " + this.getHostName() + ".\nPeer ipAddress : " + this.getIpAddress();

			/*
			 * checking whether current peer is the source. If yes then we print the success
			 * of the insert operation to the console. (this happens only when success
			 * message comes back to the source) Else we send the success message back to
			 * the previous peer until it reaches the source.
			 */
			if (wiredInsert.getSourceInfo().getIpAddress().equals(this.getIpAddress())) {

				// creating a WiredSuccess object and routing it to the same machine (as this
				// machine is the source)
				WiredSuccess wiredSuccess = new WiredSuccess(CommandType.INSERT, wiredInsert.getSourceInfo(),
						wiredInsert.getSourceInfo(), successMessage, wiredInsert.getRouteInformation());
				//JistAPI.sleep(10000000);
				this.sendThreaded(wiredSuccess);
			} else {

				/*
				 * create PeerInfo object for the affected peer (current peer). create
				 * WiredSuccess object and send the success message to the source. spawn new
				 * thread to send the WiredSuccess object to the source.
				 */
				PeerInfo affectedPeer = new PeerInfo();
				affectedPeer.setHostName(this.getHostName());
				affectedPeer.setIpAddress(this.getIpAddress());
				affectedPeer.setNeighbours(this.getNeighbours());
				affectedPeer.setZone(this.getZone());
				affectedPeer.setFileNames(this.getFileNames());

				WiredSuccess wiredSuccess = new WiredSuccess(CommandType.INSERT, affectedPeer,
						wiredInsert.getSourceInfo(), successMessage, wiredInsert.getRouteInformation());

				this.sendThreaded(wiredSuccess);
			}
		} else if (this.tempZone != null && isDestination(mappedCoordinate, this.getTempZone())) {

			// adding the keyword (file name) to the list of filenames in the current Peer
			this.tempFileNames.add(wiredInsert.getKeyword());

			// creating the success message
			successMessage = "INSERT operation successful.\nInserted file : " + wiredInsert.getKeyword()
					+ ".\nPeer hostName : " + this.getHostName() + ".\nPeer ipAddress : " + this.getIpAddress();

			/*
			 * checking whether current peer is the source. If yes then we print the success
			 * of the insert operation to the console. (this happens only when success
			 * message comes back to the source) Else we send the success message back to
			 * the previous peer until it reaches the source.
			 */
			if (wiredInsert.getSourceInfo().getIpAddress().equals(this.getIpAddress())) {

				// setting the zone in the sourceInfo to the tempZone
				wiredInsert.getSourceInfo().setZone(this.tempZone);

				// creating a WiredSuccess object and routing it to the same machine (as this
				// machine is the source)
				WiredSuccess wiredSuccess = new WiredSuccess(CommandType.INSERT, wiredInsert.getSourceInfo(),
						wiredInsert.getSourceInfo(), successMessage, wiredInsert.getRouteInformation());
				this.sendThreaded(wiredSuccess);
			} else {

				/*
				 * create PeerInfo object for the affected peer (current peer). create
				 * WiredSuccess object and send the success message to the source. spawn new
				 * thread to send the WiredSuccess object to the source.
				 */
				PeerInfo affectedPeer = new PeerInfo();
				affectedPeer.setHostName(this.getHostName());
				affectedPeer.setIpAddress(this.getIpAddress());
				affectedPeer.setNeighbours(this.getNeighbours());
				affectedPeer.setZone(this.getTempZone());
				affectedPeer.setFileNames(this.getFileNames());

				WiredSuccess wiredSuccess = new WiredSuccess(CommandType.INSERT, affectedPeer,
						wiredInsert.getSourceInfo(), successMessage, wiredInsert.getRouteInformation());

				this.sendThreaded(wiredSuccess);
			}
		} else {
			NeighbourInfo neighbourToRoute;
			try {

				neighbourToRoute = findClosestNeighbour(mappedCoordinate, wiredInsert.getRouteInformation(), null);
				wiredInsert.setNeighbourToRoute(neighbourToRoute);
				wiredInsert.getRouteInformation().addPeerToRoute(this.getMacAddress(), this.getIpAddress());

				// routing insert command to neighbour
				this.sendThreaded(wiredInsert);
			} catch (ClosestNeighbourUnavailableException e) {
				WiredFailure wiredFailure = new WiredFailure(CommandType.INSERT, wiredInsert.getSourceInfo(),
						"Sorry! Neighbour unavailable");
				this.sendThreaded(wiredFailure);
			}

		}
	}

	public synchronized void search(WiredSearch wiredSearch) {

		if (wiredSearch == null) {
			String file = "C:\\Testes\\ArchTest.txt";
			wiredSearch = new WiredSearch(CommandType.SEARCH, file, null, null, new RouteInformation());
			// this.search(wiredSearch);
		}
		/*
		 * Map keyword to coordinate space. Check whether sourceInfo is null. If yes
		 * then the source is the current peer. Check whether current peer is the
		 * destination peer. If yes then, Check whether the current peer contains the
		 * keyword If yes then add current peer to the routeInformation and create
		 * WiredSuccess object and send it back to the source If not then create
		 * WiredFailure object and spawn new thread to send to the source of the
		 * request. If not then, find the closest neighbour to route the request to. add
		 * current peer to the routeInformation and then forward the WiredSearch object
		 * to the closest neighbour.
		 */

		String keyword = wiredSearch.getKeyword();
		Coordinate mappedCoordinate = Utils.mapKeyToCoordinate(keyword);

		if (wiredSearch.getSourceInfo() == null) {

			PeerInfo sourceInfo = new PeerInfo();
			sourceInfo.setHostName(this.getHostName());
			sourceInfo.setIpAddress(this.getIpAddress());
			sourceInfo.setMacAddress(this.macAddress);
			sourceInfo.setFileNames(this.getFileNames());
			sourceInfo.setNeighbours(this.getNeighbours());
			sourceInfo.setZone(this.zone);

			wiredSearch.setSourceInfo(sourceInfo);
		}

		if (isDestination(mappedCoordinate, this.getZone())) {

			if (this.getFileNames().size() > 0) {
				if (this.getFileNames().contains(keyword)) {

					PeerInfo affectedPeer = new PeerInfo();
					affectedPeer.setHostName(this.getHostName());
					affectedPeer.setIpAddress(this.getIpAddress());
					affectedPeer.setMacAddress(this.macAddress);
					affectedPeer.setNeighbours(this.getNeighbours());
					affectedPeer.setFileNames(this.getFileNames());
					affectedPeer.setZone(this.getZone());

					String successMessage = "Search successful";
					wiredSearch.getRouteInformation().addPeerToRoute(this.getMacAddress(), this.getIpAddress());
					WiredSuccess wiredSuccess = new WiredSuccess(CommandType.SEARCH, affectedPeer,
							wiredSearch.getSourceInfo(), successMessage, wiredSearch.getRouteInformation());

					/*
					 * if source is the current peer then we print the success on the console. Else
					 * we serialize the WiredSuccess object to the source peer.
					 */
					if (wiredSearch.getSourceInfo().getIpAddress().equals(this.getIpAddress())) {

						Utils.printToConsole(wiredSuccess.toString());
					} else {
						//JistAPI.sleep(10000000);
						this.sendThreaded(wiredSuccess);
					}
				}
			} else {

				String failureMessage = "Failure";
				WiredFailure wiredFailure = new WiredFailure(CommandType.SEARCH, wiredSearch.getSourceInfo(),
						failureMessage);
				//JistAPI.sleep(10000000);
				this.sendThreaded(wiredFailure);
			}
		} else if (this.tempZone != null && isDestination(mappedCoordinate, this.tempZone)) {

			if (this.tempFileNames.size() > 0) {

				PeerInfo affectedPeer = new PeerInfo();
				affectedPeer.setHostName(this.getHostName());
				affectedPeer.setIpAddress(this.getIpAddress());
				affectedPeer.setMacAddress(this.macAddress);
				affectedPeer.setNeighbours(this.getNeighbours());
				affectedPeer.setFileNames(this.tempFileNames);
				affectedPeer.setZone(this.tempZone);

				String successMessage = "Search successful";
				wiredSearch.getRouteInformation().addPeerToRoute(this.getMacAddress(), this.getIpAddress());
				WiredSuccess wiredSuccess = new WiredSuccess(CommandType.SEARCH, affectedPeer,
						wiredSearch.getSourceInfo(), successMessage, wiredSearch.getRouteInformation());
				//JistAPI.sleep(10000000);
				this.sendThreaded(wiredSuccess);
			} else {

				String failureMessage = "Failure";
				WiredFailure wiredFailure = new WiredFailure(CommandType.SEARCH, wiredSearch.getSourceInfo(),
						failureMessage);
				//JistAPI.sleep(10000000);
				this.sendThreaded(wiredFailure);
			}
		} else {

			NeighbourInfo neighbourToRoute;
			try {
				neighbourToRoute = findClosestNeighbour(mappedCoordinate, wiredSearch.getRouteInformation(), null);
				wiredSearch.getRouteInformation().addPeerToRoute(this.getMacAddress(), this.getIpAddress());
				wiredSearch.setNeighbourToRoute(neighbourToRoute);

				//JistAPI.sleep(10000000);
				this.sendThreaded(wiredSearch);
			} catch (Exception e) {

				String failureMessage = "Failure";
				WiredFailure wiredFailure = new WiredFailure(CommandType.SEARCH, wiredSearch.getSourceInfo(),
						failureMessage);

				//JistAPI.sleep(10000000);
				this.sendThreaded(wiredFailure);
			}
		}
	}

	/*
	 * function that is called by new node to join the network
	 */
	public synchronized void join(WiredJoin wiredJoin, MacAddress lastHop) {

		MacAddress eita = new MacAddress(1000);
		if (wiredJoin.getSourceMacAddress().equals(eita)) {
			System.out.println();
		}
		/*
		 * check if current peer is bootstrap and whether neighbourToRoute information
		 * is null. if yes then we select random ipAddresses of some active peers and
		 * add them to wiredJoin We also set boolean isSendBackToSource to indicate that
		 * the information needs to go back to the new node else forward the WiredJoin
		 * object to neighbourToRoute
		 */
		try {
			// System.out.print(wiredJoin.getNumberOfHops());
			/*
			 * executed when the WiredJoin is routed back from Bootstrap node to NewNode
			 * here numberOfHops = 0
			 */
			if (isBootstrap() && wiredJoin.getNumberOfHops() == 0) {

				/*
				 * check if number of active peers is less than 10 if not then send back a
				 * WiredFailure message indicating JOIN not possible
				 */
				// if (peerLimit < 10) {

				/*
				 * select some random ipAddress of active peers add them to wiredJoin set
				 * neighbourToRoute to newNode
				 * 
				 */
				int activePeers = 0;
				try {
					activePeers = this.activePeers.size();
				} catch (Exception e) {
					// TODO: handle exception
				}

				int numberOfActivePeers = activePeers;

				if (numberOfActivePeers == 1) {

					wiredJoin.setActivePeersInfo(this.activePeers);
				} else {

					Map<MacAddress, InetAddress> someActivePeers = new HashMap<MacAddress, InetAddress>();
					int i = 0;
					for (Map.Entry<MacAddress, InetAddress> entry : Peer.activePeers.entrySet()) {

						if (i < Math.ceil(numberOfActivePeers / 2)) {
							someActivePeers.put(entry.getKey(), entry.getValue());
						} else {
							break;
						}
						i++;
					}
					wiredJoin.setActivePeersInfo(someActivePeers);
				}
				

				wiredJoin.setNumberofHops(wiredJoin.getNumberOfHops() + 1);
				this.sendThreaded(wiredJoin);
				// } else {
				// PeerInfo sourceInfo = new PeerInfo();
				// sourceInfo.setHostName(wiredJoin.getSourceHostname());
				// sourceInfo.setIpAddress(wiredJoin.getSourceIpAddress());
				// String statusMessage = "Sorry! Peer limit of " + peerLimit + " has already
				// been reached.";
				// WiredFailure wiredFailure = new WiredFailure(CommandType.JOIN, sourceInfo,
				// statusMessage);
				// this.sendMessage(wiredFailure);
				// }
			}
			/*
			 * executed when the WiredJoin is route from NewNode to Peer in the network here
			 * numberOfHops = 1
			 */
			else if (!wiredJoin.getActivePeersInfo().isEmpty() && wiredJoin.getNumberOfHops() == 1) {

//				System.out.println("(B)-" + this.macAddress + " recebendo wiredJoin de "
//						+ wiredJoin.getSourceMacAddress() + " " + System.currentTimeMillis());
				/*
				 * select random point in space and add it to wiredJoin select a peer from
				 * active peers to route the join request set neighbourToRoute to peer selected
				 * set isSendBackToSource to false send wiredJoin to peer
				 */
				Coordinate randomPoint = computeRandomPointInSpace();
				Set<MacAddress> activePeersKeySet = wiredJoin.getActivePeersInfo().keySet();
				Object[] keys = activePeersKeySet.toArray();
				int randomInt = (int) ((Math.random() * 100) % keys.length);
				MacAddress randomKey = (MacAddress) keys[randomInt];
				InetAddress randomIpAddress = wiredJoin.getActivePeersInfo().get(randomKey);
				// System.out.print("first statement. ");
				wiredJoin.setRandomCoordinate(randomPoint);
				wiredJoin.setHostnameToRoute(randomKey.toString());
				wiredJoin.setMacAddressToRoute((MacAddress) randomKey);
				wiredJoin.setIpAddressToRoute(randomIpAddress);
				wiredJoin.setNumberofHops(wiredJoin.getNumberOfHops() + 1);
				wiredJoin.setRouteInformation(this.getMacAddress(), this.getIpAddress());

				// System.out.print("sirst statement. ");
				this.sendThreaded(wiredJoin);

			}
			/*
			 * executed when WiredJoin has come to an active Peer here numberOfHope >= 2
			 */
			else if (wiredJoin.getRandomCoordinate() != null && wiredJoin.getNumberOfHops() >= 2) {

				/*
				 * check whether random point present in current peer's zone. if yes then, split
				 * zone determine neighbours of new node and add it to the list initialize
				 * routing table of new node update neighbours list of current node update
				 * routing table update current peer's zone update neighbours of the change
				 * notify Bootstrap node and send ipAddress and hostName of the new node else
				 * increment numberOfHops by 1 find closest neighbour to random point and route
				 * the WiredJoin object to it
				 */
				if (isDestination(wiredJoin.getRandomCoordinate(), this.getZone())) {

					Zone[] zoneSplits = splitZone(this.getZone(), wiredJoin);
					Zone updatedPeerZone = zoneSplits[0];
					Zone newZoneOfNewNode = zoneSplits[1];

					ConcurrentHashMap<String, NeighbourInfo> updatedRoutingTable = new ConcurrentHashMap<String, NeighbourInfo>(
							100, 0.75f, 100);
					ConcurrentHashMap<String, NeighbourInfo> newRoutingTable = new ConcurrentHashMap<String, NeighbourInfo>(
							100, 0.75f, 100);
					// initializing updatedRoutingTable and newRoutingTable
					for (Map.Entry<String, NeighbourInfo> routingTableEntry : this.routingTable.entrySet()) {

						// checking whether neighbour of peer (now with updated zone)
						if (isNeighbour(updatedPeerZone, routingTableEntry.getValue().getZone())) {

							updatedRoutingTable.put(routingTableEntry.getKey(), routingTableEntry.getValue());
						}
						// checking whether neighbour of new node
						if (isNeighbour(newZoneOfNewNode, routingTableEntry.getValue().getZone())) {

							newRoutingTable.put(routingTableEntry.getKey(), routingTableEntry.getValue());
						}
					}

					
					
					/*
					 * need to find all the files that need to be stored in the new node that just
					 * joined. we loop over the current peer's filename and then check if the mapped
					 * coordinate lies in the new node's zone. if yes then we add the file to the
					 * list of files to be stored at the new node.
					 */
					HashSet<String> filesToBeTransfered = new HashSet<String>();
					Coordinate mappingForFile;
					for (String file : this.fileNames) {

						mappingForFile = Utils.mapKeyToCoordinate(file);
						if (isDestination(mappingForFile, newZoneOfNewNode)) {

							// adding file to the new list
							filesToBeTransfered.add(file);
						}
					}
					// removing all the files, that were transfered, from the current list of
					// filenames
					this.fileNames.removeAll(filesToBeTransfered);

					/*
					 * creating JoinConfirmation object initializing it with newZone and
					 * newRoutingTable sending it to the new node
					 */
					JoinConfirmation joinConfirmation = new JoinConfirmation(newZoneOfNewNode, newRoutingTable,
							wiredJoin.getSourceIpAddress(), wiredJoin.getSourceHostname(),
							wiredJoin.getSourceMacAddress(), Peer.numberOfSplits, filesToBeTransfered);
//
//					System.out.println("(D)-" + this.macAddress + " enviando joinConfirmation para "
//							+ wiredJoin.getSourceMacAddress() + " " + System.currentTimeMillis());

					//JistAPI.sleep(500);
					
					this.sendThreaded(joinConfirmation);
					
					JistAPI.sleep(100000000);
					
					this.sendThreaded(joinConfirmation);
					
					
					
					/*
					 * creating WiredJoinUpdate object initializing it with the update information
					 * sending it to all the neighbours of current peer (spawning thread for each
					 * neighbour)
					 */
					for (NeighbourInfo neighbour : getNeighbours()) {

						JoinUpdateNeighbours joinUpdateNeighbours = new JoinUpdateNeighbours(this.getHostName(),
								wiredJoin.getSourceHostname(), this.getIpAddress(), wiredJoin.getSourceIpAddress(),
								this.macAddress, wiredJoin.getSourceMacAddress(), updatedPeerZone, newZoneOfNewNode,
								neighbour);

//						System.out.println("XXXXXXXX- "+localAddr+ "Enviando joinUpdateNeighbours");
						this.sendThreaded(joinUpdateNeighbours);
					}

					/*
					 * creating JoinUpdateBootstrap object initializing it with the hostname and
					 * ipaddress of the new node sending it to the Bootstrap node to update it's
					 * list of active peers
					 */
					JoinUpdateBootstrap updateBootstrap = new JoinUpdateBootstrap(wiredJoin.getSourceHostname(),
							wiredJoin.getSourceIpAddress(), wiredJoin.getSourceMacAddress());

//					System.out.println("(C)-" + this.macAddress + " recebendo wiredJoin de "
//							+ wiredJoin.getSourceMacAddress() + " " + System.currentTimeMillis());

					this.sendThreaded(updateBootstrap);

					/*
					 * updating routing table for current Peer for each neighbour in the current
					 * routing table, check whether it is present in the updated routing table if
					 * yes then ignore else remove corresponding neighbour from the current routing
					 * table
					 */
					if (this.routingTable.size() != 0) {
						for (Map.Entry<String, NeighbourInfo> currentNeighbourEntry : this.routingTable.entrySet()) {

							if (!updatedRoutingTable.containsKey(currentNeighbourEntry.getKey())) {

								this.routingTable.remove(currentNeighbourEntry.getKey());
							}
						}
					}

					// adding new node to the current peer's routing table
					this.routingTable.put(wiredJoin.getSourceHostname(),
							new NeighbourInfo(newZoneOfNewNode, wiredJoin.getSourceIpAddress(),
									wiredJoin.getSourceMacAddress(), 49161, wiredJoin.getSourceHostname()));

					// adding peer to new node's routing table because it has now become a neighbour
					newRoutingTable.put(this.getHostName(), new NeighbourInfo(this.getZone(), this.getIpAddress(),
							this.getMacAddress(), 49161, this.getHostName()));

					// updating current peer's zone
					this.setZone(updatedPeerZone);

				
//					this.send(joinConfirmation);
				} else if (this.tempZone != null && isDestination(wiredJoin.getRandomCoordinate(), this.tempZone)) {

					/*
					 * need to update the neighbours of the tempZone to update their routing tables
					 * need to update the bootstrap of the joining of the new node need to transfer
					 * the tempRoutingTable to the new joining node need to transfer the tempFiles
					 * to the new joining node set tempZone to null setTempRoutingTable to null
					 */
					TemporaryZoneReleaseUpdateNeighbours zoneReleaseUpdateNeighbours;
					for (Map.Entry<String, NeighbourInfo> tempRoutingEntry : this.tempRoutingTable.entrySet()) {

						zoneReleaseUpdateNeighbours = new TemporaryZoneReleaseUpdateNeighbours(this.getHostName(),
								this.getIpAddress(), this.macAddress, wiredJoin.getSourceHostname(),
								wiredJoin.getSourceIpAddress(), wiredJoin.getSourceMacAddress(), this.getTempZone(),
								tempRoutingEntry.getValue().getHostname(), tempRoutingEntry.getValue().getIpAddress(), tempRoutingEntry.getValue().getMacAddress());
						this.sendThreaded(zoneReleaseUpdateNeighbours);
						try {
							// Thread.sleep(500);
							//JistAPI.sleep(500);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

					JoinUpdateBootstrap joinUpdateBootstrap = new JoinUpdateBootstrap(wiredJoin.getSourceHostname(),
							wiredJoin.getSourceIpAddress(), wiredJoin.getSourceMacAddress());
					this.sendThreaded(joinUpdateBootstrap);

					JoinConfirmation joinConfirmation = new JoinConfirmation(this.tempZone, this.tempRoutingTable,
							wiredJoin.getSourceIpAddress(), wiredJoin.getSourceHostname(),
							wiredJoin.getSourceMacAddress(), Peer.numberOfSplits - 1, this.tempFileNames);

//					System.out.println("(E)-" + this.macAddress + " recebendo wiredJoin de "
//							+ wiredJoin.getSourceMacAddress() + " " + System.currentTimeMillis());

					this.sendThreaded(joinConfirmation);

					this.tempZone = null;
					this.tempRoutingTable.clear();
					this.tempFileNames.clear();

				} else {

					wiredJoin.setNumberofHops(wiredJoin.getNumberOfHops() + 1);
					NeighbourInfo neighbourToRoute;
					try {
						neighbourToRoute = findClosestNeighbour(wiredJoin.getRandomCoordinate(),
								wiredJoin.getRouteInformation(), lastHop);
						if (neighbourToRoute != null) {

							wiredJoin.setHostnameToRoute(neighbourToRoute.getHostname());
							wiredJoin.setIpAddressToRoute(neighbourToRoute.getIpAddress());
							wiredJoin.setMacAddressToRoute(neighbourToRoute.getMacAddress());
							this.sendThreaded(wiredJoin);
						} else {

							PeerInfo sourceInfo = new PeerInfo();
							sourceInfo.setHostName(wiredJoin.getSourceHostname());
							sourceInfo.setIpAddress(wiredJoin.getSourceIpAddress());
							String failureMessage = "JOIN Failure";
							WiredFailure wiredFailure = new WiredFailure(CommandType.FAILURE, sourceInfo,
									failureMessage);

							this.sendThreaded(wiredFailure);
						}
					} catch (ClosestNeighbourUnavailableException e) {
						PeerInfo sourceInfo = new PeerInfo();
						sourceInfo.setHostName(wiredJoin.getSourceHostname());
						sourceInfo.setIpAddress(wiredJoin.getSourceIpAddress());
						String statusMessage = "JOIN Failure";
						WiredFailure wiredFailure = new WiredFailure(CommandType.JOIN, sourceInfo, statusMessage);
						this.sendThreaded(wiredFailure);
						e.printStackTrace();
					}
				}
			}
			/*
			 * executed when neither of the above 2 conditions satisfy
			 */
			else {

				/*
				 * create WiredFailure and send it back to the source
				 */
				PeerInfo sourceInfo = new PeerInfo();
				sourceInfo.setHostName(wiredJoin.getSourceHostname());
				sourceInfo.setIpAddress(wiredJoin.getSourceIpAddress());
				String failureMessage = "JOIN Failure";
				WiredFailure wiredFailure = new WiredFailure(CommandType.FAILURE, sourceInfo, failureMessage);

				this.sendThreaded(wiredFailure);
			}
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	/*
	 * updating routing table for new node that has joined this is done for all the
	 * neighbours of the node that split its zone
	 */
	public void updateRoutingTableForNewNode(JoinUpdateNeighbours joinUpdateNeighbours) {

		NeighbourInfo neighbourInfo;
		/*
		 * check whether newZone is neighbour of current peer if yes then add it's
		 * information to the routing table check whether oldPeer's updated zone is
		 * neighbour of current peer if not then remove it from the routing table
		 */
		// Zone newNodeZone;
		// try {
		// newNodeZone = joinUpdateNeighbours.getNewNodeZone();
		// } catch (Exception e) {
		// newNodeZone = null;
		// }
		//

//		while (this.zone == null) {
//			JistAPI.sleep(100000);
//			System.out.println("HERE AT " + this.localAddr + " this.zone = " + this.zone);
//			System.out.println("joinUpdateNeighbours " + joinUpdateNeighbours);
//		}

		if (this.zone == null) {
			//this.startNodes();
			return;
		}
		//
		// System.out.println("Here at: "+this.localAddr);
		// System.out.println("Zona atual: "+this.zone);
		// System.out.println("Get zone: "+joinUpdateNeighbours.getNewNodeZone());

		if (isNeighbour(this.zone, joinUpdateNeighbours.getNewNodeZone())) {
			// checking if current peer's zone is neighbour of new node's zone

			neighbourInfo = new NeighbourInfo(joinUpdateNeighbours.getNewNodeZone(),
					joinUpdateNeighbours.getNewNodeIpAddress(), joinUpdateNeighbours.getNewNodeMacAddress(), 49161,
					joinUpdateNeighbours.getNewNodeHostname());
			this.routingTable.put(joinUpdateNeighbours.getNewNodeHostname(), neighbourInfo);
		}

		if (isNeighbour(this.zone, joinUpdateNeighbours.getActivePeerUpdatedZone())) {
			// checking if current peer's zone is still neighbour of node that split its
			// zone

			neighbourInfo = new NeighbourInfo(joinUpdateNeighbours.getActivePeerUpdatedZone(),
					joinUpdateNeighbours.getActivePeerIpAddress(), joinUpdateNeighbours.getActivePeerMacAddress(),
					49161, joinUpdateNeighbours.getActivePeerHostname());

			this.routingTable.put(joinUpdateNeighbours.getActivePeerHostname(), neighbourInfo);
		} else if (this.tempZone != null && isNeighbour(this.tempZone, joinUpdateNeighbours.getNewNodeZone())) {

			neighbourInfo = new NeighbourInfo(joinUpdateNeighbours.getNewNodeZone(),
					joinUpdateNeighbours.getNewNodeIpAddress(), joinUpdateNeighbours.getNewNodeMacAddress(), 49161,
					Peer.getBootstrapHostname());

			this.tempRoutingTable.put(joinUpdateNeighbours.getNewNodeHostname(), neighbourInfo);
		} else
		// if
		// (this.routingTable.get(joinUpdateNeighbours.getActivePeerHostname()).getTempZone()
		// != null
		// && !isNeighbour(this.zone,
		// this.routingTable.get(joinUpdateNeighbours.getActivePeerHostname()).getTempZone()))
		{
			Zone tmpZone;
			try {
				NeighbourInfo nb = this.routingTable.get(joinUpdateNeighbours.getActivePeerHostname());
				tmpZone = nb.getTempZone();
			} catch (Exception e) {
				// TODO: handle exception
				tmpZone = null;

			}

			if (tmpZone != null && !isNeighbour(this.zone, tmpZone)) {
				this.routingTable.remove(joinUpdateNeighbours.getActivePeerHostname());
			}

		}
	}

	/*
	 * setting the temp zone information of the node that released the temp zone to
	 * null checking whether the released zone is a neighbour and if yes then adding
	 * the new node to the routing table this is done for all neighbours of the node
	 * that release a temporarily taken over zone to the new node
	 */
	public void updateTempZoneRelease(TemporaryZoneReleaseUpdateNeighbours temporaryZoneReleaseUpdateNeighbours) {

		this.routingTable.get(temporaryZoneReleaseUpdateNeighbours.getReleasingHostname()).setTempZone(null);

		if (isNeighbour(this.getZone(), temporaryZoneReleaseUpdateNeighbours.getReleasedZone())) {

			NeighbourInfo newNodeInfo = new NeighbourInfo(temporaryZoneReleaseUpdateNeighbours.getReleasedZone(),
					temporaryZoneReleaseUpdateNeighbours.getNewNodeIpAddress(),
					temporaryZoneReleaseUpdateNeighbours.getNewNodeMacAddress(), 49161, Peer.getBootstrapHostname());
			this.routingTable.put(temporaryZoneReleaseUpdateNeighbours.getNewNodeHostname(), newNodeInfo);
		} else if (this.tempZone != null
				&& isNeighbour(this.tempZone, temporaryZoneReleaseUpdateNeighbours.getReleasedZone())) {

			NeighbourInfo newNodeInfo = new NeighbourInfo(temporaryZoneReleaseUpdateNeighbours.getReleasedZone(),
					temporaryZoneReleaseUpdateNeighbours.getNewNodeIpAddress(),
					temporaryZoneReleaseUpdateNeighbours.getNewNodeMacAddress(), 49161, Peer.getBootstrapHostname());
			this.tempRoutingTable.put(temporaryZoneReleaseUpdateNeighbours.getNewNodeHostname(), newNodeInfo);
		}
	}

	/*
	 * updating active peers (this is executed only for the bootstrap node)
	 */
	public void updateActivePeers(JoinUpdateBootstrap joinUpdateBootstrap) {

		if (Peer.activePeers == null) {
			Peer.activePeers = new HashMap<MacAddress, InetAddress>();
		}
		Peer.activePeers.put(joinUpdateBootstrap.getNewMacAddress(), joinUpdateBootstrap.getNewIpAddress());
		self.setPeerLimit(getPeerLimit() + 1);
		// Peer.peerLimit ++;
	}

	/*
	 * initializing zone and routing table (this is executed only when a new node,
	 * having made a join request, gets a confirmation)
	 */
	public void initializeState(JoinConfirmation joinConfirmation) {

		this.setZone(joinConfirmation.getZone());
		this.tempZone = null;
		this.routingTable = joinConfirmation.getRoutingTable();
		this.tempRoutingTable.clear();
		Peer.numberOfSplits = joinConfirmation.getNumberOfSplits();
		HashSet<String> filesTransfered;
		if ((filesTransfered = joinConfirmation.getTransferedFiles()).size() > 0) {
			this.fileNames.addAll(filesTransfered);
		}
		this.tempFileNames.clear();

		/*
		 * notifying user of join success notifying ipAddress, hostname, zone and
		 * neighbours to the user
		 */
		Utils.printToConsole("----------------------------------------------------------------------");
		Utils.printToConsole("JOIN SUCCESSUL!");
		Utils.printToConsole("IP of New Peer : " + joinConfirmation.getSourceIpAddress());
		Utils.printToConsole("MAC of New Peer : " + joinConfirmation.getSourceMaccAddres());
		Utils.printToConsole("Hostname of New Peer : " + joinConfirmation.getSourceHostName());
		Utils.printToConsole("Zone of New Peer : " + joinConfirmation.getZone());
		Utils.printToConsole("----------------------------------------------------------------------");
	}

	/*
	 * function that is called by node when leaving the network this function is
	 * called from the main() function.
	 */
	public void leave() {

		boolean isCompleteTakeOver = false;
		int i;
		boolean alongX = false;
		boolean alongY = false;

		if (this.tempZone != null) {
			PeerInfo sourceInfo = new PeerInfo();
			sourceInfo.setHostName(this.hostname);
			sourceInfo.setIpAddress(this.IPaddress);
			sourceInfo.setMacAddress(this.macAddress);
			sourceInfo.setNeighbours(this.getNeighbours());
			sourceInfo.setZone(this.zone);
			sourceInfo.setFileNames(this.fileNames);
			String statusMessage = "Sorry! Cannot leave the network. Taking over a temporary zone.";
			WiredFailure leaveFailure = new WiredFailure(CommandType.LEAVE, sourceInfo, statusMessage);
			Utils.printToConsole(leaveFailure.toString());

			possibleCommands.put(CommandType.INSERT, true);
			possibleCommands.put(CommandType.SEARCH, true);
			possibleCommands.put(CommandType.VIEW, true);
			possibleCommands.put(CommandType.JOIN, false);
			possibleCommands.put(CommandType.LEAVE, true);
		} else {

			/*
			 * loop over neighbours (routing table) check whether neighbour can takeover
			 * zone. if yes then, update bootstrap node (here the bootstrap node will remove
			 * entry corresponding to leaving node from its list of active peers) handover
			 * zone to neighbour update all the current neighbours about the takeover
			 * (provide information about the neighbour that is going to takeover) stop
			 * checking further If no neighbour can takeover zone then, update bootstrap
			 * node (here the bootstrap node will entry corresponding to leaving node from
			 * its list of active peers) select neighbour with the smallest zone and send
			 * takeover message to it. update all the current neighbours about the takeover
			 * (provide information about the neighbour that is going to takeover) update
			 * current neighbours to change the their routing table
			 */
			for (Map.Entry<String, NeighbourInfo> routingTableEntry : this.routingTable.entrySet()) {

				if ((i = canTakeOver(routingTableEntry.getValue())) > 0) {

					isCompleteTakeOver = true;

					LeaveUpdateBootstrap leaveUpdateBootstrap = new LeaveUpdateBootstrap(this.getHostName(),
							this.getIpAddress(), this.getMacAddress());
					this.sendThreaded(leaveUpdateBootstrap);

					if (i == 1) {
						alongX = true;
					} else {
						// i=2
						alongY = true;
					}

					WiredZoneTransfer wiredZoneTransfer = new WiredZoneTransfer(this.getZone(), this.fileNames,
							routingTableEntry.getValue(), alongX, alongY, isCompleteTakeOver, this.routingTable,
							this.getHostName(), this.getIpAddress(), this.getMacAddress());
					this.sendThreaded(wiredZoneTransfer);

					/*
					 * updating all current neighbours to remove entry corresponding to this peer
					 * from their routing tables
					 */
					LeaveUpdateNeighbours leaveUpdateNeighbours;
					for (NeighbourInfo neighbour : getNeighbours()) {

						if (!(routingTableEntry.getKey().equals(neighbour.getHostname()))) {
							leaveUpdateNeighbours = new LeaveUpdateNeighbours(this.getHostName(), this.getIpAddress(),
									this.macAddress, neighbour.getIpAddress(), neighbour.getHostname(),
									neighbour.getMacAddress(), routingTableEntry.getValue());

							// Realizar a atualização da zone do Takeover node, a fim de impedir que seus
							// novos vizinhos mantenham suas routing tables contendo as dimensões erradas
							// da nova área do nó.
							Zone mergedZone = mergeZones(leaveUpdateNeighbours.getNeighbourTakingOver().getZone(),
									wiredZoneTransfer);
							// Atualizando a zona do TakingOver Node
							leaveUpdateNeighbours.getNeighbourTakingOver().setZone(mergedZone);

							this.sendThreaded(leaveUpdateNeighbours);
						}

						try {
							// Thread.sleep(500);
						} catch (Exception ie) {
							ie.printStackTrace();
						}
					}

					break;
				}
			}

			/*
			 * if no neighbour is possible to takeover the zone then, we need to find the
			 * neighbour with the smallest zone and then ask it temporarily takeover the
			 * zone
			 */
			if (!isCompleteTakeOver) {
				System.out.println(
						"isCompleteTakeOverisCompleteTakeOverisCompleteTakeOverisCompleteTakeOverisCompleteTakeOverisCompleteTakeOverisCompleteTakeOverisCompleteTakeOver");
				LeaveUpdateBootstrap leaveUpdateBootstrap = new LeaveUpdateBootstrap(this.getHostName(),
						this.getIpAddress(), this.getMacAddress());
				this.sendThreaded(leaveUpdateBootstrap);

				NeighbourInfo smallestZoneNeighbour = retrieveNeighbourWithSmallestZone();
				if (smallestZoneNeighbour == null) {
					System.out.println("ERRO Escolhendo a menor zona");
					PeerInfo sourceInfo = new PeerInfo();
					sourceInfo.setHostName(this.getHostName());
					sourceInfo.setIpAddress(this.getIpAddress());
					sourceInfo.setZone(this.getZone());
					WiredFailure wiredFailure = new WiredFailure(CommandType.FAILURE, sourceInfo,
							"No Neighbours exist for take over.");
					Utils.printErrorMessage(wiredFailure.toString());
				} else {
					WiredZoneTransfer wiredZoneTransfer = new WiredZoneTransfer(this.getZone(), this.fileNames,
							smallestZoneNeighbour, false, false, isCompleteTakeOver, this.routingTable,
							this.getHostName(), this.getIpAddress(), this.getMacAddress());
					this.sendThreaded(wiredZoneTransfer);

					/*
					 * updating all current neighbours to remove entry corresponding to this peer
					 * from their routing tables
					 */
					LeaveUpdateNeighbours leaveUpdateNeighbours;
					for (NeighbourInfo neighbour : getNeighbours()) {

						leaveUpdateNeighbours = new LeaveUpdateNeighbours(this.getHostName(), this.getIpAddress(),
								this.macAddress, neighbour.getIpAddress(), neighbour.getHostname(),
								neighbour.getMacAddress(), smallestZoneNeighbour);
						this.sendThreaded(leaveUpdateNeighbours);

						try {
							// Thread.sleep(500);
						} catch (Exception ie) {
							ie.printStackTrace();
						}
					}
				}
			}
		}

	}

	/*
	 * takeover zone when a node leaves the can
	 */
	public synchronized void takeover(WiredZoneTransfer wiredZoneTransfer) {
		System.out.println("WIREDZONETRANSFER antes: " + this);
		Zone mergedZone;
		/*
		 * if complete takeover need to merge the zones need to update all the
		 * neighbours about change of state of the current peer update routing table
		 * entry to include union of neighbours send a wiredSuccess message else need to
		 * add new zone into the temporary zone need to update all the neighbours about
		 * the change of state of the current peer update the routing table entry to
		 * include union of neighbours
		 *
		 */
		if (wiredZoneTransfer.isCompleteTakeOver()) {

			// merging the zones
			mergedZone = mergeZones(this.getZone(), wiredZoneTransfer);
			this.setZone(mergedZone);

			/*
			 * updating current set of files names by adding all the files that were
			 * transfered from the leaving node.
			 */
			this.fileNames.addAll(wiredZoneTransfer.getFilesToBeTransfered());
			for (Map.Entry<String, NeighbourInfo> leavingNodeRoutingEntry : wiredZoneTransfer
					.getLeavingNodeRoutingTable().entrySet()) {

				if (!(leavingNodeRoutingEntry.getKey().equals(this.getHostName()))) {

					if (leavingNodeRoutingEntry.getKey().equals(wiredZoneTransfer.getSourceHostname())) {
						this.routingTable.remove(leavingNodeRoutingEntry.getKey());
					} else {
						this.routingTable.put(leavingNodeRoutingEntry.getKey(), leavingNodeRoutingEntry.getValue());
					}
				}
			}

			this.routingTable.remove(wiredZoneTransfer.getSourceHostname());

			/*
			 * updating neighbours about the change of state of the current peer. we won't
			 * be updating the leaving node.
			 */
			for (NeighbourInfo neighbour : this.routingTable.values()) {

				TakeoverUpdate takeoverUpdate = new TakeoverUpdate(this.getHostName(), this.getIpAddress(),
						this.getMacAddress(), neighbour.getHostname(), this.getZone(), true, neighbour.getIpAddress(),
						neighbour.getMacAddress());
				this.sendThreaded(takeoverUpdate);
			}

			//JistAPI.sleep(1000000);
			TakeoverConfirmation takeoverConfirmation = new TakeoverConfirmation(wiredZoneTransfer.getSourceHostname(),
					wiredZoneTransfer.getSourceIpAddress(), wiredZoneTransfer.getSourceMacAddress());
			this.sendThreaded(takeoverConfirmation);
		} else {

			// adding new zone to temporary zone. Not merging because merge into one
			// complete zone is not possible.
			this.setTempZone(wiredZoneTransfer.getZoneToTakeover());

			// creating takeover update message and sending it to all the neighbours
			for (NeighbourInfo neighbour : getNeighbours()) {

				if (!(this.getHostName().equals(neighbour.getHostname()))) {

					TakeoverUpdate takeoverUpdate = new TakeoverUpdate(this.getHostName(), this.getIpAddress(),
							this.getMacAddress(), neighbour.getHostname(), this.getTempZone(), false,
							neighbour.getIpAddress(), neighbour.getMacAddress());
					this.sendThreaded(takeoverUpdate);
				}
			}

			// creating takeover update message and sending it to all the neighbours of the
			// temp zone
			for (NeighbourInfo neighbour : wiredZoneTransfer.getLeavingNodeRoutingTable().values()) {

				if (!isNeighbour(this.zone, neighbour.getZone())
						|| (neighbour.getTempZone() != null && !isNeighbour(this.zone, neighbour.getTempZone()))) {

					TakeoverUpdate takeoverUpdate = new TakeoverUpdate(this.getHostName(), this.getIpAddress(),
							this.getMacAddress(), neighbour.getHostname(), this.tempZone, false,
							neighbour.getIpAddress(), neighbour.getMacAddress());
					this.sendThreaded(takeoverUpdate);
				}
			}

			// updating adding information to the tempRoutingTable table entry to include
			// new neighbours
			this.tempRoutingTable.putAll(wiredZoneTransfer.getLeavingNodeRoutingTable());

			// adding all the files names transfered to tempFileNames
			this.tempFileNames.addAll(wiredZoneTransfer.getFilesToBeTransfered());

			TakeoverConfirmation takeoverConfirmation = new TakeoverConfirmation(wiredZoneTransfer.getSourceHostname(),
					wiredZoneTransfer.getSourceIpAddress(), wiredZoneTransfer.getSourceMacAddress());
			this.sendThreaded(takeoverConfirmation);
		}
		System.out.println("WIREDZONETRANSFER depois: " + this);
	}

	/*
	 * merging zones for complete takeover
	 */
	private Zone mergeZones(Zone currentZone, WiredZoneTransfer wiredZoneTransfer) {

		Zone mergedZone = null;
		double startX = currentZone.getStartX();
		double startY = currentZone.getStartY();
		double endX = currentZone.getEndX();
		double endY = currentZone.getEndY();
		/*
		 * if zones abut along x-axis change startY to smallest startY change endY to
		 * largest endY else if zones abut along y-axis change startX to smallest startX
		 * change endX to largest endX else create WiredFailure object and send it back
		 * to the leaving node indicating failure of takeover return null
		 */
		if (wiredZoneTransfer.isAlongX()) {

			if (currentZone.getStartY() < wiredZoneTransfer.getZoneToTakeover().getStartY()) {

				endY = wiredZoneTransfer.getZoneToTakeover().getEndY();
				mergedZone = new Zone(startX, startY, endX, endY);
				return mergedZone;
			} else {

				startY = wiredZoneTransfer.getZoneToTakeover().getStartY();
				mergedZone = new Zone(startX, startY, endX, endY);
				return mergedZone;
			}
		} else if (wiredZoneTransfer.isAlongY()) {

			if (currentZone.getStartX() < wiredZoneTransfer.getZoneToTakeover().getStartX()) {

				endX = wiredZoneTransfer.getZoneToTakeover().getEndX();
				mergedZone = new Zone(startX, startY, endX, endY);
				return mergedZone;
			} else {

				startX = wiredZoneTransfer.getZoneToTakeover().getStartX();
				mergedZone = new Zone(startX, startY, endX, endY);
				return mergedZone;
			}
		} else {

			PeerInfo sourceInfo = new PeerInfo();
			sourceInfo.setHostName(this.getHostName());
			sourceInfo.setIpAddress(this.getIpAddress());
			String statusMessage = "Takeover Failed.";
			WiredFailure wiredFailure = new WiredFailure(CommandType.FAILURE, sourceInfo, statusMessage);
			this.sendThreaded(wiredFailure);
			return null;
		}
	}

	/*
	 * function to remove entry pertaining to the leaving node from the list of
	 * active peers this is executed only for the bootstrap node
	 */
	public void removeActivePeerEntry(LeaveUpdateBootstrap leaveUpdateBootstrap) {
		System.out.println();
		try {
			if (isBootstrap() && this.activePeers.containsKey(leaveUpdateBootstrap.getMacAddress())) {
				System.out.println("LEAVEUPDATEBOOTSTRAP antes: " + this.activePeers);
				this.activePeers.remove(leaveUpdateBootstrap.getMacAddress());
				System.out.println("LEAVEUPDATEBOOTSTRAP depois: " + this.activePeers);
			} else {

				System.out.println("FALHA AO ATUALIZAR O BOOTSTARP");
				/*
				 * create WiredFailure object and send it back to the leaving node
				 */
				PeerInfo sourceInfo = new PeerInfo();
				sourceInfo.setHostName(leaveUpdateBootstrap.getHostname());
				sourceInfo.setIpAddress(leaveUpdateBootstrap.getIpAddress());
				String statusMessage = "Cannot leave the network.\n Please try again";
				WiredFailure wiredFailure = new WiredFailure(CommandType.FAILURE, sourceInfo, statusMessage);
				this.sendThreaded(wiredFailure);
			}
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	/*
	 * function to remove entry pertaining to the leaving node from the routing
	 * table function also adds neighbour taking over into the routingTable if not
	 * present. (If present that means it has already updated information regarding
	 * leave and takeover) this is executed only for the neighbours of the leaving
	 * node (the bootstrap node could happen to be a neighbour)
	 */
	public void removeNeighbourFromRoutingTable(LeaveUpdateNeighbours leaveUpdateNeighbours) {

		System.out.println("(W - ) ANTES removeNeighbourFromRoutingTable Aki em " + this);
		System.out.println("(W2- )   ANTES " + routingTable);

		if (this.routingTable.containsKey(leaveUpdateNeighbours.getLeavingHostname())) {
			this.routingTable.remove(leaveUpdateNeighbours.getLeavingHostname());
		}
		if (!this.routingTable.containsKey(leaveUpdateNeighbours.getNeighbourTakingOver().getHostname())) {
			this.routingTable.put(leaveUpdateNeighbours.getNeighbourTakingOver().getHostname(),
					leaveUpdateNeighbours.getNeighbourTakingOver());
		}

		// Se o takeover node já estava na routing table do nó atual, é necessário
		// atualizar sua zone, uma vez que agora ela comtempa uma área maior
		if (this.routingTable.containsKey(leaveUpdateNeighbours.getNeighbourTakingOver().getHostname())) {
			this.routingTable.remove(leaveUpdateNeighbours.getNeighbourTakingOver().getHostname());
			this.routingTable.put(leaveUpdateNeighbours.getNeighbourTakingOver().getHostname(),
					leaveUpdateNeighbours.getNeighbourTakingOver());
		}

		System.out.println("(W - ) DEPOIS removeNeighbourFromRoutingTable Aki em " + this);
		System.out.println("(W2- )   DEPOIS " + routingTable);
	}

	/*
	 * function to update change of state of neighbour (after taking over)
	 */
	public void updateNeighbourState(TakeoverUpdate takeoverUpdate) {

		if (this.routingTable.containsKey(takeoverUpdate.getUpdatedHostname())) {

			if (takeoverUpdate.isCompleteTakeover()) {

				this.routingTable.get(takeoverUpdate.getUpdatedHostname()).setZone(takeoverUpdate.getUpdatedZone());
			} else {

				this.routingTable.get(takeoverUpdate.getUpdatedHostname()).setTempZone(takeoverUpdate.getUpdatedZone());
			}
		} else if (isNeighbour(this.zone, takeoverUpdate.getUpdatedZone())) {
			// means that the node is the neighbour of the temporarily taken over zone
			if (!this.getHostName().equals(takeoverUpdate.getUpdatedHostname())) {
				this.routingTable.put(takeoverUpdate.getUpdatedHostname(),
						new NeighbourInfo(takeoverUpdate.getUpdatedZone(), takeoverUpdate.getUpdatedIpAddress(),
								takeoverUpdate.getUpdatedMacAddress(), 49161, Peer.BOOTSTRAP_HOSTNAME));
			}
		} else if ((this.tempZone != null && isNeighbour(this.tempZone, takeoverUpdate.getUpdatedZone()))) {

			this.tempRoutingTable.put(takeoverUpdate.getUpdatedHostname(),
					new NeighbourInfo(takeoverUpdate.getUpdatedZone(), takeoverUpdate.getUpdatedIpAddress(),
							takeoverUpdate.getUpdatedMacAddress(), 49161, Peer.BOOTSTRAP_HOSTNAME));
		} else {

			// create failure message and send it back to the leaving node
			String statusMessage = "Could not update routing table.\nHostname : " + this.getHostName()
					+ "\nIpAddress : " + this.getIpAddress();
			PeerInfo sourceInfo = new PeerInfo();
			sourceInfo.setHostName(takeoverUpdate.getUpdatedHostname());
			sourceInfo.setIpAddress(takeoverUpdate.getUpdatedIpAddress());
			WiredFailure wiredFailure = new WiredFailure(CommandType.FAILURE, sourceInfo, statusMessage);
			this.sendThreaded(wiredFailure);
		}

	}

	/*
	 * check whether node can takeover
	 */
	private int canTakeOver(NeighbourInfo neighbour) {

		Zone currentPeerZone = getZone();
		Zone zoneOfNeighbourThatWillTakeOver = neighbour.getZone();
		/*
		 * check whether the zones abut along one side. If they do then a takeover is
		 * possible first check whether they abut along x-axis if yes then return 1 then
		 * check whether they abut along y-axis if yes then return 2 if both are not
		 * possible then return 0
		 */

		if (currentPeerZone.getStartX() == zoneOfNeighbourThatWillTakeOver.getStartX()
				&& currentPeerZone.getEndX() == zoneOfNeighbourThatWillTakeOver.getEndX()) {

			return 1;
		} else if (currentPeerZone.getStartY() == zoneOfNeighbourThatWillTakeOver.getStartY()
				&& currentPeerZone.getEndY() == zoneOfNeighbourThatWillTakeOver.getEndY()) {

			return 2;
		}

		return 0;
	}

	/*
	 * function to perform de-initialization of information and exit of node from
	 * the network
	 */
	public void deinitializeState() {

		StringBuilder builder = new StringBuilder("");
		builder.append("------------------------------------------------------------------\n");
		builder.append("Leave Successful\n");
		builder.append("-----------------\n");
		builder.append("Hostname of node that left : " + this.getHostName() + "\n");
		builder.append("Ip address of node that left : " + this.getIpAddress() + "\n");
		builder.append("------------------------------------------------------------------\n\n");

		Utils.printToConsole(builder.toString());

		this.routingTable.clear();
		this.tempRoutingTable.clear();
		this.setZone(null);
		this.setTempZone(null);
		this.fileNames.clear();
		this.tempFileNames.clear();
		Peer.numberOfSplits = 0;

		return;
	}

	/*
	 * function to retrieve neighbour with smallest zone here we are retrieving
	 * neighbour who has not temporarily taken over any zone
	 */
	private NeighbourInfo retrieveNeighbourWithSmallestZone() {

		List<NeighbourInfo> neighbours = getNeighbours();
		if (neighbours.size() == 0) {
			return null;
		}
		double area = computeZoneArea(neighbours.get(0).getZone());
		double temp;
		NeighbourInfo neighbourWithSmallestZone = neighbours.get(0);

		for (int i = 1; i < neighbours.size(); i++) {

			if (neighbours.get(i).getTempZone() == null) {

				temp = computeZoneArea(neighbours.get(i).getZone());
				if (temp < area) {
					area = temp;
					neighbourWithSmallestZone = neighbours.get(i);
				}
			}
		}
		return neighbourWithSmallestZone;
	}

	/*
	 * function to compute area of zone
	 */
	private double computeZoneArea(Zone zone) {

		double length = Math.abs(zone.getEndX() - zone.getStartX());
		double breadth = Math.abs(zone.getEndY() - zone.getStartY());

		double area = length * breadth;
		return area;
	}

	private boolean isNeighbour(Zone peerZone, Zone neighbourZone) {

		/*
		 * checking whether neighbours abuts in more than 1 dimension if yes then return
		 * false else return true
		 */
		double peerStartX = peerZone.getStartX();
		double peerStartY = peerZone.getStartY();
		double peerEndX = peerZone.getEndX();
		double peerEndY = peerZone.getEndY();

		double neighbourStartX = neighbourZone.getStartX();
		double neighbourStartY = neighbourZone.getStartY();
		double neighbourEndX = neighbourZone.getEndX();
		double neighbourEndY = neighbourZone.getEndY();

		// checking top right corner abut
		if (peerEndX == neighbourStartX && peerEndY == neighbourStartY) {

			if (peerEndX == neighbourStartY && peerEndY == neighbourStartX) {

				return false;
			}
		}
		// checking top left corner abut
		if (peerStartX == neighbourEndX && peerEndY == neighbourEndX) {

			if (peerStartX == neighbourStartY && peerEndY == neighbourStartY) {

				return false;
			}
		}
		// checking for bottom left corner abut
		if (peerStartX == neighbourEndX && peerStartY == neighbourEndX) {

			if (peerStartX == neighbourEndY && peerStartY == neighbourEndY) {

				return false;
			}
		}
		// checking for bottom right corner abut
		if (peerEndX == neighbourStartX && peerStartY == neighbourStartX) {

			if (peerEndX == neighbourEndY && peerStartY == neighbourEndY) {

				return false;
			}
		}
		/*
		 * checking whether the zones abut along the y-axis or the x-axis trying out all
		 * possibilities
		 */
		if (peerStartY == neighbourStartY) {

			if (peerEndX == neighbourStartX) {
				return true;
			} else if (peerStartX == neighbourEndX) {
				return true;
			}
		}
		if (peerStartX == neighbourStartX) {

			if (peerStartY == neighbourEndY) {
				return true;
			} else if (peerEndY == neighbourStartY) {
				return true;
			}
		}
		if (peerEndX == neighbourEndX) {

			if (peerEndY == neighbourStartY) {
				return true;
			} else if (neighbourEndY == peerStartY) {
				return true;
			}
		}
		if (peerEndY == neighbourEndY) {

			if (peerEndX == neighbourStartX) {
				return true;
			} else if (neighbourEndX == peerStartX) {
				return true;
			}
		}
		if (peerEndX == neighbourStartX || peerStartX == neighbourEndX) {
			if (peerStartY >= neighbourStartY && peerEndY <= neighbourEndY) {
				return true;
			} else if (peerStartY <= neighbourStartY && peerEndY >= neighbourEndY) {
				return true;
			}
		}
		if (peerStartY == neighbourEndY || peerEndY == neighbourStartY) {
			if (peerStartX >= neighbourStartX && peerEndX <= neighbourEndX) {
				return true;
			} else if (peerStartX <= neighbourStartX && peerEndX >= neighbourEndX) {
				return true;
			}
		}
		// if here then it is not a neighbour
		return false;
	}

	private Coordinate computeRandomPointInSpace() {

		double x, y;
		Coordinate randomCoordinate;

		x = new BigDecimal(Math.random() * 100).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
		y = new BigDecimal(Math.random() * 100).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();

		randomCoordinate = new Coordinate(x, y);

		return randomCoordinate;
	}

	public void retrieveActivePeers(WiredViewActivePeersRequest activePeersRequest) {

		activePeersRequest.setActivePeers(Peer.activePeers);
		this.sendThreaded(activePeersRequest);
	}

	public void forwardWiredView(WiredViewActivePeersRequest activePeersRequest) {

		// RevisedReceive r = new RevisedReceive();
		this.setTotalViewsRequired(activePeersRequest.getActivePeers().size());

		for (MacAddress peerHostname : activePeersRequest.getActivePeers().keySet()) {

			if (!(this.getHostName().equals(peerHostname))) {
				// WiredView wiredView = new WiredView(this.getHostName(), this.getIpAddress(),
				// peerHostname,
				// ViewCategory.MULTI);
				// RevisedSend.sendMessage(wiredView);
			}
			try {
				// Thread.sleep(50);
				//JistAPI.sleep(500);
			} catch (Exception ie) {
				ie.printStackTrace();
			}
		}
	}

	public void retrievePeerInformation(WiredView view) {

		view.setPeerInformation(this.toString());
		view.setHostnameToRoute(view.getSourceHostname());
		this.sendThreaded(view);
	}

	public void view(String hostname) {

		if (hostname == null) {

			WiredViewActivePeersRequest activePeersRequest = new WiredViewActivePeersRequest();
			activePeersRequest.setSourceHostname(this.getHostName());
			activePeersRequest.setSourceIpAddress(this.getIpAddress());

			try {
				if (isBootstrap()) {

					activePeersRequest.setActivePeers(Peer.activePeers);
					forwardWiredView(activePeersRequest);
				} else {
					this.sendThreaded(activePeersRequest);
				}
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
		}

		else if (hostname.equals(this.getHostName())) {
			Utils.printToConsole(this.toString());
		} else {

			WiredView wiredView = new WiredView(this.getHostName(), this.getIpAddress(), hostname, ViewCategory.SINGLE);
		}
		// RevisedSend.sendMessage(wiredView);
	}

	/*
	 * splitting the zone for new node.
	 */
	private Zone[] splitZone(Zone peerZone, WiredJoin wiredJoin) {

		Coordinate joiningCoordinate = wiredJoin.getRandomCoordinate();

		double startX, startY, endX, endY;
		Zone[] zoneHalvesArray = new Zone[2];
		Zone newZone;
		/*
		 * if numberOfSplits is even then split vertically (along the y-axis) else split
		 * horizontally (along the x-axis)
		 */
		if (Peer.numberOfSplits % 2 == 0) {

			startX = peerZone.getStartX();
			startY = peerZone.getStartY();
			endX = peerZone.getEndX();
			endY = peerZone.getEndY();

			/*
			 * check which half of peerZone joiningCoordinate is present in (left half or
			 * right half)
			 */
			if (joiningCoordinate.getXCoord() >= ((endX - startX) / 2)) {

				/*
				 * creating zone for right half of peerZone
				 */
				double newStartX = startX + ((endX - startX) / 2);

				newZone = new Zone(newStartX, startY, endX, endY);

				/*
				 * changing the endX of peerZone to startX or new zone assigning endX to
				 * peerZone.endX
				 */
				endX = newStartX;
				peerZone.setEndX(endX);

			} else {

				/*
				 * creating zone for left half of peerZone
				 */
				double newEndX = startX + ((endX - startX) / 2);

				newZone = new Zone(startX, startY, newEndX, endY);

				/*
				 * changing the startX of peerZone to endX of new zone assigning startX to
				 * peerZone.startX
				 */
				startX = newEndX;
				peerZone.setStartX(startX);

			}

			Peer.numberOfSplits++;

		} else {

			startX = peerZone.getStartX();
			startY = peerZone.getStartY();
			endX = peerZone.getEndX();
			endY = peerZone.getEndY();

			/*
			 * checking which half of the peerZone joiningCoordinate is present in (bottom
			 * half or upper half)
			 */
			if (joiningCoordinate.getYCoord() >= (endY - startY) / 2) {

				/*
				 * creating zone for top half of peerZone
				 */
				double newStartY = startY + ((endY - startY) / 2);

				newZone = new Zone(startX, newStartY, endX, endY);

				/*
				 * changing the endY of peerZone to endY of new zone assigning endY to
				 * peerZone.endY
				 */
				endY = newStartY;
				peerZone.setEndY(endY);
			} else {

				/*
				 * creating zone for bottom half of peerZone
				 */
				double newEndY = startY + ((endY - startY) / 2);

				newZone = new Zone(startX, startY, endX, newEndY);

				/*
				 * changing the startY of peerZone to endY of new zone assigning startY to
				 * peerZone.startY
				 */
				startY = newEndY;
				peerZone.setStartY(startY);

			}

			// if current node is the bootstrap then increment the number of splits
			Peer.numberOfSplits++;
		}

		/*
		 * storing the peerZone at location i=0 and storing newZone at location i=1
		 * returning zoneHalvesArray
		 */
		zoneHalvesArray[0] = peerZone;
		zoneHalvesArray[1] = newZone;

		return zoneHalvesArray;

	}

	/*
	 * If one of the neighbours' zone/tempZone contains the coordinate then return
	 * that neighbour Else return the closest neighbor to route
	 */
	public NeighbourInfo findClosestNeighbour(Coordinate destinationCoordinate, RouteInformation routeInformation,
			MacAddress lastHop) throws ClosestNeighbourUnavailableException {
		NeighbourInfo neighbourInfo = null;
		double minDist = -999999;
		double dist;
		Zone tempZone;

		try {
			for (Map.Entry<String, NeighbourInfo> routinTableEntry : this.routingTable.entrySet()) {

				
				
				if (!routeInformation.getRoute().containsKey(routinTableEntry.getKey())) {

					// checking if neighbour's zone contains the coordinates
					if (isDestination(destinationCoordinate, routinTableEntry.getValue().getZone())) {
						return routinTableEntry.getValue();
					} else if ((tempZone = routinTableEntry.getValue().getTempZone()) != null) {
						if (isDestination(destinationCoordinate, tempZone)) {
							return routinTableEntry.getValue();
						}
					}

					dist = Utils.computeDistance(routinTableEntry.getValue().getZone(), destinationCoordinate);
					if (minDist == -999999 && !routinTableEntry.getValue().getMacAddress().equals(lastHop)) {

						minDist = dist;
						neighbourInfo = routinTableEntry.getValue();
					} else if (dist < minDist && !routinTableEntry.getValue().getMacAddress().equals(lastHop)) {

						minDist = dist;
						neighbourInfo = routinTableEntry.getValue();
					}

					if (routinTableEntry.getValue().getTempZone() != null) {

						dist = Utils.computeDistance(routinTableEntry.getValue().getTempZone(), destinationCoordinate);
						if (dist < minDist) {
							minDist = dist;
							neighbourInfo = routinTableEntry.getValue();
						}
					}
				}
			}

			if ( neighbourInfo == null || !neighbourInfo.hasContents()) {
								
				throw new ClosestNeighbourUnavailableException();
			}

		} catch (Exception closestNeighbourUnavailableException) {

			throw closestNeighbourUnavailableException;
		}

		return neighbourInfo;

	}

	private InetAddress getIpAddress() {

		try {
			return InetAddress.getByName(this.hostname);
		} catch (UnknownHostException e) {
			getIpAddress();
		}

		return null;
	}

	/*
	 * checks whether the destination has been reached
	 */
	public synchronized boolean isDestination(Coordinate destCoord, Zone zone) {

		/*
		 * checking whether the x coordinate of destCoord lies within the Peer's zone if
		 * destCoord on the boundaries of the entire coordinate space then we need to
		 * take the boundary into the zone else take value > startX (for along y) take
		 * value > startY (for along x)
		 */

		if (destCoord.getXCoord() == Peer.LOWER_BOUND_X) {

			if (destCoord.getYCoord() == Peer.LOWER_BOUND_Y) {
				// checking for (0,0)

				if (zone.getStartX() == destCoord.getXCoord()) {

					// checking whether the y coordinate of destCoord lies within the Peer's zone
					if (zone.getStartY() == destCoord.getYCoord()) {

						return true;
					} else {
						return false;
					}
				}
			} else if (destCoord.getYCoord() == Peer.UPPER_BOUND_Y) {
				// checking for (0,100)

				if (zone.getStartX() == destCoord.getXCoord()) {

					// checking whether the y coordinate of destCoord lies within the Peer's zone
					if (destCoord.getYCoord() == zone.getEndY()) {

						return true;
					} else {
						return false;
					}
				}
			} else {
				// checking for (0, (LOWER_BOUND_Y,UPPER_BOUND_Y))
				if (zone.getStartX() == destCoord.getXCoord()) {

					// checking whether the y coordinate of destCoord lies within the Peer's zone
					if (zone.getStartY() < destCoord.getYCoord() && destCoord.getYCoord() < zone.getEndY()) {

						return true;
					} else {
						return false;
					}
				}
			}
		}
		if (destCoord.getXCoord() == Peer.UPPER_BOUND_X) {

			if (destCoord.getYCoord() == Peer.LOWER_BOUND_Y) {
				// checking for (100,0)
				if (zone.getEndX() == destCoord.getXCoord()) {

					// checking whether the y coordinate of destCoord lies within the Peer.s zone
					if (zone.getStartY() == destCoord.getYCoord()) {

						return true;
					} else {
						return false;
					}
				}
			} else if (destCoord.getYCoord() == Peer.UPPER_BOUND_Y) {
				// checking for (100,100)

				if (zone.getEndX() == destCoord.getXCoord()) {

					// checking whether the y coordinate of destCoord lies within the Peer's zone
					if (zone.getEndY() == destCoord.getYCoord()) {

						return true;
					} else {
						return false;
					}
				}
			} else {
				// means that coordinate is in (100, (LOWER_BOUND_Y,UPPER_BOUND_Y))

				if (zone.getEndX() == destCoord.getXCoord()) {

					// checking whether the y coordinate of destCoord lies within the Peer's zone
					if (zone.getStartY() < destCoord.getYCoord() && destCoord.getYCoord() < zone.getEndY()) {

						return true;
					} else {
						return false;
					}
				}
			}
		}
		if (destCoord.getYCoord() == Peer.LOWER_BOUND_Y) {
			// here x coordinate is in (LOWER_BOUND_X, UPPER_BOUND_X)

			if (zone.getStartY() == destCoord.getYCoord()) {

				if (zone.getStartX() < destCoord.getXCoord() && destCoord.getXCoord() < zone.getEndX()) {

					return true;
				} else {
					return false;
				}
			}
		}
		if (destCoord.getYCoord() == Peer.UPPER_BOUND_Y) {
			// here x coordinate is in (LOWER_BOUND_X, UPPER_BOUND_X)

			if (zone.getEndY() == destCoord.getYCoord()) {

				if (zone.getStartX() < destCoord.getXCoord() && destCoord.getXCoord() < zone.getEndX()) {

					return true;
				} else {
					return false;
				}
			}
		}

		if (zone != null) {

			if (zone.getStartX() < destCoord.getXCoord() && destCoord.getXCoord() <= zone.getEndX()) {
				// means that the x coordinate and the y coordinate are in
				// ((LOWER_BOUND_X,UPPER_BOUND_X) , (LOWER_BOUND_Y,UPPER_BOUND_Y))

				// checking whether the y coordinate of destCoord lies within the Peer's zone
				if (zone.getStartY() < destCoord.getYCoord() && destCoord.getYCoord() <= zone.getEndY()) {

					return true;
				} else {
					return false;
				}
			}
		}

		// destCoord does not lie within Peer's zone and hence the message needs to be
		// further routed to another peer
		return false;
	}

	public List<NeighbourInfo> getNeighbours() {

		List<NeighbourInfo> neighbours = new ArrayList<NeighbourInfo>();
		for (Map.Entry<String, NeighbourInfo> entry : this.routingTable.entrySet()) {
			neighbours.add(entry.getValue());
		}
		if (this.tempRoutingTable.size() > 0) {
			for (Map.Entry<String, NeighbourInfo> tempEntry : this.tempRoutingTable.entrySet()) {
				if (!this.routingTable.containsKey(tempEntry.getKey())) {
					neighbours.add(tempEntry.getValue());
				}
			}
		}

		return neighbours;
	}

	public InetAddress getIPaddress() {
		return IPaddress;
	}

	public void setIPaddress(InetAddress iPaddress) {
		IPaddress = iPaddress;
	}

	public String getHostName() {
		return hostname;
	}

	public MacAddress getMacAddress() {
		return macAddress;
	}

	public void setHostName(String identifier) {
		this.hostname = identifier;
	}

	public Zone getZone() {
		return zone;
	}

	public void setZone(Zone zone) {
		this.zone = zone;
	}

	public Zone getTempZone() {

		return this.tempZone;
	}

	public void setTempZone(Zone zone) {

		this.tempZone = zone;
	}

	public HashSet<String> getFileNames() {
		return fileNames;
	}

	public static String getBootstrapHostname() {
		return Peer.BOOTSTRAP_HOSTNAME;
	}

	public static InetAddress getBootstrapIp() {
		return bootstrapIp;
	}

	public static void setBootstrapIp() throws UnknownHostException {
		// VOU DEFINIR O BOOTSTRAP COMO O PRIMEIRO PEER SEMPRE: 0.0.0.1
		InetAddress bootstrapAddress = InetAddress.getByName(Peer.BOOTSTRAP_HOSTNAME);
		Peer.bootstrapIp = bootstrapAddress;
	}

	public boolean isBootstrap() throws UnknownHostException {

		// InetAddress localHostAddress = localAddr.getIP();
		// String localHostName = localHostAddress.getHostName();

		if (localAddr.getIP().toString().substring(1).equals(Peer.BOOTSTRAP_HOSTNAME)) {
			return true;
		} else {
			return false;
		}

	}

	public String toString() {

		StringBuilder builder = new StringBuilder("");
		builder.append("Hostname : " + this.getHostName() + "\n");
		builder.append("------------------------------------------------------------------\n");
		builder.append("Ip Address : " + this.getIpAddress().getHostAddress() + "\n");
		builder.append("Zone : " + this.getZone() + "\n");
		builder.append("Temp Zone : " + this.getTempZone() + "\n");
		builder.append("Files : " + this.fileNames + "\n");
		builder.append("Temp files : " + this.tempFileNames + "\n");
		builder.append("Neighbours : ");
		for (NeighbourInfo neighbour : this.getNeighbours()) {
			builder.append(neighbour.getHostname() + ", ");
		}
		builder.append("\n");
		builder.append("------------------------------------------------------------------\n");

		return builder.toString();
	}

	// public void main(String[] args) throws InterruptedException {
	//
	// possibleCommands.put(CommandType.INSERT, false);
	// possibleCommands.put(CommandType.SEARCH, false);
	// possibleCommands.put(CommandType.JOIN, true);
	// possibleCommands.put(CommandType.LEAVE, false);
	// possibleCommands.put(CommandType.VIEW, true);
	//
	// formats.put(CommandType.INSERT, "INSERT filename");
	// formats.put(CommandType.SEARCH, "SEARCH filename");
	// formats.put(CommandType.JOIN, "JOIN");
	// formats.put(CommandType.LEAVE, "LEAVE");
	// formats.put(CommandType.VIEW, "VIEW [hostname]");
	//
	// try {
	//
	// boolean isBootstrap = isBootstrap();
	// if (isBootstrap) {
	//
	// Peer bootstrap = Peer.getInstance();
	// /*
	// * Setting bootstrap zone Here lowX = 0, lowY = 0, highX = 100, highY = 100
	// */
	// Zone bootstrapZone = new Zone(0, 0, 100, 100);
	// bootstrap.setZone(bootstrapZone);
	//
	// // setting active peers
	// Peer.activePeers = new HashMap<String, InetAddress>();
	// Peer.activePeers.put(Peer.BOOTSTRAP_HOSTNAME, Peer.bootstrapIp);
	//
	// // setting number of splits to 0
	// Peer.numberOfSplits = 0;
	//
	// // disabling JOIN command
	// possibleCommands.put(CommandType.JOIN, false);
	// possibleCommands.put(CommandType.INSERT, true);
	// possibleCommands.put(CommandType.SEARCH, true);
	// possibleCommands.put(CommandType.LEAVE, false);
	//
	// Utils.printToConsole("Bootstrap loaded and Initialized.");
	//
	// }
	//
	// } catch (UnknownHostException e) {
	//
	// Utils.printToConsole("Couldn't load the bootstrap node. Try again.");
	// }
	//
	// /*
	// * Spawning thread to listen on a port
	// */
	// RevisedReceive r = new RevisedReceive();
	// r.startServer(49161);
	//
	// Scanner scanner = new Scanner(System.in);
	// Peer peerInstance;
	// try {
	// peerInstance = Peer.getInstance();
	// peerInstance.setHostName(InetAddress.getLocalHost().getHostName());
	// peerInstance.setIPaddress(InetAddress.getLocalHost());
	//
	// // setting bootstrap ip
	// Peer.bootstrapIp = InetAddress.getByName(Peer.BOOTSTRAP_HOSTNAME);
	//
	// while (true) {
	//
	// System.out.println("Please provide a command. The possible commands are :");
	// for (CommandType command : possibleCommands.keySet()) {
	//
	// if (possibleCommands.get(command)) {
	// System.out.println(command + " -- " + formats.get(command));
	// }
	// }
	//
	// String[] input = scanner.nextLine().split(" ");
	// switch (input[0].toLowerCase()) {
	// case "insert":
	// if (possibleCommands.get(CommandType.INSERT) == false) {
	//
	// Utils.printToConsole("Illegal command");
	// } else {
	// if (input.length != 2) {
	// Utils.printErrorMessage("Wrong format on INSERT command.");
	// Utils.printToConsole("Correct format : INSERT " +
	// formats.get(CommandType.INSERT));
	// } else {
	// String filename = args[1];
	// WiredInsert wiredInsert = new WiredInsert(CommandType.INSERT, filename, null,
	// null,
	// new RouteInformation());
	// peerInstance.insert(wiredInsert);
	// }
	// }
	// // Thread.sleep(500);
	// JistAPI.sleep(500 * Constants.SECOND);
	// break;
	// case "search":
	// if (possibleCommands.get(CommandType.SEARCH) == false) {
	//
	// Utils.printToConsole("Illegal command.");
	// } else {
	// if (input.length != 2) {
	// Utils.printErrorMessage("Wrong format for SEARCH command.");
	// Utils.printToConsole("Correct format : SEARCH " +
	// formats.get(CommandType.SEARCH));
	// } else {
	// String filename = args[1];
	// WiredSearch wiredSearch = new WiredSearch(CommandType.SEARCH, filename, null,
	// null,
	// new RouteInformation());
	// peerInstance.search(wiredSearch);
	// }
	// }
	// // Thread.sleep(500);
	// JistAPI.sleep(500 * Constants.SECOND);
	// break;
	// case "join":
	// if (possibleCommands.get(CommandType.JOIN) == false) {
	//
	// Utils.printToConsole("Illegal command");
	// } else {
	// if (input.length > 1) {
	// Utils.printErrorMessage("Wrong format for JOIN command");
	// Utils.printToConsole("Correct format : " + formats.get(CommandType.JOIN));
	// } else {
	//
	// possibleCommands.put(CommandType.INSERT, true);
	// possibleCommands.put(CommandType.SEARCH, true);
	// possibleCommands.put(CommandType.VIEW, true);
	// possibleCommands.put(CommandType.JOIN, false);
	// possibleCommands.put(CommandType.LEAVE, true);
	//
	// WiredJoin wiredJoin = new WiredJoin(peerInstance.getHostName(),
	// peerInstance.getIpAddress(),
	// Peer.getBootstrapHostname(), Peer.getBootstrapIp(), new RouteInformation());
	// RevisedSend.sendMessage(wiredJoin);
	//
	// }
	// }
	// // Thread.sleep(500);
	// JistAPI.sleep(500 * Constants.SECOND);
	// break;
	// case "leave":
	// if (possibleCommands.get(CommandType.LEAVE) == false) {
	//
	// Utils.printToConsole("Illegal command");
	// } else {
	// if (input.length > 1) {
	// Utils.printErrorMessage("Wrong format for LEAVE command");
	// Utils.printToConsole("Correct format : " + formats.get(CommandType.LEAVE));
	// } else {
	// if (peerInstance.getTempZone() == null) {
	// possibleCommands.put(CommandType.INSERT, false);
	// possibleCommands.put(CommandType.SEARCH, false);
	// possibleCommands.put(CommandType.VIEW, true);
	// possibleCommands.put(CommandType.JOIN, true);
	// possibleCommands.put(CommandType.LEAVE, false);
	// peerInstance.leave();
	// } else {
	// Utils.printErrorMessage(
	// "Sorry! Cannot leave the network due to temporary take over of another
	// zone.");
	// }
	// }
	// }
	//
	// // Thread.sleep(500);
	// JistAPI.sleep(500 * Constants.SECOND);
	// break;
	// case "view":
	// if (possibleCommands.get(CommandType.VIEW) == false) {
	//
	// Utils.printToConsole("Illegal command");
	// } else {
	// if (input.length == 2) {
	// peerInstance.view(input[1].toString());
	// } else if (input.length == 1) {
	// peerInstance.view(null);
	// } else {
	// Utils.printErrorMessage("Wrong format for VIEW command");
	// Utils.printToConsole("Correct format : " + formats.get(CommandType.VIEW));
	// }
	// }
	// // Thread.sleep(500);
	// JistAPI.sleep(500 * Constants.SECOND);
	// break;
	//
	// default:
	// Utils.printErrorMessage("Please enter a valid command.");
	// }
	//
	// }
	// } catch (UnknownHostException e) {
	// e.printStackTrace();
	// }
	// }

	// public void joinCAN(NetAddress nodeAddress, int i) throws
	// UnknownHostException {
	//
	// WiredJoin wiredJoin = new WiredJoin(this.getHostName(),
	// this.localAddr.getIP(), this.getBootstrapHostname(),
	// this.getBootstrapIp(), new RouteInformation());
	// this.sendMessage(wiredJoin);
	// }

	// public void directStart(String option, InetAddress nodeHostName, InetAddress
	// nodeIp, int nodePort) throws InterruptedException {

	// ****************//
	public void startNodes() {

		try {

			boolean isBootstrap = this.isBootstrap();
			listening(this);
			if (isBootstrap) {
				// Peer bootstrap = Peer.getInstance();
				/*
				 * Setting bootstrap zone Here lowX = 0, lowY = 0, highX = 100, highY = 100
				 */
				Zone bootstrapZone = new Zone(0, 0, 100, 100);
				this.setZone(bootstrapZone);

				// setting active peers
				this.activePeers = new HashMap<MacAddress, InetAddress>();
				this.activePeers.put(this.BOOTSTRAP_MACADDRESS, this.bootstrapIp);

				// setting number of splits to 0
				this.numberOfSplits = 0;

				Utils.printToConsole("Bootstrap loaded and Initialized.");
				return;
			} else {

				// Start all nodes but bootstrap node with a JOIN
				WiredJoin wiredJoin = new WiredJoin(this.getHostName(), this.localAddr.getIP(), this.macAddress,
						this.getBootstrapHostname(), this.getBootstrapIp(), null, new RouteInformation());
				this.sendThreaded(wiredJoin);
			}

		} catch (UnknownHostException e) {

			Utils.printToConsole("Couldn't load the bootstrap node. Try again.");
		}

	}

	public RouteInterface.Can getProxy() {
		return self;
	}

	public void setNetEntity(NetInterface netEntity) {
		this.netEntity = netEntity;
	}

	@Override
	public void peek(NetMessage msg, MacAddress lastHop) {
		// TODO Auto-generated method stub

		//
//				  NetMessage.Ip ipMsg = null;
//				  ipMsg = (NetMessage.Ip)msg;
//				  System.out.println(localAddr+" PEEK msg from "+ ipMsg.getSrc()+" "+ipMsg );
		// System.out.println("peek: src=" + ipMsg.getSrc());
		// System.out.println("peek: dst=" + ipMsg.getDst());
		// System.out.println("peek: payload=" + ipMsg.getPayload());
		// System.out.println("peek: priority=" + ipMsg.getPriority());
		// System.out.println("peek: protocol=" + ipMsg.getProtocol());
		// System.out.println("peek: TTL=" + ipMsg.getTTL());
		// System.out.println("peek: msg size = " + ipMsg.getSize());
		// System.out.println();
		// final Socket socket = serverSocket.accept();
		// ObjectInputStream objectInputStream = new
		// ObjectInputStream(socket.getInputStream());

		// Object wiredObject = msg;/// objectInputStream.readObject();

	}

	public static class RevisedSend implements AppJava.Runnable {

		private Object wiredObject;
		private Peer peer;

		public RevisedSend(Object wiredObject, Peer peer) {
			this.wiredObject = wiredObject;
			this.peer = peer;
		}

		@Override
		public void run() throws Continuation {
			// TODO Auto-generated method stub
			if (this.wiredObject instanceof WiredJoin) {
				// call send for JOIN
				this.peer.send((WiredJoin) this.wiredObject);
			} else if (wiredObject instanceof WiredInsert) {
				// call send for INSERT
				this.peer.send((WiredInsert) wiredObject);
			} else if (wiredObject instanceof WiredSearch) {
				// call send for SEARCH
				this.peer.send((WiredSearch) wiredObject);
			} else if (wiredObject instanceof JoinUpdateNeighbours) {
				// call send for JOIN_UPDATE
				this.peer.send((JoinUpdateNeighbours) this.wiredObject);
			}
			 else if(wiredObject instanceof TemporaryZoneReleaseUpdateNeighbours){
			 //call send for Temporary release of node udpate to the neighbours
			 this.peer.send((TemporaryZoneReleaseUpdateNeighbours)wiredObject);
			 }
			else if (this.wiredObject instanceof JoinUpdateBootstrap) {
				// call send for JOIN_UPDATE_BOOTSTRAP
				this.peer.send((JoinUpdateBootstrap) this.wiredObject);

			} else if (this.wiredObject instanceof JoinConfirmation) {
				// call send for join confirmation
				this.peer.send((JoinConfirmation) this.wiredObject);

			} else if (wiredObject instanceof LeaveUpdateBootstrap) {
				// call send for leave update to be sent to the bootstrap node
				this.peer.send((LeaveUpdateBootstrap) wiredObject);

			} else if (wiredObject instanceof LeaveUpdateNeighbours) {
				// call send for leave update to be sent to the neighbours
				this.peer.send((LeaveUpdateNeighbours) wiredObject);

			} else if (wiredObject instanceof WiredZoneTransfer) {
				// call send for transferring zone when node is leaving
				this.peer.send((WiredZoneTransfer) wiredObject);
			}

			else if (wiredObject instanceof TakeoverUpdate) {
				// call send for updating neighbours about the change of state of current peer
				// after taking over
				this.peer.send((TakeoverUpdate) wiredObject);
			} else if (wiredObject instanceof TakeoverConfirmation) {
				// call send for sending takeoverConfirmation back to the leaving node
				this.peer.send((TakeoverConfirmation) wiredObject);
			}
			// else if(wiredObject instanceof WiredViewActivePeersRequest){
			// //call send for sending WiredViewActivePeersRequest to the bootstrap node
			// send((WiredViewActivePeersRequest)wiredObject);
			// }
			// else if(wiredObject instanceof WiredView){
			// //call send for sending WiredView object to all the nodes in the network
			// send((WiredView)wiredObject);
			// }
			else if (wiredObject instanceof WiredSuccess) {
				// call send for success and failure
				this.peer.send((WiredSuccess) wiredObject);
			} else if (wiredObject instanceof WiredFailure) {
				// call send for success and failure
				this.peer.send((WiredFailure) wiredObject);
			} else if (true) {

				System.out.println("LOSING SEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEND"
						+ this.wiredObject);
			}
		}

	}

	@Override
	public void sendThreaded(Object wiredObject) {
		// TODO Auto-generated method stub

//		System.out.println(this.macAddress + " sendMessage");

		AppJava.Runnable worker = new RevisedSend(wiredObject, this);
		AppJava.Runnable workerEntity = (AppJava.Runnable) JistAPI.proxy(worker, AppJava.Runnable.class);
		Thread thread = new Thread(workerEntity);
		// System.out.println("Spawning thread: "+ thread.getName()+" em "+
		// this.localAddr);

		thread.start();

	}

	/*
	 * forward JOIN request
	 */
	private void send(WiredJoin wiredJoin) {
		java.util.Date d = new Date();
		int n = 0;
		try {

			
			/*
			 * if numberOfHops = 0 then route to Bootstrap node else if numberOfHops = 1 the
			 * route to source else route to neighbourToRoute
			 */
			if (wiredJoin.getNumberOfHops() == 0) {
				NetAddress ipDest = new NetAddress(this.getBootstrapIp());
				
				//JistAPI.sleep(1000000000);
				
				//System.out.println(localAddr+" Sending message to: "+ipDest);

				NetMessage.Ip ipMsg = new NetMessage.Ip(wiredJoin, localAddr, ipDest, Constants.NET_PROTOCOL_CAN,
						Constants.NET_PRIORITY_NORMAL, Constants.TTL_DEFAULT);
					
//				System.out.println("(1)-" + this.macAddress + " enviando wiredJoin para " + BOOTSTRAP_MACADDRESS + " "
//						+ System.currentTimeMillis());
				
				 
				//this.router.sendMessage(wiredJoin,ipMsg, localAddr, ipDest);
				this.udp.send(ipMsg, ipDest, PORT, PORT, Constants.NET_PRIORITY_NORMAL);
				//this.netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT, BOOTSTRAP_MACADDRESS);
				// JistAPI.sleep(1000000000);
				//JistAPI.sleep(1000000000);
				// this.netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT,
				// BOOTSTRAP_MACADDRESS);

				// while(socket == null){
				// trying to connect every 2 seconds until connection is established
				try {
					// socket = new Socket(Peer.getBootstrapHostname(), 49161);
				} catch (Exception e) {
					try {
						n++;
						if (n == 3) {
							PeerInfo sourceInfo = new PeerInfo();
							sourceInfo.setHostName(wiredJoin.getHostnameToRoute());
							sourceInfo.setIpAddress(wiredJoin.getIpAddressToRoute());
							String statusMessage = "FAILURE : Hostname does not exist in the netork.\n";
							WiredFailure connectionFailure = new WiredFailure(CommandType.JOIN, sourceInfo,
									statusMessage);
							Utils.printErrorMessage(connectionFailure.toString());

							Peer.possibleCommands.put(CommandType.INSERT, false);
							Peer.possibleCommands.put(CommandType.SEARCH, false);
							Peer.possibleCommands.put(CommandType.JOIN, true);
							Peer.possibleCommands.put(CommandType.LEAVE, false);
							Peer.possibleCommands.put(CommandType.VIEW, true);

							// break;
						}
						Utils.printErrorMessage("Couldn't connect. Trying again...");
						// Thread.sleep(2000);

					} catch (Exception ie) {
						ie.printStackTrace();
					}
				}
				// }
				// connection established
				// serializing wiredJoin to Bootstrap node
				// objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
				// objectOutputStream.writeObject(wiredJoin);
				// objectOutputStream.flush();

			} else if (wiredJoin.getNumberOfHops() == 1) {
				n = 0;
				// while(socket == null){

				NetAddress ipDest = new NetAddress(wiredJoin.getSourceIpAddress());

				NetMessage.Ip ipMsg = new NetMessage.Ip(wiredJoin, localAddr, ipDest, Constants.NET_PROTOCOL_CAN,
						Constants.NET_PRIORITY_NORMAL, Constants.TTL_DEFAULT);

//				System.out.println("(2)-" + this.macAddress + " enviando wiredJoin para "
//						+ wiredJoin.getSourceMacAddress() + " " + System.currentTimeMillis());
				
				System.out.println("(A)-" + this.macAddress + " enviando wiredJoin para: "
						+ ipDest + " com ActivePeers = " +wiredJoin.getActivePeersInfo()+" "+ System.currentTimeMillis());
				
				//this.netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT, wiredJoin.getSourceMacAddress());
				this.udp.send(ipMsg, ipDest, PORT, PORT, Constants.NET_PRIORITY_NORMAL);
				// JistAPI.sleep(1000000000);
				try {
					// socket = new Socket(wiredJoin.getSourceHostname(), 49161);

				} catch (Exception e) {
					try {
						n++;
						if (n == 3) {
							PeerInfo sourceInfo = new PeerInfo();
							sourceInfo.setHostName(wiredJoin.getHostnameToRoute());
							sourceInfo.setIpAddress(wiredJoin.getIpAddressToRoute());
							String statusMessage = "FAILURE : Hostname does not exist in the netork.\n";
							WiredFailure connectionFailure = new WiredFailure(CommandType.JOIN, sourceInfo,
									statusMessage);
							Utils.printErrorMessage(connectionFailure.toString());
							// break;
						}
						Utils.printErrorMessage("Couldn't connect. Trying again...");
						// Thread.sleep(2000);
					} catch (Exception ie) {
						ie.printStackTrace();
					}
				}
				// }
				// serializing wiredJoin from Bootstrap node to new node
				// objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
				// objectOutputStream.writeObject(wiredJoin);
				// objectOutputStream.flush();

			} else {
				n = 0;
				// while(socket == null){
				// trying to connect every 2 seconds until connection us established

				NetAddress ipDest = new NetAddress(wiredJoin.getIpAddressToRoute());

				NetMessage.Ip ipMsg = new NetMessage.Ip(wiredJoin, localAddr, ipDest, Constants.NET_PROTOCOL_CAN,
						Constants.NET_PRIORITY_NORMAL, Constants.TTL_DEFAULT);

//				System.out.println("(3)-" + this.macAddress + " enviando wiredJoin para "
//						+ wiredJoin.getMacAddressToRoute() + " " + System.currentTimeMillis()+"Solicitando entrada em "+"("+wiredJoin.getRandomCoordinate()+")");
				System.out.println("(B)-" + this.macAddress + " enviando wiredJoin para: "
						+ ipDest + " com getRouteInformation = " +wiredJoin.getRouteInformation()+" "+ System.currentTimeMillis());
				
				//this.netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT, wiredJoin.getMacAddressToRoute());
				this.udp.send(ipMsg, ipDest, PORT, PORT, Constants.NET_PRIORITY_NORMAL);
				

				try {
					// socket = new Socket(wiredJoin.getHostnameToRoute(), 49161);
				} catch (Exception e) {
					try {
						n++;
						if (n == 3) {
							PeerInfo sourceInfo = new PeerInfo();
							sourceInfo.setHostName(wiredJoin.getHostnameToRoute());
							sourceInfo.setIpAddress(wiredJoin.getIpAddressToRoute());
							String statusMessage = "FAILURE : Hostname does not exist in the netork.\n";
							WiredFailure connectionFailure = new WiredFailure(CommandType.JOIN, sourceInfo,
									statusMessage);
							Utils.printErrorMessage(connectionFailure.toString());
							// break;
						}
						Utils.printErrorMessage("Couldn't connect. Trying again...");
						// Thread.sleep(2000);
					} catch (Exception ie) {
						ie.printStackTrace();
					}
				}
				// serializing wiredJoin from new node to peer
				// objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
				// objectOutputStream.writeObject(wiredJoin);
				// objectOutputStream.flush();
				// }

			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		// finally{
		// if(socket != null){
		// try{
		// socket.close();
		// }
		// catch(IOException e){
		// e.printStackTrace();
		// }
		// }
		// if(objectOutputStream != null){
		// try{
		// objectOutputStream.close();
		// }
		// catch(IOException e){
		// e.printStackTrace();
		// }
		// }
		// }
	}

	private void send(JoinUpdateBootstrap joinUpdateBootstrap) {
		java.util.Date d = new Date();
		int n = 0;

		NetAddress ipDest = new NetAddress(this.getBootstrapIp());

		NetMessage.Ip ipMsg = new NetMessage.Ip(joinUpdateBootstrap, localAddr, ipDest, Constants.NET_PROTOCOL_CAN,
				Constants.NET_PRIORITY_NORMAL, Constants.TTL_DEFAULT);
//
//		System.out.println("(Z - )"+this.macAddress + " enviando joinUpdateBootstrap para " + BOOTSTRAP_MACADDRESS + " "
//				+ System.currentTimeMillis());
		
		
		//this.netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT, BOOTSTRAP_MACADDRESS);
		this.udp.send(ipMsg, ipDest, PORT, PORT, Constants.NET_PRIORITY_NORMAL);
		
		try {
			// while(socket == null){
			// trying to connect every 2 seconds until connection is established
			try {
				// socket = new Socket(Peer.getBootstrapHostname(), 49161);
			} catch (Exception e) {
				try {
					n++;
					if (n == 3) {
						PeerInfo sourceInfo = new PeerInfo();
						sourceInfo.setHostName(Peer.getBootstrapHostname());
						sourceInfo.setIpAddress(Peer.getBootstrapIp());
						String statusMessage = "FAILURE : Hostname does not exist in the netork.\n";
						WiredFailure connectionFailure = new WiredFailure(CommandType.JOIN, sourceInfo, statusMessage);
						Utils.printErrorMessage(connectionFailure.toString());
						// break;
					}
					Utils.printErrorMessage("Couldn't connect. Trying again...");
					// Thread.sleep(2000);
				} catch (Exception ie) {
					ie.printStackTrace();
				}
			}
			// }
			// serializing joinUpdateBootstrap to Bootstrap node
			// objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
			// objectOutputStream.writeObject(joinUpdateBootstrap);
			// objectOutputStream.flush();

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void send(JoinConfirmation joinConfirmation) {
		java.util.Date d = new Date();
		int n = 0;

		NetAddress ipDest = new NetAddress(joinConfirmation.getSourceIpAddress());

		NetMessage.Ip ipMsg = new NetMessage.Ip(joinConfirmation, localAddr, ipDest, Constants.NET_PROTOCOL_CAN,
				Constants.RADIO_MODE_RECEIVING, Constants.TTL_DEFAULT);

		System.out.println("(YY)"+this.macAddress + " enviando joinConfirmation para " + joinConfirmation.getSourceMaccAddres()
		+ " " + System.currentTimeMillis());
		
		//this.netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT, joinConfirmation.getSourceMaccAddres());
		this.udp.send(ipMsg, ipDest, PORT, PORT, Constants.NET_PRIORITY_NORMAL);
		
		try {
			// while(socket == null){
			// trying to connect every 2 seconds until connection is established
			try {
				// socket = new Socket(joinConfirmation.getSourceHostName(),49161);
			} catch (Exception e) {
				try {
					n++;
					if (n == 3) {
						PeerInfo sourceInfo = new PeerInfo();
						sourceInfo.setHostName(joinConfirmation.getSourceHostName());
						sourceInfo.setIpAddress(joinConfirmation.getSourceIpAddress());
						String statusMessage = "FAILURE : Hostname does not exist in the netork.\n";
						WiredFailure connectionFailure = new WiredFailure(CommandType.JOIN, sourceInfo, statusMessage);
						Utils.printErrorMessage(connectionFailure.toString());
						// break;
					}
					Utils.printErrorMessage("Couldn't connect. Trying again...");
					// Thread.sleep(2000);
				} catch (Exception ie) {
					ie.printStackTrace();
				}
			}
			// }
			// serializing joinConfirmation from peer to new joining node
			// objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
			// objectOutputStream.writeObject(joinConfirmation);
			// objectOutputStream.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/*
	 * send JOIN_UPDATE message to neighbours
	 */
	private void send(JoinUpdateNeighbours wiredJoinUpdate) {
		java.util.Date d = new Date();
		int n = 0;

		NetAddress ipDest = new NetAddress(wiredJoinUpdate.getNeighbourToRoute().getIpAddress());

		NetMessage.Ip ipMsg = new NetMessage.Ip(wiredJoinUpdate, localAddr, ipDest, Constants.NET_PROTOCOL_CAN,
				Constants.NET_PRIORITY_NORMAL, Constants.TTL_DEFAULT);

//		System.out.println("(X-)" + this.macAddress + " enviando wiredJoinUpdate para "
//				+ wiredJoinUpdate.getNeighbourToRoute().getMacAddress() + " " + System.currentTimeMillis());
		
		//this.netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT,wiredJoinUpdate.getNeighbourToRoute().getMacAddress());
		
		this.udp.send(ipMsg, ipDest, PORT, PORT, Constants.NET_PRIORITY_NORMAL);

		try {
			// while(socket == null){
			// trying to connect every 2 seconds until connection is established
			try {
				// socket = new Socket(wiredJoinUpdate.getNeighbourToRoute().getHostname(),
				// 49161);
			} catch (Exception e) {
				try {
					n++;
					if (n == 3) {
						PeerInfo sourceInfo = new PeerInfo();
						sourceInfo.setHostName(wiredJoinUpdate.getNeighbourToRoute().getHostname());
						sourceInfo.setIpAddress(wiredJoinUpdate.getNeighbourToRoute().getIpAddress());
						String statusMessage = "FAILURE : Hostname does not exist in the netork.\n";
						WiredFailure connectionFailure = new WiredFailure(CommandType.JOIN, sourceInfo, statusMessage);
						Utils.printErrorMessage(connectionFailure.toString());
						// break;
					}
					Utils.printErrorMessage("Couldn't connect. Trying again...");
					// Thread.sleep(2000);
				} catch (Exception ie) {
					ie.printStackTrace();
				}
			}
			// }
			// serializing wiredJoinUpdate from affected neighbour (whose zone has gotten
			// split) to its neighbours
			// objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
			// objectOutputStream.writeObject(wiredJoinUpdate);
			// objectOutputStream.flush();

		} catch (Exception e) {
			e.printStackTrace();
		}
		// finally{
		// if(socket != null){
		// try{
		// socket.close();
		// }
		// catch(IOException e){
		// e.printStackTrace();
		// }
		// }
		// if(objectOutputStream != null){
		// try{
		// objectOutputStream.close();
		// }
		// catch(IOException e){
		// e.printStackTrace();
		// }
		// }
		// }
	}

	private void send(LeaveUpdateBootstrap leaveUpdateBootstrap) {

		int n = 0;

		NetAddress ipDest = new NetAddress(this.bootstrapIp);

		NetMessage.Ip ipMsg = new NetMessage.Ip(leaveUpdateBootstrap, localAddr, ipDest, Constants.NET_PROTOCOL_CAN,
				Constants.NET_PRIORITY_NORMAL, Constants.TTL_DEFAULT);

//		System.out.println("(X-)" + this.macAddress + " enviando wiredJoinUpdate para "
//				+ wiredJoinUpdate.getNeighbourToRoute().getMacAddress() + " " + System.currentTimeMillis());
		
		
		//this.netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT, BOOTSTRAP_MACADDRESS);
		this.udp.send(ipMsg, ipDest, PORT, PORT, Constants.NET_PRIORITY_NORMAL);

		try {
			// while(socket == null){
			// trying to connect every 2 seconds until connection is established

			try {
				// socket = new Socket(Peer.getBootstrapHostname(), 49161);
			} catch (Exception e) {
				try {
					n++;
					if (n == 3) {
						PeerInfo sourceInfo = new PeerInfo();
						sourceInfo.setHostName(leaveUpdateBootstrap.getHostname());
						sourceInfo.setIpAddress(leaveUpdateBootstrap.getIpAddress());
						String statusMessage = "FAILURE : Hostname does not exist in the netork.\n";
						WiredFailure connectionFailure = new WiredFailure(CommandType.LEAVE, sourceInfo, statusMessage);
						Utils.printErrorMessage(connectionFailure.toString());

						Peer.possibleCommands.put(CommandType.INSERT, true);
						Peer.possibleCommands.put(CommandType.SEARCH, true);
						Peer.possibleCommands.put(CommandType.VIEW, true);
						Peer.possibleCommands.put(CommandType.JOIN, false);
						Peer.possibleCommands.put(CommandType.LEAVE, true);

						;
					}
					Utils.printErrorMessage("Couldn't connect. Trying again...");
					// Thread.sleep(2000);
				} catch (Exception ie) {
					ie.printStackTrace();
				}
			}
			// }
			// serializing leaveUpdateBootstrap to Bootstrap node from leaving peer
//			objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
//			objectOutputStream.writeObject(leaveUpdateBootstrap);
//			objectOutputStream.flush();
		} catch (Exception e) {
			System.out.println("LeaveUpdateBootstrap Exeption");
		}

	}

	private void send(WiredZoneTransfer wiredZoneTransfer) {

		int n = 0;

		NetAddress ipDest = new NetAddress(wiredZoneTransfer.getNeighbourToRoute().getIpAddress());

		NetMessage.Ip ipMsg = new NetMessage.Ip(wiredZoneTransfer, localAddr, ipDest, Constants.NET_PROTOCOL_CAN,
				Constants.NET_PRIORITY_NORMAL, Constants.TTL_DEFAULT);

//		System.out.println("(X-)" + this.macAddress + " enviando wiredJoinUpdate para "
//				+ wiredJoinUpdate.getNeighbourToRoute().getMacAddress() + " " + System.currentTimeMillis());

		//this.netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT,	wiredZoneTransfer.getNeighbourToRoute().getMacAddress());
		this.udp.send(ipMsg, ipDest, PORT, PORT, Constants.NET_PRIORITY_NORMAL);
		
		try {
//			while(socket == null){
			try {
				// trying to connect every 2 seconds until connection is established
				// socket = new Socket(wiredZoneTransfer.getNeighbourToRoute().getHostname(),
				// 49161);
			} catch (Exception e) {
				try {
					n++;
					if (n == 3) {
						PeerInfo sourceInfo = new PeerInfo();
						sourceInfo.setHostName(wiredZoneTransfer.getNeighbourToRoute().getHostname());
						sourceInfo.setIpAddress(wiredZoneTransfer.getNeighbourToRoute().getIpAddress());
						String statusMessage = "FAILURE : Hostname does not exist in the netork.\n";
						WiredFailure connectionFailure = new WiredFailure(CommandType.LEAVE, sourceInfo, statusMessage);
						Utils.printErrorMessage(connectionFailure.toString());
						// break;
					}
					Utils.printErrorMessage("Couldn't connect. Trying again...");
					// Thread.sleep(2000);
				} catch (Exception ie) {
					ie.printStackTrace();
				}

				// }
			}
			// connection established
			// serializing wiredZoneTransfer to the neighbour
//			objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
//			objectOutputStream.writeObject(wiredZoneTransfer);
//			objectOutputStream.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
//		finally{
//			if(socket != null){
//				try{
//					socket.close();
//				}
//				catch(IOException e){
//					e.printStackTrace();
//				}
//			}
//			if(objectOutputStream != null){
//				try{
//					objectOutputStream.close();
//				}
//				catch(IOException e){
//					e.printStackTrace();
//				}
//			}
//		}

	}

	/*
	 * send TakeoverUpdate message to the neighbours
	 */
	private void send(TakeoverUpdate takeoverUpdate) {
		int n = 0;

		NetAddress ipDest = new NetAddress(takeoverUpdate.getDestinationIpAddress());

		NetMessage.Ip ipMsg = new NetMessage.Ip(takeoverUpdate, localAddr, ipDest, Constants.NET_PROTOCOL_CAN,
				Constants.NET_PRIORITY_NORMAL, Constants.TTL_DEFAULT);

//		System.out.println("(X-)" + this.macAddress + " enviando wiredJoinUpdate para "
//				+ wiredJoinUpdate.getNeighbourToRoute().getMacAddress() + " " + System.currentTimeMillis());
		
		//this.netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT, takeoverUpdate.getDestinationMacAddress());
		this.udp.send(ipMsg, ipDest, PORT, PORT, Constants.NET_PRIORITY_NORMAL);

		try {
			// while(socket == null){
			try {
				// trying to connect every 2 seconds until connection is established
				// socket = new Socket(takeoverUpdate.getDestinationHostname(), 49161);
			} catch (Exception e) {
				try {
					n++;
					if (n == 3) {
						PeerInfo sourceInfo = new PeerInfo();
						sourceInfo.setHostName(takeoverUpdate.getUpdatedHostname());
						sourceInfo.setIpAddress(takeoverUpdate.getUpdatedIpAddress());
						String statusMessage = "FAILURE : Hostname does not exist in the netork.\n";
						WiredFailure connectionFailure = new WiredFailure(CommandType.LEAVE, sourceInfo, statusMessage);
						Utils.printErrorMessage(connectionFailure.toString());
						// break;
					}
					Utils.printErrorMessage("Couldn't connect. Trying again...");
					// Thread.sleep(2000);
				} catch (Exception ie) {
					ie.printStackTrace();
				}

				// }
			}
			// connection established
			// serializing wiredZoneTransfer to the neighbour
//			objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
//			objectOutputStream.writeObject(takeoverUpdate);
//			objectOutputStream.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
//		finally{
//			if(socket != null){
//				try{
//					socket.close();
//				}
//				catch(IOException e){
//					e.printStackTrace();
//				}
//			}
//			if(objectOutputStream != null){
//				try{
//					objectOutputStream.close();
//				}
//				catch(IOException e){
//					e.printStackTrace();
//				}
//			}
//		}

	}

	private void send(TakeoverConfirmation takeoverConfirmation) {

		int n = 0;

		NetAddress ipDest = new NetAddress(takeoverConfirmation.getDestinationIpAddress());

		NetMessage.Ip ipMsg = new NetMessage.Ip(takeoverConfirmation, localAddr, ipDest, Constants.NET_PROTOCOL_CAN,
				Constants.NET_PRIORITY_NORMAL, Constants.TTL_DEFAULT);

//		System.out.println("(X-)" + this.macAddress + " enviando wiredJoinUpdate para "
//				+ wiredJoinUpdate.getNeighbourToRoute().getMacAddress() + " " + System.currentTimeMillis());
		
		
		//this.netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT, takeoverConfirmation.getDestinationMacAddress());
		this.udp.send(ipMsg, ipDest, PORT, PORT, Constants.NET_PRIORITY_NORMAL);

		try {
			// while(socket == null){
			try {
				// trying to connect every 2 seconds until connection is established
				// socket = new Socket(takeoverConfirmation.getDestinationHostname(), 49161);
			} catch (Exception e) {
				try {
					n++;
					if (n == 3) {
						PeerInfo sourceInfo = new PeerInfo();
						sourceInfo.setHostName(this.getHostName());
						sourceInfo.setIpAddress(this.getIPaddress());
						String statusMessage = "FAILURE : Hostname does not exist in the netork.\n";
						WiredFailure connectionFailure = new WiredFailure(CommandType.LEAVE, sourceInfo, statusMessage);
						Utils.printErrorMessage(connectionFailure.toString());
						// break;
					}
					Utils.printErrorMessage("Couldn't connect. Trying again...");
					Thread.sleep(2000);

				} catch (InterruptedException ie) {
					ie.printStackTrace();
				}

				// }
			}
			// connection established
			// serializing wiredZoneTransfer to the neighbour
//			objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
//			objectOutputStream.writeObject(takeoverConfirmation);
//			objectOutputStream.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
//		finally{
//			if(socket != null){
//				try{
//					socket.close();
//				}
//				catch(IOException e){
//					e.printStackTrace();
//				}
//			}
//			if(objectOutputStream != null){
//				try{
//					objectOutputStream.close();
//				}
//				catch(IOException e){
//					e.printStackTrace();
//				}
//			}
//		}
	}

	private void send(LeaveUpdateNeighbours leaveUpdateNeighbours) {

		int n = 0;

		NetAddress ipDest = new NetAddress(leaveUpdateNeighbours.getDestinationIpAddress());

		NetMessage.Ip ipMsg = new NetMessage.Ip(leaveUpdateNeighbours, localAddr, ipDest, Constants.NET_PROTOCOL_CAN,
				Constants.NET_PRIORITY_NORMAL, Constants.TTL_DEFAULT);

		System.out.println("(j)Enviando leaveUpdateNeighbours para: " + leaveUpdateNeighbours.getDestinationMacAddress());
		
		
		//this.netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT, leaveUpdateNeighbours.getDestinationMacAddress());
		this.udp.send(ipMsg, ipDest, PORT, PORT, Constants.NET_PRIORITY_NORMAL);
		
		
		try {
			// while(socket == null){
			// trying to connect every 2 seconds until connection is established
			try {
				// socket = new Socket(leaveUpdateNeighbours.getDestinationHostName(), 49161);
			} catch (Exception e) {
				try {
					n++;
					if (n == 3) {
						PeerInfo sourceInfo = new PeerInfo();
						sourceInfo.setHostName(leaveUpdateNeighbours.getDestinationHostName());
						sourceInfo.setIpAddress(leaveUpdateNeighbours.getDestinationIpAddress());
						String statusMessage = "FAILURE : Hostname does not exist in the netork.\n";
						WiredFailure connectionFailure = new WiredFailure(CommandType.LEAVE, sourceInfo, statusMessage);
						Utils.printErrorMessage(connectionFailure.toString());

						Peer.possibleCommands.put(CommandType.INSERT, true);
						Peer.possibleCommands.put(CommandType.SEARCH, true);
						Peer.possibleCommands.put(CommandType.VIEW, true);
						Peer.possibleCommands.put(CommandType.JOIN, false);
						Peer.possibleCommands.put(CommandType.LEAVE, true);

						// break;
					}
					Utils.printErrorMessage("Couldn't connect. Trying again...");
					// Thread.sleep(2000);
				} catch (Exception ie) {
					ie.printStackTrace();
				}
				// }
			}
			// serializing leaveUpdateNeighbours to the neighbours from the leaving node
//			objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
//			objectOutputStream.writeObject(leaveUpdateNeighbours);
//			objectOutputStream.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
//		finally{
//			if(socket != null){
//				try{
//					socket.close();
//				}
//				catch(IOException e){
//					e.printStackTrace();
//				}
//			}
//			if(objectOutputStream != null){
//				try{
//					objectOutputStream.close();
//				}
//				catch(IOException e){
//					e.printStackTrace();
//				}
//			}
//		}
	}

	private void send(WiredSuccess wiredSuccess) {

		int n = 0;

		NetAddress ipDest = new NetAddress(wiredSuccess.getSourcePeer().getIpAddress());

		NetMessage.Ip ipMsg = new NetMessage.Ip(wiredSuccess, localAddr, ipDest, Constants.NET_PROTOCOL_CAN,
				Constants.NET_PRIORITY_NORMAL, Constants.TTL_DEFAULT);

		System.out.println("(0)Enviando wiredSuccess para: " + wiredSuccess.getSourcePeer().getMacAddress());
		
		
		//this.netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT, wiredSuccess.getSourcePeer().getMacAddress());
		this.udp.send(ipMsg, ipDest, PORT, PORT, Constants.NET_PRIORITY_NORMAL);

		try {
			// while(socket == null){
			// trying to connect every 2 seconds until connection is established
			try {
				// socket = new Socket(wiredSuccess.getSourcePeer().getHostname(), 49161);
			} catch (Exception e) {
				try {
					n++;
					if (n == 3) {
						PeerInfo sourceInfo = new PeerInfo();
						sourceInfo.setHostName(wiredSuccess.getSourcePeer().getHostname());
						sourceInfo.setIpAddress(wiredSuccess.getSourcePeer().getIpAddress());
						String statusMessage = "FAILURE : Hostname does not exist in the netork.\n";
						WiredFailure connectionFailure = new WiredFailure(wiredSuccess.getCommand(), sourceInfo,
								statusMessage);
						Utils.printErrorMessage(connectionFailure.toString());
						// break;
					}
					Utils.printErrorMessage("Couldn't connect. Trying again...");
					Thread.sleep(2000);
				} catch (Exception ie) {
					ie.printStackTrace();
				}
				// }
			}
			// serializing wiredSuccess back to the source node
//			objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
//			objectOutputStream.writeObject(wiredSuccess);
//			objectOutputStream.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
//		finally{
//			if(socket != null){
//				try{
//					socket.close();
//				}
//				catch(IOException e){
//					e.printStackTrace();
//				}
//			}
//			if(objectOutputStream != null){
//				try{
//					objectOutputStream.close();
//				}
//				catch(IOException e){
//					e.printStackTrace();
//				}
//			}
//		}
	}

	/*
	 * functions to send messages for each type of object passed
	 */
	private void send(WiredInsert wiredInsert) {
		int n = 0;

		NetAddress ipDest = new NetAddress(wiredInsert.getNeighbourToRoute().getIpAddress());

		NetMessage.Ip ipMsg = new NetMessage.Ip(wiredInsert, localAddr, ipDest, Constants.NET_PROTOCOL_CAN,
				Constants.NET_PRIORITY_NORMAL, Constants.TTL_DEFAULT);

		System.out.println(
				"(send) "+this.macAddress+" Enviando wiredInsert para: " + wiredInsert.getNeighbourToRoute().getMacAddress());
		
		
		//this.netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT, wiredInsert.getNeighbourToRoute().getMacAddress());
		this.udp.send(ipMsg, ipDest, PORT, PORT, Constants.NET_PRIORITY_NORMAL);

		// NeighbourInfo neighbourToRoute = wiredInsert.getNeighbourToRoute();
		try {

			// trying to connect every 2 seconds until connection is established
			// while(socket == null){
			try {
				// socket = new Socket(neighbourToRoute.getHostname(), 49161);
			} catch (Exception e) {
				try {
					n++;
					if (n == 3) {
						PeerInfo sourceInfo = new PeerInfo();
						sourceInfo.setHostName(wiredInsert.getNeighbourToRoute().getHostname());
						sourceInfo.setIpAddress(wiredInsert.getNeighbourToRoute().getIpAddress());
						String statusMessage = "FAILURE : Hostname does not exist in the netork.\n";
						WiredFailure connectionFailure = new WiredFailure(CommandType.INSERT, sourceInfo,
								statusMessage);
						Utils.printErrorMessage(connectionFailure.toString());
						// break;
					}
					Utils.printErrorMessage("Couldn't connect. Trying again...");
					Thread.sleep(2000);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				// }
			}
			// connection established
			// seriliazing wiredInsert
//			objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
//			objectOutputStream.writeObject(wiredInsert);
//			objectOutputStream.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
//		finally{
//			if(socket != null){
//				try{
//					socket.close();
//				}
//				catch(IOException e){
//					e.printStackTrace();
//				}
//			}
//			if(objectOutputStream != null){
//				try{
//					objectOutputStream.close();
//				}
//				catch(IOException e){
//					e.printStackTrace();
//				}
//			}
//		}
	}
	
	
	private void send(TemporaryZoneReleaseUpdateNeighbours tempZoneReleaseUpdateNeighbours){

		int n = 0;
		
		NetAddress ipDest = new NetAddress(tempZoneReleaseUpdateNeighbours.getIpAddressToRoute());

		NetMessage.Ip ipMsg = new NetMessage.Ip(tempZoneReleaseUpdateNeighbours, localAddr, ipDest, Constants.NET_PROTOCOL_CAN,
				Constants.NET_PRIORITY_NORMAL, Constants.TTL_DEFAULT);

		System.out.println(
				"(send)Enviando tempZoneReleaseUpdateNeighbours para: " + tempZoneReleaseUpdateNeighbours.getMacAddressToRoute());
		
		//this.netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT, tempZoneReleaseUpdateNeighbours.getMacAddressToRoute());
		this.udp.send(ipMsg, ipDest, PORT, PORT, Constants.NET_PRIORITY_NORMAL);

		
		try{
			//while(socket == null){
				//trying to connect every 2 seconds until connection is established
				try{
					//socket = new Socket(tempZoneReleaseUpdateNeighbours.getHostnameToRoute(), 49161);
				}
				catch(Exception e){
					try{
						n++;
						if(n == 3){
							PeerInfo sourceInfo = new PeerInfo();
							sourceInfo.setHostName(tempZoneReleaseUpdateNeighbours.getHostnameToRoute());
							sourceInfo.setIpAddress(tempZoneReleaseUpdateNeighbours.getIpAddressToRoute());
							String statusMessage = "FAILURE : Hostname does not exist in the netork.\n";
							WiredFailure connectionFailure = new WiredFailure(CommandType.LEAVE, sourceInfo, statusMessage);
							Utils.printErrorMessage(connectionFailure.toString());
							//break;
						}
						Utils.printErrorMessage("Couldn't connect. Trying again...");
						Thread.sleep(2000);
					}
					catch(Exception ie){
						ie.printStackTrace();
					}
				//}
			}
			//serializing wiredJoinUpdate from affected neighbour (whose zone has gotten split) to its neighbours
//			objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
//			objectOutputStream.writeObject(tempZoneReleaseUpdateNeighbours);
//			objectOutputStream.flush();

		}
		catch(Exception e){
			e.printStackTrace();
		}
//		finally{
//			if(socket != null){
//				try{
//					socket.close();
//				}
//				catch(IOException e){
//					e.printStackTrace();
//				}
//			}
//			if(objectOutputStream != null){
//				try{
//					objectOutputStream.close();
//				}
//				catch(IOException e){
//					e.printStackTrace();
//				}
//			}
//		}
	}

	// public interface ServerInterface extends JistAPI.Proxiable {
	// /** Starts the server. */
	// void run();
	//
	// }
	//
	private void send(WiredSearch wiredSearch){
		int n = 0;
		//NeighbourInfo neighbourToRoute = wiredSearch.getNeighbourToRoute();

		
		NetAddress ipDest = new NetAddress(wiredSearch.getNeighbourToRoute().getIpAddress());

		NetMessage.Ip ipMsg = new NetMessage.Ip(wiredSearch, localAddr, ipDest, Constants.NET_PROTOCOL_CAN,
				Constants.NET_PRIORITY_NORMAL, Constants.TTL_DEFAULT);

		System.out.println(
				"(k)Enviando wiredSearch para: " + wiredSearch.getNeighbourToRoute().getMacAddress());
		
		
		//this.netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT, wiredSearch.getNeighbourToRoute().getMacAddress());
		this.udp.send(ipMsg, ipDest, PORT, PORT, Constants.NET_PRIORITY_NORMAL);

		
		
		try{
			//while(socket == null){
				//trying to connect every 2 seconds until connection is established
				try{
					//socket = new Socket(neighbourToRoute.getHostname(),49161);
				}
				catch(Exception e){
					try{
						n++;
						if(n == 3){
							PeerInfo sourceInfo = new PeerInfo();
							sourceInfo.setHostName(wiredSearch.getSourceInfo().getHostname());
							sourceInfo.setIpAddress(wiredSearch.getSourceInfo().getIpAddress());
							String statusMessage = "FAILURE : Hostname does not exist in the netork.\n";
							WiredFailure connectionFailure = new WiredFailure(CommandType.SEARCH, sourceInfo, statusMessage);
							Utils.printErrorMessage(connectionFailure.toString());
							//break;
						}
						Utils.printErrorMessage("Couldn't connect. Trying again...");
						Thread.sleep(2000);
					}
					catch(InterruptedException ie){
						ie.printStackTrace();
					}
				//}
			}
			//connection established
			//serializing wiredSearch
//			objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
//			objectOutputStream.writeObject(wiredSearch);

		}
		catch(Exception e){
			e.printStackTrace();
		}
//		finally{
//			if(socket != null){
//				try{
//					socket.close();
//				}
//				catch(IOException e){
//					e.printStackTrace();
//				}
//			}
//			if(objectOutputStream != null){
//				try{
//					objectOutputStream.close();
//				}
//				catch(IOException e){
//					e.printStackTrace();
//				}
//			}
//		}
	}
	
	
	private void send(WiredFailure wiredFailure){

		int n = 0;
		
		NetAddress ipDest = new NetAddress(wiredFailure.getSourceInfo().getIpAddress());

		NetMessage.Ip ipMsg = new NetMessage.Ip(wiredFailure, localAddr, ipDest, Constants.NET_PROTOCOL_CAN,
				Constants.NET_PRIORITY_NORMAL, Constants.TTL_DEFAULT);

		
		
		//this.netEntity.send(ipMsg, Constants.NET_INTERFACE_DEFAULT, wiredFailure.getSourceInfo().getMacAddress());
		this.udp.send(ipMsg, ipDest, PORT, PORT, Constants.NET_PRIORITY_NORMAL);

		
		try{
			//while(socket == null){
				//trying to connect every 2 seconds until connection is established
				try{
					//socket = new Socket(wiredFailure.getSourceInfo().getHostname(),49161);
				}
				catch(Exception e){
					try{
						n++;
						if(n == 3){
							PeerInfo sourceInfo = new PeerInfo();
							sourceInfo.setHostName(wiredFailure.getSourceInfo().getHostname());
							sourceInfo.setIpAddress(wiredFailure.getSourceInfo().getIpAddress());
							String statusMessage = "FAILURE : Hostname does not exist in the netork.\n";
							WiredFailure connectionFailure = new WiredFailure(wiredFailure.getCommand(), sourceInfo, statusMessage);
							Utils.printErrorMessage(connectionFailure.toString());
							//break;
						}
						Utils.printErrorMessage("Couldn't connect. Trying again...");
						Thread.sleep(2000);
					}
					catch(InterruptedException ie){
						ie.printStackTrace();
					}
			//	}
			}
			//serializing wiredFailure back to the source node
//			objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
//			objectOutputStream.writeObject(wiredFailure);
//			objectOutputStream.flush();
		}
		catch(Exception e){
			e.printStackTrace();
		}
//		finally{
//			if(socket != null){
//				try{
//					socket.close();
//				}
//				catch(IOException e){
//					e.printStackTrace();
//				}
//			}
//			if(objectOutputStream != null){
//				try{
//					objectOutputStream.close();
//				}
//				catch(IOException e){
//					e.printStackTrace();
//				}
//			}
//		}
	}
	
	
	public static class RevisedReceive implements AppJava.Runnable {
		private Message msg;
		private NetAddress src;
		private MacAddress lastHop;
		private byte macId;
		private NetAddress dst;
		private byte priority;
		private byte ttl;
		private Peer peer;

		public RevisedReceive(Message msg, NetAddress src, MacAddress lastHop, byte macId, NetAddress dst,
				byte priority, byte ttl, Peer peer) {

			this.msg = msg;
			this.src = src;
			this.lastHop = lastHop;
			this.macId = macId;
			this.dst = dst;
			this.priority = priority;
			this.ttl = ttl;
			this.peer = peer;

		}

		@Override
		public void run() {
			// TODO Auto-generated method stub

			// System.out.println("AKI na thread at "+JistAPI.getTime());

			Object wiredObject = msg;

			if (wiredObject instanceof WiredInsert) {
				System.out.println(this.peer.macAddress + " recebendo WiredInsert de " + this.lastHop + " "
						+ System.currentTimeMillis());
				
				
				WiredInsert wiredInsert = (WiredInsert) wiredObject;

				this.peer.insert(wiredInsert);
			} else if (wiredObject instanceof WiredSearch) {
				System.out.println(this.peer.macAddress + " recebendo WiredSearch de " + this.lastHop + " "
						+ System.currentTimeMillis());
				
				WiredSearch wiredSearch = (WiredSearch) wiredObject;
				this.peer.search(wiredSearch);
			} else if (wiredObject instanceof WiredJoin) {
				System.out.println(this.peer.macAddress + " recebendo WiredJoin de " + this.lastHop + " "
						+ System.currentTimeMillis());
				
				WiredJoin wiredJoin = (WiredJoin) wiredObject;

				this.peer.join(wiredJoin, lastHop);
			} else if (wiredObject instanceof JoinUpdateNeighbours) {
				System.out.println(this.peer.macAddress + " recebendo JoinUpdateNeighbours de " + this.lastHop + " "
						+ System.currentTimeMillis());
				
				JoinUpdateNeighbours joinUpdateNeighbours = (JoinUpdateNeighbours) wiredObject;

//				System.out.println(this.peer.macAddress + " recebendo joinUpdateNeighbours de "
//						+ joinUpdateNeighbours.getActivePeerMacAddress() + " " + System.currentTimeMillis());

				//JistAPI.sleep(10000);
				this.peer.updateRoutingTableForNewNode(joinUpdateNeighbours);
			} else if (wiredObject instanceof TemporaryZoneReleaseUpdateNeighbours) {
				System.out.println(this.peer.macAddress + " recebendo TemporaryZoneReleaseUpdateNeighbours de " + this.lastHop + " "
						+ System.currentTimeMillis());
				
				TemporaryZoneReleaseUpdateNeighbours temporaryZoneReleaseUpdateNeighbours = (TemporaryZoneReleaseUpdateNeighbours) wiredObject;

//				System.out.println(this.peer.macAddress + " recebendo temporaryZoneReleaseUpdateNeighbours de "
//						+ System.currentTimeMillis());

				this.peer.updateTempZoneRelease(temporaryZoneReleaseUpdateNeighbours);
			} else if (wiredObject instanceof JoinUpdateBootstrap) {
				System.out.println(this.peer.macAddress + " recebendo JoinUpdateBootstrap de " + this.lastHop + " "
						+ System.currentTimeMillis());
				
				JoinUpdateBootstrap joinUpdateBootstrap = (JoinUpdateBootstrap) wiredObject;
 

				this.peer.updateActivePeers(joinUpdateBootstrap);

			} else if (wiredObject instanceof JoinConfirmation) {
				System.out.println(this.peer.macAddress + " recebendo JoinConfirmation de " + this.lastHop + " "
						+ System.currentTimeMillis());
				
				JoinConfirmation joinConfirmation = (JoinConfirmation) wiredObject;

//				System.out.println(this.peer.macAddress + " recebendo joinConfirmation de "
//						+ joinConfirmation.getSourceMaccAddres() +" "+ System.currentTimeMillis());

				this.peer.initializeState(joinConfirmation);
			} else if (wiredObject instanceof LeaveUpdateBootstrap) {
				System.out.println(this.peer.macAddress + " recebendo LeaveUpdateBootstrap de " + this.lastHop + " "
						+ System.currentTimeMillis());
				
				LeaveUpdateBootstrap leaveUpdateBootstrap = (LeaveUpdateBootstrap) wiredObject;
				this.peer.removeActivePeerEntry(leaveUpdateBootstrap);
			} else if (wiredObject instanceof LeaveUpdateNeighbours) {
				System.out.println(this.peer.macAddress + " recebendo LeaveUpdateNeighbours de " + this.lastHop + " "
						+ System.currentTimeMillis());
				
				LeaveUpdateNeighbours leaveUpdateNeighbours = (LeaveUpdateNeighbours) wiredObject;

				 
				this.peer.removeNeighbourFromRoutingTable(leaveUpdateNeighbours);
			} else if (wiredObject instanceof TakeoverUpdate) {
				System.out.println(this.peer.macAddress + " recebendo TakeoverUpdate de " + this.lastHop + " "
						+ System.currentTimeMillis());
				
				TakeoverUpdate takeoverUpdate = (TakeoverUpdate) wiredObject;
				this.peer.updateNeighbourState(takeoverUpdate);
			} else if (wiredObject instanceof TakeoverConfirmation) {
				System.out.println(this.peer.macAddress + " recebendo TakeoverConfirmation de " + this.lastHop + " "
						+ System.currentTimeMillis());
				
				this.peer.deinitializeState();
			} else if (wiredObject instanceof WiredZoneTransfer) {
				System.out.println(this.peer.macAddress + " recebendo WiredZoneTransfer de " + this.lastHop + " "
						+ System.currentTimeMillis());
				
				WiredZoneTransfer wiredZoneTransfer = (WiredZoneTransfer) wiredObject;
				this.peer.takeover(wiredZoneTransfer);
			} else if (wiredObject instanceof WiredViewActivePeersRequest) {
				System.out.println(this.peer.macAddress + " recebendo WiredViewActivePeersRequest de " + this.lastHop + " "
						+ System.currentTimeMillis());
				
				WiredViewActivePeersRequest activePeersRequest = (WiredViewActivePeersRequest) wiredObject;
				try {
					if (this.peer.isBootstrap()) {
						this.peer.retrieveActivePeers(activePeersRequest);
					} else {
						this.peer.forwardWiredView(activePeersRequest);
					}
				} catch (UnknownHostException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				// } else if (wiredObject instanceof WiredView) {
				//
				// WiredView view = (WiredView) wiredObject;
				// if (view.getViewCategory().equals(ViewCategory.MULTI)) {
				// if (view.getSourceHostname().equals(this.peer.getHostName())) {
				// this.peer.peerInformation.add(view.getPeerInformation());
				// this.peer.peerviewsReturned++;
				// StringBuilder builder = new StringBuilder("");
				// if (this.peer.viewsReturned == totalViewsRequired) {
				//
				// for (String peerInfo : peerInformation) {
				// builder.append(peerInfo);
				// }
				//
				// // adding current peer's information to builder
				// builder.append(this.toString());
				//
				// Utils.printToConsole(builder.toString());
				// totalViewsRequired = 0;
				// viewsReturned = 1;
				// peerInformation.clear();
				// }
				// } else {
				// this.retrievePeerInformation(view);
				// }
				// } else {
				// if (this.getHostName().equals(view.getSourceHostname())) {
				// Utils.printToConsole(view.getPeerInformation());
				// } else {
				// this.retrievePeerInformation(view);
				// }
				// }

			} else if (wiredObject instanceof WiredSuccess) {
				System.out.println(this.peer.macAddress + " recebendo WiredSuccess de " + this.lastHop + " "
						+ System.currentTimeMillis());
				
				WiredSuccess wiredSuccess = (WiredSuccess) wiredObject;
				Utils.printToConsole(wiredSuccess.toString());
				
				if (wiredSuccess.getStatusMessage() == "Search successful") {
					CAN can = new CAN();
					can.fimSimulacao();
				}
			} else if (wiredObject instanceof WiredFailure) {
				System.out.println(this.peer.macAddress + " recebendo WiredFailure de " + this.lastHop + " "
						+ System.currentTimeMillis());
				
				WiredFailure wiredFailure = (WiredFailure) wiredObject;
				Utils.printErrorMessage(wiredFailure.toString());
			}

		}

	}



	@Override
	public void receive(Message msg, NetAddress src, MacAddress lastHop, byte macId, NetAddress dst, byte priority,
			byte ttl) {
		java.util.Date d = new Date();
		
		AppJava.Runnable worker = new RevisedReceive(msg, src, lastHop, macId, dst, priority, ttl, this);
		AppJava.Runnable workerEntity = (AppJava.Runnable) JistAPI.proxy(worker, AppJava.Runnable.class);
		Thread thread = new Thread(workerEntity);

		thread.start();

	}
	

	public void listening(Peer peer) 
	{
	//	System.out.println("Start Listening here at: "+this.localAddr);
		
		setInstance(peer);
		TransInterface.SocketHandler handler = new TransInterface.SocketHandler() {
			

			public void receive(Message msg, NetAddress src, MacAddress lastHop, byte macId, NetAddress dst, byte priority,
					byte ttl) {
				
				try {
					Peer peer = getInstance();
				} catch (UnknownHostException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				
				if(!(peer.compareMessages(msg))) {
					setMessageReceived(msg);
					Object msgRec  = ((NetMessage.Ip) msg).getPayload();	
					
					AppJava.Runnable worker = new RevisedReceive((Message)msgRec, src, lastHop, macId, dst, priority, ttl, peer);
					AppJava.Runnable workerEntity = (AppJava.Runnable) JistAPI.proxy(worker, AppJava.Runnable.class);
					Thread thread = new Thread(workerEntity);
					thread.start();
				}else {
					System.out.println(peer.getHostName()+" - **************************MSG REPETIDA****************************");
				}
				
			}
 
		};

		udp.addSocketHandler(3001, handler);
	}
	 
 
	 

	/** {@inheritDoc} */
	public int getPeerLimit() {
		// TODO Auto-generated method stub
		return this.peerLimit;
	}

	/** {@inheritDoc} */
	public void setPeerLimit(int peerLimit) {
		// TODO Auto-generated method stub
		this.peerLimit = peerLimit;
	}

	@Override
	public void start() {
		// TODO Auto-generated method stub

	}

	public List<String> getPeerInformation() {

		return peerInformation;
	}

	public void setTotalViewsRequired(int n) {

		totalViewsRequired = n;
	}

	@Override
	public void send(NetMessage msg) {
		// TODO Auto-generated method stub

	}

}
