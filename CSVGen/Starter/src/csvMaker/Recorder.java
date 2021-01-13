package csvMaker;

import jist.runtime.JistAPI;
import jist.swans.Constants;

public class Recorder {

	private static final Recorder INSTANCE = new Recorder();
	private static long startTime;
	private static long elapsedTime;
	private static long stopTime;
	private static String fileName;
	
	private Recorder() {
		
	}
	
	public static Recorder getInstance () {
		return INSTANCE;
	}
	
	public void startSimulation(String fileName) {
		this.fileName = fileName;
		startTime = System.currentTimeMillis();
	}
	
	public void endSimulation() {
		stopTime = System.currentTimeMillis();
		recordFile();
		//DEVE CHAMAR A CLASSE PARA ESCREVER NO ARQUIVO INFORMANDO OS VALORES OBTIDOS
	}
	
	public void recordFile() {
		JistAPI.sleep(1000);
		// ************************************************ REGISTRANDO O TEMPO DECORRIDO **********************************************//
		elapsedTime = stopTime - startTime;
		System.out.println("Início: " + startTime);
		System.out.println("Fim: " + stopTime);
		System.out.println("Decorrido = " + elapsedTime);

		// ************************************************ REGISTRANDO O TEMPO
		// DECORRIDO **********************************************//

		// ***************************************** REGISTRANDO A DISTÂNCIA ENTRE OS
		// NÓS **********************************************//
		//registrar(4, pair.locationOrigem.distance(pair.locationDestino) + "");
//		System.out.println("Origem: " + pair.locationOrigem);
//		System.out.println("Destino: " + pair.locationDestino);
		// System.out.println("distancia: " +
		// pair.locationOrigem.distance(pair.locationDestino) + "");
		// ***************************************** REGISTRANDO A DISTÂNCIA ENTRE OS
		// NÓS **********************************************//

		//CSVMaker csv = new CSVMaker();
		// roteamento-dimensao-# Nodes-loss-movement.csv
		 
		//csv.makeFile(coleta, fileName);
	}
	
	
	public String fileNameFormat(int protocol, float fieldX, float fieldY, int nodes,String loss, String mobility) {
		String fileName = null;
		
		//Formata o protocólo utilizado
		String protocolo = null;
		switch(protocol) {
		case (135):
			protocolo = "DSR";
			break;
		case (123):
			protocolo = "AODV";
			break;
		case (133):
			protocolo = "ZPR";
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
		
		return protocolo+"-"+x+"x"+y+"-"+nodes+"-"+loss+'-'+mobility;
		
	}
	
	
}
