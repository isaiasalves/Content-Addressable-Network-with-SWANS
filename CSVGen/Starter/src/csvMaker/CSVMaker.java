package csvMaker;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public  class CSVMaker {

 
	
	public void makeFile(HashMap content, String fileName) {
	
//		HashMap table  = new HashMap();
//		table.put("RTT","666");
//		table.put("RX","69");
//		table.put("xT","13");
				
		Iterator header = content.entrySet().iterator();
		Iterator body = content.entrySet().iterator();
		    
     
		try { FileWriter myWriter = new FileWriter("D:\\Dropbox\\PGC\\PGC\\Source\\Swans_CAN\\Content-Addressable-Network-with-SWANS\\Simulacoes\\"+fileName+".csv");
		    //Escreve o cabeçalho do arquivo	
//			String cabecalho = "RTT(ms);Numero de Nos;Numero de Retransmissoes;Distancia(m)\n";
//			myWriter.write(cabecalho+dados.RTT+";"+dados.qtdNos+";"+dados.qtdRetrs.toArray().length+";"+dados.distancia);
			
			while (header.hasNext()) {
				 Map.Entry entry = (Entry) header.next();
				
				 myWriter.write((String) entry.getKey()+";");
			}
			myWriter.write("\n");
			while (body.hasNext()) {
				 Map.Entry entry = (Entry) body.next();
				
				 myWriter.write((String) entry.getValue()+";");
			}
			
			myWriter.close(); 
			
			System.out.println();
			System.out.println("Successfully wrote to the file."); }
		catch (IOException e) { System.out.println("An error occurred.");
			e.printStackTrace(); }
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
