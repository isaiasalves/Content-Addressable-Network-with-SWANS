package csvMaker;

import java.util.ArrayList;

public class Structure {

	public String RTT; ///1
	
	public String qtdNos; ///2
	
	public ArrayList qtdRetrs = new ArrayList(); ///3
	
	public String distancia; ///4

	
	
	
	public String getRTT() {
		return RTT;
	}

	public void setRTT(String rTT) {
		RTT = rTT;
	}

	public String getQtdNos() {
		return qtdNos;
	}

	public void setQtdNos(String qtdNos) {
		this.qtdNos = qtdNos;
	}

	public ArrayList getQtdRetrs() {
		return qtdRetrs;
	}

	public void setQtdRetrs(String qtdRetrs) {
		this.qtdRetrs.add(qtdRetrs);
	}

	public String getDistancia() {
		return distancia;
	}

	public void setDistancia(String distancia) {
		this.distancia = distancia;
	}
	
	
	
 
}
