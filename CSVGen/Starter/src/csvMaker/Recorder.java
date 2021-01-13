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
		System.out.println("In�cio: " + startTime);
		System.out.println("Fim: " + stopTime);
		System.out.println("Decorrido = " + elapsedTime);

		// ************************************************ REGISTRANDO O TEMPO
		// DECORRIDO **********************************************//

		// ***************************************** REGISTRANDO A DIST�NCIA ENTRE OS
		// N�S **********************************************//
		//registrar(4, pair.locationOrigem.distance(pair.locationDestino) + "");
//		System.out.println("Origem: " + pair.locationOrigem);
//		System.out.println("Destino: " + pair.locationDestino);
		// System.out.println("distancia: " +
		// pair.locationOrigem.distance(pair.locationDestino) + "");
		// ***************************************** REGISTRANDO A DIST�NCIA ENTRE OS
		// N�S **********************************************//

		//CSVMaker csv = new CSVMaker();
		// roteamento-dimensao-# Nodes-loss-movement.csv
		 
		//csv.makeFile(coleta, fileName);
	}
	
	
	public String fileNameFormat(int protocol, float fieldX, float fieldY, int nodes,String loss, String mobility) {
		String fileName = null;
		
		//Formata o protoc�lo utilizado
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
		
		//Formata o campo utilizado para a simula��o
		int x = (int)fieldX;
		int y = (int)fieldY;
		
		//Formata o padr�o de perda de pac�tes
		if(loss == "") {
			loss = 0+"";
		}
		
		//Formata o padr�o de mobilidade
		if(mobility == "") {
			mobility = 0+"";
		}
		
		return protocolo+"-"+x+"x"+y+"-"+nodes+"-"+loss+'-'+mobility;
		
	}
	
	
}
