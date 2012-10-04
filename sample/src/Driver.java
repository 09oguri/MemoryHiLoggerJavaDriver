import java.net.*;
import java.util.ArrayList;
import java.io.*;


public class Driver {
	
	static final String HOSTNAME = "192.168.172.2";
	static final int PORT = 8802;
	static final int INTERVAL = 10;	// (TODO) 遅延を少なくするように動的に設定
	
	// 計測するチャンネル数、ユニット数
	// 変更する場合はメモリハイロガーを再設定する必要がある
	// ユニット毎に別々のチャンネル数を設定できない
	static final int MAX_CH = 10;	// 最大14ch
	static final int MAX_UNIT = 1;	// 最大8unit
	
	private Socket theSocket;
	private InputStream is;
	private InputStreamReader isr;
	private BufferedReader br;
	private OutputStream os;
	
	private ArrayList<Double> volt = new ArrayList<Double>();	// 取得した電圧
	private ArrayList<Double> power = new ArrayList<Double>();	// 電圧から計算した消費電力
	
	private byte[] req;	// データ要求コマンド
	private byte[] raw;	// 電圧の生データ
	private int intrval = INTERVAL;
	private long count = 0;
	
	
	public static void main(String[] args) {
		Driver app = new Driver();
		app.communication();
	}
	
	
	// 初期化
	private void init() {
		try{
			theSocket = new Socket(HOSTNAME, PORT);
			theSocket.setSoTimeout(INTERVAL + 1000);
			
			// ソケットオープン時の応答処理
			is = theSocket.getInputStream();
			isr = new InputStreamReader(is);
			br = new BufferedReader(isr);
			while(is.available() == 0);
			char[] line = new char[is.available()];
			br.read(line);
			System.out.println(line);
			
			os = theSocket.getOutputStream();
			
			// データの取得間隔を設定
			if(INTERVAL == 10) {
				command(Command.SAMP_10ms);
			}else if(INTERVAL == 50) {
				command(Command.SAMP_50ms);
			}else {
				command(Command.SAMP_100ms);
			}
			
			// スタート
			readable(command(Command.START));
			Thread.sleep(1000);	// 状態が変化するのを待つ
			byte rec = (byte) 0xff;
			while(rec != 65){
				rec = getState(command(Command.REQUIRE_STATE));
			}
			rec = (byte) 0xff;
			
			// システムトリガー
			readable(command(Command.SYSTRIGGER));
			Thread.sleep(1000);
			while(rec != 35){
				rec = getState(command(Command.REQUIRE_STATE));
			}
			rec = (byte) 0xff;
			
			// データ要求
			req = Command.REQUIRE_DATA;
			readable(command(req));
			while(is.available() == 0);
			raw = new byte[is.available()];
			is.read(raw);
			
			Thread.sleep(INTERVAL);
		}catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	
	// 終了時の処理
	private void end() {
		if(theSocket != null) {
			try {
				// ストップ
				readable(command(Command.STOP));
				theSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	
	private void communication() {
		init();
		try{
			while(true) {
				getData();
				for(int i = 0; 0 < power.size(); i++) {
					System.out.println("消費電力" + (i + 1) + "：" + power.get(0) + "[W]");
					power.remove(0);
				}
				volt.clear();
			}
		}catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}finally {
			end();
		}
	}
	
	
	// コマンド実行
	private byte[] command(byte[] msg) {
		sendMsg(msg);
		return getMsg();
	}
	
	
	// MemoryHiLoggerから電圧データを受け取り消費電力を計算
	private void getData() throws InterruptedException, IOException {
		try {
			byte[] rec = command(req);	// データ要求コマンド
			/*
			 * 遅延解消
			if(getNumData(rec) - count > 100 && intrval > 0) {
				intrval -= 1;
			}else {
				intrval = INTERVAL;
				System.out.println(getNumData(rec) - count);
			}
			*/
			is.read(raw);	// データ転送コマンドを受け取る
			getVolt(raw);	// 受け取ったデータを電圧に変換
			getPower();	// 電圧から消費電力を計算
			incRequireCommand();	// データ要求時のサンプリング番号を１つ進める
			Thread.sleep(intrval);	// データの取得間隔
		}catch(Exception e) {
			e.printStackTrace();
			end();
			System.exit(1);
		}
	}
	
	
	//  MemoryHiLoggerにコマンドを送信
	private void sendMsg(byte[] msg) {
		try{
			os.write(msg);
			os.flush();
		}catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	
	// MemoryHiLoggerからコマンドを受信
	private byte[] getMsg() {
		try{
			while(is.available() == 0);
			byte[] rec = new byte[is.available()];
			is.read(rec);
			return rec;
		}catch(Exception e) {
			e.printStackTrace();
			end();
			System.exit(1);
			return null;
		}
	}
	
	
	// 生データから電圧リストを取得
	private void getVolt(byte[] rec) {
		String raw = "";
		int index = 21;
		if(rec[0] == 0x01 && rec[1] == 0x00 && rec[2] == 0x01) {	// データ転送コマンド
			for(int unit = 1; unit < 9; unit++) {
				for(int ch = 1; ch < 17; ch++) {
					for(int i = 0; i < 4; i++) {	// 個々の電圧
						if(ch <= MAX_CH && unit <= MAX_UNIT) {
							raw += String.format("%02x", rec[index]);
						}
						index++;
					}
					if(ch <= MAX_CH && unit <= MAX_UNIT) {
						// 電圧値に変換(スライドp47)
						// 電圧軸レンジ
						// 資料： 1(V/10DIV)
						// ロガーユーティリティ： 100 mv f.s. -> 0.1(V/10DIV)???
						volt.add(((double) Integer.parseInt(raw, 16) - 32768.0) * 0.1 / 20000.0);
					}
					raw = "";
				}
			}
		}else {	// データ転送コマンドでない場合
			volt = null;
		}
	}
	
	
	// 電圧リストから消費電力リストを取得
	private void getPower() {
		int voltListSize = volt.size();
		
		if(voltListSize % 2 != 0) {
			voltListSize--;
		}
		for(int i = 0; i < voltListSize; i += 2) {
			// (TODO) どっちのチャンネルが12Vか5Vなのかを判別できるようにする必要がある
			power.add(Math.abs(volt.get(i)) * 120.0 + Math.abs(volt.get(i + 1)) * 50.0);
		}
	}
	
	
	// MemoryHiLoggerの状態を取得
	private byte getState(byte[] rec) {
		if(rec[0] == 0x02 && rec[1] == 0x01) {
			switch(rec[2]) {
			case 0x50:	// スタートコマンド
			case 0x51:	// ストップコマンド
			case 0x57:	// 測定状態要求コマンド
			case 0x58:	// アプリシステムトリガコマンド
				return rec[5];
			}
		}
		return (byte) 0xff;	// 不明なコマンド
	}
	
	
	// メモリ内データ数を取得
	private long getNumData(byte[] rec) {
		String raw = "";
		if(rec[0] == 0x02 && rec[1] == 0x01) {
			switch(rec[2]) {
			case 0x55:
				for(int i = 13; i < 21; i++) {
					raw += String.format("%02x", rec[i]);
				}
				return Long.parseLong(raw, 16);
			}
		}
		return -1;
	}
	
	
	// データ要求コマンドのサンプリング番号のインクリメント
	private void incRequireCommand() {
		count++;
		for(int i = 12; i > 5; i--) {
			if(req[i] == 0xffffffff) {
				req[i] = 0x00000000;
				req[i - 1]++;
				if(req[i - 1] != 0xffffffff) {
					break;
				}
			}else if(req[5] == 0xffffffff) {
				return;
			}else {
				req[12]++;
				break;
			}
		}
	}
	
	
	// 読めるように表示する
	private void readable(byte[] rec) {
		String raw = "";
		if(rec.length < 1) {
			return;
		}
		switch(rec[0]) {
		case 0x01:
			switch(rec[1]) {
			case 0x00:
				switch(rec[2]) {
				case 0x01:
					raw = "";
					System.out.println("データ転送コマンド1st:" + System.currentTimeMillis());
					System.out.print("\tサンプリング番号:\t");
					for(int i = 5; i < 13; i++) {
						raw += String.format("%02x", rec[i]);
					}
					System.out.println(Integer.parseInt(raw, 16) + ": " + raw);
					raw = "";
					System.out.print("\tハンドシェイク時の残りデータ数:\t");
					for(int i = 13; i < 21; i++) {
						raw += String.format("%02x", rec[i]);
					}
					System.out.println(Integer.parseInt(raw, 16) + ": " + raw);
					int index = 21;
					raw = "";
					double volt = 0;
					for(int unit = 1; unit < 9; unit++) {
						for(int ch = 1; ch < 17; ch++) {
							if(ch <= MAX_CH && unit <= MAX_UNIT)
								System.out.print("\tU" + unit + "CH" + ch + ":\t");
							for(int i = 0; i < 4; i++) {	// 個々の電圧
								if(ch <= MAX_CH && unit <= MAX_UNIT) {
									raw += String.format("%02x", rec[index]);
								}
								index++;
							}
							if(ch <= MAX_CH && unit <= MAX_UNIT) {
								// 電圧値に変換(スライドp47)
								// 電圧軸レンジ
								// 	資料： 1(V/10DIV)
								//	ロガーユーティリティ： 100 mv f.s. -> 0.1(V/10DIV)???
								volt = ((double) Integer.parseInt(raw, 16) - 32768.0) * 0.1 / 20000.0;
								System.out.printf("%.5f [v]\n", volt);
							}
							raw = "";
						}
					}
					System.out.println("");
					return;
				default:
					for(int i = 0; i < rec.length; i++) {
						System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					break;	
				}
				break;
			default:
				for(int i = 0; i < rec.length; i++) {
					System.out.print(Integer.toHexString(rec[i]) + " ");
				}
				break;
			}
			break;
		case 0x02:
			switch(rec[1]) {
			case 0x01:
				switch(rec[2]) {
				case 0x50:
					System.out.print("スタートコマンド:\t");
					if(rec[5] == 0x000)
						System.out.println("OK");
					else if(rec[5] == 0x01)
						System.out.println("既にスタート中");
					else
						System.out.println("UNIT数が9個以上でスタートできません");
					return;
				case 0x51:
					System.out.print("ストップコマンド:\t");
					if(rec[5] == 0)
						System.out.println("OK");
					else
						System.out.println("NG");
					return;
				case 0x53:
					System.out.println("メモリ内先頭番号、データ数要求コマンド1st（高速）:");
					System.out.print("\tメモリ内先頭サンプリング番号:\t");
					for(int i = 5; i < 13; i++) {
						System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					System.out.println("");
					System.out.print("\tメモリ内データ数:\t");
					for(int i = 13; i < 21; i++){
						System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					System.out.println("");
					return;
				case 0x55:
					raw = "";
					System.out.println("データ要求コマンド1st:" +  + System.currentTimeMillis());
					System.out.print("\tメモリ内先頭サンプリング番号:\t");
					for(int i = 5; i < 13; i++) {
						raw += String.format("%02x", rec[i]);
					}
					System.out.println(Integer.parseInt(raw, 16) + ": " + raw);
					raw = "";
					System.out.print("\tメモリ内データ数:\t");
					for(int i = 13; i < 21; i++) {
						raw += String.format("%02x", rec[i]);
					}
					System.out.println(Integer.parseInt(raw, 16) + ": " + raw);
					raw = "";
					System.out.print("\tデータ転送サンプル番号:\t");
					for(int i = 21; i < 29; i++) {
						raw += String.format("%02x", rec[i]);
					}
					System.out.println(Integer.parseInt(raw, 16) + ": " + raw);
					raw = "";
					System.out.print("\tデータ数:\t");
					for(int i = 29; i < 37; i++) {
						raw += String.format("%02x", rec[i]);
					}
					System.out.println(Integer.parseInt(raw, 16) + ": " + raw);
					raw = "";
					System.out.print("\t測定状態:\t");
					System.out.println(Integer.toHexString(rec[37]));
					return;
				case 0x57:
					System.out.print("測定状態要求コマンド:\t");
					if(rec[5] == 0x00)
						System.out.println("スタート前またはスタート動作終わり");
					else if(rec[5] == 0x41)
						System.out.println("アプリシステムトリガ待ち");
					else if(rec[5] == 0x23)
						System.out.println("リアルタイムセーブ中");
					else
						System.out.println(Integer.toHexString(rec[5]));
					return;
				case 0x58:
					System.out.print("アプリシステムトリガコマンド:\t");
					if(rec[5] == 0)
						System.out.println("OK");
					else
						System.out.println("NG");
					return;
				case 0x5b:
					System.out.println("スタート状態とPCのMAC要求コマンド:");
					System.out.print("\tスタート状態:\t");
					if(rec[5] == 0)
						System.out.println("STOP");
					else
						System.out.println("START");
					System.out.print("\tMAC:\t");
					for(int i = 6; i < 12; i++) {
						System.out.print(Integer.toHexString(rec[i]) + ":");
					}
					System.out.println("");
					return;
				default:
					for(int i = 0; i < rec.length; i++) {
						System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					break;
				}
				break;
			case 0x20:
				switch(rec[2]) {
				case 0x01:
					System.out.print("記録間隔（高速側）:\t");
					if(rec[5] == 0x00)
						System.out.println("10ms");
					else if(rec[5] == 0x01)
						System.out.println("20ms");
					else if(rec[5] == 0x02)
						System.out.println("50ms");
					else if(rec[5] == 0x03)
						System.out.println("100ms");
					else
						System.out.println("200ms以上");
					return;
				default:
					break;
				}
				break;
			default:
				for(int i = 0; i < rec.length; i++) {
					System.out.print(Integer.toHexString(rec[i]) + " ");
				}
				break;
			}
			break;
		default:
			for(int i = 0; i < rec.length; i++) {
				System.out.print(Integer.toHexString(rec[i]) + " ");
			}
			break;
		}
		System.out.println("");
		return;
	}

}
