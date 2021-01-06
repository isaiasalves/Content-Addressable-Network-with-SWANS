package com.can.utilities;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import com.can.exceptions.ClosestNeighbourUnavailableException;
import com.can.serializables.Coordinate;
import com.can.serializables.Zone;
import com.sun.istack.internal.Nullable;

public class Utils {

	public synchronized static void printErrorMessage(String message){

		System.err.println(message);
	}

	public synchronized static void printToConsole(String message){

		System.out.println(message);
	}


	public static double computeDistance(Zone zone1, Coordinate destCoord){
		double dist;

		double lowX1 = zone1.getStartX();
		double highX1 = zone1.getEndX();

		double lowY1 = zone1.getStartY();
		double highY1 = zone1.getEndY();
		
		
		double XA = 0;
		double XB = 0;
		double YA = 0;
		double YB = 0;

		/*
		 * computing mid point in the zones
		 */
		double x1 = lowX1 + Math.abs(lowX1 - highX1)/2;
		double x2 = destCoord.getXCoord();
		double y1 = lowY1 + Math.abs(lowY1 - highY1)/2;
		double y2 = destCoord.getYCoord();

		//dist = Math.sqrt(Math.pow((x1-x2),2) + Math.pow((y1-y2), 2));

		dist = 0;
		
		//Ponto no espa�o de X
		if (zone1.getStartX() <= destCoord.getXCoord() && zone1.getEndX() >= destCoord.getXCoord()) {
			//calcula a dist�ncia como uma diferen�a apenas de Y
			dist =  Math.min(Math.abs(destCoord.getYCoord() - zone1.getStartY()), Math.abs(destCoord.getYCoord()-zone1.getEndY()));
		}
		
		//Ponto no espa�o de Y
		if (zone1.getStartY() <= destCoord.getYCoord() && zone1.getEndY() >= destCoord.getYCoord()) {
			//calcula a dist�ncia como uma diferen�a apenas em X
			dist = Math.min(Math.abs(destCoord.getXCoord() - zone1.getStartX()), Math.abs(destCoord.getXCoord()-zone1.getEndX()));
		}
		
		//Ser� necess�rio calcular a dist�ncia entre dois pontos
		if ( dist ==0 ) {
			
			//Definindo as vari�veis: XA e XB
			if (destCoord.getXCoord() > highX1 ) {
				XA = highX1;
				XB = destCoord.getXCoord();
			} else {
				XA = destCoord.getXCoord();
				XB = lowX1;
			}
			
			//Definindo as vari�veis: YA e YB 
			if (destCoord.getYCoord() > highY1) {
				YA = highY1;
				YB = destCoord.getYCoord();
			} else {
				YA = destCoord.getYCoord();
				YB = lowY1;
			}
			
			//Pitagoras
			if (XA > 0 && XB  > 0 && YA > 0 && YB > 0 ) {
				dist = Math.sqrt(Math.pow((XB - XA), 2) + Math.pow((YB - YA), 2));
			} else {
				System.err.println("ERROR WHEN TRYING TO FIND SHORTER PATH TO POINT");
				 
			}
		}
		
		
		return dist;
	}

	public static Coordinate mapKeyToCoordinate(String keyword){

		double xCoord = 0.0;
		double yCoord = 0.0;
		Coordinate mappedCoordinate;

		char[] charArray = keyword.toCharArray();

		for(int i = 0;i < charArray.length;i++){

			if(i%2 == 0){

				yCoord += charArray[i];
			}
			else{

				xCoord += charArray[i];
			}
		}

		xCoord = xCoord%10;
		yCoord = yCoord%10;
		mappedCoordinate = new Coordinate(xCoord, yCoord);
		return mappedCoordinate;
	}


	public static InetAddress getIpAddress(String hostName) throws UnknownHostException{

		InetAddress address = Inet4Address.getByName(hostName);
		return address;

	}

}
