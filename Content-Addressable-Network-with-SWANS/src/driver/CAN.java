//////////////////////////////////////////////////
// JIST (Java In Simulation Time) Project
// Timestamp: <CBR.java Tue 2004/04/06 11:57:22 barr pompom.cs.cornell.edu>
//

// Copyright (C) 2004 by Cornell University
// All rights reserved.
// Refer to LICENSE for terms and conditions of use.

package driver;

import jist.swans.field.Field;
import jist.swans.field.Mobility;
import jist.swans.field.Placement;
import jist.swans.radio.RadioNoise;
import jist.swans.radio.RadioNoiseIndep;
import jist.swans.radio.RadioInfo;
import jist.swans.mac.MacAddress;
import jist.swans.mac.MacDumb;
import jist.swans.net.NetAddress;
import jist.swans.net.NetIp;
import jist.swans.net.PacketLoss;
import jist.swans.trans.TransUdp;
import jist.swans.trans.TransInterface;
import jist.swans.route.RouteInterface;
import jist.swans.route.RouteDsr;
import jist.swans.route.RouteAodv;
import jist.swans.route.RouteZrp;
import jist.swans.route.RouteZrpNdp;
import jist.swans.route.RouteZrpIarp;
import jist.swans.route.RouteZrpBrp;
import jist.swans.route.RouteZrpIerp;
import jist.swans.route.RouteZrpZdp;
import jist.swans.misc.Util;
import jist.swans.misc.Mapper;
import jist.swans.misc.Location;
import jist.swans.misc.Message;
import jist.swans.misc.MessageBytes;
import jist.swans.Constants;

import jist.runtime.JistAPI;

import jargs.gnu.*;

import java.io.FileWriter;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.*;

import com.can.nodes.Peer;
import com.puppycrawl.tools.checkstyle.checks.UpperEllCheck;
import com.ziclix.python.sql.util.Queue;

import csvMaker.*;
//import driver.CBR.Client;

/**
 * Constant Bit Rate simulation program. This program creates a field and places
 * nodes randomly throughout it. Some of the nodes are designated as
 * client-server pairs, and these pairs transmit packets to each other for the
 * duration of the simulation. It is possible to specify, among other things,
 * the routing protocol used, the packet loss probability, and the rate of node
 * movement.
 *
 * @author Ben Viglietta
 * @author Rimon Barr
 * 
 */

public class CAN {
	/** Default port number to send and receive packets. */
	private static final int PORT = 3001;

	//////////////////////////////////////////////////
	// locals
	//

	/** The number of clients currently transmitting. */
	private static int numClientsTransmitting = 0;

	//////////////////////////////////////////////////
	// server
	//

	public static Structure coleta = new Structure();

	private static int msgsReceived = 0;

	private static int msgsSent = 0;

	private static CommandLineOptions params;

	private static long startTime;

	private static Pairs pair = new Pairs();

	private static ArrayList canPeers = new ArrayList();
	
	private static Peer CANInsertPeer;
	
	private static Peer CANLeavePeer;
	
	private static Peer CANSearchPeer;

	/**
	 * The interface for server nodes in the simulation.
	 */
	public interface ServerInterface extends JistAPI.Proxiable {
		/** Starts the server. */
		void run();

	}

	/**
	 * A server node in the simulation. This node simply listens for incoming
	 * packets and prints a message for each one that it receives.
	 */
	public static class Server implements ServerInterface {
		/** The proxy interface for this object. */
		private ServerInterface self;
		/** The UDP interface used by this node. */
		private TransInterface.TransUdpInterface udp;
		/** The IP address of this node. */
		private NetAddress localAddr;
		/** The number of packets this node has received. */
		private int packetsReceived;

		/**
		 * Creates a new <code>Server</code>.
		 *
		 * @param udp       the UDP interface used by this node
		 * @param localAddr the IP address of this node
		 */
		public Server(TransInterface.TransUdpInterface udp, NetAddress localAddr) {
			this.self = (ServerInterface) JistAPI.proxy(this, ServerInterface.class);
			this.udp = udp;
			this.localAddr = localAddr;
			this.packetsReceived = 0;

		}

		/**
		 * Returns the proxy interface for this object.
		 *
		 * @return the proxy interface for this object.
		 */
		public ServerInterface getProxy() {
			return self;
		}

		/** {@inheritDoc} */
		public void run() {
			// Create a UDP event handler that just makes a note of incoming packets
			TransInterface.SocketHandler handler = new TransInterface.SocketHandler() {
				public void receive(Message msg, NetAddress src, MacAddress lastHop, byte macId, NetAddress dst,
						byte priority, byte ttl) {
					int msgNum = (++packetsReceived);
					System.out.println("***Received message " + msgNum + " from " + src + " Here at: " + localAddr);

					msgsReceived++;

					if (msgsReceived == msgsSent) {
						fimSimulacao();

//						// ************************************************ REGISTRANDO O TEMPO
//						// DECORRIDO **********************************************//
//						long stopTime = System.currentTimeMillis();
//						long elapsedTime = stopTime - startTime;
//						registrar(1, elapsedTime + "");
//						System.out.println("Fim: " + stopTime);
//						System.out.println("Decorrido = " + elapsedTime);
//
//						// ************************************************ REGISTRANDO O TEMPO
//						// DECORRIDO **********************************************//
//
//						// ***************************************** REGISTRANDO A DIST�NCIA ENTRE OS
//						// N�S **********************************************//
//						registrar(4, pair.locationOrigem.distance(pair.locationDestino) + "");
//						System.out.println("Origem: " + pair.locationOrigem);
//						System.out.println("Destino: " + pair.locationDestino);
//						System.out.println("distancia: " + pair.locationOrigem.distance(pair.locationDestino) + "");
//						// ***************************************** REGISTRANDO A DIST�NCIA ENTRE OS
//						// N�S **********************************************//
//
//						CSVMaker csv = new CSVMaker();
//						// roteamento-dimensao-# Nodes-loss-movement.csv
//						String fileName = csv.fileNameFormat(params.protocol, params.field.getX(), params.field.getY(),
//								params.nodes, params.lossOpts, params.mobilityOpts + "");
//						csv.makeFile(coleta, fileName);

					}
				}
			};

			udp.addSocketHandler(PORT, handler);
		}
	}

	//////////////////////////////////////////////////
	// client
	//

	/**
	 * The interface for client nodes in the simulation.
	 */
	public interface ClientInterface extends JistAPI.Proxiable {
		/** Starts the client. */
		void run();

		/**
		 * Sends message number <code>i</code> to this client's corresponding server.
		 *
		 * @param i the number of the message to send
		 */
		void sendMessage(int i);
	}

	/**
	 * A client node in the simulation. Each client node sends
	 * <code>numTransmissions</code> packets to its corresponding server at a rate
	 * of one per second, then terminates.
	 */
	public static class Client implements ClientInterface {
		/** self-referencing proxy entity. */
		private ClientInterface self;
		/** UDP entity used by this node. */
		private TransInterface.TransUdpInterface udp;
		/** local IP address. */
		private NetAddress localAddr;
		/** address of the server that corresponds to this client. */
		private NetAddress serverAddr;
		/** number of outgoing transmissions. */
		private int transmissions;
		/** instance of CAN when simulating it. */
		private Peer CAN;
 
		/**
		 * Creates a new <code>Client</code>.
		 *
		 * @param udp           the UDP interface for this node to use
		 * @param transmissions number of outgoing tranmissions
		 * @param localAddr     this node's IP address
		 * @param serverAddr    the IP address of the server to send messages to
		 */
		public Client(TransInterface.TransUdpInterface udp, int transmissions, NetAddress localAddr,
				NetAddress serverAddr, Peer CAN) {
			self = (ClientInterface) JistAPI.proxy(this, ClientInterface.class);
			this.udp = udp;
			this.localAddr = localAddr;
			this.serverAddr = serverAddr;
			this.transmissions = transmissions;
			msgsSent += transmissions;
			this.CAN = CAN;
		}

		/**
		 * Returns this object's proxy interface.
		 *
		 * @return this object's proxy interface
		 */
		public ClientInterface getProxy() {
			return self;
		}

		/** {@inheritDoc} */
		public void run() {
			// if (this.CAN != null) {
			//////////////////////////////////////////////// **INICIA O
			//////////////////////////////////////////////// TIMER PARA A
			// CAN**/////////////////////////////////////////////
			startTime = System.currentTimeMillis();
			System.out.println("In�cio: " + startTime);
			//////////////////////////////////////////////////////////////////////////////////////////////////////////////

			// this.CAN.leave();
			// JistAPI.sleep(100000000);
			// this.CAN.insert(null);
			// JistAPI.sleep(1000000000);
			// this.CAN.search(null);
			// } else {
			JistAPI.sleep(100 * Constants.SECOND);
			// Send the appropriate number of messages to the corresponding server
			for (int i = 0; i < transmissions; i++) {
				JistAPI.sleep(20 * Constants.SECOND);
				JistAPI.sleep(Util.randomTime(5 * Constants.SECOND));
				self.sendMessage(i + 1);
			}
			// }
		}

		/** {@inheritDoc} */
		public void sendMessage(int i) {
			//////////////////////////////////////////////// **INICIA O
			//////////////////////////////////////////////// TIMER**/////////////////////////////////////////////
			startTime = System.currentTimeMillis();
			System.out.println("In�cio: " + startTime);
			//////////////////////////////////////////////////////////////////////////////////////////////////////////////
			MessageBytes msg = new MessageBytes("message");
			udp.send(msg, serverAddr, PORT, PORT, Constants.NET_PRIORITY_NORMAL);
			if (i == transmissions) {
				numClientsTransmitting--;
			}
		}
	}

	//////////////////////////////////////////////////
	// command-line options
	//

	/** Simulation parameters with default values. */
	private static class CommandLineOptions {
		/** Whether to print a usage statement. */
		private boolean help = false;
		/** Time to end simulation. */
		private int endTime = 1000;
		/** Routing protocol to use. */
		private int protocol = Constants.NET_PROTOCOL_ZRP;
		/** Routing protocol options. */
		private String protocolOpts = "";
		/** Number of nodes. */
		private int nodes = 100;
		/** Field dimensions (in meters). */
		private Location.Location2D field = new Location.Location2D(1000, 1000);
		/** Node placement model. */
		private int placement = Constants.PLACEMENT_RANDOM;
		/** Node placement options. */
		private String placementOpts = "";
		/** Node mobility model. */
		private int mobility = Constants.MOBILITY_STATIC;
		/** Node mobility options. */
		private String mobilityOpts = "";
		/** Packet loss model. */
		private int loss = Constants.NET_LOSS_NONE;
		/** Packet loss options. */
		private String lossOpts = "";
		/** Number of client-server pairs. */
		private int clients = 10;
		/** Number of transmissions for each client-server pair. */
		private int transmissions = 10;
		/** Random number generator seed. */
		private int randseed = 0;

		/** CAN Only. */
		private int canLeave = 0;
		private int canInsert = 0;
		private int canSearch = 0;
	} // class: CommandLineOptions

	/** Prints a usage statement. */
	private static void showUsage() {
		System.out.println("Usage: java driver.CBR [options]");
		System.out.println();
		System.out.println("  -h, --help           print this message");
		System.out.println("  -e, --endat          simulation ending time: [infinite]");
		System.out.println("  -p, --protocol       routing protocol: [zrp],aodv,dsr");
		System.out.println("  -n, --nodes          number of nodes: n [100] ");
		System.out.println("  -f, --field          field dimensions: x,y [100,100]");
		System.out.println("  -a, --arrange        placement model: [random],grid:ixj");
		System.out.println("  -m, --mobility       mobility model: [static],waypoint:opts,teleport:p");
		System.out.println("  -l, --loss           packet loss model: [none],uniform:p");
		System.out.println("  -c, --clients        client/server pairs: c [10]");
		System.out.println("  -t, --transmissions  transmissions per client: t [10]");
		System.out.println("  -r, --randomseed     set random seed");
		System.out.println();
	}

	/**
	 * Parses command-line arguments.
	 *
	 * @param args command-line arguments
	 * @return parsed command-line options
	 * @throws CmdLineParser.OptionException if the command-line arguments are not
	 *                                       well-formed.
	 */
	private static CommandLineOptions parseCommandLineOptions(String[] args) throws CmdLineParser.OptionException {
		CmdLineParser parser = new CmdLineParser();
		CmdLineParser.Option opt_help = parser.addBooleanOption('h', "help");
		CmdLineParser.Option opt_endat = parser.addIntegerOption('e', "endat");
		CmdLineParser.Option opt_protocol = parser.addStringOption('p', "protocol");
		CmdLineParser.Option opt_nodes = parser.addIntegerOption('n', "nodes");
		CmdLineParser.Option opt_field = parser.addStringOption('f', "field");
		CmdLineParser.Option opt_placement = parser.addStringOption('a', "arrange");
		CmdLineParser.Option opt_mobility = parser.addStringOption('m', "mobility");
		CmdLineParser.Option opt_loss = parser.addStringOption('l', "loss");
		CmdLineParser.Option opt_clients = parser.addIntegerOption('c', "clients");
		CmdLineParser.Option opt_transmissions = parser.addIntegerOption('t', "transmissions");
		CmdLineParser.Option opt_seed = parser.addIntegerOption('r', "randomseed");

		//CAN Options
		CmdLineParser.Option opt_canLeave = parser.addIntegerOption('v', "leave");
		CmdLineParser.Option opt_canInsert = parser.addIntegerOption('i', "insert");
		CmdLineParser.Option opt_canSearch = parser.addIntegerOption('s', "search");
		parser.parse(args);

		CommandLineOptions cmdOpts = new CommandLineOptions();
		// help
		if (parser.getOptionValue(opt_help) != null) {
			cmdOpts.help = true;
		}
		// endat
		if (parser.getOptionValue(opt_endat) != null) {
			cmdOpts.endTime = ((Integer) parser.getOptionValue(opt_endat)).intValue();
		}
		// protocol
		if (parser.getOptionValue(opt_protocol) != null) {
			String routeProtocolString = ((String) parser.getOptionValue(opt_protocol)).split(":")[0];
			if (routeProtocolString != null) {
				if (routeProtocolString.equalsIgnoreCase("dsr")) {
					cmdOpts.protocol = Constants.NET_PROTOCOL_DSR;
				} else if (routeProtocolString.equalsIgnoreCase("can")) {
					cmdOpts.protocol = Constants.NET_PROTOCOL_CAN;
				} else if (routeProtocolString.equalsIgnoreCase("aodv")) {
					cmdOpts.protocol = Constants.NET_PROTOCOL_AODV;
				} else if (routeProtocolString.equalsIgnoreCase("zrp")) {
					cmdOpts.protocol = Constants.NET_PROTOCOL_ZRP;
				} else {
					throw new CmdLineParser.IllegalOptionValueException(opt_protocol, "Unrecognized routing protocol");
				}
			}
			cmdOpts.protocolOpts = Util
					.stringJoin((String[]) Util.rest(((String) parser.getOptionValue(opt_protocol)).split(":")), ":");
		}
		// nodes
		if (parser.getOptionValue(opt_nodes) != null) {
			cmdOpts.nodes = ((Integer) parser.getOptionValue(opt_nodes)).intValue();
		}
		// field
		if (parser.getOptionValue(opt_field) != null) {
			cmdOpts.field = (Location.Location2D) Location.parse((String) parser.getOptionValue(opt_field));
		}
		// placement
		if (parser.getOptionValue(opt_placement) != null) {
			String placementString = ((String) parser.getOptionValue(opt_placement)).split(":")[0];
			if (placementString != null) {
				if (placementString.equalsIgnoreCase("random")) {
					cmdOpts.placement = Constants.PLACEMENT_RANDOM;
				} else if (placementString.equalsIgnoreCase("grid")) {
					cmdOpts.placement = Constants.PLACEMENT_GRID;
				} else {
					throw new CmdLineParser.IllegalOptionValueException(opt_placement, "unrecognized placement model");
				}
			}
			cmdOpts.placementOpts = Util
					.stringJoin((String[]) Util.rest(((String) parser.getOptionValue(opt_placement)).split(":")), ":");
		}
		// mobility
		if (parser.getOptionValue(opt_mobility) != null) {
			String mobilityString = ((String) parser.getOptionValue(opt_mobility)).split(":")[0];
			if (mobilityString != null) {
				if (mobilityString.equalsIgnoreCase("static")) {
					cmdOpts.mobility = Constants.MOBILITY_STATIC;
				} else if (mobilityString.equalsIgnoreCase("waypoint")) {
					cmdOpts.mobility = Constants.MOBILITY_WAYPOINT;
				} else if (mobilityString.equalsIgnoreCase("teleport")) {
					cmdOpts.mobility = Constants.MOBILITY_TELEPORT;
				} else {
					throw new CmdLineParser.IllegalOptionValueException(opt_mobility, "unrecognized mobility model");
				}
			}
			cmdOpts.mobilityOpts = Util
					.stringJoin((String[]) Util.rest(((String) parser.getOptionValue(opt_mobility)).split(":")), ":");
		}
		// loss
		if (parser.getOptionValue(opt_loss) != null) {
			String lossString = ((String) parser.getOptionValue(opt_loss)).split(":")[0];
			if (lossString != null) {
				if (lossString.equalsIgnoreCase("none")) {
					cmdOpts.loss = Constants.NET_LOSS_NONE;
				} else if (lossString.equalsIgnoreCase("uniform")) {
					cmdOpts.loss = Constants.NET_LOSS_UNIFORM;
				} else {
					throw new CmdLineParser.IllegalOptionValueException(opt_loss, "unrecognized mobility model");
				}
			}
			cmdOpts.lossOpts = Util
					.stringJoin((String[]) Util.rest(((String) parser.getOptionValue(opt_loss)).split(":")), ":");
		}
		// clients
		if (parser.getOptionValue(opt_clients) != null) {
			cmdOpts.clients = ((Integer) parser.getOptionValue(opt_clients)).intValue();
		}
		if (parser.getOptionValue(opt_transmissions) != null) {
			cmdOpts.transmissions = ((Integer) parser.getOptionValue(opt_transmissions)).intValue();
		}
		// random seed
		if (parser.getOptionValue(opt_seed) != null) {
			cmdOpts.randseed = ((Integer) parser.getOptionValue(opt_seed)).intValue();
		}

		// Make sure there are enough nodes to support numClients distinct client/server
		// pairs
		if (2 * cmdOpts.clients > cmdOpts.nodes) {
			throw new CmdLineParser.IllegalOptionValueException(opt_clients,
					"There must be at least twice as many nodes as clients");
		}
		return cmdOpts;

	} // parseCommandLineOptions

	//////////////////////////////////////////////////
	// simulation setup
	//

	/**
	 * Constructs field and nodes with given command-line options, establishes
	 * client/server pairs and starts them.
	 *
	 * @param opts command-line parameters
	 * @throws UnknownHostException
	 */
	private static void buildField(CommandLineOptions opts) throws UnknownHostException {
		ArrayList servers = new ArrayList();
		ArrayList clients = new ArrayList();

		// initialize field
		Field field = new Field(opts.field, true);
		// initialize shared radio information
		RadioInfo.RadioInfoShared radioInfo = RadioInfo.createShared(Constants.FREQUENCY_DEFAULT,
				Constants.BANDWIDTH_DEFAULT, Constants.TRANSMIT_DEFAULT, Constants.GAIN_DEFAULT,
				Util.fromDB(Constants.SENSITIVITY_DEFAULT), Util.fromDB(Constants.THRESHOLD_DEFAULT),
				Constants.TEMPERATURE_DEFAULT, Constants.TEMPERATURE_FACTOR_DEFAULT, Constants.AMBIENT_NOISE_DEFAULT);
		// initialize shared protocol mapper
		Mapper protMap = new Mapper(new int[] { Constants.NET_PROTOCOL_UDP, opts.protocol, });
		// initialize packet loss model
		PacketLoss loss = null;
		switch (opts.loss) {
		case Constants.NET_LOSS_NONE:
			loss = new PacketLoss.Zero();
			break;
		case Constants.NET_LOSS_UNIFORM:
			loss = new PacketLoss.Uniform(Double.parseDouble(opts.lossOpts));
			break;
		default:
			throw new RuntimeException("unknown packet loss model");
		}

		// Adds the bootstrap node to the total number of nodes
		if (opts.protocol == Constants.NET_PROTOCOL_CAN) {
			opts.nodes++;
		}

		// initialize node mobility model
		Mobility mobility = null;
		switch (opts.mobility) {
		case Constants.MOBILITY_STATIC:
			mobility = new Mobility.Static();
			break;
		case Constants.MOBILITY_WAYPOINT:
			mobility = new Mobility.RandomWaypoint(opts.field, opts.mobilityOpts);
			break;
		case Constants.MOBILITY_TELEPORT:
			mobility = new Mobility.Teleport(opts.field, Long.parseLong(opts.mobilityOpts));
			break;
		default:
			throw new RuntimeException("unknown node mobility model");
		}
		// initialize node placement model
		Placement place = null;
		switch (opts.placement) {
		case Constants.PLACEMENT_RANDOM:
			place = new Placement.Random(opts.field);
			break;
		case Constants.PLACEMENT_GRID:
			place = new Placement.Grid(opts.field, opts.placementOpts);
			break;
		default:
			throw new RuntimeException("unknown node placement model");
		}

		// create each node
		for (int i = 1; i <= opts.nodes; i++) {

			boolean isClient;
			boolean isServer;
			Peer CANAtualPeer = new Peer();

			// alternate initializing servers and clients until there are numClients of each
			isServer = (opts.nodes - i <= opts.clients - 1);
			isClient = (i <= opts.clients);

			// radio
			RadioNoise radio = new RadioNoiseIndep(i, radioInfo);

			// mac
			MacDumb mac = new MacDumb(new MacAddress(i), radio.getRadioInfo());
			MacAddress macAddress = new MacAddress(i);

			// network
			final NetAddress address = new NetAddress(i);
			NetIp net = new NetIp(address, protMap, loss, loss);

			// routing
			RouteInterface route = null;
			switch (opts.protocol) {
			case Constants.NET_PROTOCOL_CAN:
				// CANAtualPeer = new Peer(address, macAddress);
				// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
				route = CANAtualPeer.getProxy();
				CANAtualPeer.setNetEntity(net.getProxy());
				canPeers.add(CANAtualPeer);
				break;
			case Constants.NET_PROTOCOL_DSR:
				RouteDsr dsr = new RouteDsr(address);
				route = dsr.getProxy();
				dsr.setNetEntity(net.getProxy());
				break;
			case Constants.NET_PROTOCOL_AODV:
				RouteAodv aodv = new RouteAodv(address);
				route = aodv.getProxy();
				aodv.setNetEntity(net.getProxy());
				aodv.getProxy().start();
				break;
			case Constants.NET_PROTOCOL_ZRP:
				// REGISTRANDO A QUANTIDADE DE N�S PADR�O
				registrar(2, "1");
				final boolean zdp = true;
				RouteZrp zrp = new RouteZrp(address, 2);
				zrp.setNetEntity(net.getProxy());
				zrp.getProxy().start();
				route = zrp.getProxy();
				final RouteInterface.Zrp.Iarp iarp = zdp ? (RouteInterface.Zrp.Iarp) new RouteZrpZdp(zrp, "inf")
						: (RouteInterface.Zrp.Iarp) new RouteZrpIarp(zrp, "inf");
				JistAPI.runAt(new Runnable() {
					public void run() {
						System.out.println("[CBR] " + address + ": links=" + iarp.getNumLinks() + " routes="
								+ iarp.getNumRoutes());
						iarp.showLinks();
						iarp.showRoutes();
					}
				}, (opts.endTime - 1) * Constants.SECOND + i);
				zrp.setSubProtocols(new RouteZrpNdp(zrp), iarp, new RouteZrpBrp(zrp), new RouteZrpIerp(zrp));
				break;
			default:
				throw new RuntimeException("invalid routing protocol");
			}

			// transport
			TransUdp udp = new TransUdp();

			// placement
			Location location = place.getNextLocation();
			field.addRadio(radio.getRadioInfo(), radio.getProxy(), location);

			// node entity hookup
			radio.setFieldEntity(field.getProxy());
			radio.setMacEntity(mac.getProxy());
			byte intId = net.addInterface(mac.getProxy());
			net.setRouting(route);
			mac.setRadioEntity(radio.getProxy());
			mac.setNetEntity(net.getProxy(), intId);
			udp.setNetEntity(net.getProxy());
			net.setProtocolHandler(Constants.NET_PROTOCOL_UDP, udp.getProxy());
			net.setProtocolHandler(opts.protocol, route);

			if (opts.protocol == Constants.NET_PROTOCOL_CAN) {
				JistAPI.sleep(10000000);

				Client client = new Client(udp.getProxy(), opts.transmissions, address,
						new NetAddress(opts.nodes - i + 1), (Peer) canPeers.get(i - 1));

				CANAtualPeer.startNodes();

			
			} else {

				// ******************************************************** COLETANDO O IP E
				// POSI��O DOS N�S QUE COMP�EM O PAR CLIENTE/SERVIDOR
				// **************************************************************************************//

				if (i == 1) {
					pair.ipOrigem = net.getAddress().toString();
					pair.locationOrigem = location;
				}
				if (i == opts.nodes) {
					pair.ipDestino = net.getAddress().toString();
					pair.locationDestino = location;
				}

				// ******************************************************** COLETANDO O IP E
				// POSI��O DOS N�S QUE COMP�EM O PAR CLIENTE/SERVIDOR
				// **************************************************************************************//
				// initialize client/server apps
				// if (isServer) {
//					Server server = new Server(udp.getProxy(), address);
//					servers.add(server.getProxy());
				// }
				// if (isClient) {
//					Client client = new Client(udp.getProxy(), opts.transmissions, address,
//							new NetAddress(1), null);

				
				
				
				
				
				CANAtualPeer = new Peer(address, macAddress, udp.getProxy());
				canPeers.add(CANAtualPeer);
				// clients.add(client.getProxy());

				// Sets the client that will do special commands in CAN
				MacAddress macAddressCANClientInsert = new MacAddress(3);
				if (macAddress.equals(macAddressCANClientInsert)) {
					CANInsertPeer = CANAtualPeer;
				}
				
				MacAddress macAddressCANClientLeave = new MacAddress(6);
				if (macAddress.equals(macAddressCANClientLeave)) {
					CANLeavePeer = CANAtualPeer;
				}
				
				MacAddress macAddressCANClientSearch = new MacAddress(9);
				if (macAddress.equals(macAddressCANClientSearch)) {
					CANSearchPeer = CANAtualPeer;
				}
				// }
			}
			
			
		}
 
	 

		
		
//		 start clients and servers
//		numClientsTransmitting = opts.clients;
//		Iterator serverIter = servers.iterator();
//		while (serverIter.hasNext())
//			((ServerInterface) serverIter.next()).run();
//		

//		Iterator clientIter = clients.iterator();
//		while (clientIter.hasNext()) {
//			JistAPI.sleep(999999999);
//			((ClientInterface) clientIter.next()).run();
//		}
//		

//		while (clientIter.hasNext())
//			((ClientInterface) clientIter.next()).sendMessage(2);;
//		

		Iterator canNodesIter = canPeers.iterator();
		
			 
 		while (canNodesIter.hasNext()) {

//			Peer noAtual = ((Peer) canNodesIter.next());
//			
//			noAtual.startNodes();
//			
//			JistAPI.sleep(20000 * Constants.SECOND);
//			    
//			  
//			for (int i=0 ; i < 4; i++) {
//				if (noAtual.getZone() == null) {
//					JistAPI.sleep(20000 * Constants.SECOND);
//					System.out.println("ESPERANDOESPERANDOESPERANDOESPERANDOESPERANDOESPERANDOESPERANDOESPERANDOESPERANDO");
//				} 
//			}
//			
//			System.out.println("A Zona do N� "+ noAtual.getHostName()+" � : "+ noAtual.getZone());
			 JistAPI.sleep(20000 * Constants.SECOND);
//			 JistAPI.sleep(70000 * Constants.SECOND);
//			 JistAPI.sleep(20000 * Constants.SECOND);
//			 JistAPI.sleep(20000 * Constants.SECOND);
			((Peer) canNodesIter.next()).startNodes();
			   
		}
 		
 		
 		//Execute CAN Operations
 		if (CANInsertPeer != null || CANLeavePeer  != null || CANSearchPeer != null) {
 			JistAPI.sleep(20000 * Constants.SECOND);
 			CANInsertPeer.insert(null);
 			JistAPI.sleep(20000 * Constants.SECOND);
 			CANLeavePeer.leave();
 			JistAPI.sleep(20000 * Constants.SECOND);
 			JistAPI.sleep(20000 * Constants.SECOND);
 			CANSearchPeer.search(null);
 			JistAPI.sleep(20000 * Constants.SECOND);
 		}
		
		

	} // buildField

	
	public void processa(Peer noAtual) {
		
	}
	/**
	 * Starts the CBR simulation.
	 *
	 * @param args command-line arguments that may determine the parameters of the
	 *             simulation
	 * @throws UnknownHostException
	 */
	public static void main(String[] args) throws UnknownHostException {
		try {
			CommandLineOptions options = parseCommandLineOptions(args);
			params = options;
			if (options.help) {
				showUsage();
				return;
			}
			if (options.endTime > 0) {
				JistAPI.endAt(options.endTime * Constants.SECOND);
			}
			Constants.random = new Random(options.randseed);
			buildField(options);
		} catch (CmdLineParser.OptionException e) {
			System.out.println(e.getMessage());

		}
	}

	public static void registrar(int tipo, String info) {

		switch (tipo) {

		case (1):

			coleta.RTT = info;
			break;

		case (2):
			coleta.qtdNos = info;
			break;

		case (3):
			coleta.qtdRetrs.add(info);
			break;

		case (4):
			coleta.distancia = info;
			break;

		}

	}

	public static void fimSimulacao() {
		// ************************************************ REGISTRANDO O TEMPO
		// DECORRIDO **********************************************//
		long stopTime = System.currentTimeMillis();
		long elapsedTime = stopTime - startTime;
		registrar(1, elapsedTime + "");
		System.out.println("Fim: " + stopTime);
		System.out.println("Decorrido = " + elapsedTime);

		// ************************************************ REGISTRANDO O TEMPO
		// DECORRIDO **********************************************//

		// ***************************************** REGISTRANDO A DIST�NCIA ENTRE OS
		// N�S **********************************************//
		registrar(4, pair.locationOrigem.distance(pair.locationDestino) + "");
		System.out.println("Origem: " + pair.locationOrigem);
		System.out.println("Destino: " + pair.locationDestino);
		// System.out.println("distancia: " +
		// pair.locationOrigem.distance(pair.locationDestino) + "");
		// ***************************************** REGISTRANDO A DIST�NCIA ENTRE OS
		// N�S **********************************************//

		CSVMaker csv = new CSVMaker();
		// roteamento-dimensao-# Nodes-loss-movement.csv
		String fileName = csv.fileNameFormat(params.protocol, params.field.getX(), params.field.getY(), params.nodes,
				params.lossOpts, params.mobilityOpts);
		csv.makeFile(coleta, fileName);
	}

} // class: CBR