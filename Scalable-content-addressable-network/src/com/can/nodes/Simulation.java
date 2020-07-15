package com.can.nodes;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.can.connections.RevisedReceive;
import com.can.connections.RevisedSend;
import com.can.exceptions.ClosestNeighbourUnavailableException;
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

import jist.swans.net.NetAddress;
 

public class Simulation
{

  public static void main(String[] args) throws UnknownHostException, InterruptedException {
	  System.out.println("MAIN SIMULATION");
	  Peer p = new Peer();
 	  p.heart();
 	  p.directStart("JOIN");
	  
	  
	  
	  
	  
//	  String[] arg = new String[10];
//	  arg[0] = "JOIN";
//	  p.main(arg);
  }
  
  public static void EITA() throws UnknownHostException {
	  System.out.println("AKI NA SIMULATION");
	  Peer p = new Peer();
	  p.heart();
  }

} // class: CBR

