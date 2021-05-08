package csvMaker;

import java.util.HashMap;

import jist.runtime.JistAPI;
import jist.swans.Constants;

public class Recorder {

	private static final Recorder INSTANCE = new Recorder();
	private static long startTime;
	private static long elapsedTime;
	private static long stopTime;
	private static String fileName;
	private static HashMap content = new HashMap();
	private static int messagesAtBootstrapCount = 0;
	private static int messagesAtNodesCount = 0;
	private static int hopCount = 0;
	private static String protocol;
	public static int setZRPRadius = 0;
	public static boolean isRunning = false;
	public static int nodes; 
	public static int[] nodesList = new int[101];
	
	private Recorder() {
		
	}
	
	public static void setContent(String key, String value) {
		content.put(key, value);
	}
	
	public static void setMessagesAtBootstrapCount() {
		messagesAtBootstrapCount++;
	}
	
	public static void setMessagesAtNodesCount() {
		messagesAtNodesCount++;
	}
	
	public static void setHopCount(int hops) {
		hopCount += hops;
	}
	
	public static Recorder getInstance () {
		return INSTANCE;
	}
	
	public void startSimulation(String fileName) {
		this.fileName = fileName;
		this.isRunning = true;
		startTime = System.currentTimeMillis();
	}
	
	public void endSimulation() {
		stopTime = System.currentTimeMillis();
		recordFile();
	}
	
	public void recordFile() {
	
		elapsedTime = stopTime - startTime;
		System.out.println("Início: " + startTime);
		System.out.println("Fim: " + stopTime);
		System.out.println("Decorrido = " + elapsedTime);
		String strLong = Long.toString(elapsedTime);
		content.put("RTT", strLong);
		content.put("BootstrapMSGCount", messagesAtBootstrapCount+"");
		content.put("NodesMSGCount", messagesAtNodesCount+"");

		if (hopCount == 0) {
			hopCount = 1;
		}
		
		content.put("hopCount-"+this.protocol, hopCount+"");

		if (this.setZRPRadius > 0) {
			content.put("ZRPRadius", setZRPRadius+"");
		}
		
		CSVMaker csv = new CSVMaker();
		 
		 
		csv.makeFile(content, fileName);
	}
	
	
	public String fileNameFormat(int protocol, float fieldX, float fieldY, int nodes,String loss, String mobility) {
		String fileName = null;
		
				
		switch(protocol) {
		case (135):
			this.protocol = "DSR";
			break;
		case (123):
			this.protocol = "AODV";
			break;
		case (133):
			this.protocol = "ZPR";
			break;
		}
		
		//Formata o campo utilizado para a simulação
		int x = (int)fieldX;
		int y = (int)fieldY;
		
		//Formata o padrão de perda de pacótes
		if(loss == "") {
			loss = 0+"";
		}
		
		//Formata o padrão de mobilidade
		if(mobility == "") {
			mobility = 0+"";
		}
		
		return this.protocol+"-"+x+"x"+y+"-"+nodes+"-"+loss+'-'+mobility;
		
	}
	
	
}
